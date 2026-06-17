package com.examguard.proctoring.core

/**
 * Domain models for the proctoring engine.
 *
 * These mirror the Python reference implementation (config/settings.py and
 * core/alert_engine.py) so scoring behaviour is identical across platforms.
 * Everything here is pure Kotlin with no Android dependencies, which keeps it
 * fully unit-testable on the JVM.
 */

/** Severity buckets used for colour-coding alerts in the UI. */
enum class Severity { LOW, MEDIUM, HIGH }

/**
 * The set of detectable violations. Each carries its score weight, per-type
 * cooldown (seconds) and a human-readable label — identical to the Python
 * VIOLATIONS table.
 */
enum class ViolationType(
    val key: String,
    val scoreDelta: Int,
    val cooldownSeconds: Long,
    val label: String,
    val severity: Severity,
) {
    NO_FACE("no_face", 3, 2, "No face detected", Severity.MEDIUM),
    MULTIPLE_FACES("multiple_faces", 10, 5, "Multiple persons detected", Severity.HIGH),
    LOOKING_AWAY("looking_away", 2, 1, "Looking away from screen", Severity.LOW),
    LARGE_MOTION("large_motion", 2, 2, "Suspicious hand movement", Severity.LOW),
    EXCESS_MOTION("excess_motion", 5, 3, "Excessive body movement", Severity.MEDIUM),
    SEAT_EMPTY("seat_empty", 8, 5, "Student left their seat", Severity.HIGH),
}

/** Coarse motion magnitude derived from frame-to-frame difference. */
enum class MotionLevel { NONE, LOW, MEDIUM, HIGH }

/** Risk tiers mapped from the running score. Colours match the Python theme. */
enum class RiskLevel(val label: String, val colorArgb: Long) {
    LOW("LOW", 0xFF2ECC71),
    MEDIUM("MEDIUM", 0xFFF39C12),
    HIGH("HIGH", 0xFFE67E22),
    CRITICAL("CRITICAL", 0xFFE74C3C);

    companion object {
        /** Map a score to a tier using the same thresholds as the Python engine. */
        fun fromScore(score: Int): RiskLevel = when {
            score <= 10 -> LOW
            score <= 30 -> MEDIUM
            score <= 60 -> HIGH
            else -> CRITICAL
        }
    }
}

/** One fired violation alert. */
data class AlertEvent(
    val timestampMillis: Long,
    val type: ViolationType,
    val scoreDelta: Int,
)

/**
 * One frame's worth of detector output, fed into [AlertEngine.evaluate].
 *
 * @param faceCount number of faces detected this frame
 * @param lookingAway true when a single face is turned away from the screen
 * @param motionLevel coarse motion magnitude this frame
 * @param seatMotion true when large motion is seen low in the frame with no face
 *                   present (proxy for "stood up / left seat")
 */
data class DetectionFrame(
    val faceCount: Int,
    val lookingAway: Boolean,
    val motionLevel: MotionLevel,
    val seatMotion: Boolean,
)
