package com.example.ped

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ped.ui.components.CameraPreview
import com.example.ped.ui.components.SkeletonOverlay
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class MainActivity : ComponentActivity() {

    private var poseLandmarker: PoseLandmarker? = null
    private val exerciseAnalyzer = ExerciseAnalyzer()

    // Dashboard State (Session persistent)
    private val totalCurlsToday = mutableIntStateOf(0)
    private val totalSquatsToday = mutableIntStateOf(0)

    // Current Session State
    private val currentRepCount = mutableIntStateOf(0)
    private val feedbackText = mutableStateOf("Stand in view")
    private val landmarksState = mutableStateOf<List<Pair<Float, Float>>>(emptyList())
    private val activeExercise = mutableStateOf<ExerciseType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission()
        setupPoseLandmarker()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val currentEx = activeExercise.value
                    if (currentEx == null) {
                        DashboardScreen(
                            curlsCount = totalCurlsToday.intValue,
                            squatsCount = totalSquatsToday.intValue,
                            onSelectExercise = { type ->
                                exerciseAnalyzer.setExercise(type)
                                activeExercise.value = type
                                currentRepCount.intValue = 0
                                feedbackText.value = "Get ready!"
                            }
                        )
                    } else {
                        ExerciseSessionScreen(
                            exerciseType = currentEx,
                            repCount = currentRepCount.intValue,
                            feedback = feedbackText.value,
                            landmarks = landmarksState.value,
                            onExit = {
                                if (currentEx == ExerciseType.BICEP_CURL) {
                                    totalCurlsToday.intValue += currentRepCount.intValue
                                } else {
                                    totalSquatsToday.intValue += currentRepCount.intValue
                                }
                                activeExercise.value = null
                                landmarksState.value = emptyList()
                                exerciseAnalyzer.reset()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestCameraPermission() {
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> processPose(result) }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("MediaPipe", "Init failed", e)
        }
    }

    private fun processPose(result: PoseLandmarkerResult) {
        if (activeExercise.value == null) return

        if (result.landmarks().isEmpty()) {
            landmarksState.value = emptyList()
            feedbackText.value = "Stand in view"
            return
        }

        val landmarks = result.landmarks()[0]
        exerciseAnalyzer.analyze(landmarks)
        val processedPoints = landmarks.map { (1f - it.x()) to it.y() }
        
        runOnUiThread {
            landmarksState.value = processedPoints
            currentRepCount.intValue = exerciseAnalyzer.repCount
            feedbackText.value = exerciseAnalyzer.feedback
        }
    }

    @Composable
    fun DashboardScreen(curlsCount: Int, squatsCount: Int, onSelectExercise: (ExerciseType) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("Fitness Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Today's Summary", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Curls", curlsCount, Color(0xFFBB86FC), Modifier.weight(1f))
                    StatCard("Squats", squatsCount, Color(0xFF03DAC5), Modifier.weight(1f))
                }
            }

            item {
                Text("Select Exercise", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 16.dp))
            }

            item {
                ExerciseListItem("Bicep Curls", "Focus on full range and control.", Color(0xFFBB86FC)) { onSelectExercise(ExerciseType.BICEP_CURL) }
            }

            item {
                ExerciseListItem("Air Squats", "Lower hips to parallel. Keep chest up.", Color(0xFF03DAC5)) { onSelectExercise(ExerciseType.SQUAT) }
            }
        }
    }

    @Composable
    fun StatCard(label: String, count: Int, color: Color, modifier: Modifier) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(label, color = color, fontWeight = FontWeight.SemiBold)
                Text(count.toString(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }

    @Composable
    fun ExerciseListItem(title: String, description: String, color: Color, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 40.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
    }

    @Composable
    fun ExerciseSessionScreen(exerciseType: ExerciseType, repCount: Int, feedback: String, landmarks: List<Pair<Float, Float>>, onExit: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(onImageCaptured = { bitmap, rotation ->
                val matrix = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()) }
                val correctedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                val mpImage = BitmapImageBuilder(correctedBitmap).build()
                poseLandmarker?.detectAsync(mpImage, SystemClock.uptimeMillis())
            })
            SkeletonOverlay(landmarks, showFullBody = exerciseType == ExerciseType.SQUAT)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.4f)))))
            IconButton(onClick = onExit, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(0.5f), RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(exerciseType.name.replace("_", " "), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.6f))
                Text(repCount.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize = 100.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(feedback.uppercase(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (feedback.contains("Great", true) || feedback.contains("Perfect", true)) Color(0xFF00FF9D) else Color.White))
            }
        }
    }
}
