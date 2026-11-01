package com.example.gpstracker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.GPX_MIME_TYPE
import com.example.gpstracker.domain.SessionState
import com.example.gpstracker.domain.canStartTracking
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatSpeed
import com.example.gpstracker.domain.hasRoute
import com.example.gpstracker.domain.requiredPermissions
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingRepository.loadHistory(filesDir) // REQ-9
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackerScreen()
                }
            }
        }
    }
}

@Composable
private fun TrackerScreen() {
    val context = LocalContext.current
    val stats by TrackingRepository.stats.collectAsStateWithLifecycle()
    val lastRun by TrackingRepository.lastRun.collectAsStateWithLifecycle()
    val history by TrackingRepository.history.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.JOGGING) }
    val state = stats?.state ?: SessionState.IDLE
    val active = state == SessionState.RUNNING || state == SessionState.PAUSED
    var showMap by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // REQ-6: ask for permissions, then start the foreground service.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (canStartTracking(granted)) {
            TrackingService.start(context, selectedType)
        } else {
            Toast.makeText(context, "Precise location permission is required to track", Toast.LENGTH_LONG).show()
        }
    }

    // REQ-7: the map follows the live stats, so the path keeps growing while it is open.
    val track = stats?.track.orEmpty()
    if (showMap) {
        RouteMapScreen(track = track, onClose = { showMap = false })
        return
    }

    // REQ-9: the record of finished runs.
    if (showHistory) {
        RunHistoryScreen(runs = history, onClose = { showHistory = false })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        // REQ-1: activity type selection, locked while a session is active.
        SingleChoiceSegmentedButtonRow {
            ActivityType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = (if (active) stats?.activityType else selectedType) == type,
                    onClick = { selectedType = type },
                    enabled = !active,
                    shape = SegmentedButtonDefaults.itemShape(index, ActivityType.entries.size),
                ) { Text(type.label) }
            }
        }

        val shown = stats
        val type = shown?.activityType ?: selectedType
        val elapsed = shown?.elapsedMillis ?: 0L
        val distance = shown?.distanceMeters ?: 0.0
        Text(formatDuration(elapsed), style = MaterialTheme.typography.displayLarge) // REQ-4
        Text(formatDistance(distance), style = MaterialTheme.typography.headlineMedium) // REQ-3, REQ-5
        Text(formatSpeed(type, distance, elapsed), style = MaterialTheme.typography.headlineMedium) // REQ-5
        if (state == SessionState.PAUSED) Text("Paused")

        // REQ-1: session controls.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                SessionState.IDLE, SessionState.STOPPED -> Button(
                    onClick = { permissionLauncher.launch(requiredPermissions(Build.VERSION.SDK_INT).toTypedArray()) },
                ) { Text("Start") }
                SessionState.RUNNING -> {
                    Button(onClick = { TrackingService.pause(context) }) { Text("Pause") }
                    OutlinedButton(onClick = { TrackingService.stop(context) }) { Text("Stop") }
                }
                SessionState.PAUSED -> {
                    Button(onClick = { TrackingService.resume(context) }) { Text("Resume") }
                    OutlinedButton(onClick = { TrackingService.stop(context) }) { Text("Stop") }
                }
            }
        }

        // REQ-7: enabled once a position has been recorded.
        OutlinedButton(onClick = { showMap = true }, enabled = hasRoute(track)) { Text("Map") }

        // REQ-8: share the saved GPX file of the stopped run with another app.
        val run = lastRun
        if (state == SessionState.STOPPED && run != null) {
            OutlinedButton(onClick = { shareRun(context, run) }) { Text("Export GPX") }
        }

        // REQ-9: list of finished runs.
        OutlinedButton(onClick = { showHistory = true }) { Text("History") }
    }
}

private fun shareRun(context: Context, run: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", run)
    val send = Intent(Intent.ACTION_SEND)
        .setType(GPX_MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Export run"))
}
