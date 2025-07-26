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
import androidx.wear.compose.material.*

class MainActivity : ComponentActivity() {
    private val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val permissionStatus = mutableStateOf("Permissions Needed")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                permissionStatus.value = if (perms.values.all { it }) {
                    "Permissions Granted!"
                } else {
                    "Permissions Denied"
                }
            }

        setContent {
            WearApp(
                permissionStatus = permissionStatus.value,
                onGrantPermissions = { requestPermissionLauncher.launch(permissions) }
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
        // CORRECTED: This is a valid signature for vertical arrangement.
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

        // --- BUTTONS ---
        item {
            Button(onClick = onGrantPermissions) {
                Text("1. Grant Permissions")
            }
        }
        item {
            Button(
                onClick = { viewModel.startOrStopWorkout() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("2. Start/Stop Walk")
            }
        }
        item {
            Button(
                onClick = { viewModel.syncSleepData() },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("3. Sync Sleep Data")
            }
        }
    }
}
