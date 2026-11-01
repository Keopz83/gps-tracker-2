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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val gradient = Brush.linearGradient(
        listOf(colorResource(R.color.gradient_red), purple, colorResource(R.color.gradient_blue)),
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(modifier = Modifier.fillMaxSize().background(gradient).safeDrawingPadding()) {
                content()
            }
        }
    }
}
