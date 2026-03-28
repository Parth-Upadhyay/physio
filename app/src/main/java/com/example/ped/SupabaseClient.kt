package com.example.ped

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

object SupabaseConfig {
    const val URL = "https://egsdngzkqcewonmouyjc.supabase.co"
    const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVnc2RuZ3prcWNld29ubW91eWpjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ0Njc2NDIsImV4cCI6MjA5MDA0MzY0Mn0.R-bHc7jOkaQkqljWE6crQatsOIjn03eEUEXmhRTvyR8"
}

val supabase = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.KEY
) {
    install(Auth)
    install(Postgrest)
}

@Serializable
data class Profile(
    @SerialName("id")
    val id: String,
    @SerialName("role")
    val role: String = "patient"
)

@Serializable
data class Assignment(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("doctor_id")
    val doctorId: String,
    @SerialName("patient_id")
    val patientId: String,
    @SerialName("exercise_type")
    val exerciseType: String,
    @SerialName("target_reps")
    val targetReps: Int,
    @SerialName("target_sets")
    val targetSets: Int,
    @SerialName("days_duration")
    val daysDuration: Int? = null
)

@Serializable
data class ExerciseLog(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("patient_id")
    val patientId: String,
    @SerialName("exercise_type")
    val exerciseType: String,
    @SerialName("reps_completed")
    val repsCompleted: Int,
    @SerialName("sets_completed")
    val setsCompleted: Int,
    @SerialName("created_at")
    val timestamp: String? = null
)
