package com.plexbubble.app.overlay

import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Verified
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plexbubble.app.R
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class RecentRatingItemUi(
    val title: String,
    val artist: String?,
    val stars0to5: Float,
    val artUrl: String?
)

/** The small floating circular bubble showing the Plex glyph. */
@Composable
fun BubbleContent(bubbleAlpha: Float, sizeDp: Int = 56, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = bubbleAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bubble_logo_readable),
            contentDescription = "Plex bubble",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size((sizeDp * 0.92f).dp)
        )
    }
}

/** Expanded panel: current track, star rating, and a transparency slider. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RatingPanelContent(
    panelAlpha: Float,
    accentColor: Color,
    trackTitle: String?,
    trackArtist: String?,
    trackAlbum: String?,
    trackYear: Int?,
    trackArtUrl: String?,
    isPlaying: Boolean,
    rating: Float,
    onRatingPreview: (Float) -> Unit,
    onRatingCommit: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    ratingPresets: List<Float>,
    onPresetRating: (Float) -> Unit,
    onUndoLastRating: () -> Unit,
    canUndo: Boolean,
    transparencyPercent: Int,
    onTransparencyChange: (Int) -> Unit,
    showRecentRatings: Boolean,
    recentRatings: List<RecentRatingItemUi>,
    onClose: () -> Unit,
    statusMessage: String? = null
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var introAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        introAnimated = true
    }
    val panelScale by animateFloatAsState(
        targetValue = if (introAnimated) 1f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "panelScale"
    )
    val saveConfirmed = statusMessage == "Rating saved"
    val albumYearLine = when {
        !trackAlbum.isNullOrBlank() && (trackYear ?: 0) > 0 -> "${trackAlbum} (${trackYear})"
        !trackAlbum.isNullOrBlank() -> trackAlbum
        (trackYear ?: 0) > 0 -> "(${trackYear})"
        else -> null
    }

    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 300.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF202126).copy(alpha = panelAlpha),
                        Color(0xFF141519).copy(alpha = panelAlpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((4 * panelScale).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0f),
                            accentColor.copy(alpha = 0.72f),
                            accentColor.copy(alpha = 0f)
                        )
                    )
                )
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0E11))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = trackArtUrl,
                    contentDescription = "Album",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.bubble_logo_readable),
                    error = painterResource(id = R.drawable.bubble_logo_readable),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle ?: "Nothing playing",
                    color = Color(0xFFF7F8FA),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (trackArtist != null) {
                    Text(
                        text = trackArtist,
                        color = Color(0xFFB8BCC7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (albumYearLine != null) {
                    Text(
                        text = albumYearLine,
                        color = Color(0xFFA2A8B8),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isPlaying) {
                    "Playing now"
                } else if (!trackTitle.isNullOrBlank() && trackTitle != "Nothing playing") {
                    "Paused"
                } else {
                    "Not playing"
                },
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                accentColor = accentColor,
                onClick = onPrevious,
                onLongClick = onSeekBack,
                repeatOnLongPress = true
            )
            TransportControlButton(
                modifier = Modifier.weight(1f),
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                accentColor = accentColor,
                onClick = onPlayPause,
                onLongClick = onPlayPause
            )
            TransportControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next",
                accentColor = accentColor,
                onClick = onNext,
                onLongClick = onSeekForward,
                repeatOnLongPress = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        StarRatingControl(
            rating = rating,
            onRatingPreview = onRatingPreview,
            onRatingCommit = onRatingCommit,
            modifier = Modifier.fillMaxWidth(),
            filledColor = accentColor
        )

        Spacer(modifier = Modifier.height(8.dp))
        RatingValueLabel(
            rating = rating,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        if (ratingPresets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Quick presets",
                color = Color(0xFFB8BCC7),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ratingPresets.forEach { preset ->
                    Text(
                        text = if (preset % 1f == 0f) preset.toInt().toString() else preset.toString(),
                        color = Color(0xFFEDEFF5),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable { onPresetRating(preset) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .clickable { showAdvanced = !showAdvanced }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced",
                color = Color(0xFFE7E9F0),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = accentColor
            )
        }

        if (showAdvanced) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Bubble transparency",
                    color = Color(0xFFB8BCC7),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = transparencyPercent.toFloat(),
                    onValueChange = { onTransparencyChange(it.toInt()) },
                    valueRange = 5f..100f
                )

                if (showRecentRatings && recentRatings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Recent",
                        color = Color(0xFFB8BCC7),
                        style = MaterialTheme.typography.labelSmall
                    )
                    recentRatings.take(3).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.artUrl,
                                contentDescription = "Recent track art",
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.bubble_logo_readable),
                                error = painterResource(id = R.drawable.bubble_logo_readable),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color(0xFFDDE1EB),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist ?: "Unknown artist",
                                    color = Color(0xFFB8BCC7),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${item.stars0to5}/5",
                                color = Color(0xFFB8BCC7),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = canUndo) {
            TextButton(onClick = onUndoLastRating) {
                Text("Undo last rating", color = accentColor)
            }
        }

        AnimatedVisibility(visible = saveConfirmed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF39D98A), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Saved to Plex",
                    color = Color(0xFF9DF0C7),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (statusMessage != null && statusMessage != "Rating saved") {
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedContent(targetState = statusMessage, label = "statusMessage") { message ->
                Text(
                    text = message,
                    color = Color(0xFFD5D9E3),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransportControlButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    repeatOnLongPress: Boolean = false
) {
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.2f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .pointerInput(onClick, onLongClick, repeatOnLongPress, longPressTimeoutMs) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            var longPressed = false
                            val longPressJob = launch {
                                kotlinx.coroutines.delay(longPressTimeoutMs.toLong())
                                longPressed = true
                                onLongClick()
                                if (repeatOnLongPress) {
                                    while (true) {
                                        kotlinx.coroutines.delay(180)
                                        onLongClick()
                                    }
                                }
                            }

                            val released = tryAwaitRelease()
                            longPressJob.cancel()
                            if (released && !longPressed) {
                                onClick()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(0xFFEFF2FA),
            modifier = Modifier.size(18.dp)
        )
    }
}
