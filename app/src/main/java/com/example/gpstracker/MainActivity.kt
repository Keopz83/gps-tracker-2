package com.example.gpstracker

import android.content.ActivityNotFoundException
import android.content.ClipData
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.example.gpstracker.domain.hasRoute
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
    // REQ-1: the activity type of the start button tapped, kept while the permissions are asked for.
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
    // REQ-1, REQ-15: starts a session of the type picked, after the power saving note and the permissions.
    val startSession = { type: ActivityType ->
        selectedType = type
        if (isGpsLimitedByPowerSaving(context)) showPowerSavingNote = true else requestPermissions()
    }

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
    // REQ-7: an active session follows the live stats, so the path keeps growing while the map is open.
    // REQ-17: otherwise the run selected in the history, or the newest.
    val (mapTrack, mapRunRecord) = if (shownRun == null) {
        val live = stats?.takeIf { active }
        live?.track.orEmpty() to live?.let(::runRecord)
    } else {
        shownTrack?.takeIf { it.first.startMillis == shownRun.startMillis }?.second.orEmpty() to shownRun
    }

    // REQ-17: the bar of icons lies over the views, so the map runs on beneath it; the other views stop above it.
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        // REQ-19: the map turns one-finger drags into swipes itself, so the pager leaves them to it.
        val mapShown = hasRoute(mapTrack) && mapRunRecord != null
        HorizontalPager(state = pager, userScrollEnabled = page != Page.MAP || !mapShown) { index ->
            val view = pageAt(index)
            Box(modifier = Modifier.padding(bottom = if (view == Page.MAP) 0.dp else barHeight)) {
                when (view) {
                    // REQ-13: the settings view.
                    Page.SETTINGS -> SettingsScreen(settings = settings, onChange = { SettingsRepository.update(context, it) })
                    Page.TRACKER -> TrackerView(
                        state = state,
                        lastRun = lastRun,
                        voiceNotes = settings.voiceNotes,
                        onVoiceNotes = { SettingsRepository.update(context, settings.copy(voiceNotes = it)) },
                        onStart = startSession,
                    )
                    Page.MAP -> RouteMapScreen(
                        track = mapTrack,
                        run = mapRunRecord,
                        activeState = state.takeIf { active && shownRun == null },
                        bottomInset = barHeight,
                        onSwipeBy = { pager.dispatchRawDelta(-it) },
                        onSwipeEnd = { step -> scope.launch { pager.animateScrollToPage(index + step) } },
                    )
                    // REQ-9: the record of finished runs. REQ-17: its map icon shows a run on the map.
                    Page.HISTORY -> RunHistoryScreen(
                        runs = history,
                        selected = shownRun,
                        onShowOnMap = {
                            selectedStart = it.startMillis
                            show(Page.MAP)
                        },
                        // REQ-22: share the GPX files of one or more runs.
                        onShare = { runs -> scope.launch { shareRuns(context, TrackingRepository.gpxFiles(context.filesDir, runs)) } },
                        onDelete = { TrackingRepository.delete(it, context.filesDir) }, // REQ-18
                    )
                }
            }
        }
        // REQ-10: the app icon and name over the top left corner of every view.
        // Its gradient backdrop cannot be seen on the other views but keeps the name readable over the map.
        Row(
            modifier = Modifier
                .padding(start = 10.dp, top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .screenGradientBackground(alpha = OVERLAY_ALPHA)
                .padding(start = 6.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon()
            Text("Summit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                .screenGradientBackground(alpha = OVERLAY_ALPHA)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            // REQ-24: starting from the record button also moves to the front view, where the session is shown.
            RecordButton(state, onStart = { type ->
                startSession(type)
                show(Page.TRACKER)
            })
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                BarIcon(Icons.Filled.Map, "Map", selected = page == Page.MAP) { show(Page.MAP) } // REQ-7
                BarIcon(Icons.Filled.History, "History", selected = page == Page.HISTORY) { show(Page.HISTORY) } // REQ-9
                BarIcon(Icons.Filled.Menu, "Settings", selected = page == Page.SETTINGS) { show(Page.SETTINGS) } // REQ-13
            }
        }
    }
}

/** The front view: the start buttons, then once started time, distance, pace and the session controls. */
@Composable
private fun TrackerView(
    state: SessionState,
    lastRun: File?,
    voiceNotes: Boolean,
    onVoiceNotes: (Boolean) -> Unit,
    onStart: (ActivityType) -> Unit,
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
            if (shown == null || state == SessionState.IDLE || state == SessionState.STOPPED) {
                // REQ-1: only the runner above the bicycle in the centre; each starts its activity.
                Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    ActivityType.entries.forEach { type ->
                        IconButton(
                            onClick = { onStart(type) },
                            modifier = Modifier.size(112.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                        ) {
                            Icon(type.icon, contentDescription = "Start ${type.label}", tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                    }
                }
                // REQ-8: share the saved GPX file of the stopped run with another app.
                if (state == SessionState.STOPPED && lastRun != null) {
                    OutlinedButton(onClick = { shareRuns(context, listOf(lastRun)) }) { Text("Export GPX") }
                }
                return@Column
            }
            val type = shown.activityType
            val elapsed = shown.elapsedMillis
            val distance = shown.distanceMeters
            Text(type.label, style = MaterialTheme.typography.titleMedium) // REQ-1
            Text(formatDuration(elapsed), style = MaterialTheme.typography.displayLarge) // REQ-4
            Text(formatDistance(distance), style = MaterialTheme.typography.headlineMedium) // REQ-3, REQ-5
            Text(formatSpeed(type, distance, elapsed), style = MaterialTheme.typography.headlineMedium) // REQ-5
            if (state == SessionState.PAUSED) Text("Paused")

            // REQ-12: pace of the last kilometre, then the three kilometres before it.
            val splits = recentSplits(shown.splits)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Last km  ${splits.firstOrNull()?.let { formatKmPace(it.millis) } ?: "--:-- /km"}",
                    style = MaterialTheme.typography.titleLarge,
                )
                splits.drop(1).forEach { Text("km ${it.km}  ${formatKmPace(it.millis)}") }
            }

            // REQ-1: session controls.
            SessionControls(state)
        }
    }
}

/** REQ-10: the launcher icon, its mountain layered on the gradient and cut to the safe zone of the adaptive icon. */
@Composable
private fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        // An adaptive icon layer is 108 units wide with the visible part in the middle 72.
        val layer = Modifier.requiredSize(size * 108 / 72)
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = layer)
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = layer)
    }
}

/** REQ-16: an icon of the bottom bar, highlighted while its view or choice is selected. */
@Composable
private fun BarIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
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

/** REQ-8, REQ-22: shares saved GPX files through the Android share sheet, several at once when more are given. */
private fun shareRuns(context: Context, runs: List<File>) {
    if (runs.isEmpty()) return
    val uris = runs.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    send.setType(GPX_MIME_TYPE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip carries the read permission of every file to the app picked in the share sheet.
    send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    context.startActivity(Intent.createChooser(send, if (uris.size == 1) "Export run" else "Export ${uris.size} runs"))
}
