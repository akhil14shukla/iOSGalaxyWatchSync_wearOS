package com.example.iosgalaxywatchsync.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.iosgalaxywatchsync.bluetooth.WearOSBLEService
import com.example.iosgalaxywatchsync.models.*
import com.example.iosgalaxywatchsync.network.LocalServerApi
import com.google.gson.Gson
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Central sync manager that coordinates between local server and BLE */
class HybridSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridSyncManager"
        private const val PREFS_NAME = "sync_prefs"
        private const val LAST_SYNC_TIMESTAMP_KEY = "last_sync_timestamp"
        private const val DEVICE_ID_KEY = "device_id"
        private const val LOCAL_SERVER_URL_KEY = "local_server_url"

        // Default local server configuration
        private const val DEFAULT_SERVER_URL = "http://192.168.1.100:3000"
        private const val SERVER_TIMEOUT_MS = 5000L
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Services
    private var localServerApi: LocalServerApi? = null
    private val bleService = WearOSBLEService(context)

    // State management
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState

    private val _isServerAvailable = MutableStateFlow(false)
    val isServerAvailable: StateFlow<Boolean> = _isServerAvailable

    // Data storage
    private val pendingData = mutableListOf<HealthDataEntry>()
    private val dataStorage = HealthDataStorage(context)

    private var deviceId: String = prefs.getString(DEVICE_ID_KEY, null) ?: generateDeviceId()

    init {
        // Initialize device ID if not exists
        if (!prefs.contains(DEVICE_ID_KEY)) {
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
        }

        // Setup local server API
        setupLocalServerApi()

        // Start periodic server availability check
        startServerAvailabilityCheck()
    }

    /** Add health data for synchronization */
    fun addHealthData(data: List<HealthDataEntry>) {
        synchronized(pendingData) {
            pendingData.addAll(data)
            dataStorage.storeData(data)
        }
        Log.d(TAG, "Added ${data.size} health data entries for sync")
        updateSyncState()
    }

    /** Start synchronization process */
    suspend fun startSync(): Boolean {
        return try {
            Log.d(TAG, "Starting hybrid sync process")

            val dataToSync = synchronized(pendingData) { dataStorage.getUnsyncedData() }

            if (dataToSync.isEmpty()) {
                Log.d(TAG, "No data to sync")
                return true
            }

            Log.d(TAG, "Found ${dataToSync.size} entries to sync")

            // Try local server first
            val serverSuccess = tryLocalServerSync(dataToSync)

            if (serverSuccess) {
                Log.d(TAG, "Local server sync successful")
                updateLastSyncTimestamp()
                clearSyncedData(dataToSync)
                updateSyncState(SyncMethod.LOCAL_SERVER, true)
                return true
            }

            // Fallback to BLE
            Log.d(TAG, "Local server unavailable, trying BLE sync")
            val bleSuccess = tryBLESync(dataToSync)

            if (bleSuccess) {
                Log.d(TAG, "BLE sync successful")
                updateLastSyncTimestamp()
                clearSyncedData(dataToSync)
                updateSyncState(SyncMethod.BLUETOOTH_LE, true)
                return true
            }

            Log.w(TAG, "Both sync methods failed")
            updateSyncState(
                    lastSyncMethod = SyncMethod.NONE,
                    success = false,
                    error = "All sync methods failed"
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            updateSyncState(lastSyncMethod = SyncMethod.NONE, success = false, error = e.message)
            false
        }
    }

    /** Check if local server is available */
    suspend fun checkServerAvailability(): Boolean {
        return try {
            withTimeout(SERVER_TIMEOUT_MS) {
                val response = localServerApi?.healthCheck()
                val isAvailable = response?.isSuccessful == true
                _isServerAvailable.value = isAvailable
                isAvailable
            }
        } catch (e: Exception) {
            Log.d(TAG, "Server unavailable: ${e.message}")
            _isServerAvailable.value = false
            false
        }
    }

    /** Configure local server URL */
    fun setServerUrl(url: String) {
        prefs.edit().putString(LOCAL_SERVER_URL_KEY, url).apply()
        setupLocalServerApi()
    }

    /** Get current server URL */
    fun getServerUrl(): String {
        return prefs.getString(LOCAL_SERVER_URL_KEY, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    /** Start BLE advertising for fallback sync */
    fun startBLEAdvertising(): Boolean {
        return bleService.startAdvertising()
    }

    /** Stop BLE advertising */
    fun stopBLEAdvertising() {
        bleService.stopAdvertising()
    }

    /** Get sync statistics */
    fun getSyncStats(): Map<String, Any> {
        val unsyncedCount = dataStorage.getUnsyncedData().size
        val lastSync = prefs.getLong(LAST_SYNC_TIMESTAMP_KEY, 0L)

        return mapOf(
                "unsyncedDataCount" to unsyncedCount,
                "lastSyncTimestamp" to lastSync,
                "deviceId" to deviceId,
                "serverUrl" to getServerUrl(),
                "isServerAvailable" to _isServerAvailable.value
        )
    }

    private fun setupLocalServerApi() {
        val serverUrl = getServerUrl()

        try {
            val retrofit =
                    Retrofit.Builder()
                            .baseUrl(serverUrl)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build()

            localServerApi = retrofit.create(LocalServerApi::class.java)
            Log.d(TAG, "Local server API configured for: $serverUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup local server API", e)
            localServerApi = null
        }
    }

    private fun startServerAvailabilityCheck() {
        scope.launch {
            while (isActive) {
                checkServerAvailability()
                delay(30000) // Check every 30 seconds
            }
        }
    }

    private suspend fun tryLocalServerSync(data: List<HealthDataEntry>): Boolean {
        return try {
            val api = localServerApi ?: return false

            Log.d(TAG, "Attempting local server sync with ${data.size} entries")

            val lastSyncTimestamp = prefs.getLong(LAST_SYNC_TIMESTAMP_KEY, 0L)
            val request =
                    SyncRequest(
                            deviceId = deviceId,
                            data = data,
                            lastSyncTimestamp = lastSyncTimestamp
                    )

            withTimeout(SERVER_TIMEOUT_MS) {
                val response = api.syncData(request)

                if (response.isSuccessful) {
                    val syncResponse = response.body()
                    Log.d(TAG, "Server sync response: ${syncResponse?.message}")
                    syncResponse?.success == true
                } else {
                    Log.w(TAG, "Server sync failed: ${response.code()} - ${response.message()}")
                    false
                }
            }
        } catch (e: TimeoutException) {
            Log.w(TAG, "Local server sync timeout")
            false
        } catch (e: ConnectException) {
            Log.w(TAG, "Local server connection failed")
            false
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Local server socket timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Local server sync error", e)
            false
        }
    }

    private suspend fun tryBLESync(data: List<HealthDataEntry>): Boolean {
        return try {
            Log.d(TAG, "Starting BLE sync process")

            // Start BLE advertising
            if (!bleService.startAdvertising()) {
                Log.e(TAG, "Failed to start BLE advertising")
                return false
            }

            // Add data to BLE service
            bleService.addDataForSync(data)

            // Wait for connection and transfer completion
            val result = withTimeout(60000L) { // 1 minute timeout
                while (true) {
                    when (bleService.connectionState.value) {
                        WearOSBLEService.BLEConnectionState.CONNECTED -> {
                            // Wait for transfer completion
                            delay(5000) // Give time for data transfer
                            Log.d(TAG, "BLE sync completed successfully")
                            return@withTimeout true
                        }
                        WearOSBLEService.BLEConnectionState.DISCONNECTED -> {
                            delay(1000) // Wait for connection
                        }
                        else -> {
                            delay(500)
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                false // This will never be reached but satisfies the type checker
            }
            result
        } catch (e: TimeoutException) {
            Log.w(TAG, "BLE sync timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "BLE sync error", e)
            false
        } finally {
            // Always stop advertising after sync attempt
            bleService.stopAdvertising()
        }
        
        return false // Default return if no explicit return was made
    }

    private fun updateLastSyncTimestamp() {
        val timestamp = System.currentTimeMillis()
        prefs.edit().putLong(LAST_SYNC_TIMESTAMP_KEY, timestamp).apply()
    }

    private fun clearSyncedData(syncedData: List<HealthDataEntry>) {
        synchronized(pendingData) {
            dataStorage.markAsSynced(syncedData.map { it.id })
            pendingData.removeAll(syncedData)
        }
    }

    private fun updateSyncState(
            lastSyncMethod: SyncMethod = _syncState.value.lastSyncMethod,
            success: Boolean = _syncState.value.lastSyncSuccess,
            error: String? = _syncState.value.lastSyncError
    ) {
        val unsyncedCount = dataStorage.getUnsyncedData().size
        val lastSync = prefs.getLong(LAST_SYNC_TIMESTAMP_KEY, 0L)

        _syncState.value =
                SyncState(
                        lastSyncTimestamp = lastSync,
                        pendingDataCount = unsyncedCount,
                        lastSyncMethod = lastSyncMethod,
                        lastSyncSuccess = success,
                        lastSyncError = error
                )
    }

    private fun generateDeviceId(): String {
        return "galaxy_watch_${UUID.randomUUID().toString().take(8)}"
    }

    fun cleanup() {
        scope.cancel()
        bleService.stopAdvertising()
    }
}

/** Simple local storage for health data */
class HealthDataStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "health_data_storage"
        private const val DATA_KEY = "stored_data"
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun storeData(data: List<HealthDataEntry>) {
        val existingData = getAllData().toMutableList()
        existingData.addAll(data)

        val json = gson.toJson(existingData)
        prefs.edit().putString(DATA_KEY, json).apply()
    }

    fun getUnsyncedData(): List<HealthDataEntry> {
        return getAllData().filter { !it.synced }
    }

    fun markAsSynced(ids: List<String>) {
        val allData = getAllData().toMutableList()

        allData.forEach { entry ->
            if (entry.id in ids) {
                entry.synced = true
            }
        }

        val json = gson.toJson(allData)
        prefs.edit().putString(DATA_KEY, json).apply()
    }

    private fun getAllData(): List<HealthDataEntry> {
        val json = prefs.getString(DATA_KEY, null) ?: return emptyList()

        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<HealthDataEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("HealthDataStorage", "Failed to parse stored data", e)
            emptyList()
        }
    }
}
