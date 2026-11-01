package com.example.gpstracker

import com.example.gpstracker.domain.SessionStats
import com.example.gpstracker.domain.TrackingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the current session so the service and the UI share one source of truth. */
object TrackingRepository {
    private val _stats = MutableStateFlow<SessionStats?>(null)
    val stats: StateFlow<SessionStats?> = _stats.asStateFlow()

    var session: TrackingSession? = null
        private set

    fun begin(session: TrackingSession) {
        this.session = session
        publish()
    }

    fun publish() {
        _stats.value = session?.stats()
    }
}
