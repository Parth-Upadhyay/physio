package com.example.ped

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.atan2

class BicepCurlAnalyzer {
    var repCount = 0
        private set
    var feedback = "Stand in view"
        private set
    private var isCurled = false

    fun analyze(landmarks: List<NormalizedLandmark>) {
        if (landmarks.size < 17) {
            feedback = "Stand in view"
            return
        }

        // Bicep Curl Logic: Shoulder(11/12), Elbow(13/14), Wrist(15/16)
        val leftAngle = calculateAngle(landmarks[11], landmarks[13], landmarks[15])
        val rightAngle = calculateAngle(landmarks[12], landmarks[14], landmarks[16])
        
        // Track the one that is more curled
        val angle = minOf(leftAngle, rightAngle)

        if (angle < 35) {
            if (!isCurled) {
                isCurled = true
                feedback = "Now Lower Slowly"
            }
        } else if (isCurled && angle > 160) {
            repCount++
            isCurled = false
            feedback = "Great! Next Rep."
        } else if (!isCurled && angle > 160) {
            feedback = "Curl your arm fully"
        }
    }

    private fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val radians = atan2(c.y() - b.y(), c.x() - b.x()) - atan2(a.y() - b.y(), a.x() - b.x())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }
}
