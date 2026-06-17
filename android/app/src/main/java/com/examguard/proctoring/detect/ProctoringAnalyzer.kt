package com.examguard.proctoring.detect

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.examguard.proctoring.core.DetectionFrame
import com.examguard.proctoring.core.MotionLevel
import com.examguard.proctoring.core.ProctoringConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

/**
 * CameraX [ImageAnalysis.Analyzer] that turns each frame into a [DetectionFrame].
 *
 *  - Faces (count + gaze) come from ML Kit's bundled, fully-offline detector.
 *  - Motion level comes from a cheap mean-absolute-difference of the luma (Y)
 *    plane against the previous frame — no OpenCV dependency required.
 *
 * Results are delivered on the analyzer's background executor via [onResult];
 * callers are responsible for marshalling to the main thread.
 */
class ProctoringAnalyzer(
    private val onResult: (DetectionFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
    )

    // Downsampled luma signature of the previous frame, for motion diffing.
    private var prevLuma: IntArray? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Motion is computed synchronously from the Y plane before we hand the
        // frame to ML Kit (which retains the image until processing completes).
        val motionLevel = computeMotionLevel(imageProxy)

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val faceCount = faces.size
                val lookingAway = faceCount == 1 &&
                    abs(faces[0].headEulerAngleY) > ProctoringConfig.LOOKING_AWAY_YAW_DEGREES
                val seatMotion = faceCount == 0 && motionLevel == MotionLevel.HIGH
                onResult(
                    DetectionFrame(
                        faceCount = faceCount,
                        lookingAway = lookingAway,
                        motionLevel = motionLevel,
                        seatMotion = seatMotion,
                    )
                )
            }
            .addOnFailureListener {
                // On detector failure, still report motion so the engine keeps moving.
                onResult(
                    DetectionFrame(
                        faceCount = 1,
                        lookingAway = false,
                        motionLevel = motionLevel,
                        seatMotion = false,
                    )
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Mean absolute luma difference vs. previous frame → coarse motion level. */
    private fun computeMotionLevel(imageProxy: ImageProxy): MotionLevel {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        // Sample a coarse grid (~32x32) to keep this cheap on the analysis thread.
        val cols = 32
        val rows = 32
        val sample = IntArray(cols * rows)
        var idx = 0
        for (r in 0 until rows) {
            val y = (r * height) / rows
            for (c in 0 until cols) {
                val x = (c * width) / cols
                val pos = y * rowStride + x * pixelStride
                sample[idx++] = if (pos < buffer.limit()) (buffer.get(pos).toInt() and 0xFF) else 0
            }
        }

        val prev = prevLuma
        prevLuma = sample
        if (prev == null) return MotionLevel.NONE

        var total = 0L
        for (i in sample.indices) total += abs(sample[i] - prev[i])
        val meanDelta = total.toDouble() / sample.size

        return when {
            meanDelta >= ProctoringConfig.MOTION_HIGH_THRESHOLD -> MotionLevel.HIGH
            meanDelta >= ProctoringConfig.MOTION_MEDIUM_THRESHOLD -> MotionLevel.MEDIUM
            meanDelta >= ProctoringConfig.MOTION_LOW_THRESHOLD -> MotionLevel.LOW
            else -> MotionLevel.NONE
        }
    }

    fun close() = detector.close()
}
