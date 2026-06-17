package com.examguard.proctoring.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the end-of-session summary and per-event CSV log to local storage.
 * Mirrors ui/report_generator.py. Fully offline — nothing leaves the device.
 */
class SessionReporter(private val outputDir: File) {

    data class Report(val csvFile: File, val summaryFile: File, val summaryText: String)

    /**
     * Persist a session. [studentName] is logged verbatim, [events] is the full
     * alert log, [finalScore] / [riskLabel] the closing state.
     */
    fun write(
        studentName: String,
        startedAtMillis: Long,
        endedAtMillis: Long,
        events: List<AlertEvent>,
        finalScore: Int,
        riskLabel: String,
    ): Report {
        if (!outputDir.exists()) outputDir.mkdirs()
        val stamp = fileStamp.format(Date(startedAtMillis))

        val csvFile = File(outputDir, "session_$stamp.csv")
        csvFile.bufferedWriter().use { w ->
            w.appendLine("timestamp,violation,label,severity,score_delta")
            for (e in events) {
                val ts = isoTime.format(Date(e.timestampMillis))
                w.appendLine("$ts,${e.type.key},\"${e.type.label}\",${e.type.severity},${e.scoreDelta}")
            }
        }

        val durationSec = ((endedAtMillis - startedAtMillis) / 1000).coerceAtLeast(0)
        val counts = events.groupingBy { it.type.label }.eachCount()
        val summary = buildString {
            appendLine("ExamGuard — Session Summary")
            appendLine("============================")
            appendLine("Student        : $studentName")
            appendLine("Started        : ${isoTime.format(Date(startedAtMillis))}")
            appendLine("Ended          : ${isoTime.format(Date(endedAtMillis))}")
            appendLine("Duration       : ${durationSec}s")
            appendLine("Total alerts   : ${events.size}")
            appendLine("Final score    : $finalScore")
            appendLine("Final risk     : $riskLabel")
            appendLine()
            appendLine("Breakdown:")
            if (counts.isEmpty()) {
                appendLine("  (no violations recorded)")
            } else {
                counts.entries.sortedByDescending { it.value }.forEach { (label, n) ->
                    appendLine("  $label: $n")
                }
            }
        }
        val summaryFile = File(outputDir, "summary_$stamp.txt")
        summaryFile.writeText(summary)

        return Report(csvFile, summaryFile, summary)
    }

    private companion object {
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val isoTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
