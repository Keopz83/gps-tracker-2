package com.example.gpstracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.RecordAction
import com.example.gpstracker.domain.SessionState
import com.example.gpstracker.domain.recordAction

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

private val CHOICE_GAP = 12.dp

/**
 * REQ-24: the round button in the middle of the bottom bar, larger than the icons beside it: a record icon while no
 * session is active, which shows the activity buttons of the front view, smaller, stacked above it; pause while running
 * and resume while paused.
 */
@Composable
fun RecordButton(state: SessionState, onStart: (ActivityType) -> Unit, size: Dp = 60.dp) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    val action = recordAction(state)
    Box {
        IconButton(
            onClick = {
                when (action) {
                    RecordAction.START -> choosing = true
                    RecordAction.PAUSE -> TrackingService.pause(context)
                    RecordAction.RESUME -> TrackingService.resume(context)
                }
            },
            modifier = Modifier.size(size).background(Color.White.copy(alpha = 0.3f), CircleShape),
        ) {
            val (icon, label) = when (action) {
                RecordAction.START -> Icons.Filled.FiberManualRecord to "Record"
                RecordAction.PAUSE -> Icons.Filled.Pause to "Pause"
                RecordAction.RESUME -> Icons.Filled.PlayArrow to "Resume"
            }
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size / 2))
        }
        // The activity buttons of the front view (REQ-1), smaller, stacked above the record button. They slide up out of
        // it and back down into it, so the popup stays until they are gone.
        val shown = remember { MutableTransitionState(false) }
        shown.targetState = choosing && action == RecordAction.START
        if (shown.currentState || shown.targetState) {
            val lift = with(LocalDensity.current) { (size + CHOICE_GAP).roundToPx() }
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -lift),
                onDismissRequest = { choosing = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = shown,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(CHOICE_GAP)) {
                        ActivityType.entries.forEach { type ->
                            IconButton(
                                onClick = {
                                    choosing = false
                                    onStart(type)
                                },
                                // Solid underneath, so the buttons stay readable over the map.
                                modifier = Modifier
                                    .size(size)
                                    .background(colorResource(R.color.gradient_purple), CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            ) {
                                Icon(type.icon, contentDescription = "Start ${type.label}", tint = Color.White, modifier = Modifier.size(size * 4 / 7))
                            }
                        }
                    }
                }
            }
        }
    }
}
