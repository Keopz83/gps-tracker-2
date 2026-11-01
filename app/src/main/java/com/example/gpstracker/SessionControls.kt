package com.example.gpstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gpstracker.domain.SessionState

/**
 * REQ-1: the controls of an active session as round icon buttons: pause (resume while paused) and stop.
 * Shown on the front view and, smaller, on the map (REQ-17).
 */
@Composable
fun SessionControls(state: SessionState, modifier: Modifier = Modifier, size: Dp = 80.dp) {
    val context = LocalContext.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(size / 3)) {
        if (state == SessionState.PAUSED) {
            ControlButton(Icons.Filled.PlayArrow, "Resume", size) { TrackingService.resume(context) }
        } else {
            ControlButton(Icons.Filled.Pause, "Pause", size) { TrackingService.pause(context) }
        }
        ControlButton(Icons.Filled.Stop, "Stop", size) { TrackingService.stop(context) }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}
