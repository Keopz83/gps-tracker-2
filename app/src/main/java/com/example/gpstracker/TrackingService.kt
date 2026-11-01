package com.example.gpstracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.gpstracker.domain.ACTIVE_RUN_SAVE_MILLIS
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.GpsRequest
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.NotificationContent
import com.example.gpstracker.domain.PAUSE_GPS_GRACE_MILLIS
import com.example.gpstracker.domain.SessionState
import com.example.gpstracker.domain.TrackingSession
import com.example.gpstracker.domain.elapsedMinutes
import com.example.gpstracker.domain.gpsRequest
import com.example.gpstracker.domain.gpxFileName
import com.example.gpstracker.domain.kmVoiceNote
import com.example.gpstracker.domain.minuteVoiceNote
import com.example.gpstracker.domain.notificationContent
import com.example.gpstracker.domain.runRecord
import com.example.gpstracker.domain.screenOnVoiceNote
import com.example.gpstracker.domain.toGpx
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * REQ-6: foreground service that keeps receiving GPS updates while the screen is off.
 * REQ-11: adapts the GPS request, screen refreshes and notification updates to save battery without losing positions.
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var pauseTimeout: Job? = null
    private var activeRunSaver: Job? = null

    /** REQ-27: one write at a time, so the active run is cleared only after a write still in progress. */
    private val activeRunIo = Dispatchers.IO.limitedParallelism(1)
    private lateinit var locationManager: LocationManager
    private var screenOn = true
    private var activeGpsRequest: GpsRequest? = null
    private var shownNotification: NotificationContent? = null
    private lateinit var voiceNotes: VoiceNotes

    /** REQ-25: the report waits for the batched positions so it gives the distance up to now. */
    private var reportAfterFlush = false

    /** REQ-20: the whole minutes of active time already reached, so each minute is announced once. */
    private var minutesReached = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocations(listOf(location))

        // REQ-11: a batch delivered after the screen was off is recorded in one go and published once.
        override fun onLocationChanged(locations: MutableList<Location>) = onLocations(locations)

        override fun onFlushComplete(requestCode: Int) {
            updateGps()
            if (reportAfterFlush) {
                reportAfterFlush = false
                announceReport()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            if (screenOn) {
                // REQ-25: pressing the power button with the screen off turns it on, which is what asks for the report.
                val report = SettingsRepository.settings.value.screenOnNote
                // Deliver the held-back positions first so the screen shows the whole track, then go live.
                if (flushGps()) {
                    reportAfterFlush = report
                } else {
                    updateGps()
                    if (report) announceReport()
                }
                TrackingRepository.publish()
                updateNotification()
            } else {
                updateGps()
            }
            updateTicker()
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        voiceNotes = VoiceNotes(this)
        SettingsRepository.load(this)
        // REQ-14, REQ-20: turning voice notes on or off during a run changes how long positions may be batched.
        // REQ-26: turning long distance mode on or off changes how often they are measured.
        scope.launch { SettingsRepository.settings.drop(1).collect { updateGps() } }
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(ActivityType.valueOf(intent.getStringExtra(EXTRA_ACTIVITY_TYPE)!!))
            ACTION_PAUSE -> TrackingRepository.session?.takeIf { it.state == SessionState.RUNNING }?.let {
                it.pause()
                updateGps()
                // REQ-11: switch the GPS off once the pause outlasts the grace period.
                pauseTimeout = scope.launch {
                    delay(PAUSE_GPS_GRACE_MILLIS)
                    updateGps()
                }
            }
            ACTION_RESUME -> TrackingRepository.session?.takeIf { it.state == SessionState.PAUSED }?.let {
                pauseTimeout?.cancel()
                it.resume()
                updateGps()
            }
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
        }
        TrackingRepository.publish()
        updateNotification()
        return START_NOT_STICKY
    }

    private fun start(activityType: ActivityType) {
        val session = TrackingSession(activityType, clock = SystemClock::elapsedRealtime)
        session.start()
        TrackingRepository.begin(session)
        minutesReached = 0

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        updateGps()
        updateTicker()
        saveActiveRun(session)
    }

    /** REQ-27: writes the session to file every 10 s, whenever it has new positions, until it is stopped. */
    private fun saveActiveRun(session: TrackingSession) {
        val activeRun = TrackingRepository.activeRun(filesDir)
        activeRunSaver = scope.launch {
            var savedPositions = 0
            while (isActive) {
                delay(ACTIVE_RUN_SAVE_MILLIS)
                val stats = session.stats()
                val positions = stats.track.sumOf { it.size }
                if (positions == savedPositions) continue
                savedPositions = positions
                withContext(activeRunIo) { activeRun.save(stats) }
            }
        }
    }

    /** REQ-11: applies the GPS request for the current session state and screen, or switches the GPS off. */
    @SuppressLint("MissingPermission") // MainActivity only starts the service after location permission is granted.
    private fun updateGps() {
        val session = TrackingRepository.session
        val settings = SettingsRepository.settings.value
        val request = session?.let { gpsRequest(it.state, it.pausedMillis, screenOn, settings.anyVoiceNotes, settings.gpsIntervalMillis) }
        if (request == activeGpsRequest) return
        activeGpsRequest = request
        when {
            request == null -> locationManager.removeUpdates(listener)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LocationRequest.Builder(request.intervalMillis)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(request.intervalMillis)
                    .setMaxUpdateDelayMillis(request.maxDelayMillis)
                    .build(),
                mainExecutor,
                listener,
            )
            // Before Android 12 the GPS cannot batch, so positions are delivered as they are measured.
            else -> locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                request.intervalMillis,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
    }

    /** REQ-11: asks for the batched positions to be delivered now; [updateGps] follows once they are. */
    private fun flushGps(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (activeGpsRequest?.maxDelayMillis ?: 0L) == 0L) return false
        locationManager.requestFlush(LocationManager.GPS_PROVIDER, listener, 0)
        return true
    }

    /** REQ-11: the elapsed time is refreshed every second only while the screen is on to show it. */
    private fun updateTicker() {
        val active = TrackingRepository.session?.state.let { it == SessionState.RUNNING || it == SessionState.PAUSED }
        if (screenOn && active) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    TrackingRepository.publish()
                }
            }
        } else {
            ticker?.cancel()
        }
    }

    private fun stop() {
        TrackingRepository.session?.takeIf { it.state == SessionState.RUNNING || it.state == SessionState.PAUSED }?.let {
            it.stop()
            TrackingRepository.saved(saveRun(it))
            runRecord(it.stats())?.let { record ->
                TrackingRepository.recorded(record, filesDir) // REQ-9
                TrackingRepository.namePlaces(this)
            }
        }
        // REQ-27: the run is saved now, so the copy kept while it ran is no longer needed.
        activeRunSaver?.cancel()
        val activeRun = TrackingRepository.activeRun(filesDir)
        // Not in the service's scope, which is cancelled as the service stops.
        CoroutineScope(activeRunIo).launch { activeRun.clear() }
        TrackingRepository.publish()
        locationManager.removeUpdates(listener)
        activeGpsRequest = null
        ticker?.cancel()
        pauseTimeout?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** REQ-8: stores the run as a GPX file in the app's `runs` folder, or returns null when nothing was recorded. */
    private fun saveRun(session: TrackingSession): File? {
        val track = session.stats().track
        val gpx = toGpx(session.activityType, track) ?: return null
        val start = track.first { it.isNotEmpty() }.first().timeMillis
        val dir = File(filesDir, RUNS_DIR).apply { mkdirs() }
        return File(dir, gpxFileName(session.activityType, start, ZoneId.systemDefault())).apply { writeText(gpx) }
    }

    private fun onLocations(locations: List<Location>) {
        val session = TrackingRepository.session ?: return
        val kmBefore = session.completedKm
        val recorded = locations.count { location ->
            session.onLocation(
                LocationFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                    timeMillis = location.time,
                    altitudeMeters = altitudeOf(location),
                ),
            )
        }
        if (recorded > 0) {
            TrackingRepository.publish()
            updateNotification()
            announceKm(kmBefore)
        }
        announceMinute()
    }

    /** REQ-23: the altitude above sea level when the phone knows it (Android 14+), else above the GPS ellipsoid. */
    private fun altitudeOf(location: Location): Double? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude() -> location.mslAltitudeMeters
        location.hasAltitude() -> location.altitude
        else -> null
    }

    /** REQ-14: speaks the latest kilometre when one was completed since [kmBefore] and voice notes are on. */
    private fun announceKm(kmBefore: Int) {
        val stats = TrackingRepository.stats.value ?: return
        if (stats.splits.size <= kmBefore || !SettingsRepository.settings.value.voiceNotes) return
        voiceNotes.speak(kmVoiceNote(stats.activityType, stats.splits.size, stats.elapsedMillis, stats.splits.last()))
    }

    /**
     * REQ-20: speaks the minute note when a new whole minute of active time was reached and minute notes are on.
     * Checked with every delivery of positions, so it comes at most one batch late (REQ-14).
     */
    private fun announceMinute() {
        val stats = TrackingRepository.session?.takeIf { it.state == SessionState.RUNNING }?.stats() ?: return
        val minutes = elapsedMinutes(stats.elapsedMillis)
        if (minutes <= minutesReached) return
        minutesReached = minutes
        if (SettingsRepository.settings.value.minuteNotes) voiceNotes.speak(minuteVoiceNote(stats.activityType, minutes, stats.distanceMeters))
    }

    /** REQ-25: speaks the time, distance and average pace (speed when cycling) of the running or paused session. */
    private fun announceReport() {
        val session = TrackingRepository.session ?: return
        if (session.state != SessionState.RUNNING && session.state != SessionState.PAUSED) return
        val stats = session.stats()
        voiceNotes.speak(screenOnVoiceNote(stats.activityType, stats.elapsedMillis, stats.distanceMeters))
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stats = TrackingRepository.stats.value
        val content = stats?.let(::notificationContent)
        shownNotification = content
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(content?.title ?: getString(R.string.app_name))
            .setContentText(content?.text ?: "")
            // REQ-11: the system ticks the elapsed time, so the notification is not rebuilt every second.
            .setShowWhen(content?.chronometer == true)
            .setUsesChronometer(content?.chronometer == true)
            .setWhen(System.currentTimeMillis() - (stats?.elapsedMillis ?: 0L))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** REQ-11: refreshes the notification only when what it shows has changed. */
    private fun updateNotification() {
        if (TrackingRepository.session?.state == SessionState.STOPPED) return
        if (TrackingRepository.stats.value?.let(::notificationContent) == shownNotification) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        locationManager.removeUpdates(listener)
        unregisterReceiver(screenReceiver)
        voiceNotes.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_ACTIVITY_TYPE = "activityType"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        const val RUNS_DIR = "runs"

        fun start(context: Context, activityType: ActivityType) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ACTIVITY_TYPE, activityType.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, TrackingService::class.java).setAction(action))
        }
    }
}
