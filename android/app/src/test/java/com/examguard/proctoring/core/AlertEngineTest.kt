package com.examguard.proctoring.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AlertEngine], mirroring the Python test_system.py alert-engine
 * suite. Time is driven by a controllable fake clock so behaviour is deterministic.
 */
class AlertEngineTest {

    private var now = 1_000_000L
    private fun engine() = AlertEngine(clock = { now })

    private fun frame(
        faceCount: Int = 1,
        lookingAway: Boolean = false,
        motion: MotionLevel = MotionLevel.NONE,
        seatMotion: Boolean = false,
    ) = DetectionFrame(faceCount, lookingAway, motion, seatMotion)

    @Test
    fun newEngineStartsAtZeroAndLow() {
        val e = engine()
        assertEquals(0, e.score)
        assertEquals(RiskLevel.LOW, e.riskLevel)
        assertTrue(e.events.isEmpty())
    }

    @Test
    fun multipleFacesFiresAfterConfirmationThreshold() {
        val e = engine()
        // Needs MULTIPLE_FACE_FRAME_THRESHOLD consecutive frames to confirm.
        var fired = e.evaluate(frame(faceCount = 2))
        repeat(ProctoringConfig.MULTIPLE_FACE_FRAME_THRESHOLD - 1) {
            fired = e.evaluate(frame(faceCount = 2))
        }
        assertEquals(1, fired.size)
        assertEquals(ViolationType.MULTIPLE_FACES, fired[0].type)
        assertEquals(10, e.score)
    }

    @Test
    fun cooldownPreventsImmediateRefire() {
        // Use looking_away (1s cooldown) so we can re-fire well inside the 5s
        // score-decay window and isolate cooldown behaviour from decay.
        val e = engine()
        repeat(ProctoringConfig.LOOKING_AWAY_FRAME_THRESHOLD) {
            e.evaluate(frame(faceCount = 1, lookingAway = true))
        }
        assertEquals(2, e.score)
        // Same violation again immediately — still cooling down, no new score.
        val again = e.evaluate(frame(faceCount = 1, lookingAway = true))
        assertTrue(again.isEmpty())
        assertEquals(2, e.score)

        // Advance past the 1s cooldown (but under the 5s decay) → fires again.
        now += 1_500
        val third = e.evaluate(frame(faceCount = 1, lookingAway = true))
        assertEquals(1, third.size)
        assertEquals(4, e.score)
    }

    @Test
    fun noFaceFiresAndScores() {
        val e = engine()
        var fired = emptyList<AlertEvent>()
        repeat(ProctoringConfig.NO_FACE_FRAME_THRESHOLD) { fired = e.evaluate(frame(faceCount = 0)) }
        assertEquals(1, fired.size)
        assertEquals(ViolationType.NO_FACE, fired[0].type)
        assertEquals(3, e.score)
    }

    @Test
    fun lookingAwayRequiresExactlyOneFace() {
        val e = engine()
        // Two faces + away should NOT trigger looking_away (handled as multiple).
        repeat(ProctoringConfig.LOOKING_AWAY_FRAME_THRESHOLD) {
            e.evaluate(frame(faceCount = 2, lookingAway = true))
        }
        assertFalse(e.events.any { it.type == ViolationType.LOOKING_AWAY })
    }

    @Test
    fun scoreDecaysOverCleanTime() {
        val e = engine()
        repeat(ProctoringConfig.MULTIPLE_FACE_FRAME_THRESHOLD) { e.evaluate(frame(faceCount = 2)) }
        assertEquals(10, e.score)
        // Clean frames after the decay interval reduce the score.
        now += ProctoringConfig.SCORE_DECAY_INTERVAL_MILLIS + 1
        e.evaluate(frame())
        assertEquals(10 - ProctoringConfig.SCORE_DECAY_AMOUNT, e.score)
    }

    @Test
    fun riskLevelThresholdsMatchReference() {
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(0))
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(10))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(11))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(30))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(31))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(60))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(61))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(500))
    }

    @Test
    fun resetClearsScoreButPreservesEventLog() {
        // The "Reset Score" action zeroes the running score/cooldowns but keeps
        // the all-time event log so the end-of-session report stays complete
        // (matches the Python reset_score semantics).
        val e = engine()
        repeat(ProctoringConfig.MULTIPLE_FACE_FRAME_THRESHOLD) { e.evaluate(frame(faceCount = 2)) }
        assertTrue(e.score > 0)
        val loggedBefore = e.events.size
        e.reset()
        assertEquals(0, e.score)
        assertEquals(RiskLevel.LOW, e.riskLevel)
        assertEquals("event log must persist across a score reset", loggedBefore, e.events.size)
    }

    @Test
    fun excessMotionFiresOnHighMotion() {
        val e = engine()
        var fired = emptyList<AlertEvent>()
        repeat(ProctoringConfig.HIGH_MOTION_FRAME_THRESHOLD) {
            fired = e.evaluate(frame(motion = MotionLevel.HIGH))
        }
        assertTrue(fired.any { it.type == ViolationType.EXCESS_MOTION })
        assertEquals(5, e.score)
    }

    @Test
    fun seatEmptyRequiresNoFaceAndSeatMotion() {
        val e = engine()
        repeat(ProctoringConfig.NO_FACE_FRAME_THRESHOLD) {
            e.evaluate(frame(faceCount = 0, motion = MotionLevel.HIGH, seatMotion = true))
        }
        assertTrue(e.events.any { it.type == ViolationType.SEAT_EMPTY })
    }
}
