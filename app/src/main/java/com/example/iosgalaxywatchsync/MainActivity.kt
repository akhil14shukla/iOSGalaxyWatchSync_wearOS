package com.example.iosgalaxywatchsync

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*

class MainActivity : ComponentActivity() {
        private val permissions =
                arrayOf(
                        Manifest.permission.BODY_SENSORS,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE
                )
        private val permissionStatus = mutableStateOf("Permissions Needed")

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)

                val requestPermissionLauncher =
                        registerForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                        ) { perms ->
                                permissionStatus.value =
                                        if (perms.values.all { it }) {
                                                "Permissions Granted!"
                                        } else {
                                                "Permissions Denied"
                                        }
                        }

                setContent {
                        WearApp(
                                permissionStatus = permissionStatus.value,
                                onGrantPermissions = {
                                        requestPermissionLauncher.launch(permissions)
                                }
                        )
                }
        }
}

@Composable
fun WearApp(
        permissionStatus: String,
        onGrantPermissions: () -> Unit,
        viewModel: MainViewModel = viewModel()
) {
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
                item { Text("Galaxy Sync", style = MaterialTheme.typography.title1) }

                item {
                        Text(
                                text = "Status: ${viewModel.syncStatus.value}",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 12.sp
                        )
                }

                item {
                        Text(
                                text = viewModel.getSyncStats(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                }

                // --- BUTTONS ---
                item { Button(onClick = onGrantPermissions) { Text("1. Grant Permissions") } }

                item {
                        Button(
                                onClick = { viewModel.startPassiveDailySync() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF4CAF50)
                                        )
                        ) { Text("2. Start Daily Sync") }
                }

                item {
                        Button(
                                onClick = { viewModel.syncSleepData() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                        )
                        ) { Text("3. Sync Sleep Data") }
                }

                item {
                        Button(
                                onClick = { viewModel.triggerManualSync() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF2196F3)
                                        )
                        ) { Text("4. Manual Sync") }
                }

                item {
                        Button(
                                onClick = { viewModel.checkServerConnection() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFFFF9800)
                                        )
                        ) { Text("5. Check Server") }
                }

                item {
                        Button(
                                onClick = { 
                                    // Cycle through common local IPs for testing
                                    val currentUrl = when {
                                        viewModel.getSyncStats().contains("192.168.1.100") -> "http://192.168.1.101:3000"
                                        viewModel.getSyncStats().contains("192.168.1.101") -> "http://192.168.0.100:3000"
                                        viewModel.getSyncStats().contains("192.168.0.100") -> "http://10.0.0.100:3000"
                                        else -> "http://192.168.1.100:3000"
                                    }
                                    viewModel.setServerUrl(currentUrl)
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF607D8B)
                                        )
                        ) { Text("6. Change Server") }
                }
        }
}
