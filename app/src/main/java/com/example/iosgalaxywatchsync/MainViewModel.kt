package com.example.iosgalaxywatchsync

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iosgalaxywatchsync.models.*
import com.example.iosgalaxywatchsync.sync.HybridSyncManager
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val syncStatus = mutableStateOf("Ready")
    val context = application.applicationContext

    private val healthClient = HealthServices.getClient(application.applicationContext)
    private val passiveClient: PassiveMonitoringClient = healthClient.passiveMonitoringClient

    // Replace Firebase with HybridSyncManager
    private val hybridSyncManager = HybridSyncManager(application.applicationContext)

    init {
        // Observe sync state
        viewModelScope.launch {
            hybridSyncManager.syncState.collect { state ->
                syncStatus.value =
                        when {
                            state.pendingDataCount > 0 -> "Pending: ${state.pendingDataCount} items"
                            state.lastSyncSuccess -> "Last sync: ${state.lastSyncMethod.name}"
                            state.lastSyncError != null -> "Error: ${state.lastSyncError}"
                            else -> "Ready"
                        }
            }
        }

        // Start BLE advertising for fallback sync
        hybridSyncManager.startBLEAdvertising()
    }

    /** Start passive daily monitoring and sync */
    fun startPassiveDailySync() {
        viewModelScope.launch {
            try {
                syncStatus.value = "Starting Daily Sync..."

                // Simulate daily data collection
                val dailyData = collectDailyHealthData()

                // Add to hybrid sync manager
                hybridSyncManager.addHealthData(listOf(dailyData))

                // Trigger sync
                val success = hybridSyncManager.startSync()

                if (success) {
                    syncStatus.value = "Daily Sync Complete"
                } else {
                    syncStatus.value = "Daily Sync Failed - Will retry"
                }

                Log.d("PassiveData", "Daily sync completed: $success")
            } catch (e: Exception) {
                Log.e("PassiveData", "Failed to start passive sync", e)
                syncStatus.value = "Passive Sync Error"
            }
        }
    }

    /** Sync sleep data */
    fun syncSleepData() {
        viewModelScope.launch {
            try {
                syncStatus.value = "Syncing Sleep Data..."

                // Create sample sleep session data
                val sleepData = createSampleSleepData()

                // Add to hybrid sync manager
                hybridSyncManager.addHealthData(listOf(sleepData))

                // Trigger sync
                val success = hybridSyncManager.startSync()

                if (success) {
                    syncStatus.value = "Sleep Sync Complete"
                } else {
                    syncStatus.value = "Sleep Sync Failed - Will retry"
                }

                Log.d("SleepData", "Sleep sync completed: $success")
            } catch (e: Exception) {
                Log.e("HealthData", "Could not sync sleep data", e)
                syncStatus.value = "Sleep Sync Failed"
            }
        }
    }

    /** Manual sync trigger */
    fun triggerManualSync() {
        viewModelScope.launch {
            try {
                syncStatus.value = "Manual Sync Starting..."

                val success = hybridSyncManager.startSync()

                if (success) {
                    syncStatus.value = "Manual Sync Complete"
                } else {
                    syncStatus.value = "Manual Sync Failed"
                }
            } catch (e: Exception) {
                Log.e("ManualSync", "Manual sync failed", e)
                syncStatus.value = "Manual Sync Error"
            }
        }
    }

    /** Check server connectivity */
    fun checkServerConnection() {
        viewModelScope.launch {
            val isAvailable = hybridSyncManager.checkServerAvailability()
            val url = hybridSyncManager.getServerUrl()

            syncStatus.value =
                    if (isAvailable) {
                        "Server OK: $url"
                    } else {
                        "Server Unavailable - BLE Fallback"
                    }
        }
    }

    /** Configure server URL */
    fun setServerUrl(url: String) {
        hybridSyncManager.setServerUrl(url)
        syncStatus.value = "Server URL Updated: $url"
    }

    /** Get sync statistics */
    fun getSyncStats(): String {
        val stats = hybridSyncManager.getSyncStats()
        return "Unsynced: ${stats["unsyncedDataCount"]}, " +
                "Server: ${if (stats["isServerAvailable"] == true) "OK" else "OFFLINE"}, " +
                "Device: ${stats["deviceId"]}"
    }

    private fun collectDailyHealthData(): HealthDataEntry {
        // In a real implementation, this would collect actual health data
        // For now, we'll create sample data

        val data =
                mapOf(
                        "date" to Date().time,
                        "totalDistanceMeters" to (5000.0 + Math.random() * 10000.0),
                        "totalCalories" to (1500.0 + Math.random() * 1000.0),
                        "steps" to (8000 + Math.random() * 5000).toInt(),
                        "activeMinutes" to (45 + Math.random() * 60).toInt()
                )

        return HealthDataEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = HealthDataType.DAILY_METRICS,
                data = data,
                source = "Galaxy Watch"
        )
    }

    private fun createSampleSleepData(): HealthDataEntry {
        val sleepStages =
                listOf(
                        mapOf(
                                "stage" to "DEEP",
                                "startTime" to (System.currentTimeMillis() - 8 * 60 * 60 * 1000),
                                "endTime" to (System.currentTimeMillis() - 6 * 60 * 60 * 1000)
                        ),
                        mapOf(
                                "stage" to "LIGHT",
                                "startTime" to (System.currentTimeMillis() - 6 * 60 * 60 * 1000),
                                "endTime" to (System.currentTimeMillis() - 4 * 60 * 60 * 1000)
                        ),
                        mapOf(
                                "stage" to "REM",
                                "startTime" to (System.currentTimeMillis() - 4 * 60 * 60 * 1000),
                                "endTime" to (System.currentTimeMillis() - 2 * 60 * 60 * 1000)
                        ),
                        mapOf(
                                "stage" to "AWAKE",
                                "startTime" to (System.currentTimeMillis() - 2 * 60 * 60 * 1000),
                                "endTime" to System.currentTimeMillis()
                        )
                )

        val data =
                mapOf(
                        "startTime" to (System.currentTimeMillis() - 8 * 60 * 60 * 1000),
                        "endTime" to System.currentTimeMillis(),
                        "durationMillis" to (8 * 60 * 60 * 1000),
                        "stages" to sleepStages,
                        "sleepQuality" to (60 + Math.random() * 40).toInt()
                )

        return HealthDataEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = HealthDataType.SLEEP_SESSION,
                data = data,
                source = "Galaxy Watch"
        )
    }

    override fun onCleared() {
        super.onCleared()
        hybridSyncManager.cleanup()
    }
}
