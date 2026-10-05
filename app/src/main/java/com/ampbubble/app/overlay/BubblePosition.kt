package com.ampbubble.app.overlay

internal data class BubbleSafeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun clampBubblePosition(
    x: Int,
    y: Int,
    bubbleWidth: Int,
    bubbleHeight: Int,
    bounds: BubbleSafeBounds
): Pair<Int, Int> {
    val maxX = (bounds.right - bubbleWidth).coerceAtLeast(bounds.left)
    val maxY = (bounds.bottom - bubbleHeight).coerceAtLeast(bounds.top)
    return x.coerceIn(bounds.left, maxX) to y.coerceIn(bounds.top, maxY)
}
