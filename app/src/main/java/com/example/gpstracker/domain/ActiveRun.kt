package com.example.gpstracker.domain

import java.io.File
import java.time.ZoneId

/** REQ-27: the session in progress is written to file at least this often. */
const val ACTIVE_RUN_SAVE_MILLIS = 10_000L

/**
 * REQ-27: the session in progress, kept in [dir] while it runs so it is not lost when the app is closed or crashes.
 * It is written apart from the saved runs (REQ-8), so a late write can never overwrite the final GPX file.
 */
class ActiveRun(private val dir: File) {
    private val gpxFile = File(dir, "active.gpx")
    private val recordFile = File(dir, "active.tsv")

    /** Writes the track and history entry of the session; nothing is written before its first position. */
    fun save(stats: SessionStats) {
        val record = runRecord(stats) ?: return
        val gpx = toGpx(stats.activityType, stats.track) ?: return
        dir.mkdirs()
        // Each file is written next to its place and then moved in, so a crash mid-write keeps the previous one.
        replace(gpxFile) { it.writeText(gpx) }
        replace(recordFile) { RunHistory(it).add(record) }
    }

    /**
     * Adds a session left unfinished by the app closing to [history], with its GPX file in [runsDir], and returns it;
     * returns null when there is none or it is already in the history.
     */
    fun recover(history: RunHistory, runsDir: File, zone: ZoneId): RunRecord? {
        val record = RunHistory(recordFile).load().firstOrNull()
            ?.takeIf { run -> history.load().none { it.startMillis == run.startMillis && it.activityType == run.activityType } }
        if (record != null) {
            val target = File(runsDir, gpxFileName(record.activityType, record.startMillis, zone))
            if (!target.exists() && gpxFile.exists()) {
                runsDir.mkdirs()
                gpxFile.renameTo(target)
            }
            history.add(record)
        }
        clear()
        return record
    }

    /** Forgets the session once it is stopped and saved as a run. */
    fun clear() {
        gpxFile.delete()
        recordFile.delete()
    }

    private fun replace(target: File, write: (File) -> Unit) {
        val temp = File(dir, "${target.name}.tmp")
        temp.delete()
        write(temp)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }
}
