package com.ampbubble.app.overlay

import android.os.SystemClock
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Verified
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import com.ampbubble.app.R
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
            painter = painterResource(id = R.drawable.app_logo_mark),
            contentDescription = "App bubble",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size((sizeDp * 0.92f).dp)
        )
    }
}

/** Expanded panel: current track, playback controls, and star rating. */
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
    trackArtBitmap: ImageBitmap? = null,
    isPlaying: Boolean,
    playbackPositionMs: Long?,
    playbackPositionUpdatedAtMs: Long?,
    durationMs: Long?,
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
    showRecentRatings: Boolean,
    recentRatings: List<RecentRatingItemUi>,
    statusMessage: String? = null
) {
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
                .shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A2C33), Color(0xFF1B1C21))
                    )
                )
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(8.dp, RoundedCornerShape(16.dp), clip = false)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF14151A), Color(0xFF0A0B0E))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (trackArtBitmap != null) {
                        Image(
                            bitmap = trackArtBitmap,
                            contentDescription = "Album",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        AsyncImage(
                            model = trackArtUrl,
                            contentDescription = "Album",
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.app_logo_mark),
                            error = painterResource(id = R.drawable.app_logo_mark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                    // Soft glass highlight to keep the tile from reading as a flat black void while art loads.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(120f, 180f)
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = trackTitle ?: "Nothing playing",
                    color = Color(0xFFF7F8FA),
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (trackArtist != null) {
                    Text(
                        text = trackArtist,
                        color = Color(0xFFC7CBD6),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (albumYearLine != null) {
                    Text(
                        text = albumYearLine,
                        color = Color(0xFF9BA1B0),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        PlaybackProgressIndicator(
            playbackPositionMs = playbackPositionMs,
            playbackPositionUpdatedAtMs = playbackPositionUpdatedAtMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

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

        if (ratingPresets.isNotEmpty() || saveConfirmed) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ratingPresets.isNotEmpty()) {
                    Text(
                        text = "Quick presets",
                        color = Color(0xFFB8BCC7),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                AnimatedVisibility(visible = saveConfirmed, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF39D98A), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rating saved",
                            color = Color(0xFF9DF0C7),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (ratingPresets.isNotEmpty()) {
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
        }

        if (showRecentRatings && recentRatings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
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
                        placeholder = painterResource(id = R.drawable.app_logo_mark),
                        error = painterResource(id = R.drawable.app_logo_mark),
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
                        text = formatStarRating(item.stars0to5),
                        color = Color(0xFFB8BCC7),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (statusMessage != null && !saveConfirmed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(top = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = statusMessage,
                    color = Color(0xFFD5D9E3),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

    }
}

@Composable
private fun PlaybackProgressIndicator(
    playbackPositionMs: Long?,
    playbackPositionUpdatedAtMs: Long?,
    durationMs: Long?,
    isPlaying: Boolean,
    accentColor: Color
) {
    if (playbackPositionMs == null || durationMs == null || durationMs <= 0L) return

    var displayedPositionMs by remember(playbackPositionMs, playbackPositionUpdatedAtMs, isPlaying) {
        mutableStateOf(playbackPositionMs)
    }
    LaunchedEffect(playbackPositionMs, playbackPositionUpdatedAtMs, isPlaying, durationMs) {
        while (isPlaying) {
            val updatedAt = playbackPositionUpdatedAtMs ?: SystemClock.elapsedRealtime()
            displayedPositionMs = (playbackPositionMs + SystemClock.elapsedRealtime() - updatedAt)
                .coerceIn(0L, durationMs)
            delay(500)
        }
    }
    if (!isPlaying) displayedPositionMs = playbackPositionMs.coerceIn(0L, durationMs)

    val progress = (displayedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val showLabels = maxWidth >= 260.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showLabels) {
                Text(
                    text = formatPlaybackTime(displayedPositionMs),
                    color = Color(0xFFB8BCC7),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            LinearProgressIndicator(
                progress = { progress },
                color = accentColor,
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier.weight(1f).height(3.dp)
            )
            if (showLabels) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatPlaybackTime(durationMs),
                    color = Color(0xFFB8BCC7),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatPlaybackTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
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
