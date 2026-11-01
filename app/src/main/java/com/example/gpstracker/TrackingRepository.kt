package com.example.gpstracker

import com.example.gpstracker.domain.RunHistory
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.SessionStats
import java.io.File
import com.example.gpstracker.domain.TrackingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        runHistory = RunHistory(File(filesDir, HISTORY_FILE)).also { _history.value = it.load() }
    }

    fun recorded(record: RunRecord, filesDir: File) {
        loadHistory(filesDir)
        runHistory?.let {
            it.add(record)
            _history.value = it.load()
        }
    }

    private const val HISTORY_FILE = "history.tsv"
}
