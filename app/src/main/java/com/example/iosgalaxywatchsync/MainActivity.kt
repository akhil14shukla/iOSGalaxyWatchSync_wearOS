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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
        private val permissionStatus = mutableStateOf("Setup Required")

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)

                val requestPermissionLauncher =
                        registerForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                        ) { perms ->
                                permissionStatus.value =
                                        if (perms.values.all { it }) {
                                                "✅ Ready to Sync"
                                        } else {
                                                "❌ Permissions Missing"
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
        var currentScreen by remember { mutableStateOf("main") }

        when (currentScreen) {
                "main" ->
                        MainScreen(
                                permissionStatus = permissionStatus,
                                onGrantPermissions = onGrantPermissions,
                                viewModel = viewModel,
                                onNavigateToHealth = { currentScreen = "health" },
                                onNavigateToSync = { currentScreen = "sync" }
                        )
                "health" ->
                        HealthScreen(
                                viewModel = viewModel,
                                onBackToMain = { currentScreen = "main" }
                        )
                "sync" ->
                        SyncScreen(viewModel = viewModel, onBackToMain = { currentScreen = "main" })
        }
}

@Composable
fun MainScreen(
        permissionStatus: String,
        onGrantPermissions: () -> Unit,
        viewModel: MainViewModel,
        onNavigateToHealth: () -> Unit,
        onNavigateToSync: () -> Unit
) {
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
                // Header
                item {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                                Text(
                                        "🌌 Galaxy Watch",
                                        style = MaterialTheme.typography.title2,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colors.primary
                                )
                                Text(
                                        "Health Sync",
                                        style = MaterialTheme.typography.caption1,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                                )
                        }
                }

                // Status
                item {
                        Text(
                                text = permissionStatus,
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color =
                                        if (permissionStatus.contains("Ready")) Color(0xFF4CAF50)
                                        else Color(0xFFFF5722),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                        )
                }

                item {
                        Text(
                                text = "Status: ${viewModel.syncStatus.value}",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                }

                // Quick Navigation
                item {
                        Text(
                                "Quick Actions",
                                style = MaterialTheme.typography.caption1,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                }

                // Health Data Button
                item {
                        Button(
                                onClick = onNavigateToHealth,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF4CAF50)
                                        )
                        ) { Text("❤️ Health Data") }
                }

                // Sync Control Button
                item {
                        Button(
                                onClick = onNavigateToSync,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF2196F3)
                                        )
                        ) { Text("🔄 Sync Control") }
                }

                // Setup button if needed
                if (permissionStatus.contains("Setup") || permissionStatus.contains("Missing")) {
                        item {
                                Button(
                                        onClick = onGrantPermissions,
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        backgroundColor =
                                                                MaterialTheme.colors.primary
                                                )
                                ) { Text("🔓 Setup Permissions") }
                        }
                }
        }
}

@Composable
fun HealthScreen(viewModel: MainViewModel, onBackToMain: () -> Unit) {
        val healthEngine = remember { HealthAnalyticsEngine(viewModel.context) }
        var healthSummary by remember { mutableStateOf<HealthAnalyticsEngine.HealthSummary?>(null) }

        LaunchedEffect(Unit) { healthSummary = healthEngine.generateHealthSummary() }

        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
                item {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                                Button(
                                        onClick = onBackToMain,
                                        modifier = Modifier.size(32.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        backgroundColor = Color.Transparent
                                                )
                                ) { Text("←", fontSize = 16.sp) }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                        "Health Data",
                                        style = MaterialTheme.typography.title3,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                        }
                }

                healthSummary?.let { summary ->
                        item {
                                HealthCard(
                                        title = "❤️ Heart Rate",
                                        value = "${summary.heartRate.average} BPM",
                                        subtitle = "Zone: ${summary.heartRate.zone}"
                                )
                        }

                        item {
                                HealthCard(
                                        title = "👟 Steps",
                                        value = "${summary.steps.dailyAverage}",
                                        subtitle = "Daily average"
                                )
                        }

                        item {
                                HealthCard(
                                        title = "😴 Sleep",
                                        value = "${summary.sleep.averageDuration.toInt()}h",
                                        subtitle = "Average duration"
                                )
                        }

                        item {
                                HealthCard(
                                        title = "🏃 Activity",
                                        value = "${summary.activity.totalActiveMinutes}min",
                                        subtitle = "This week"
                                )
                        }
                }
        }
}

@Composable
fun HealthCard(title: String, value: String, subtitle: String) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { /* Future: show details */}
        ) {
                Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.caption1,
                                fontWeight = FontWeight.Medium
                        )

                        Text(
                                text = value,
                                style = MaterialTheme.typography.title3,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                }
        }
}

@Composable
fun SyncScreen(viewModel: MainViewModel, onBackToMain: () -> Unit) {
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
                item {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                                Button(
                                        onClick = onBackToMain,
                                        modifier = Modifier.size(32.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        backgroundColor = Color.Transparent
                                                )
                                ) { Text("←", fontSize = 16.sp) }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                        "Sync Control",
                                        style = MaterialTheme.typography.title3,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                        }
                }

                item {
                        Button(
                                onClick = { viewModel.startPassiveDailySync() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF4CAF50)
                                        )
                        ) { Text("🔄 Start Daily Sync") }
                }

                item {
                        Button(
                                onClick = { viewModel.syncSleepData() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF9C27B0)
                                        )
                        ) { Text("😴 Sync Sleep Data") }
                }

                item {
                        Button(
                                onClick = { viewModel.triggerManualSync() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF2196F3)
                                        )
                        ) { Text("⚡ Manual Sync") }
                }

                item {
                        Button(
                                onClick = { viewModel.checkServerConnection() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFFFF9800)
                                        )
                        ) { Text("🌐 Check Server") }
                }

                item {
                        Button(
                                onClick = {
                                        val currentUrl =
                                                when {
                                                        viewModel
                                                                .getSyncStats()
                                                                .contains("192.168.1.100") ->
                                                                "http://192.168.1.101:3000"
                                                        viewModel
                                                                .getSyncStats()
                                                                .contains("192.168.1.101") ->
                                                                "http://192.168.0.100:3000"
                                                        viewModel
                                                                .getSyncStats()
                                                                .contains("192.168.0.100") ->
                                                                "http://10.0.0.100:3000"
                                                        else -> "http://192.168.1.100:3000"
                                                }
                                        viewModel.setServerUrl(currentUrl)
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor = Color(0xFF607D8B)
                                        )
                        ) { Text("🔗 Change Server") }
                }

                item {
                        Text(
                                text = viewModel.getSyncStats(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                }
        }
}
