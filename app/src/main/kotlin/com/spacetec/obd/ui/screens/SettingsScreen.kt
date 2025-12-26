package com.spacetec.obd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacetec.obd.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var darkMode by remember { mutableStateOf(false) }
    var autoConnect by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    var metricUnits by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Connection Settings
            SettingsSection("Connection") {
                SwitchSetting(
                    icon = Icons.Default.BluetoothSearching,
                    title = "Auto-connect",
                    subtitle = "Connect to last device automatically",
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it }
                )
                ClickableSetting(
                    icon = Icons.Default.Bluetooth,
                    title = "Preferred Scanner",
                    subtitle = "OBDLink MX+",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.Timer,
                    title = "Connection Timeout",
                    subtitle = "30 seconds",
                    onClick = { }
                )
            }
            
            // Display Settings
            SettingsSection("Display") {
                SwitchSetting(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )
                SwitchSetting(
                    icon = Icons.Default.Speed,
                    title = "Metric Units",
                    subtitle = "Use km/h, °C, liters",
                    checked = metricUnits,
                    onCheckedChange = { metricUnits = it }
                )
                ClickableSetting(
                    icon = Icons.Default.Language,
                    title = "Language",
                    subtitle = "English",
                    onClick = { }
                )
            }
            
            // Notifications
            SettingsSection("Notifications") {
                SwitchSetting(
                    icon = Icons.Default.Notifications,
                    title = "Enable Notifications",
                    subtitle = "Get alerts for new DTCs",
                    checked = notifications,
                    onCheckedChange = { notifications = it }
                )
            }
            
            // Data Management
            SettingsSection("Data") {
                ClickableSetting(
                    icon = Icons.Default.History,
                    title = "Scan History",
                    subtitle = "View past diagnostic sessions",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.CloudDownload,
                    title = "Update DTC Database",
                    subtitle = "Last updated: Dec 2024",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.Delete,
                    title = "Clear Cache",
                    subtitle = "Free up storage space",
                    onClick = { }
                )
            }
            
            // About
            SettingsSection("About") {
                ClickableSetting(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "1.0.0 (Build 1)",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.Description,
                    title = "Terms of Service",
                    subtitle = "",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "",
                    onClick = { }
                )
                ClickableSetting(
                    icon = Icons.Default.Help,
                    title = "Help & Support",
                    subtitle = "",
                    onClick = { }
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ClickableSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}