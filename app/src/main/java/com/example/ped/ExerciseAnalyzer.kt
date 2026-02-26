package com.example.ped

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.atan2

enum class ExerciseType {
    SQUAT, BICEP_CURL
}

class ExerciseAnalyzer {
    var repCount = 0
    var feedback = "Stand in view"
    private var isHalfway = false
    private var currentType = ExerciseType.BICEP_CURL
    
    // Better state tracking
    private var lastStateChangeTime = 0L
    private val COOLDOWN = 500L // 0.5s between state changes to prevent double counting

    fun setExercise(type: ExerciseType) {
        if (currentType != type) {
            currentType = type
            reset()
        }
    }

    fun reset() {
        repCount = 0
        feedback = "Get ready!"
        isHalfway = false
    }

    fun analyze(landmarks: List<NormalizedLandmark>) {
        if (landmarks.size < 33) {
            feedback = "Make sure your full body is visible"
            return
        }

        when (currentType) {
            ExerciseType.SQUAT -> analyzeSquat(landmarks)
            ExerciseType.BICEP_CURL -> analyzeBicepCurl(landmarks)
        }
    }

    private fun analyzeSquat(landmarks: List<NormalizedLandmark>) {
        // Hip(23,24), Knee(25,26), Ankle(27,28)
        val leftAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])
        val rightAngle = calculateAngle(landmarks[24], landmarks[26], landmarks[28])
        val avgAngle = (leftAngle + rightAngle) / 2

        val currentTime = System.currentTimeMillis()

        if (avgAngle < 100) { // Going down
            if (!isHalfway && currentTime - lastStateChangeTime > COOLDOWN) {
                isHalfway = true
                feedback = "Good depth! Now stand up"
                lastStateChangeTime = currentTime
            }
        } else if (isHalfway && avgAngle > 160) { // Coming up
            if (currentTime - lastStateChangeTime > COOLDOWN) {
                repCount++
                isHalfway = false
                feedback = "Great rep! Next one."
                lastStateChangeTime = currentTime
            }
        } else if (!isHalfway && avgAngle > 170) {
            feedback = "Lower your hips to parallel"
        }
    }

    private fun analyzeBicepCurl(landmarks: List<NormalizedLandmark>) {
        // Shoulder(11,12), Elbow(13,14), Wrist(15,16)
        val leftAngle = calculateAngle(landmarks[11], landmarks[13], landmarks[15])
        val rightAngle = calculateAngle(landmarks[12], landmarks[14], landmarks[16])
        
        // Use the arm that is more active (smaller angle means more curled)
        val activeAngle = if (leftAngle < rightAngle) leftAngle else rightAngle
        
        val currentTime = System.currentTimeMillis()

        if (activeAngle < 45) { // Curled up
            if (!isHalfway && currentTime - lastStateChangeTime > COOLDOWN) {
                isHalfway = true
                feedback = "Nice squeeze! Lower slowly"
                lastStateChangeTime = currentTime
            }
        } else if (isHalfway && activeAngle > 150) { // Extended down
            if (currentTime - lastStateChangeTime > COOLDOWN) {
                repCount++
                isHalfway = false
                feedback = "Perfect form! Keep it up"
                lastStateChangeTime = currentTime
            }
        } else if (!isHalfway && activeAngle > 150) {
            feedback = "Curl all the way up"
        }
    }

    private fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val radians = atan2(c.y() - b.y(), c.x() - b.x()) - atan2(a.y() - b.y(), a.x() - b.x())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }
}
