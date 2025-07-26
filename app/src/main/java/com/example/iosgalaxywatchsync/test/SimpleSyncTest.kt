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

/** Simple test for basic sync functionality */
class SimpleSyncTest(private val context: Context) {

    companion object {
        private const val TAG = "SimpleSyncTest"
    }

    private val syncManager = HybridSyncManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Test server connectivity */
    fun testServerConnectivity() {
        scope.launch {
            try {
                Log.d(TAG, "Testing server connectivity...")
                val available = syncManager.checkServerAvailability()
                Log.d(TAG, "Server available: $available")
            } catch (e: Exception) {
                Log.e(TAG, "Server test failed", e)
            }
        }
    }

    /** Test BLE advertising */
    fun testBLEAdvertising() {
        scope.launch {
            try {
                Log.d(TAG, "Testing BLE advertising...")
                val started = syncManager.startBLEAdvertising()
                Log.d(TAG, "BLE advertising started: $started")
                
                // Stop after 10 seconds
                kotlinx.coroutines.delay(10000)
                syncManager.stopBLEAdvertising()
                Log.d(TAG, "BLE advertising stopped")
            } catch (e: Exception) {
                Log.e(TAG, "BLE test failed", e)
            }
        }
    }

    /** Test data sync */
    fun testDataSync() {
        scope.launch {
            try {
                Log.d(TAG, "Testing data sync...")
                
                // Create test data
                val testData = listOf(
                    HealthDataEntry(
                        id = "test_${UUID.randomUUID()}",
                        timestamp = System.currentTimeMillis(),
                        type = HealthDataType.DAILY_METRICS,
                        data = mapOf(
                            "steps" to 10000,
                            "calories" to 2500
                        ),
                        source = "Test"
                    )
                )
                
                // Add and sync
                syncManager.addHealthData(testData)
                val success = syncManager.startSync()
                
                Log.d(TAG, "Sync completed: $success")
            } catch (e: Exception) {
                Log.e(TAG, "Data sync test failed", e)
            }
        }
    }

    /** Clean up resources */
    fun cleanup() {
        syncManager.cleanup()
    }
}
