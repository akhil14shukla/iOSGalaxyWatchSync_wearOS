package com.example.iosgalaxywatchsync

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.example.iosgalaxywatchsync.test.SyncTester
import kotlinx.coroutines.launch

class TestActivity : ComponentActivity() {
    
    private val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )
    
    private val permissionStatus = mutableStateOf("Permissions Needed")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            permissionStatus.value = if (perms.values.all { it }) {
                "All Permissions Granted!"
            } else {
                "Some Permissions Denied"
            }
        }
        
        setContent {
            TestApp(
                permissionStatus = permissionStatus.value,
                onGrantPermissions = {
                    requestPermissionLauncher.launch(permissions)
                }
            )
        }
    }
}

@Composable
fun TestApp(
    permissionStatus: String,
    onGrantPermissions: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncTester = remember { SyncTester(context) }
    val scope = rememberCoroutineScope()
    
    var testResults by remember { mutableStateOf("No tests run yet") }
    var currentServerUrl by remember { mutableStateOf("http://192.168.1.100:3000") }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        item {
            Text(
                "Hybrid Sync Test",
                style = MaterialTheme.typography.title1,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Text(
                text = "Status: ${viewModel.syncStatus.value}",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colors.secondary
            )
        }
        
        item {
            Text(
                text = permissionStatus,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 10.sp,
                color = if (permissionStatus.contains("Granted")) Color.Green else Color.Red
            )
        }
        
        item {
            Text(
                text = viewModel.getSyncStats(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
        
        // Permission Button
        item {
            Button(
                onClick = onGrantPermissions,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
            ) {
                Text("1. Grant Permissions", fontSize = 10.sp)
            }
        }
        
        // Server Configuration
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Server URL:", fontSize = 10.sp)
                Text(currentServerUrl, fontSize = 8.sp, color = Color.Gray)
            }
        }
        
        item {
            Button(
                onClick = { 
                    // Cycle through common local IPs for testing
                    currentServerUrl = when {
                        currentServerUrl.contains("192.168.1.100") -> "http://192.168.1.101:3000"
                        currentServerUrl.contains("192.168.1.101") -> "http://192.168.0.100:3000"
                        currentServerUrl.contains("192.168.0.100") -> "http://10.0.0.100:3000"
                        else -> "http://192.168.1.100:3000"
                    }
                    viewModel.setServerUrl(currentServerUrl)
                    syncTester.setTestServerUrl(currentServerUrl)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5722))
            ) {
                Text("2. Change Server", fontSize = 10.sp)
            }
        }
        
        // Individual Test Buttons
        item {
            Button(
                onClick = { 
                    scope.launch {
                        testResults = "Testing server connectivity..."
                        syncTester.testServerConnectivity()
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("3. Test Server", fontSize = 10.sp)
            }
        }
        
        item {
            Button(
                onClick = { 
                    scope.launch {
                        testResults = "Testing BLE functionality..."
                        syncTester.testBLEFunctionality()
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF9C27B0))
            ) {
                Text("4. Test BLE", fontSize = 10.sp)
            }
        }
        
        item {
            Button(
                onClick = { 
                    scope.launch {
                        testResults = "Running full sync test..."
                        syncTester.runFullSyncTest()
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF9800))
            ) {
                Text("5. Full Sync Test", fontSize = 10.sp)
            }
        }
        
        // Data Generation and Sync
        item {
            Button(
                onClick = { viewModel.startPassiveDailySync() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF607D8B))
            ) {
                Text("6. Daily Data Sync", fontSize = 10.sp)
            }
        }
        
        item {
            Button(
                onClick = { viewModel.syncSleepData() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF795548))
            ) {
                Text("7. Sleep Data Sync", fontSize = 10.sp)
            }
        }
        
        item {
            Button(
                onClick = { viewModel.triggerManualSync() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3F51B5))
            ) {
                Text("8. Manual Sync", fontSize = 10.sp)
            }
        }
        
        // Server Check
        item {
            Button(
                onClick = { viewModel.checkServerConnection() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF009688))
            ) {
                Text("9. Check Server", fontSize = 10.sp)
            }
        }
        
        // Test Results Display
        item {
            Text(
                text = "Test Results:",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        
        item {
            Text(
                text = testResults,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
            )
        }
    }
    
    // Cleanup when activity is destroyed
    DisposableEffect(syncTester) {
        onDispose {
            syncTester.cleanup()
        }
    }
}
