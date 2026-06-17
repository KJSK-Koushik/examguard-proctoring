package com.examguard.proctoring.core

/**
 * Stateful violation-scoring engine. Call [evaluate] once per analysed frame.
 *
 * Port of the Python `core/alert_engine.py`. Responsibilities:
 *   1. Confirm each violation condition over consecutive frames (anti-flicker).
 *   2. Apply a per-violation cooldown so identical alerts don't spam.
 *   3. Add to / decay the running risk score.
 *   4. Emit [AlertEvent]s for the UI and session log.
 *
 * Time is injected via [clock] so the logic is deterministic under unit test.
 */
class AlertEngine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var score: Int = 0
        private set

    private val cooldowns = HashMap<ViolationType, Long>()
    private val streaks = HashMap<ViolationType, Int>()
    private val _events = ArrayList<AlertEvent>()
    private var lastDecay: Long = clock()

    /** All events fired this session, oldest first. */
    val events: List<AlertEvent> get() = _events

    /** The most recent [limit] events, newest last. */
    fun recentEvents(limit: Int = 50): List<AlertEvent> =
        if (_events.size <= limit) _events.toList() else _events.takeLast(limit)

    val riskLevel: RiskLevel get() = RiskLevel.fromScore(score)

    /**
     * Evaluate one frame of detections and return any newly fired alerts.
     */
    fun evaluate(frame: DetectionFrame): List<AlertEvent> {
        applyDecay()
        val fired = ArrayList<AlertEvent>()

        // ── Face-based violations ──────────────────────────────────────────
        if (confirmed(
                ViolationType.NO_FACE,
                frame.faceCount == 0,
                ProctoringConfig.NO_FACE_FRAME_THRESHOLD,
            )
        ) tryFire(ViolationType.NO_FACE)?.let(fired::add)

        if (confirmed(
                ViolationType.MULTIPLE_FACES,
                frame.faceCount > 1,
                ProctoringConfig.MULTIPLE_FACE_FRAME_THRESHOLD,
            )
        ) tryFire(ViolationType.MULTIPLE_FACES)?.let(fired::add)

        if (confirmed(
                ViolationType.LOOKING_AWAY,
                frame.faceCount == 1 && frame.lookingAway,
                ProctoringConfig.LOOKING_AWAY_FRAME_THRESHOLD,
            )
        ) tryFire(ViolationType.LOOKING_AWAY)?.let(fired::add)

        // ── Motion-based violations ────────────────────────────────────────
        when (frame.motionLevel) {
            MotionLevel.HIGH -> {
                if (confirmed(
                        ViolationType.EXCESS_MOTION,
                        true,
                        ProctoringConfig.HIGH_MOTION_FRAME_THRESHOLD,
                    )
                ) tryFire(ViolationType.EXCESS_MOTION)?.let(fired::add)
                confirmed(ViolationType.LARGE_MOTION, false, ProctoringConfig.BODY_MOTION_FRAME_THRESHOLD)
            }
            MotionLevel.MEDIUM -> {
                confirmed(ViolationType.EXCESS_MOTION, false, ProctoringConfig.HIGH_MOTION_FRAME_THRESHOLD)
                if (confirmed(
                        ViolationType.LARGE_MOTION,
                        true,
                        ProctoringConfig.BODY_MOTION_FRAME_THRESHOLD,
                    )
                ) tryFire(ViolationType.LARGE_MOTION)?.let(fired::add)
            }
            else -> {
                confirmed(ViolationType.EXCESS_MOTION, false, ProctoringConfig.HIGH_MOTION_FRAME_THRESHOLD)
                confirmed(ViolationType.LARGE_MOTION, false, ProctoringConfig.BODY_MOTION_FRAME_THRESHOLD)
            }
        }

        // ── Seat empty ─────────────────────────────────────────────────────
        if (confirmed(
                ViolationType.SEAT_EMPTY,
                frame.faceCount == 0 && frame.seatMotion,
                ProctoringConfig.NO_FACE_FRAME_THRESHOLD,
            )
        ) tryFire(ViolationType.SEAT_EMPTY)?.let(fired::add)

        return fired
    }

    /** Reset score, cooldowns and streaks (the dashboard "Reset" button). */
    fun reset() {
        score = 0
        cooldowns.clear()
        streaks.clear()
        lastDecay = clock()
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun tryFire(type: ViolationType): AlertEvent? {
        val now = clock()
        if (now < (cooldowns[type] ?: 0L)) return null // still cooling down

        score = (score + type.scoreDelta).coerceAtMost(ProctoringConfig.MAX_SCORE)
        cooldowns[type] = now + type.cooldownSeconds * 1000L

        val event = AlertEvent(timestampMillis = now, type = type, scoreDelta = type.scoreDelta)
        _events.add(event)
        return event
    }

    /** True only once a violation condition has persisted [threshold] frames. */
    private fun confirmed(type: ViolationType, condition: Boolean, threshold: Int): Boolean {
        val streak = if (condition) (streaks[type] ?: 0) + 1 else 0
        streaks[type] = streak
        return streak >= maxOf(1, threshold)
    }

    private fun applyDecay() {
        val now = clock()
        if (now - lastDecay >= ProctoringConfig.SCORE_DECAY_INTERVAL_MILLIS) {
            score = (score - ProctoringConfig.SCORE_DECAY_AMOUNT).coerceAtLeast(0)
            lastDecay = now
        }
    }
}
