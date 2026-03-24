package com.example.ped

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

enum class ExerciseType {
    SQUAT, BICEP_CURL, PENDULUM, CROSSOVER_STRETCH, EXTERNAL_ROTATION, WALL_CLIMB
}

class ExerciseAnalyzer {
    var repCount = 0
    var feedback = "Stand in view"
    private var currentType = ExerciseType.BICEP_CURL
    
    // State machine & timers
    private var phase = 0 
    private var holdStartTime = 0L
    private var isHolding = false
    private var lastStateChangeTime = 0L

    // Setup Phase tracking
    private var setupStartTime = 0L
    private var isSetupPhase = true
    private val setupDuration = 6000L 

    // Joint tracking for UI
    val outOfPositionJoints = mutableSetOf<Int>()

    // Buffers for motion validation
    private val wristHistory = mutableListOf<Landmark>()
    private val timestampHistory = mutableListOf<Long>()
    private var lastWristY = 0f
    
    private val cameraInstructions = mapOf(
        ExerciseType.SQUAT to "📸 PLACE CAMERA AT WAIST HEIGHT (SIDE VIEW)",
        ExerciseType.BICEP_CURL to "📸 PLACE CAMERA AT CHEST HEIGHT (FRONT VIEW)",
        ExerciseType.PENDULUM to "📸 PLACE CAMERA AT WAIST HEIGHT (SIDE VIEW)",
        ExerciseType.CROSSOVER_STRETCH to "📸 PLACE CAMERA AT CHEST HEIGHT (FRONT VIEW)",
        ExerciseType.EXTERNAL_ROTATION to "📸 PLACE CAMERA AT ELBOW HEIGHT (FRONT VIEW)",
        ExerciseType.WALL_CLIMB to "📸 PLACE CAMERA AT SHOULDER HEIGHT (SIDE VIEW)"
    )

    fun setExercise(type: ExerciseType) {
        if (currentType != type) {
            currentType = type
            reset()
        }
    }

    fun reset() {
        repCount = if (currentType == ExerciseType.PENDULUM) 30 else 0
        feedback = "Get ready!"
        phase = 0
        holdStartTime = 0L
        isHolding = false
        lastStateChangeTime = 0L
        wristHistory.clear()
        timestampHistory.clear()
        lastWristY = 0f
        isSetupPhase = true
        setupStartTime = System.currentTimeMillis()
        outOfPositionJoints.clear()
    }

    fun analyze(norm: List<NormalizedLandmark>, world: List<Landmark>) {
        val now = System.currentTimeMillis()
        outOfPositionJoints.clear()
        
        if (isSetupPhase) {
            val elapsed = now - setupStartTime
            if (elapsed < setupDuration) {
                feedback = cameraInstructions[currentType] ?: "Position your camera"
                return
            } else {
                isSetupPhase = false
                feedback = "Ready! Start when you are."
            }
        }

        if (norm.size < 33 || world.size < 33) {
            feedback = "Step into the frame"
            return
        }

        if (!validateGlobalRules(norm, world)) return

        when (currentType) {
            ExerciseType.PENDULUM -> analyzePendulum(norm, world)
            ExerciseType.CROSSOVER_STRETCH -> analyzeCrossover(norm, world)
            ExerciseType.EXTERNAL_ROTATION -> analyzeExternalRotation(norm, world)
            ExerciseType.WALL_CLIMB -> analyzeWallClimb(norm, world)
            ExerciseType.SQUAT -> analyzeSquat(norm)
            ExerciseType.BICEP_CURL -> analyzeBicepCurl(norm)
        }
    }

    private fun validateGlobalRules(norm: List<NormalizedLandmark>, world: List<Landmark>): Boolean {
        val essential = listOf(11, 12, 13, 14, 15, 16, 23, 24)
        val missingCount = essential.count { getV(norm[it]) < 0.35f }
        if (missingCount > 0) {
            feedback = "Step into full view"
            return false
        }

        val sWidth = dist3D(world[11], world[12])
        if (sWidth < 0.001f) return true
        
        val zDiff = abs(world[11].z() - world[12].z()).toDouble()
        val torsoRotation = asin(min(1.0, zDiff / sWidth.toDouble())) * 180.0 / PI
        if (torsoRotation > 75.0) {
            feedback = "Face the camera more"
            return false
        }

        return true
    }

    private fun analyzePendulum(norm: List<NormalizedLandmark>, world: List<Landmark>) {
        val isLeft = getV(norm[15]) > getV(norm[16])
        val sIdx = if (isLeft) 11 else 12
        val wIdx = if (isLeft) 15 else 16
        val hIdx = if (isLeft) 23 else 24
        val kIdx = if (isLeft) 25 else 26
        val aIdx = if (isLeft) 27 else 28
        
        val s = world[sIdx]
        val w = world[wIdx]
        val h = world[hIdx]
        val k = world[kIdx]
        val a = world[aIdx]

        val kneeAngle = calculate3DAngle(h, k, a)
        val isKneeBent = kneeAngle < 172.0 && kneeAngle > 130.0

        val leanAngle = calculate3DAngle(Landmark.create(h.x(), h.y() - 1f, h.z()), h, s)
        val isLeaning = leanAngle > 12.0

        val isHanging = w.y() > s.y() + 0.12f

        if (!isKneeBent || !isLeaning || !isHanging) {
            feedback = when {
                !isKneeBent -> "Bend your knees slightly"
                !isLeaning -> "Lean forward a little"
                else -> "Let your arm hang freely"
            }
            if (!isKneeBent) { outOfPositionJoints.add(kIdx); outOfPositionJoints.add(if (isLeft) 26 else 25) }
            if (!isHanging) outOfPositionJoints.add(wIdx)
            isHolding = false
            return
        }

        val now = System.currentTimeMillis()
        wristHistory.add(w); timestampHistory.add(now)
        if (wristHistory.size > 100) { wristHistory.removeAt(0); timestampHistory.removeAt(0) }

        if (wristHistory.size >= 20) {
            val minX = wristHistory.minOf { it.x() }
            val maxX = wristHistory.maxOf { it.x() }
            val minZ = wristHistory.minOf { it.z() }
            val maxZ = wristHistory.maxOf { it.z() }
            val diameter = max(maxX - minX, maxZ - minZ)
            
            val distMoved = dist3D(wristHistory.last(), wristHistory[max(0, wristHistory.size - 6)])
            val isMovingSlowly = distMoved in 0.008f..0.12f

            if (diameter > 0.04f && isMovingSlowly) {
                if (!isHolding) {
                    isHolding = true; holdStartTime = now
                } else {
                    val elapsed = (now - holdStartTime) / 1000
                    val remaining = max(0, 30 - elapsed.toInt())
                    repCount = remaining
                    if (remaining <= 0) {
                        feedback = "Done! Great session."
                        isHolding = false
                    } else {
                        feedback = "Good slow circles! Keep going."
                    }
                }
            } else {
                feedback = if (!isMovingSlowly && diameter > 0.04f) "Move slower" else "Start making circles"
                isHolding = false
            }
        }
    }

    private fun analyzeCrossover(norm: List<NormalizedLandmark>, world: List<Landmark>) {
        val isLeft = getV(norm[15]) > getV(norm[16])
        val sIdx = if (isLeft) 11 else 12
        val eIdx = if (isLeft) 13 else 14
        val wIdx = if (isLeft) 15 else 16
        val oppSIdx = if (isLeft) 12 else 11
        
        val s = world[sIdx]
        val e = world[eIdx]
        val w = world[wIdx]
        val oppS = world[oppSIdx]

        val midX = (world[11].x() + world[12].x()) / 2f
        val crossedMidline = if (isLeft) w.x() < midX else w.x() > midX
        
        val shoulderLineVec = doubleArrayOf((oppS.x() - s.x()).toDouble(), 0.0, (oppS.z() - s.z()).toDouble())
        val upperArmVec = doubleArrayOf((e.x() - s.x()).toDouble(), 0.0, (e.z() - s.z()).toDouble())
        val angleToOppShoulder = angleBetweenVectors(shoulderLineVec, upperArmVec)
        val horizAdduction = 180.0 - angleToOppShoulder

        if (crossedMidline && horizAdduction >= 15.0) { 
            updateHoldTimer(20, "Good stretch! Hold there")
        } else {
            outOfPositionJoints.add(wIdx)
            feedback = "Pull arm across your chest"
            isHolding = false
        }
    }

    private fun analyzeExternalRotation(norm: List<NormalizedLandmark>, world: List<Landmark>) {
        val isLeft = getV(norm[15]) > getV(norm[16])
        val sIdx = if (isLeft) 11 else 12
        val eIdx = if (isLeft) 13 else 14
        val wIdx = if (isLeft) 15 else 16
        
        val s = world[sIdx]
        val e = world[eIdx]
        val w = world[wIdx]
        
        val elbowAngle = calculate3DAngle(s, e, w)
        val isElbow90 = elbowAngle in 70.0..115.0
        val isParallel = abs(w.y() - e.y()) < 0.15f
        
        val rotX = abs(w.x() - e.x())
        val rotZ = abs(w.z() - e.z())
        val rotationAngle = atan2(rotZ.toDouble(), rotX.toDouble()) * 180.0 / PI

        if (isElbow90 && isParallel) {
            if (rotationAngle > 12.0) {
                updateHoldTimer(20, "Great rotation! Hold")
            } else {
                feedback = "Rotate your hand outward"
                isHolding = false
            }
        } else {
            feedback = if (!isElbow90) "Keep elbow at 90°" else "Keep forearm level"
            outOfPositionJoints.add(wIdx)
            isHolding = false
        }
    }

    private fun analyzeWallClimb(norm: List<NormalizedLandmark>, world: List<Landmark>) {
        val isLeft = getV(norm[15]) > getV(norm[16])
        val sIdx = if (isLeft) 11 else 12
        val wIdx = if (isLeft) 15 else 16
        val hIdx = if (isLeft) 23 else 24
        
        val s = world[sIdx]
        val w = world[wIdx]
        val h = world[hIdx]

        val flexion = calculate3DAngle(h, s, w)
        if (flexion > 20.0) {
            if (w.y() < lastWristY - 0.001f || lastWristY == 0f) {
                lastWristY = w.y()
                feedback = "Climb higher!"
            } else if (flexion > 110.0) {
                updateHoldTimer(5, "Peak reach! Hold")
            } else {
                outOfPositionJoints.add(wIdx)
            }
        } else {
            outOfPositionJoints.add(wIdx)
            feedback = "Walk fingers up the wall"
            lastWristY = 0f
        }
    }

    private fun analyzeSquat(norm: List<NormalizedLandmark>) {
        val kneeAngle = calculateAngle(norm[23], norm[25], norm[27])
        val now = System.currentTimeMillis()
        if (phase == 0 && kneeAngle < 140) {
            phase = 1; lastStateChangeTime = now; feedback = "Lower..."
        } else if (phase == 1 && kneeAngle > 160 && now - lastStateChangeTime > 600L) {
            repCount++; phase = 0; feedback = "Great rep!"
        } else if (phase == 0) {
            outOfPositionJoints.add(25)
            outOfPositionJoints.add(26)
        }
    }

    private fun analyzeBicepCurl(norm: List<NormalizedLandmark>) {
        val elbowAngle = calculateAngle(norm[11], norm[13], norm[15])
        val now = System.currentTimeMillis()
        if (phase == 0 && elbowAngle < 80) {
            phase = 1; lastStateChangeTime = now; feedback = "Squeeze!"
        } else if (phase == 1 && elbowAngle > 130 && now - lastStateChangeTime > 600L) {
            repCount++; phase = 0; feedback = "Nice!"
        } else if (phase == 0) {
            outOfPositionJoints.add(13)
            outOfPositionJoints.add(14)
        }
    }

    private fun updateHoldTimer(targetSec: Int, activeMsg: String) {
        val now = System.currentTimeMillis()
        if (!isHolding) {
            isHolding = true; holdStartTime = now
        } else {
            val elapsed = (now - holdStartTime) / 1000
            if (elapsed >= targetSec) {
                repCount++
                feedback = "Done! (Complete)"; isHolding = false
            } else {
                feedback = "$activeMsg (${targetSec - elapsed}s)"
            }
        }
    }

    private fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val radians = atan2((c.y() - b.y()).toDouble(), (c.x() - b.x()).toDouble()) - 
                      atan2((a.y() - b.y()).toDouble(), (a.x() - b.x()).toDouble())
        var angle = abs(radians * 180.0 / PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    private fun calculate3DAngle(a: Landmark, b: Landmark, c: Landmark): Double {
        val v1 = doubleArrayOf((a.x() - b.x()).toDouble(), (a.y() - b.y()).toDouble(), (a.z() - b.z()).toDouble())
        val v2 = doubleArrayOf((c.x() - b.x()).toDouble(), (c.y() - b.y()).toDouble(), (c.z() - b.z()).toDouble())
        val dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2]
        val mag1 = sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2])
        val mag2 = sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2])
        val combinedMag = mag1 * mag2
        if (combinedMag == 0.0) return 0.0
        return acos(max(-1.0, min(1.0, dot / combinedMag))) * 180.0 / PI
    }

    private fun angleBetweenVectors(v1: DoubleArray, v2: DoubleArray): Double {
        val dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2]
        val mag1 = sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2])
        val mag2 = sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2])
        val combinedMag = mag1 * mag2
        if (combinedMag == 0.0) return 0.0
        return acos(max(-1.0, min(1.0, dot / combinedMag))) * 180.0 / PI
    }

    private fun dist3D(a: Landmark, b: Landmark): Float {
        val dx = (a.x() - b.x()).toDouble()
        val dy = (a.y() - b.y()).toDouble()
        val dz = (a.z() - b.z()).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz).toFloat()
    }

    private fun getV(lm: NormalizedLandmark): Float {
        return try {
            val v = lm.visibility()
            if (v.isPresent) v.get() else 0f
        } catch (_: Exception) { 0f }
    }
}
