package com.example.ped

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.atan2

class SquatAnalyzer {
    var repCount = 0
        private set
    var feedback = "Stand in view"
        private set
    private var isDown = false

    fun analyze(landmarks: List<NormalizedLandmark>): List<Pair<Float, Float>> {
        if (landmarks.isEmpty()) {
            feedback = "Stand in view"
            return emptyList()
        }

        // Squat Logic: Hip(24), Knee(26), Ankle(28)
        // Note: Landmarks 24, 26, 28 are standard for Pose Landmarker
        val angle = calculateAngle(landmarks[24], landmarks[26], landmarks[28])

        if (angle < 95) {
            isDown = true
            feedback = "Now Stand Up!"
        } else if (isDown && angle > 160) {
            repCount++
            isDown = false
            feedback = "Great! Keep going."
        } else if (!isDown && angle > 160) {
            feedback = "Lower your hips"
        }

        return landmarks.map { it.x() to it.y() }
    }

    private fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val radians = atan2(c.y() - b.y(), c.x() - b.x()) - atan2(a.y() - b.y(), a.x() - b.x())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }
}
