package com.example.gpstracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.gpstracker.domain.ActivityType
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.SessionState
import com.example.gpstracker.domain.TrackingSession
import com.example.gpstracker.domain.formatDistance
import com.example.gpstracker.domain.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** REQ-6: foreground service that keeps receiving GPS updates while the screen is off. */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private lateinit var locationManager: LocationManager

    private val listener = LocationListener { location -> onLocation(location) }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(ActivityType.valueOf(intent.getStringExtra(EXTRA_ACTIVITY_TYPE)!!))
            ACTION_PAUSE -> TrackingRepository.session?.takeIf { it.state == SessionState.RUNNING }?.pause()
            ACTION_RESUME -> TrackingRepository.session?.takeIf { it.state == SessionState.PAUSED }?.resume()
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
        }
        TrackingRepository.publish()
        updateNotification()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // MainActivity only starts the service after location permission is granted.
    private fun start(activityType: ActivityType) {
        val session = TrackingSession(activityType, clock = SystemClock::elapsedRealtime)
        session.start()
        TrackingRepository.begin(session)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            UPDATE_INTERVAL_MILLIS,
            0f,
            listener,
            Looper.getMainLooper(),
        )
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                TrackingRepository.publish()
                updateNotification()
            }
        }
    }

    private fun stop() {
        TrackingRepository.session?.takeIf { it.state == SessionState.RUNNING || it.state == SessionState.PAUSED }?.stop()
        TrackingRepository.publish()
        locationManager.removeUpdates(listener)
        ticker?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onLocation(location: Location) {
        val session = TrackingRepository.session ?: return
        val fix = LocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            timeMillis = location.time,
        )
        if (session.onLocation(fix)) TrackingRepository.publish()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stats = TrackingRepository.stats.value
        val text = stats?.let { "${formatDuration(it.elapsedMillis)} · ${formatDistance(it.distanceMeters)}" } ?: ""
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(stats?.activityType?.label ?: getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        if (TrackingRepository.session?.state == SessionState.STOPPED) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        locationManager.removeUpdates(listener)
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
        private const val UPDATE_INTERVAL_MILLIS = 1_000L

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
