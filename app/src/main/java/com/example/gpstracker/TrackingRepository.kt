package com.example.gpstracker

import com.example.gpstracker.domain.ActiveRun
import com.example.gpstracker.domain.RunHistory
import com.example.gpstracker.domain.RunRecord
import android.content.Context
import com.example.gpstracker.domain.SessionStats
import com.example.gpstracker.domain.TrackingSession
import com.example.gpstracker.domain.firstGpxPosition
import com.example.gpstracker.domain.gpxFileName
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.parseGpxTrack
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the current session so the service and the UI share one source of truth. */
object TrackingRepository {
    private val _stats = MutableStateFlow<SessionStats?>(null)
    val stats: StateFlow<SessionStats?> = _stats.asStateFlow()

    private val _lastRun = MutableStateFlow<File?>(null)

    /** REQ-8: the GPX file of the most recently stopped run, or null when it had no positions. */
    val lastRun: StateFlow<File?> = _lastRun.asStateFlow()

    private val _history = MutableStateFlow<List<RunRecord>>(emptyList())

    /** REQ-9: every finished run, newest first. */
    val history: StateFlow<List<RunRecord>> = _history.asStateFlow()

    private var runHistory: RunHistory? = null

    // History changes and place look-ups run on the main thread, so the file is only written from one place.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var placeLookUp: Job? = null

    var session: TrackingSession? = null
        private set

    fun begin(session: TrackingSession) {
        this.session = session
        _lastRun.value = null
        publish()
    }

    fun publish() {
        _stats.value = session?.stats()
    }

    fun saved(run: File?) {
        _lastRun.value = run
    }

    /** REQ-9: reads the stored history once per process; the service and the UI may both ask for it. */
    fun loadHistory(filesDir: File) {
        if (runHistory != null) return
        val history = RunHistory(File(filesDir, HISTORY_FILE))
        // REQ-27: a session left unfinished when the app was closed becomes a run, unless it is still running here.
        if (session == null) activeRun(filesDir).recover(history, File(filesDir, TrackingService.RUNS_DIR), ZoneId.systemDefault())
        runHistory = history.also { _history.value = it.load() }
    }

    /** REQ-27: where the session in progress is kept while it runs. */
    fun activeRun(filesDir: File): ActiveRun = ActiveRun(File(filesDir, ACTIVE_DIR))

    fun recorded(record: RunRecord, filesDir: File) {
        loadHistory(filesDir)
        runHistory?.let {
            it.add(record)
            _history.value = it.load()
        }
    }

    /** REQ-18: removes [record] from the history and deletes its GPX file. */
    fun delete(record: RunRecord, filesDir: File) {
        loadHistory(filesDir)
        runHistory?.let {
            it.remove(record)
            _history.value = it.load()
        }
        val gpx = gpxFile(filesDir, record)
        if (_lastRun.value == gpx) _lastRun.value = null
        scope.launch(Dispatchers.IO) { gpx.delete() }
    }

    /**
     * REQ-9: names the place where each run started, for runs that have no name yet (e.g. recorded offline or
     * before places were stored). Runs from before the start position was stored take it from their GPX file.
     * Runs that cannot be named now are tried again next time.
     */
    fun namePlaces(context: Context) {
        if (placeLookUp?.isActive == true) return
        val app = context.applicationContext
        loadHistory(app.filesDir)
        placeLookUp = scope.launch {
            // Re-read the history each time, so a run stopped during the look-ups is named as well.
            val tried = mutableSetOf<Long>()
            while (true) {
                val run = _history.value.firstOrNull { it.place == null && tried.add(it.startMillis) } ?: break
                val position = run.startLatitude?.let { lat -> run.startLongitude?.let { lat to it } }
                    ?: withContext(Dispatchers.IO) { gpxStart(app.filesDir, run) }
                    ?: continue
                val place = lookUpPlace(app, position.first, position.second) ?: continue
                runHistory?.let {
                    it.update(run.copy(startLatitude = position.first, startLongitude = position.second, place = place))
                    _history.value = it.load()
                }
            }
        }
    }

    /** REQ-17: the recorded track of a finished run, read from its GPX file, or empty when the file is gone. */
    suspend fun loadTrack(filesDir: File, run: RunRecord): List<List<LocationFix>> = withContext(Dispatchers.IO) {
        gpxFile(filesDir, run).takeIf { it.exists() }?.let { parseGpxTrack(it.readText()) }.orEmpty()
    }

    /** REQ-22: the saved GPX files of [runs] (REQ-8) to share, leaving out runs whose file is gone. */
    suspend fun gpxFiles(filesDir: File, runs: List<RunRecord>): List<File> = withContext(Dispatchers.IO) {
        runs.map { gpxFile(filesDir, it) }.filter { it.exists() }
    }

    private fun gpxStart(filesDir: File, run: RunRecord): Pair<Double, Double>? =
        gpxFile(filesDir, run).takeIf { it.exists() }?.let { firstGpxPosition(it.readText()) }

    private fun gpxFile(filesDir: File, run: RunRecord): File =
        File(File(filesDir, TrackingService.RUNS_DIR), gpxFileName(run.activityType, run.startMillis, ZoneId.systemDefault()))

    private const val HISTORY_FILE = "history.tsv"
    private const val ACTIVE_DIR = "active"
}
