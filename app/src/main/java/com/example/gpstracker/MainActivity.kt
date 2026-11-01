package com.example.gpstracker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.GPX_MIME_TYPE
import com.example.gpstracker.domain.LOCATION_POWER_SAVE_NO_CHANGE
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.PAGER_SIZE
import com.example.gpstracker.domain.Page
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.START_INDEX
import com.example.gpstracker.domain.START_PAGE
import com.example.gpstracker.domain.indexOf
import com.example.gpstracker.domain.mapRun
import com.example.gpstracker.domain.pageAt
import com.example.gpstracker.domain.runRecord
import com.example.gpstracker.domain.SessionState
import com.example.gpstracker.domain.canStartTracking
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import com.example.gpstracker.domain.formatKmPace
import com.example.gpstracker.domain.formatSpeed
import com.example.gpstracker.domain.powerSavingLimitsGps
import com.example.gpstracker.domain.recentSplits
import com.example.gpstracker.domain.requiredPermissions
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingRepository.loadHistory(filesDir) // REQ-9
        SettingsRepository.load(this) // REQ-13
        TrackingRepository.namePlaces(this) // REQ-9
        enableEdgeToEdge() // REQ-10: the gradient also fills the system bars.
        setContent {
            AppTheme {
                TrackerScreen()
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
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.JOGGING) }
    val state = stats?.state ?: SessionState.IDLE
    val active = state == SessionState.RUNNING || state == SessionState.PAUSED
    var showPowerSavingNote by remember { mutableStateOf(false) }

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

    val requestPermissions = { permissionLauncher.launch(requiredPermissions(Build.VERSION.SDK_INT).toTypedArray()) }

    // REQ-15: power saving can switch the GPS off with the screen, so the user is asked to turn it off first.
    if (showPowerSavingNote) {
        AlertDialog(
            onDismissRequest = { showPowerSavingNote = false },
            containerColor = colorResource(R.color.gradient_purple),
            title = { Text("Turn off power saving") },
            text = {
                Text(
                    "Power saving is on. It can switch the GPS off while the screen is off, " +
                        "so parts of your route would not be recorded. Turn power saving off before you start.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPowerSavingNote = false
                    openBatterySaverSettings(context)
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPowerSavingNote = false
                    requestPermissions()
                }) { Text("Start anyway") }
            },
        )
    }

    // REQ-16: swipe round the views, or pick one from the icons at the bottom.
    val pager = rememberPagerState(initialPage = START_INDEX) { PAGER_SIZE }
    val scope = rememberCoroutineScope()
    val page = pageAt(pager.currentPage)
    val show = { target: Page -> scope.launch { pager.animateScrollToPage(indexOf(target, pager.currentPage)) } }
    BackHandler(enabled = page != START_PAGE) { show(START_PAGE) }

    // REQ-17: the run picked in the history (by its start); a new session clears it so the map then shows the new run.
    var selectedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(active) { if (active) selectedStart = null }
    val shownRun = mapRun(active, selectedStart, history)
    val shownTrack by produceState<Pair<RunRecord, List<List<LocationFix>>>?>(null, shownRun?.startMillis) {
        value = shownRun?.let { it to TrackingRepository.loadTrack(context.filesDir, it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // REQ-16: on the map a swipe only pans the map.
        HorizontalPager(state = pager, userScrollEnabled = page != Page.MAP, modifier = Modifier.weight(1f)) { index ->
            when (pageAt(index)) {
                // REQ-13: the settings view.
                Page.SETTINGS -> SettingsScreen(settings = settings, onChange = { SettingsRepository.update(context, it) })
                Page.TRACKER -> TrackerView(
                    state = state,
                    selectedType = selectedType,
                    lastRun = lastRun,
                    voiceNotes = settings.voiceNotes,
                    onVoiceNotes = { SettingsRepository.update(context, settings.copy(voiceNotes = it)) },
                    onStart = { if (isGpsLimitedByPowerSaving(context)) showPowerSavingNote = true else requestPermissions() },
                )
                // REQ-7: an active session follows the live stats, so the path keeps growing while the map is open.
                // REQ-17: otherwise the run selected in the history, or the newest.
                Page.MAP -> if (shownRun == null) {
                    val live = stats?.takeIf { active }
                    RouteMapScreen(track = live?.track.orEmpty(), run = live?.let(::runRecord))
                } else {
                    val loaded = shownTrack?.takeIf { it.first.startMillis == shownRun.startMillis }
                    RouteMapScreen(track = loaded?.second.orEmpty(), run = shownRun)
                }
                // REQ-9: the record of finished runs. REQ-17: picking one shows it on the map.
                Page.HISTORY -> RunHistoryScreen(runs = history, selected = shownRun) {
                    selectedStart = it.startMillis
                    show(Page.MAP)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // REQ-1: activity type selection, locked while a session is active.
            ActivityType.entries.forEach { type ->
                BarIcon(
                    icon = type.icon,
                    label = type.label,
                    selected = (if (active) stats?.activityType else selectedType) == type,
                    enabled = !active,
                ) {
                    selectedType = type
                    show(Page.TRACKER)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            BarIcon(Icons.Filled.Map, "Map", selected = page == Page.MAP) { show(Page.MAP) } // REQ-7
            BarIcon(Icons.Filled.History, "History", selected = page == Page.HISTORY) { show(Page.HISTORY) } // REQ-9
            BarIcon(Icons.Filled.Menu, "Settings", selected = page == Page.SETTINGS) { show(Page.SETTINGS) } // REQ-13
        }
    }
}

/** The front view: time, distance, pace and the session controls. */
@Composable
private fun TrackerView(
    state: SessionState,
    selectedType: ActivityType,
    lastRun: File?,
    voiceNotes: Boolean,
    onVoiceNotes: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val stats by TrackingRepository.stats.collectAsStateWithLifecycle()

    // REQ-14: the tone icon turns the kilometre voice note on and off, as the option in the settings does.
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { onVoiceNotes(!voiceNotes) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(
                if (voiceNotes) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (voiceNotes) "Voice note on" else "Voice note off",
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            val shown = stats
            val type = shown?.activityType ?: selectedType
            val elapsed = shown?.elapsedMillis ?: 0L
            val distance = shown?.distanceMeters ?: 0.0
            Text(type.label, style = MaterialTheme.typography.titleMedium) // REQ-1
            Text(formatDuration(elapsed), style = MaterialTheme.typography.displayLarge) // REQ-4
            Text(formatDistance(distance), style = MaterialTheme.typography.headlineMedium) // REQ-3, REQ-5
            Text(formatSpeed(type, distance, elapsed), style = MaterialTheme.typography.headlineMedium) // REQ-5
            if (state == SessionState.PAUSED) Text("Paused")

            // REQ-12: pace of the last kilometre, then the three kilometres before it.
            val splits = recentSplits(shown?.splits.orEmpty())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Last km  ${splits.firstOrNull()?.let { formatKmPace(it.millis) } ?: "--:-- /km"}",
                    style = MaterialTheme.typography.titleLarge,
                )
                splits.drop(1).forEach { Text("km ${it.km}  ${formatKmPace(it.millis)}") }
            }

            // REQ-1: session controls.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    SessionState.IDLE, SessionState.STOPPED -> Button(onClick = onStart) { Text("Start") }
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

            // REQ-8: share the saved GPX file of the stopped run with another app.
            if (state == SessionState.STOPPED && lastRun != null) {
                OutlinedButton(onClick = { shareRun(context, lastRun) }) { Text("Export GPX") }
            }
        }
    }
}

/** REQ-16: an icon of the bottom bar, highlighted while its view or choice is selected. */
@Composable
private fun BarIcon(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled || selected) Color.White else Color.White.copy(alpha = 0.4f))
    }
}

/** REQ-1: the icon of an activity type in the bottom bar and the history. */
val ActivityType.icon: ImageVector
    get() = when (this) {
        ActivityType.JOGGING -> Icons.AutoMirrored.Filled.DirectionsRun
        ActivityType.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    }

/** REQ-15: whether the phone's power saving currently limits the GPS while the screen is off. */
private fun isGpsLimitedByPowerSaving(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    val locationMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) power.locationPowerSaveMode else LOCATION_POWER_SAVE_NO_CHANGE
    return powerSavingLimitsGps(Build.VERSION.SDK_INT, power.isPowerSaveMode, locationMode)
}

/** REQ-15: opens the power saving settings, or the settings home where the phone has no such screen. */
private fun openBatterySaverSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
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
