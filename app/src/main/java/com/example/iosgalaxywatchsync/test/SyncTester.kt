package com.example.iosgalaxywatchsync.test

import android.content.Context
import android.util.Log
import com.example.iosgalaxywatchsync.models.HealthDataEntry
import com.example.iosgalaxywatchsync.models.HealthDataType
import com.example.iosgalaxywatchsync.sync.HybridSyncManager
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Test utilities for the hybrid sync system */
class SyncTester(private val context: Context) {

    companion object {
        private const val TAG = "SyncTester"
    }

    private val syncManager = HybridSyncManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Test the complete sync flow */
    fun runFullSyncTest() {
        scope.launch {
            try {
                Log.d(TAG, "Starting full sync test...")

                // Step 1: Generate test data
                val testData = generateTestHealthData()
                Log.d(TAG, "Generated ${testData.size} test entries")

                // Step 2: Add data to sync manager
                syncManager.addHealthData(testData)

                // Step 3: Test server availability
                val serverAvailable = syncManager.checkServerAvailability()
                Log.d(TAG, "Server available: $serverAvailable")

                // Step 4: Attempt sync
                val syncSuccess = syncManager.startSync()
                Log.d(TAG, "Sync completed: $syncSuccess")

                // Step 5: Check sync stats
                val stats = syncManager.getSyncStats()
                Log.d(TAG, "Sync stats: $stats")

                // Step 6: Test BLE advertising (if server failed)
                if (!serverAvailable) {
                    Log.d(TAG, "Testing BLE advertising...")
                    val bleStarted = syncManager.startBLEAdvertising()
                    Log.d(TAG, "BLE advertising started: $bleStarted")

                    // Stop after a short period
                    kotlinx.coroutines.delay(10000)
                    syncManager.stopBLEAdvertising()
                    Log.d(TAG, "BLE advertising stopped")
                }

                Log.d(TAG, "Full sync test completed")
            } catch (e: Exception) {
                Log.e(TAG, "Sync test failed", e)
            }
        }
    }

    /** Test server connectivity only */
    fun testServerConnectivity() {
        scope.launch {
            try {
                Log.d(TAG, "Testing server connectivity...")

                val available = syncManager.checkServerAvailability()
                val url = syncManager.getServerUrl()

                Log.d(TAG, "Server URL: $url")
                Log.d(TAG, "Server available: $available")

                if (available) {
                    Log.d(TAG, "✅ Server connectivity test passed")
                } else {
                    Log.w(TAG, "❌ Server connectivity test failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server connectivity test error", e)
            }
        }
    }

    /** Test BLE functionality */
    fun testBLEFunctionality() {
        scope.launch {
            try {
                Log.d(TAG, "Testing BLE functionality...")

                // Generate test data
                val testData = generateTestHealthData(5)
                syncManager.addHealthData(testData)

                // Start advertising
                val started = syncManager.startBLEAdvertising()
                Log.d(TAG, "BLE advertising started: $started")

                if (started) {
                    Log.d(TAG, "✅ BLE functionality test passed")

                    // Wait a bit then stop
                    kotlinx.coroutines.delay(15000)
                    syncManager.stopBLEAdvertising()
                    Log.d(TAG, "BLE advertising stopped")
                } else {
                    Log.w(TAG, "❌ BLE functionality test failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "BLE functionality test error", e)
            }
        }
    }

    /** Generate test health data */
    private fun generateTestHealthData(count: Int = 10): List<HealthDataEntry> {
        val entries = mutableListOf<HealthDataEntry>()
        val now = System.currentTimeMillis()

        repeat(count) { i ->
            // Create daily metrics entry
            val dailyData =
                    mapOf(
                            "date" to (now - (i * 24 * 60 * 60 * 1000)),
                            "totalDistanceMeters" to (5000.0 + Math.random() * 10000.0),
                            "totalCalories" to (1500.0 + Math.random() * 1000.0),
                            "steps" to (8000 + Math.random() * 5000).toInt(),
                            "activeMinutes" to (45 + Math.random() * 60).toInt(),
                            "heartRateAvg" to (70 + Math.random() * 30).toInt()
                    )

            entries.add(
                    HealthDataEntry(
                            id = "test_daily_${UUID.randomUUID()}",
                            timestamp = now - (i * 24 * 60 * 60 * 1000),
                            type = HealthDataType.DAILY_METRICS,
                            data = dailyData,
                            source = "Galaxy Watch Test"
                    )
            )

            // Create sleep data entry
            if (i % 2 == 0) {
                val sleepData =
                        mapOf(
                                "startTime" to
                                        (now - (i * 24 * 60 * 60 * 1000) - (8 * 60 * 60 * 1000)),
                                "endTime" to (now - (i * 24 * 60 * 60 * 1000)),
                                "durationMillis" to (8 * 60 * 60 * 1000),
                                "sleepQuality" to (60 + Math.random() * 40).toInt(),
                                "stages" to
                                        listOf(
                                                mapOf("stage" to "DEEP", "duration" to 120),
                                                mapOf("stage" to "LIGHT", "duration" to 180),
                                                mapOf("stage" to "REM", "duration" to 90),
                                                mapOf("stage" to "AWAKE", "duration" to 30)
                                        )
                        )

                entries.add(
                        HealthDataEntry(
                                id = "test_sleep_${UUID.randomUUID()}",
                                timestamp = now - (i * 24 * 60 * 60 * 1000),
                                type = HealthDataType.SLEEP_SESSION,
                                data = sleepData,
                                source = "Galaxy Watch Test"
                        )
                )
            }
        }

        return entries
    }

    /** Set custom server URL for testing */
    fun setTestServerUrl(url: String) {
        syncManager.setServerUrl(url)
        Log.d(TAG, "Test server URL set to: $url")
    }

    /** Get current sync statistics */
    fun getCurrentStats(): Map<String, Any> {
        return syncManager.getSyncStats()
    }

    /** Clean up test resources */
    fun cleanup() {
        syncManager.cleanup()
        Log.d(TAG, "Test cleanup completed")
    }
}
