package com.example.ped.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun SkeletonOverlay(
    points: List<Pair<Float, Float>>, 
    showFullBody: Boolean,
    outOfPositionJoints: Set<Int> = emptySet()
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 33) return@Canvas

        val w = size.width
        val h = size.height

        fun getP(i: Int) = Offset(points[i].first * w, points[i].second * h)

        val joints = mutableListOf(11, 12, 13, 14, 15, 16)
        val bones = mutableListOf(
            11 to 12, // Shoulders
            11 to 13, 13 to 15, // Left arm
            12 to 14, 14 to 16  // Right arm
        )

        if (showFullBody) {
            joints.addAll(listOf(23, 24, 25, 26, 27, 28))
            bones.addAll(listOf(
                23 to 24, // Hips
                11 to 23, 12 to 24, // Torso
                23 to 25, 25 to 27, // Left leg
                24 to 26, 26 to 28  // Right leg
            ))
        }

        bones.forEach { (start, end) ->
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = getP(start),
                end = getP(end),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }

        joints.forEach { index ->
            val isOutOfPosition = outOfPositionJoints.contains(index)
            drawCircle(
                color = if (isOutOfPosition) Color.Red else Color(0xFF00E5FF),
                radius = if (isOutOfPosition) 12f else 8f,
                center = getP(index)
            )
        }
    }
}
