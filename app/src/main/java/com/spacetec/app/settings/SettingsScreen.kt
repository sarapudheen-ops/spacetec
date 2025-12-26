package com.spacetec.app.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    var darkMode by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    var autoScan by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf("Metric") }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsItem(
                    title = "Dark Mode",
                    description = "Use dark theme",
                    icon = Icons.Default.DarkMode,
                    trailing = {
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { darkMode = it }
                        )
                    }
                )
            }
            
            item {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsItem(
                    title = "Enable Notifications",
                    description = "Receive alerts for new DTCs",
                    icon = Icons.Default.Notifications,
                    trailing = {
                        Switch(
                            checked = notifications,
                            onCheckedChange = { notifications = it }
                        )
                    }
                )
            }
            
            item {
                Text(
                    text = "Scanning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsItem(
                    title = "Auto Scan",
                    description = "Automatically scan for DTCs on connection",
                    icon = Icons.Default.AutoMode,
                    trailing = {
                        Switch(
                            checked = autoScan,
                            onCheckedChange = { autoScan = it }
                        )
                    }
                )
            }
            
            item {
                SettingsItem(
                    title = "Units",
                    description = "Measurement units: $units",
                    icon = Icons.Default.Straighten,
                    onClick = { 
                        units = if (units == "Metric") "Imperial" else "Metric"
                    }
                )
            }
            
            item {
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsItem(
                    title = "Export Data",
                    description = "Export diagnostic sessions and reports",
                    icon = Icons.Default.FileDownload,
                    onClick = { /* TODO */ }
                )
            }
            
            item {
                SettingsItem(
                    title = "Clear Data",
                    description = "Clear all stored diagnostic data",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { /* TODO */ }
                )
            }
            
            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsItem(
                    title = "Version",
                    description = "SpaceTec v1.0.0",
                    icon = Icons.Default.Info
                )
            }
            
            item {
                SettingsItem(
                    title = "Help & Support",
                    description = "Get help and contact support",
                    icon = Icons.Default.Help,
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick ?: {},
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            trailing?.invoke()
        }
    }
}
