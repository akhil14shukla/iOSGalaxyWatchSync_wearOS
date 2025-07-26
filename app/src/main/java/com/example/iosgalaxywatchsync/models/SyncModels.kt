package com.example.iosgalaxywatchsync.models

import com.google.gson.annotations.SerializedName

/** Unified health data structure for sync */
data class HealthDataEntry(
        @SerializedName("id") val id: String,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("type") val type: HealthDataType,
        @SerializedName("data") val data: Map<String, Any>,
        @SerializedName("source") val source: String = "Galaxy Watch",
        @SerializedName("synced") var synced: Boolean = false
)

enum class HealthDataType {
    @SerializedName("daily_metrics") DAILY_METRICS,
    @SerializedName("sleep_session") SLEEP_SESSION,
    @SerializedName("heart_rate") HEART_RATE,
    @SerializedName("steps") STEPS,
    @SerializedName("workout") WORKOUT
}

/** API request/response models */
data class SyncRequest(
        @SerializedName("device_id") val deviceId: String,
        @SerializedName("data") val data: List<HealthDataEntry>,
        @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long
)

data class SyncResponse(
        @SerializedName("success") val success: Boolean,
        @SerializedName("message") val message: String,
        @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long,
        @SerializedName("synced_count") val syncedCount: Int
)

data class ServerHealthCheck(
        @SerializedName("status") val status: String,
        @SerializedName("server_time") val serverTime: Long,
        @SerializedName("version") val version: String
)

/** Sync state management */
data class SyncState(
        val lastSyncTimestamp: Long = 0L,
        val pendingDataCount: Int = 0,
        val lastSyncMethod: SyncMethod = SyncMethod.NONE,
        val lastSyncSuccess: Boolean = false,
        val lastSyncError: String? = null
)

enum class SyncMethod {
    NONE,
    LOCAL_SERVER,
    BLUETOOTH_LE
}

/** BLE specific models */
data class BLEDataPacket(
        val sequenceNumber: Int,
        val totalPackets: Int,
        val dataType: HealthDataType,
        val payload: ByteArray,
        val checksum: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BLEDataPacket

        if (sequenceNumber != other.sequenceNumber) return false
        if (totalPackets != other.totalPackets) return false
        if (dataType != other.dataType) return false
        if (!payload.contentEquals(other.payload)) return false
        if (checksum != other.checksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + totalPackets
        result = 31 * result + dataType.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + checksum.hashCode()
        return result
    }
}
