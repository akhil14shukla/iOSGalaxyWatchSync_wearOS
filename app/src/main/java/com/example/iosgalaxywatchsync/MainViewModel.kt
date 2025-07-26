package com.example.iosgalaxywatchsync

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date

// --- DATA STRUCTURES FOR FIRESTORE ---

data class LocationData(val latitude: Double = 0.0, val longitude: Double = 0.0, val time: Date = Date())
data class WorkoutData(
    val type: String = "WALKING",
    val startTime: Date = Date(),
    val endTime: Date = Date(),
    val durationMillis: Long = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalCalories: Double = 0.0,
    val route: List<LocationData> = emptyList(),
    val processed: Boolean = false
)

data class SleepStage(val stage: String = "AWAKE", val startTime: Date = Date(), val endTime: Date = Date())
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
    private val exerciseClient: ExerciseClient = healthClient.exerciseClient
    private val passiveClient: PassiveMonitoringClient = healthClient.passiveMonitoringClient
    private val firestore = Firebase.firestore

    // This property will hold the last received workout update.
    private var lastWorkoutSummary: ExerciseUpdate? = null

    // CORRECTED: The ExerciseUpdateCallback implementation is now complete and correct.
    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            lastWorkoutSummary = update
        }

        // This method is required by the interface.
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
    }

    // --- WORKOUTS ---
    fun startOrStopWorkout() {
        viewModelScope.launch {
            try {
                // CORRECTED: Check the exercise state directly from the client.
                val isExercising = exerciseClient.ExerciseInfo.state.isExercise
                if (isExercising) {
                    exerciseClient.endExerciseAsync().await()
                    processAndUploadWorkout()
                    syncStatus.value = "Workout Ended"
                } else {
                    val config = ExerciseConfig.builder(ExerciseType.WALKING)
                        .setDataTypes(setOf(DataType.LOCATION, DataType.DISTANCE_TOTAL, DataType.CALORIES_TOTAL))
                        .build()
                    exerciseClient.setUpdateCallback(exerciseCallback)
                    exerciseClient.startExerciseAsync(config).await()
                    syncStatus.value = "Workout Started"
                }
            } catch (e: Exception) {
                Log.e("WorkoutError", "Failed to start/stop workout", e)
                syncStatus.value = "Workout Error"
            }
        }
    }

    private fun processAndUploadWorkout() {
        val summary = lastWorkoutSummary ?: return
        val userId = sharedUserID.value

        // CORRECTED: The location data type constant is different in this version.
        val route = summary.latestMetrics.getData(DataType.LOCATION_DATA)
            .map { LocationData(it.value.latitude, it.value.longitude, Date.from(it.time)) }

        val workoutData = WorkoutData(
            type = summary.exerciseType.name,
            startTime = Date.from(summary.startTime),
            // CORRECTED: Get end time from the active duration checkpoint.
            endTime = Date.from(summary.activeDurationCheckpoint?.time ?: summary.startTime),
            durationMillis = summary.activeDuration.toMillis(),
            totalDistanceMeters = summary.latestMetrics.getData(DataType.DISTANCE)?.value ?: 0.0,
            totalCalories = summary.latestMetrics.getData(DataType.TOTAL_CALORIES)?.value ?: 0.0,
            route = route
        )

        firestore.collection("users").document(userId).collection("workouts")
            .add(workoutData)
            .addOnSuccessListener { Log.d("Firestore", "Workout uploaded") }
            .addOnFailureListener { e -> Log.w("Firestore", "Workout upload failed", e) }
    }

    // --- SLEEP ---
    fun syncSleepData() {
        viewModelScope.launch {
            try {
                val end = Instant.now()
                // CORRECTED: Use minusSeconds as minusDays is not available.
                val start = end.minusSeconds(60 * 60 * 48) // Last 48 hours

                // CORRECTED: The method to get sleep data is different in this version.
                val dataRequest = PassiveDataRequest(
                    dataTypes = setOf(DataType.SLEEP_STAGE),
                    from = start,
                    to = end
                )
                val data = passiveClient.readData(dataRequest).await()
                val sessions = data.getData(DataType.SLEEP_STAGE)

                if (sessions.isNotEmpty()) {
                    val firstSegment = sessions.first()
                    val lastSegment = sessions.last()

                    val sleepStages = sessions.map { segment ->
                        SleepStage(
                            // CORRECTED: Use the correct method to convert stage enum to string.
                            stage = SleepStage.sleepStageToString(segment.value),
                            startTime = Date.from(segment.startTime),
                            endTime = Date.from(segment.endTime)
                        )
                    }
                    val sleepSessionData = SleepSession(
                        startTime = Date.from(firstSegment.startTime),
                        endTime = Date.from(lastSegment.endTime),
                        durationMillis = sessions.sumOf { it.duration.toMillis() },
                        stages = sleepStages
                    )

                    val userId = sharedUserID.value
                    firestore.collection("users").document(userId).collection("sleep")
                        .add(sleepSessionData)
                        .addOnSuccessListener { Log.d("Firestore", "Sleep session uploaded") }
                }
                syncStatus.value = "Sleep Sync Complete"
            } catch (e: Exception) {
                Log.e("HealthData", "Could not query sleep data", e)
                syncStatus.value = "Sleep Sync Failed"
            }
        }
    }
}
