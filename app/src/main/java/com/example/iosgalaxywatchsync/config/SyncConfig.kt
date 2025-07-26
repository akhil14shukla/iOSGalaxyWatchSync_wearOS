package com.example.iosgalaxywatchsync.config

/** Configuration constants for the hybrid sync system */
object SyncConfig {

    // Default local server configuration
    const val DEFAULT_SERVER_URL = "http://192.168.1.100:3000"
    const val SERVER_TIMEOUT_MS = 5000L
    const val SERVER_CHECK_INTERVAL_MS = 30000L

    // BLE Configuration
    const val BLE_SCAN_TIMEOUT_MS = 60000L
    const val BLE_CONNECTION_TIMEOUT_MS = 30000L
    const val BLE_TRANSFER_TIMEOUT_MS = 120000L

    // Data sync configuration
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 2000L
    const val SYNC_BATCH_SIZE = 50

    // Storage configuration
    const val MAX_UNSYNCED_ENTRIES = 1000
    const val DATA_RETENTION_DAYS = 30

    // Device identification
    const val DEVICE_NAME_PREFIX = "Galaxy Watch"
    const val APP_VERSION = "1.0.0"
}
