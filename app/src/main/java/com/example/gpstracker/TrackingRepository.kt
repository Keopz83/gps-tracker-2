package com.example.gpstracker

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
}
