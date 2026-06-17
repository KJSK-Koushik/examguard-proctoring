package com.examguard.proctoring.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.examguard.proctoring.core.AlertEngine
import com.examguard.proctoring.core.AlertEvent
import com.examguard.proctoring.core.DetectionFrame
import com.examguard.proctoring.core.MotionLevel
import com.examguard.proctoring.core.RiskLevel
import com.examguard.proctoring.core.SessionReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class SessionPhase { SETUP, RUNNING, PAUSED, REPORT }

data class ProctoringUiState(
    val studentName: String = "",
    val phase: SessionPhase = SessionPhase.SETUP,
    val score: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val faceCount: Int = 0,
    val motionLevel: MotionLevel = MotionLevel.NONE,
    val totalAlerts: Int = 0,
    val recentEvents: List<AlertEvent> = emptyList(),
    val reportText: String? = null,
    val errorMessage: String? = null,
)

/**
 * Single source of truth for the proctoring session. Holds the [AlertEngine],
 * folds detector frames into UI state, and persists the end-of-session report.
 */
class ProctoringViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = AlertEngine()
    private val reporter = SessionReporter(File(app.filesDir, "sessions"))

    private var sessionStartMillis = 0L

    private val _state = MutableStateFlow(ProctoringUiState())
    val state: StateFlow<ProctoringUiState> = _state.asStateFlow()

    fun startSession(name: String) {
        engine.reset()
        sessionStartMillis = System.currentTimeMillis()
        _state.value = ProctoringUiState(
            studentName = name.ifBlank { "Student" },
            phase = SessionPhase.RUNNING,
        )
    }

    /** Fold one analysed frame into the engine and UI state (no-op unless running). */
    fun onFrame(frame: DetectionFrame) {
        if (_state.value.phase != SessionPhase.RUNNING) return
        engine.evaluate(frame)
        _state.value = _state.value.copy(
            score = engine.score,
            riskLevel = engine.riskLevel,
            faceCount = frame.faceCount,
            motionLevel = frame.motionLevel,
            totalAlerts = engine.events.size,
            recentEvents = engine.recentEvents(20).asReversed(),
        )
    }

    fun pause() {
        if (_state.value.phase == SessionPhase.RUNNING) {
            _state.value = _state.value.copy(phase = SessionPhase.PAUSED)
        }
    }

    fun resume() {
        if (_state.value.phase == SessionPhase.PAUSED) {
            _state.value = _state.value.copy(phase = SessionPhase.RUNNING)
        }
    }

    fun resetScore() {
        // Zero the running score/cooldowns only. The alert history is preserved
        // (the engine keeps its event log) so the final report stays complete.
        engine.reset()
        _state.value = _state.value.copy(
            score = 0,
            riskLevel = RiskLevel.LOW,
        )
    }

    fun endSession() {
        val s = _state.value
        val report = runCatching {
            reporter.write(
                studentName = s.studentName,
                startedAtMillis = sessionStartMillis,
                endedAtMillis = System.currentTimeMillis(),
                events = engine.events,
                finalScore = engine.score,
                riskLabel = engine.riskLevel.label,
            )
        }
        _state.value = if (report.isSuccess) {
            s.copy(phase = SessionPhase.REPORT, reportText = report.getOrNull()?.summaryText)
        } else {
            s.copy(
                phase = SessionPhase.REPORT,
                reportText = "Session ended.\nScore: ${engine.score} (${engine.riskLevel.label})",
                errorMessage = "Could not write report file: ${report.exceptionOrNull()?.message}",
            )
        }
    }

    fun backToSetup() {
        engine.reset()
        _state.value = ProctoringUiState()
    }
}
