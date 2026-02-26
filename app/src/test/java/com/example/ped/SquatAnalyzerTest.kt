package com.example.ped

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SquatAnalyzerTest {

    private lateinit var analyzer: SquatAnalyzer

    @Before
    fun setup() {
        analyzer = SquatAnalyzer()
    }

    @Test
    fun `initial state is correct`() {
        assertEquals(0, analyzer.repCount)
        assertEquals("Stand in view", analyzer.feedback)
    }

    @Test
    fun `empty landmarks returns empty list and resets feedback`() {
        analyzer.analyze(emptyList())
        assertEquals("Stand in view", analyzer.feedback)
    }

    @Test
    fun `detects squat movement`() {
        // Create 33 landmarks (standard MediaPipe Pose)
        val landmarks = MutableList(33) { NormalizedLandmark.create(0.5f, 0.5f, 0.0f) }

        // 1. Standing position (Angle ~180)
        landmarks[24] = NormalizedLandmark.create(0.5f, 0.4f, 0.0f)
        landmarks[26] = NormalizedLandmark.create(0.5f, 0.6f, 0.0f)
        landmarks[28] = NormalizedLandmark.create(0.5f, 0.8f, 0.0f)
        
        analyzer.analyze(landmarks)
        assertEquals(0, analyzer.repCount)
        assertEquals("Lower your hips", analyzer.feedback)

        // 2. Down position (Angle < 95)
        // Simple 90 deg: Hip(0,1), Knee(0,0), Ankle(1,0)
        landmarks[24] = NormalizedLandmark.create(0.0f, 1.0f, 0.0f)
        landmarks[26] = NormalizedLandmark.create(0.0f, 0.0f, 0.0f)
        landmarks[28] = NormalizedLandmark.create(1.0f, 0.0f, 0.0f)
        
        analyzer.analyze(landmarks)
        assertEquals("Now Stand Up!", analyzer.feedback)
        assertEquals(0, analyzer.repCount)

        // 3. Back up (Angle > 160)
        landmarks[24] = NormalizedLandmark.create(0.0f, 1.0f, 0.0f)
        landmarks[26] = NormalizedLandmark.create(0.0f, 0.0f, 0.0f)
        landmarks[28] = NormalizedLandmark.create(0.0f, -1.0f, 0.0f)
        
        analyzer.analyze(landmarks)
        assertEquals(1, analyzer.repCount)
        assertEquals("Great! Keep going.", analyzer.feedback)
    }
}
