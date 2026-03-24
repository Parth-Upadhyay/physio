package com.example.ped

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var poseLandmarker: PoseLandmarker? = null
    private val exerciseAnalyzer = ExerciseAnalyzer()
    private var lastResult: PoseLandmarkerResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission()
        setupPoseLandmarker()

        setContent {
            val context = LocalContext.current
            val history = remember { mutableStateMapOf<LocalDate, MutableMap<ExerciseType, Int>>() }
            val selectedDate = remember { mutableStateOf(LocalDate.now()) }
            
            LaunchedEffect(Unit) {
                loadHistory(context, history)
            }

            val currentRepCount = remember { mutableIntStateOf(0) }
            val feedbackText = remember { mutableStateOf("Stand in view") }
            val landmarksState = remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
            val activeExercise = remember { mutableStateOf<ExerciseType?>(null) }
            val outOfPositionJoints = remember { mutableStateOf<Set<Int>>(emptySet()) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    secondary = Color(0xFFCCC2DC),
                    tertiary = Color(0xFFEFB8C8),
                    surface = Color(0xFF1C1B1F),
                    background = Color(0xFF1C1B1F)
                ),
                typography = Typography(
                    headlineLarge = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    titleLarge = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val currentEx = activeExercise.value
                    if (currentEx == null) {
                        DashboardScreen(
                            history = history,
                            selectedDate = selectedDate.value,
                            onDateSelected = { selectedDate.value = it },
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
                            outOfPositionJoints = outOfPositionJoints.value,
                            onExit = {
                                val today = LocalDate.now()
                                val dayHistory = history.getOrPut(today) { mutableMapOf() }
                                dayHistory[currentEx] = (dayHistory[currentEx] ?: 0) + currentRepCount.intValue
                                saveHistory(context, history)
                                
                                activeExercise.value = null
                                landmarksState.value = emptyList()
                                outOfPositionJoints.value = emptySet()
                                exerciseAnalyzer.reset()
                            },
                            onPoseUpdate = { result ->
                                processPose(result, activeExercise, landmarksState, currentRepCount, feedbackText, outOfPositionJoints)
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
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> 
                    lastResult = result
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("MediaPipe", "Init failed", e)
        }
    }

    private fun processPose(
        result: PoseLandmarkerResult,
        activeExercise: State<ExerciseType?>,
        landmarksState: MutableState<List<Pair<Float, Float>>>,
        currentRepCount: MutableIntState,
        feedbackText: MutableState<String>,
        outOfPositionJoints: MutableState<Set<Int>>
    ) {
        if (activeExercise.value == null) return

        if (result.landmarks().isEmpty() || result.worldLandmarks().isEmpty()) {
            landmarksState.value = emptyList()
            feedbackText.value = "Stand in view"
            return
        }

        val landmarks = result.landmarks()[0]
        val worldLandmarks = result.worldLandmarks()[0]
        
        exerciseAnalyzer.analyze(landmarks, worldLandmarks)
        
        val processedPoints = landmarks.map { (1f - it.x()) to it.y() }
        
        runOnUiThread {
            landmarksState.value = processedPoints
            currentRepCount.intValue = exerciseAnalyzer.repCount
            feedbackText.value = exerciseAnalyzer.feedback
            outOfPositionJoints.value = exerciseAnalyzer.outOfPositionJoints.toSet()
        }
    }

    private fun saveHistory(context: Context, history: Map<LocalDate, Map<ExerciseType, Int>>) {
        val prefs = context.getSharedPreferences("ped_history", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        history.forEach { (date, counts) ->
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val countsStr = counts.entries.joinToString(",") { "${it.key.name}:${it.value}" }
            editor.putString(dateStr, countsStr)
        }
        editor.apply()
    }

    private fun loadHistory(context: Context, history: MutableMap<LocalDate, MutableMap<ExerciseType, Int>>) {
        val prefs = context.getSharedPreferences("ped_history", Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            try {
                val date = LocalDate.parse(key, DateTimeFormatter.ISO_LOCAL_DATE)
                val countsStr = value as String
                if (countsStr.isNotEmpty()) {
                    val counts = countsStr.split(",").mapNotNull {
                        val parts = it.split(":")
                        try {
                            ExerciseType.valueOf(parts[0]) to parts[1].toInt()
                        } catch (e: Exception) {
                            null
                        }
                    }.toMap().toMutableMap()
                    if (counts.isNotEmpty()) {
                        history[date] = counts
                    }
                }
            } catch (e: Exception) {
                Log.e("History", "Failed to load entry: $key", e)
            }
        }
    }

    @Composable
    fun DashboardScreen(
        history: Map<LocalDate, Map<ExerciseType, Int>>,
        selectedDate: LocalDate,
        onDateSelected: (LocalDate) -> Unit,
        onSelectExercise: (ExerciseType) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = "Track Your\nProgress",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            val dates = remember { (-6..0).map { LocalDate.now().plusDays(it.toLong()) } }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(dates) { date ->
                    DateCard(
                        date = date,
                        isSelected = date == selectedDate,
                        onClick = { onDateSelected(date) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = if (selectedDate == LocalDate.now()) "Today's Summary" else "Summary for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM dd"))}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                val dayStats = history[selectedDate] ?: emptyMap()
                
                if (dayStats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No activity recorded", color = Color.Gray)
                        }
                    }
                } else {
                    items(dayStats.entries.toList()) { entry ->
                        StatRow(entry.key, entry.value)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All Exercises",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                items(ExerciseType.entries.toTypedArray()) { type ->
                    ModernExerciseCard(type) { onSelectExercise(type) }
                }
            }
        }
    }

    @Composable
    fun DateCard(date: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val dayNum = date.dayOfMonth.toString()
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
                .clickable { onClick() }
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.Black else Color.Gray
            )
            Text(
                text = dayNum,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color.White
            )
        }
    }

    @Composable
    fun StatRow(type: ExerciseType, count: Int) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    @Composable
    fun ModernExerciseCard(type: ExerciseType, onClick: () -> Unit) {
        val description = when (type) {
            ExerciseType.SQUAT -> "Lower hips to parallel. Keep chest up."
            ExerciseType.BICEP_CURL -> "Focus on full range and control."
            ExerciseType.PENDULUM -> "Loosen the shoulder with gentle circles."
            ExerciseType.CROSSOVER_STRETCH -> "Stretch the outer shoulder muscles."
            ExerciseType.EXTERNAL_ROTATION -> "Target the rotator cuff muscles."
            ExerciseType.WALL_CLIMB -> "Improve overhead range of motion."
        }
        
        val color = if (type == ExerciseType.SQUAT || type == ExerciseType.BICEP_CURL) Color(0xFFBB86FC) else Color(0xFF03DAC5)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252429)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    @Composable
    fun ExerciseSessionScreen(
        exerciseType: ExerciseType,
        repCount: Int,
        feedback: String,
        landmarks: List<Pair<Float, Float>>,
        outOfPositionJoints: Set<Int>,
        onExit: () -> Unit,
        onPoseUpdate: (PoseLandmarkerResult) -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(onImageCaptured = { bitmap, rotation ->
                val matrix = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()) }
                val correctedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                poseLandmarker?.detectAsync(BitmapImageBuilder(correctedBitmap).build(), SystemClock.uptimeMillis())
                lastResult?.let { onPoseUpdate(it) }
            })

            SkeletonOverlay(landmarks, showFullBody = true, outOfPositionJoints = outOfPositionJoints)
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.7f))
                        )
                    )
            )

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = exerciseType.name.replace("_", " "),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(0.6f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = repCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 64.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(0.8f))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feedback.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (feedback.contains("Great") || feedback.contains("Perfect") || feedback.contains("Excellent"))
                                   Color(0xFF00FF9D) else Color.White,
                        letterSpacing = 1.2.sp
                    )
                )
            }
        }
    }
}
