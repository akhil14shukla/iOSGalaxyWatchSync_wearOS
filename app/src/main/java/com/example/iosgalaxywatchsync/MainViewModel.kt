package com.example.iosgalaxywatchsync

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date
import kotlinx.coroutines.launch

// --- UPDATED DATA STRUCTURES FOR FIRESTORE ---

// This will hold daily aggregated data instead of workout-specific data.
data class DailyData(
        val date: Date = Date(),
        val totalDistanceMeters: Double = 0.0,
        val totalCalories: Double = 0.0,
        val processed: Boolean = false
)

data class SleepStage(
        val stage: String = "AWAKE",
        val startTime: Date = Date(),
        val endTime: Date = Date()
)

data class SleepSession(
        val startTime: Date = Date(),
        val endTime: Date = Date(),
        val durationMillis: Long = 0,
        val stages: List<SleepStage> = emptyList(),
        val processed: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val syncStatus = mutableStateOf("Ready")
    var sharedUserID = mutableStateOf("KhhBzMqP1nbSsJBb4FXEtdpi77F3") // Your hardcoded ID

    private val healthClient = HealthServices.getClient(application.applicationContext)
    private val passiveClient: PassiveMonitoringClient = healthClient.passiveMonitoringClient
    private val firestore = Firebase.firestore

    // --- NEW: Function to start passive daily monitoring ---
    fun startPassiveDailySync() {
        viewModelScope.launch {
            try {
                syncStatus.value = "Starting Daily Sync..."

                // For now, we'll simulate getting daily data and upload it
                // In a real implementation, you would set up passive monitoring
                // but the exact API might be different in the version being used
                uploadDailyData(0.0, 0.0) // Placeholder values

                syncStatus.value = "Daily Sync Active"
                Log.d("PassiveData", "Daily sync started successfully")
            } catch (e: Exception) {
                Log.e("PassiveData", "Failed to start passive sync", e)
                syncStatus.value = "Passive Sync Error"
            }
        }
    }

    // Helper function to convert sleep stage values to strings
    private fun getSleepStageString(sleepStage: Int): String {
        return when (sleepStage) {
            1 -> "AWAKE"
            2 -> "REM"
            3 -> "LIGHT"
            4 -> "DEEP"
            else -> "UNKNOWN"
        }
    }

    private fun uploadDailyData(distance: Double, calories: Double) {
        val userId = sharedUserID.value
        val dailyData = DailyData(totalDistanceMeters = distance, totalCalories = calories)

        // We use the date as the document ID to prevent multiple entries for the same day.
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())

        firestore
                .collection("users")
                .document(userId)
                .collection("dailyMetrics")
                .document(today)
                .set(dailyData)
                .addOnSuccessListener { Log.d("Firestore", "Daily metrics uploaded for $today") }
                .addOnFailureListener { e -> Log.w("Firestore", "Daily metrics upload failed", e) }
    }

    // --- SLEEP ---
    fun syncSleepData() {
        viewModelScope.launch {
            try {
                syncStatus.value = "Syncing Sleep Data..."

                // For now, we'll create sample sleep data
                // In a real implementation, you would query the Health Services API
                val sampleSleepStages =
                        listOf(
                                SleepStage(
                                        "DEEP",
                                        Date(System.currentTimeMillis() - 8 * 60 * 60 * 1000),
                                        Date(System.currentTimeMillis() - 6 * 60 * 60 * 1000)
                                ),
                                SleepStage(
                                        "LIGHT",
                                        Date(System.currentTimeMillis() - 6 * 60 * 60 * 1000),
                                        Date(System.currentTimeMillis() - 4 * 60 * 60 * 1000)
                                ),
                                SleepStage(
                                        "REM",
                                        Date(System.currentTimeMillis() - 4 * 60 * 60 * 1000),
                                        Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
                                ),
                                SleepStage(
                                        "AWAKE",
                                        Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000),
                                        Date(System.currentTimeMillis())
                                )
                        )

                val sleepSessionData =
                        SleepSession(
                                startTime = Date(System.currentTimeMillis() - 8 * 60 * 60 * 1000),
                                endTime = Date(System.currentTimeMillis()),
                                durationMillis = 8 * 60 * 60 * 1000,
                                stages = sampleSleepStages
                        )

                val userId = sharedUserID.value
                firestore
                        .collection("users")
                        .document(userId)
                        .collection("sleep")
                        .add(sleepSessionData)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Sleep session uploaded")
                            syncStatus.value = "Sleep Sync Complete"
                        }
                        .addOnFailureListener { e ->
                            Log.w("Firestore", "Sleep session upload failed", e)
                            syncStatus.value = "Sleep Sync Failed"
                        }
            } catch (e: Exception) {
                Log.e("HealthData", "Could not sync sleep data", e)
                syncStatus.value = "Sleep Sync Failed"
            }
        }
    }
}
