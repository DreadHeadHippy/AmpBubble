package com.plexbubble.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plexbubble.app.R

/** The small floating circular bubble showing the Plex glyph. */
@Composable
fun BubbleContent(bubbleAlpha: Float, sizeDp: Int = 56, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xFF282A2D).copy(alpha = bubbleAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plex_bubble),
            contentDescription = "Plex bubble",
            tint = Color(0xFFE5A00D),
            modifier = Modifier.size((sizeDp * 0.5f).dp)
        )
    }
}

/** Expanded panel: current track, star rating, and a transparency slider. */
@Composable
fun RatingPanelContent(
    panelAlpha: Float,
    trackTitle: String?,
    trackArtist: String?,
    rating: Float,
    onRatingChange: (Float) -> Unit,
    transparencyPercent: Int,
    onTransparencyChange: (Int) -> Unit,
    onClose: () -> Unit,
    statusMessage: String? = null
) {
    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1C1E).copy(alpha = panelAlpha))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(220.dp)) {
                Text(
                    text = trackTitle ?: "Nothing playing",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (trackArtist != null) {
                    Text(text = trackArtist, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        StarRatingControl(
            rating = rating,
            onRatingChange = onRatingChange,
            modifier = Modifier
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Bubble transparency", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = transparencyPercent.toFloat(),
            onValueChange = { onTransparencyChange(it.toInt()) },
            valueRange = 5f..100f
        )

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = statusMessage, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
