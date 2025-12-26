package com.spacetec.obd.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacetec.obd.obd.ObdConnection
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        devices = obdService.connection.getPairedDevices()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Scanner") },
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
        ) {
            // Connection Status
            when (val state = connectionState) {
                is ObdConnection.ConnectionState.Connected -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusCleared.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(StatusCleared.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BluetoothConnected, null, tint = StatusCleared)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Connected", color = StatusCleared, fontWeight = FontWeight.Bold)
                                Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { obdService.connection.disconnect() }) {
                                Text("Disconnect", color = StatusActive)
                            }
                        }
                    }
                }
                is ObdConnection.ConnectionState.Connecting -> {
                    val status by obdService.connection.connectionStatus.collectAsState()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(16.dp))
                                Text("Connecting...", fontWeight = FontWeight.Bold)
                            }
                            if (status.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
                is ObdConnection.ConnectionState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusActive.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Error, null, tint = StatusActive)
                            Spacer(Modifier.width(8.dp))
                            Text(state.message, color = StatusActive)
                        }
                    }
                }
                else -> {}
            }
            
            Text(
                "Paired Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            
            if (devices.isEmpty()) {
                Text(
                    "No paired Bluetooth devices found.\nPair your OBD adapter in system settings first.",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            }
            
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    obdService.connection.connect(device)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bluetooth, null, tint = Primary)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Medium)
                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                        }
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Supported Adapters", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("• ELM327 compatible (v1.5+)", style = MaterialTheme.typography.bodySmall)
                    Text("• OBDLink MX/MX+/LX/SX", style = MaterialTheme.typography.bodySmall)
                    Text("• Vgate iCar Pro", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}