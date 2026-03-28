package com.example.ped

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ped.ui.components.CameraPreview
import com.example.ped.ui.components.SkeletonOverlay
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var poseLandmarker: PoseLandmarker? = null
    private val exerciseAnalyzer = ExerciseAnalyzer()
    private var lastResult: PoseLandmarkerResult? = null

    private val exerciseVideoIds = mapOf(
        ExerciseType.SQUAT to "l83R5PblSMA",
        ExerciseType.BICEP_CURL to "cBSD6mQIPQk",
        ExerciseType.PENDULUM to "MrmxEnuDN4s",
        ExerciseType.CROSSOVER_STRETCH to "aIq0fLi8iak",
        ExerciseType.EXTERNAL_ROTATION to "v1V5RiUBC8Y",
        ExerciseType.WALL_CLIMB to "kuJjYd-rdww"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission()
        setupPoseLandmarker()

        setContent {
            val scope = rememberCoroutineScope()
            var userProfile by remember { mutableStateOf<Profile?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var showSignUp by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    try {
                        userProfile = supabase.postgrest.from("profiles")
                            .select {
                                filter { eq("id", user.id) }
                            }.decodeSingle<Profile>()
                    } catch (e: Exception) {
                        Log.e("Supabase", "Profile fetch error", e)
                    }
                }
                isLoading = false
            }

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
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (userProfile == null) {
                        if (showSignUp) {
                            SignUpScreen(
                                onSignUpSuccess = { profile -> 
                                    userProfile = profile
                                    showSignUp = false
                                },
                                onBackToLogin = { showSignUp = false }
                            )
                        } else {
                            LoginScreen(
                                onLoginSuccess = { profile -> userProfile = profile },
                                onNavigateToSignUp = { showSignUp = true }
                            )
                        }
                    } else {
                        when (userProfile?.role) {
                            "doctor" -> DoctorDashboardScreen(
                                doctorProfile = userProfile!!,
                                onSignOut = {
                                    scope.launch {
                                        supabase.auth.signOut()
                                        userProfile = null
                                    }
                                }
                            )
                            "patient" -> PatientAppContent(
                                userProfile = userProfile!!,
                                onSignOut = {
                                    scope.launch {
                                        supabase.auth.signOut()
                                        userProfile = null
                                    }
                                }
                            )
                            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Unknown role: ${userProfile?.role}", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LoginScreen(onLoginSuccess: (Profile) -> Unit, onNavigateToSignUp: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PED Physio", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            supabase.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            val user = supabase.auth.currentUserOrNull()
                            if (user != null) {
                                val profile = supabase.postgrest.from("profiles")
                                    .select {
                                        filter { eq("id", user.id) }
                                    }.decodeSingle<Profile>()
                                onLoginSuccess(profile)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("LOGIN")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? Sign Up", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    @Composable
    fun SignUpScreen(onSignUpSuccess: (Profile) -> Unit, onBackToLogin: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf("patient") }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Create Account", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Register as:", color = Color.Gray, modifier = Modifier.align(Alignment.Start))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = selectedRole == "patient", onClick = { selectedRole = "patient" })
                Text("Patient", color = Color.White)
                Spacer(modifier = Modifier.width(24.dp))
                RadioButton(selected = selectedRole == "doctor", onClick = { selectedRole = "doctor" })
                Text("Doctor", color = Color.White)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    scope.launch {
                        try {
                            supabase.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                                data = buildJsonObject {
                                    put("role", selectedRole)
                                }
                            }
                            val user = supabase.auth.currentUserOrNull()
                            if (user != null) {
                                val newProfile = Profile(id = user.id, role = selectedRole)
                                // Use upsert to handle case where a database trigger might have already created the profile
                                try {
                                    supabase.postgrest.from("profiles").upsert(newProfile)
                                    onSignUpSuccess(newProfile)
                                } catch (e: Exception) {
                                    Log.e("SignUp", "Profile insert/upsert error", e)
                                    // If upsert fails, it might be RLS or schema issues. 
                                    // We attempt to fetch if it already exists as a fallback
                                    val existingProfile = supabase.postgrest.from("profiles")
                                        .select { filter { eq("id", user.id) } }
                                        .decodeSingleOrNull<Profile>()
                                    if (existingProfile != null) {
                                        onSignUpSuccess(existingProfile)
                                    } else {
                                        throw e
                                    }
                                }
                            } else {
                                // If email confirmation is enabled, user might be null here.
                                Toast.makeText(context, "Signup successful! Please check your email for confirmation.", Toast.LENGTH_LONG).show()
                                onBackToLogin()
                            }
                        } catch (e: Exception) {
                            Log.e("SignUp", "Error details", e)
                            Toast.makeText(context, "Sign up failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("SIGN UP")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBackToLogin) {
                Text("Already have an account? Login", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    @Composable
    fun PatientAppContent(userProfile: Profile, onSignOut: () -> Unit) {
        val scope = rememberCoroutineScope()
        val history = remember { mutableStateMapOf<LocalDate, MutableList<ExerciseLog>>() }
        val assignments = remember { mutableStateListOf<Assignment>() }
        val selectedDate = remember { mutableStateOf(LocalDate.now()) }

        LaunchedEffect(Unit) {
            try {
                val logs = supabase.postgrest.from("exercise_logs")
                    .select {
                        filter { eq("patient_id", userProfile.id) }
                    }.decodeList<ExerciseLog>()
                
                logs.forEach { log ->
                    try {
                        val date = if (log.timestamp != null) {
                            try { OffsetDateTime.parse(log.timestamp).toLocalDate() }
                            catch (_: Exception) { LocalDateTime.parse(log.timestamp).toLocalDate() }
                        } else LocalDate.now()
                        history.getOrPut(date) { mutableListOf() }.add(log)
                    } catch (e: Exception) {
                        Log.e("PatientApp", "Error parsing log date", e)
                    }
                }

                val fetchedAssignments = supabase.postgrest.from("assignments")
                    .select {
                        filter { eq("patient_id", userProfile.id) }
                    }.decodeList<Assignment>()
                assignments.clear()
                assignments.addAll(fetchedAssignments)
            } catch (e: Exception) {
                Log.e("Supabase", "Data fetch error", e)
            }
        }

        val currentRepCount = remember { mutableIntStateOf(0) }
        val feedbackText = remember { mutableStateOf("Stand in view") }
        val landmarksState = remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
        val activeExercise = remember { mutableStateOf<ExerciseType?>(null) }
        val outOfPositionJoints = remember { mutableStateOf<Set<Int>>(emptySet()) }
        
        val currentSet = remember { mutableIntStateOf(1) }
        val isBetweenSets = remember { mutableStateOf(false) }
        val isWaitingToStartFirstSet = remember { mutableStateOf(false) }
        val isShowingDemoDialog = remember { mutableStateOf(false) }
        val isShowingYoutubePlayer = remember { mutableStateOf(false) }
        val motivationMessage = remember { mutableStateOf("") }

        val currentEx = activeExercise.value
        if (currentEx == null) {
            DashboardScreen(
                userProfile = userProfile,
                history = history,
                assignments = assignments,
                selectedDate = selectedDate.value,
                onDateSelected = { selectedDate.value = it },
                onSelectExercise = { assignment ->
                    try {
                        val type = ExerciseType.valueOf(assignment.exerciseType.uppercase())
                        exerciseAnalyzer.setExercise(type)
                        exerciseAnalyzer.targetReps = assignment.targetReps
                        exerciseAnalyzer.totalSets = assignment.targetSets
                        activeExercise.value = type
                        currentRepCount.intValue = 0
                        feedbackText.value = "Get ready!"
                        currentSet.intValue = 1
                        isBetweenSets.value = false
                        isShowingDemoDialog.value = true
                        isWaitingToStartFirstSet.value = false
                        isShowingYoutubePlayer.value = false
                        exerciseAnalyzer.isBetweenSets = true
                    } catch (e: Exception) {
                        Log.e("PatientApp", "Error selecting exercise", e)
                    }
                },
                onSignOut = onSignOut
            )
        } else {
            ExerciseSessionScreen(
                exerciseType = currentEx,
                repCount = currentRepCount.intValue,
                currentSet = currentSet.intValue,
                totalSets = exerciseAnalyzer.totalSets,
                feedback = feedbackText.value,
                landmarks = landmarksState.value,
                outOfPositionJoints = outOfPositionJoints.value,
                isBetweenSets = isBetweenSets.value,
                isWaitingToStartFirstSet = isWaitingToStartFirstSet.value,
                isShowingDemoDialog = isShowingDemoDialog.value,
                isShowingYoutubePlayer = isShowingYoutubePlayer.value,
                videoId = exerciseVideoIds[currentEx] ?: "",
                motivation = motivationMessage.value,
                onStartDemo = {
                    isShowingDemoDialog.value = false
                    isShowingYoutubePlayer.value = true
                },
                onSkipDemo = {
                    isShowingDemoDialog.value = false
                    isWaitingToStartFirstSet.value = true
                },
                onCloseYoutube = {
                    isShowingYoutubePlayer.value = false
                    isWaitingToStartFirstSet.value = true
                },
                onStartFirstSet = {
                    isWaitingToStartFirstSet.value = false
                    exerciseAnalyzer.isBetweenSets = false
                },
                onNextSet = {
                    if (currentSet.intValue < exerciseAnalyzer.totalSets) {
                        exerciseAnalyzer.currentSet++
                        currentSet.intValue = exerciseAnalyzer.currentSet
                        exerciseAnalyzer.startNextSet()
                        isBetweenSets.value = false
                    } else {
                        scope.launch {
                            val log = ExerciseLog(
                                patientId = userProfile.id,
                                exerciseType = currentEx.name,
                                repsCompleted = currentRepCount.intValue,
                                setsCompleted = currentSet.intValue
                            )
                            try {
                                supabase.postgrest.from("exercise_logs").insert(log)
                                history.getOrPut(LocalDate.now()) { mutableListOf() }.add(log)
                            } catch (e: Exception) {
                                Log.e("Supabase", "Log insert error", e)
                            }

                            activeExercise.value = null
                            landmarksState.value = emptyList()
                            outOfPositionJoints.value = emptySet()
                            exerciseAnalyzer.reset()
                        }
                    }
                },
                onExit = {
                    scope.launch {
                        val log = ExerciseLog(
                            patientId = userProfile.id,
                            exerciseType = currentEx.name,
                            repsCompleted = currentRepCount.intValue,
                            setsCompleted = currentSet.intValue
                        )
                        try {
                            supabase.postgrest.from("exercise_logs").insert(log)
                            history.getOrPut(LocalDate.now()) { mutableListOf() }.add(log)
                        } catch (e: Exception) {
                            Log.e("Supabase", "Log insert error", e)
                        }

                        activeExercise.value = null
                        landmarksState.value = emptyList()
                        outOfPositionJoints.value = emptySet()
                        exerciseAnalyzer.reset()
                    }
                },
                onPoseUpdate = { result ->
                    processPose(result, activeExercise, landmarksState, currentRepCount, feedbackText, outOfPositionJoints, isBetweenSets, currentSet, motivationMessage)
                }
            )
        }
    }

    @Composable
    fun DoctorDashboardScreen(doctorProfile: Profile, onSignOut: () -> Unit) {
        var patients by remember { mutableStateOf<List<Profile>>(emptyList()) }
        var selectedPatient by remember { mutableStateOf<Profile?>(null) }
        var patientLogs by remember { mutableStateOf<List<ExerciseLog>>(emptyList()) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            try {
                patients = supabase.postgrest.from("profiles")
                    .select {
                        filter { eq("role", "patient") }
                    }.decodeList<Profile>()
            } catch (e: Exception) {
                Log.e("Supabase", "Patients fetch error", e)
            }
        }

        LaunchedEffect(selectedPatient) {
            if (selectedPatient != null) {
                try {
                    patientLogs = supabase.postgrest.from("exercise_logs")
                        .select {
                            filter { eq("patient_id", selectedPatient!!.id) }
                        }.decodeList<ExerciseLog>()
                } catch (e: Exception) {
                    Log.e("Supabase", "Patient logs fetch error", e)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Doctor Dashboard", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                IconButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
                }
            }
            Text("User ID: ${doctorProfile.id}", color = Color.Gray, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(24.dp))

            if (selectedPatient == null) {
                Text("Select a Patient", style = MaterialTheme.typography.titleLarge, color = Color.White)
                LazyColumn {
                    items(patients) { patient ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { selectedPatient = patient },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF252429))
                        ) {
                            Text(patient.id, modifier = Modifier.padding(16.dp), color = Color.White)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedPatient = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Patient: ${selectedPatient!!.id.take(8)}...", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                var showAssignDialog by remember { mutableStateOf(false) }
                Button(onClick = { showAssignDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("ASSIGN EXERCISE")
                }

                if (showAssignDialog) {
                    AssignExerciseDialog(
                        onDismiss = { showAssignDialog = false },
                        onAssign = { type, reps, sets ->
                            scope.launch {
                                try {
                                    val assignment = Assignment(
                                        patientId = selectedPatient!!.id,
                                        exerciseType = type.name,
                                        targetReps = reps,
                                        targetSets = sets,
                                        doctorId = doctorProfile.id
                                    )
                                    supabase.postgrest.from("assignments").insert(assignment)
                                    showAssignDialog = false
                                } catch (e: Exception) {
                                    Log.e("Supabase", "Assignment error", e)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Exercise History", style = MaterialTheme.typography.titleMedium, color = Color.White)
                LazyColumn {
                    items(patientLogs) { log ->
                        val exType = try { ExerciseType.valueOf(log.exerciseType.uppercase()) } catch (_: Exception) { ExerciseType.SQUAT }
                        StatRow(exType, log.repsCompleted)
                    }
                }
            }
        }
    }

    @Composable
    fun AssignExerciseDialog(onDismiss: () -> Unit, onAssign: (ExerciseType, Int, Int) -> Unit) {
        var selectedType by remember { mutableStateOf(ExerciseType.SQUAT) }
        var reps by remember { mutableStateOf("10") }
        var sets by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF252429),
            title = { Text("Assign Exercise", color = Color.White) },
            text = {
                Column {
                    ExerciseType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedType = type }.padding(vertical = 4.dp)) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                            Text(type.name, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("Reps") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Sets") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onAssign(selectedType, reps.toIntOrNull() ?: 10, sets.toIntOrNull() ?: 3) }) {
                    Text("Assign")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
            }
        )
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
        outOfPositionJoints: MutableState<Set<Int>>,
        isBetweenSets: MutableState<Boolean>,
        currentSet: MutableIntState,
        motivationMessage: MutableState<String>
    ) {
        if (activeExercise.value == null) return

        if (result.landmarks().isEmpty() || result.worldLandmarks().isEmpty()) {
            landmarksState.value = emptyList()
            feedbackText.value = "Stand in view"
            return
        }

        val landmarks = result.landmarks()[0]
        val worldLandmarks = result.worldLandmarks()[0]
        
        val wasBetweenSets = exerciseAnalyzer.isBetweenSets
        exerciseAnalyzer.analyze(landmarks, worldLandmarks)
        val nowBetweenSets = exerciseAnalyzer.isBetweenSets
        
        if (!wasBetweenSets && nowBetweenSets) {
            motivationMessage.value = exerciseAnalyzer.getMotivation()
        }
        
        val processedPoints = landmarks.map { (1f - it.x()) to it.y() }
        
        runOnUiThread {
            landmarksState.value = processedPoints
            currentRepCount.intValue = exerciseAnalyzer.repCount
            feedbackText.value = exerciseAnalyzer.feedback
            outOfPositionJoints.value = exerciseAnalyzer.outOfPositionJoints.toSet()
            isBetweenSets.value = nowBetweenSets
            currentSet.intValue = exerciseAnalyzer.currentSet
        }
    }

    @Composable
    fun DashboardScreen(
        userProfile: Profile,
        history: Map<LocalDate, List<ExerciseLog>>,
        assignments: List<Assignment>,
        selectedDate: LocalDate,
        onDateSelected: (LocalDate) -> Unit,
        onSelectExercise: (Assignment) -> Unit,
        onSignOut: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Welcome,\nPatient",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                IconButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
                }
            }

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

                val dayLogs = history[selectedDate] ?: emptyList()
                
                if (dayLogs.isEmpty()) {
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
                    items(dayLogs) { log ->
                        val type = try { ExerciseType.valueOf(log.exerciseType.uppercase()) } catch (_: Exception) { ExerciseType.SQUAT }
                        StatRow(type, log.repsCompleted)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Assigned Exercises",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                items(assignments) { assignment ->
                    val typeStr = assignment.exerciseType.uppercase()
                    val type = try { ExerciseType.valueOf(typeStr) } catch (_: Exception) { null }
                    if (type != null) {
                        ModernExerciseCard(type) { onSelectExercise(assignment) }
                    }
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
        currentSet: Int,
        totalSets: Int,
        feedback: String,
        landmarks: List<Pair<Float, Float>>,
        outOfPositionJoints: Set<Int>,
        isBetweenSets: Boolean,
        isWaitingToStartFirstSet: Boolean,
        isShowingDemoDialog: Boolean,
        isShowingYoutubePlayer: Boolean,
        videoId: String,
        motivation: String,
        onStartDemo: () -> Unit,
        onSkipDemo: () -> Unit,
        onCloseYoutube: () -> Unit,
        onStartFirstSet: () -> Unit,
        onNextSet: () -> Unit,
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
                    text = "${exerciseType.name.replace("_", " ")} - SET $currentSet/$totalSets",
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

            if (isShowingDemoDialog) {
                SetDialog(
                    title = "Watch Demo?",
                    message = "Would you like to see a quick demonstration of this exercise?",
                    buttonText = "WATCH DEMO",
                    secondaryButtonText = "SKIP",
                    onConfirm = onStartDemo,
                    onSecondary = onSkipDemo
                )
            } else if (isShowingYoutubePlayer) {
                YoutubePlayerOverlay(videoId = videoId, onExit = onCloseYoutube)
            } else if (isWaitingToStartFirstSet) {
                SetDialog(
                    title = "Ready to start?",
                    message = "Position yourself and tap to begin your first set.",
                    buttonText = "START FIRST SET",
                    onConfirm = onStartFirstSet
                )
            } else if (isBetweenSets) {
                SetDialog(
                    title = if (currentSet < totalSets) "Set $currentSet Complete!" else "All Sets Complete!",
                    message = if (currentSet < totalSets) "$motivation\nReady for the next one?" else "Fantastic work! You've finished all sets.",
                    buttonText = if (currentSet < totalSets) "START NEXT SET" else "FINISH SESSION",
                    onConfirm = onNextSet
                )
            }
        }
    }

    @Composable
    fun YoutubePlayerOverlay(videoId: String, onExit: () -> Unit) {
        val lifecycleOwner = LocalLifecycleOwner.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16 / 9f)
                        .clip(RoundedCornerShape(16.dp)),
                    factory = { context ->
                        YouTubePlayerView(context).apply {
                            lifecycleOwner.lifecycle.addObserver(this)
                            val listener = object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    youTubePlayer.loadVideo(videoId, 0f)
                                }
                            }
                            val options = IFramePlayerOptions.Builder()
                                .controls(1)
                                .build()
                            initialize(listener, options)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onExit,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp).padding(horizontal = 32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("CLOSE DEMO & START", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun SetDialog(
        title: String, 
        message: String, 
        buttonText: String, 
        secondaryButtonText: String? = null,
        onConfirm: () -> Unit,
        onSecondary: (() -> Unit)? = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252429)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(buttonText, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    
                    if (secondaryButtonText != null && onSecondary != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = onSecondary,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(secondaryButtonText, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
