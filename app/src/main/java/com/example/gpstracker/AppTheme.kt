package com.example.gpstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.colorResource

/**
 * REQ-10: diagonal red → purple → blue gradient behind every screen (the same colours as the launcher icon),
 * with white text and controls so they stay readable on it.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val purple = colorResource(R.color.gradient_purple)
    val colors = darkColorScheme(
        primary = Color.White,
        onPrimary = purple,
        secondaryContainer = Color.White.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        surface = Color.Transparent,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        outline = Color.White.copy(alpha = 0.7f),
        outlineVariant = Color.White.copy(alpha = 0.3f),
    )
    // linearGradient defaults to top-left → bottom-right.
    val gradient = Brush.linearGradient(gradientColors())
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(modifier = Modifier.fillMaxSize().background(gradient).safeDrawingPadding()) {
                content()
            }
        }
    }
}

/**
 * REQ-17: the opacity of the run card laid over the map: very slightly see-through. Over the gradient of the other
 * views it cannot be seen.
 */
const val OVERLAY_ALPHA = 0.9f

/** REQ-17: the opacity of the bars along the top and the bottom, more see-through than the run card. */
const val BAR_ALPHA = 0.7f

@Composable
private fun gradientColors() =
    listOf(colorResource(R.color.gradient_red), colorResource(R.color.gradient_purple), colorResource(R.color.gradient_blue))

/**
 * REQ-17: fills the element with the part of the screen gradient (REQ-10) that lies behind it, so an overlay has
 * exactly the colour of the bar of icons next to it; [alpha] below 1 lets what is under it show through.
 */
@Composable
fun Modifier.screenGradientBackground(alpha: Float = 1f): Modifier {
    val colors = gradientColors()
    var position by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned {
        position = it.positionInRoot()
        val root = it.findRootCoordinates().size
        screen = Offset(root.width.toFloat(), root.height.toFloat())
    }.drawBehind {
        drawRect(Brush.linearGradient(colors, start = -position, end = screen - position), alpha = alpha)
    }
}
