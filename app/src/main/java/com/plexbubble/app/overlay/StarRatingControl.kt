package com.plexbubble.app.overlay

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A 5-star control supporting half-star precision via tap position and horizontal drag.
 * [rating] is in the 0f..5f range.
 * [onRatingPreview] updates visual state while dragging.
 * [onRatingCommit] is fired when the user commits a rating (tap or drag release).
 */
@Composable
fun StarRatingControl(
    rating: Float,
    onRatingPreview: (Float) -> Unit,
    onRatingCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    starSizeDp: Int = 32,
    filledColor: Color = Color(0xFFE5A00D),
    emptyColor: Color = Color(0x66FFFFFF)
) {
    val starCount = 5
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
            detectTapGestures { offset ->
                val starWidthPx = size.width / starCount
                val starIndex = (offset.x / starWidthPx).toInt().coerceIn(0, starCount - 1)
                val withinStarFraction = (offset.x - starIndex * starWidthPx) / starWidthPx
                val half = if (withinStarFraction < 0.5f) 0.5f else 1f
                val newRating = (starIndex + half).coerceIn(0f, starCount.toFloat())
                onRatingPreview(newRating)
                onRatingCommit(newRating)
            }
            }
            .pointerInput(Unit) {
            var dragRating = rating
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, _ ->
                    val starWidthPx = size.width / starCount
                    val raw = (change.position.x / starWidthPx).coerceIn(0f, starCount.toFloat())
                    val snapped = (ceil(raw * 2) / 2f).coerceIn(0f, starCount.toFloat())
                    dragRating = snapped
                    onRatingPreview(snapped)
                },
                onDragEnd = {
                    onRatingCommit(dragRating)
                }
            )
        }
    ) {
        for (i in 0 until starCount) {
            val starValue = rating - i
            val icon = when {
                starValue >= 1f -> Icons.Filled.Star
                starValue >= 0.5f -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            val tint = if (starValue > 0f) filledColor else emptyColor
            val active = starValue > 0f
            val scale = animateFloatAsState(
                targetValue = if (active) 1f else 0.93f,
                animationSpec = spring(stiffness = 500f),
                label = "starScale$i"
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .scale(scale.value)
                    .size(starSizeDp.dp)
            )
        }
    }
}

@Composable
fun RatingValueLabel(rating: Float, modifier: Modifier = Modifier) {
    val rounded = (rating * 2).roundToInt() / 2f
    Text(text = "$rounded / 5", color = Color(0xFFC8CDD9), modifier = modifier)
}
