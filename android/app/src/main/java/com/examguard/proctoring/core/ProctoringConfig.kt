package com.examguard.proctoring.core

/**
 * Central tunable parameters — the Kotlin equivalent of Python's
 * config/settings.py. Values are duplicated deliberately so the Android app
 * scores violations identically to the desktop reference.
 */
object ProctoringConfig {
    // Consecutive-frame confirmation thresholds (a violation must persist this
    // many analysed frames before it can fire). Scaled down from the desktop's
    // 30fps assumptions because ML Kit analysis runs slower on a phone.
    const val NO_FACE_FRAME_THRESHOLD = 5
    const val MULTIPLE_FACE_FRAME_THRESHOLD = 2
    const val LOOKING_AWAY_FRAME_THRESHOLD = 4
    const val HIGH_MOTION_FRAME_THRESHOLD = 3
    const val BODY_MOTION_FRAME_THRESHOLD = 4

    // Score decay during clean behaviour.
    const val SCORE_DECAY_INTERVAL_MILLIS = 5_000L
    const val SCORE_DECAY_AMOUNT = 1
    const val MAX_SCORE = 999

    // Gaze: |head Euler Y angle| beyond this (degrees) counts as "looking away".
    const val LOOKING_AWAY_YAW_DEGREES = 22f

    // Motion: mean absolute luma delta between frames mapped to a level.
    const val MOTION_LOW_THRESHOLD = 8.0
    const val MOTION_MEDIUM_THRESHOLD = 18.0
    const val MOTION_HIGH_THRESHOLD = 34.0
}
