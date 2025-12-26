package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.spacetec.obd.obd.ObdConnection
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    obdService: ObdService,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val vehicleInfoState by obdService.vehicleInfo.collectAsState()
    val dtcList by obdService.dtcList.collectAsState()
    
    var isInitializing by remember { mutableStateOf(false) }
    
    val isConnected = connectionState is ObdConnection.ConnectionState.Connected
    
    // Initialize vehicle when connected
    LaunchedEffect(connectionState) {
        val vehicleInfo = vehicleInfoState
        if (isConnected && vehicleInfo == null) {
            isInitializing = true
            obdService.initializeVehicle()
            isInitializing = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpaceTec OBD") },
                actions = {
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onNavigate("connection") },
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        is ObdConnection.ConnectionState.Connected -> StatusCleared.copy(alpha = 0.1f)
                        is ObdConnection.ConnectionState.Connecting -> StatusPending.copy(alpha = 0.1f)
                        is ObdConnection.ConnectionState.Error -> StatusActive.copy(alpha = 0.1f)
                        else -> Color.Gray.copy(alpha = 0.1f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                when (connectionState) {
                                    is ObdConnection.ConnectionState.Connected -> StatusCleared
                                    is ObdConnection.ConnectionState.Connecting -> StatusPending
                                    is ObdConnection.ConnectionState.Error -> StatusActive
                                    else -> Color.Gray
                                }.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (connectionState) {
                                is ObdConnection.ConnectionState.Connected -> Icons.Default.BluetoothConnected
                                is ObdConnection.ConnectionState.Connecting -> Icons.Default.BluetoothSearching
                                else -> Icons.Default.BluetoothDisabled
                            },
                            null,
                            tint = when (connectionState) {
                                is ObdConnection.ConnectionState.Connected -> StatusCleared
                                is ObdConnection.ConnectionState.Connecting -> StatusPending
                                is ObdConnection.ConnectionState.Error -> StatusActive
                                else -> Color.Gray
                            }
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (val state = connectionState) {
                                is ObdConnection.ConnectionState.Connected -> "Connected"
                                is ObdConnection.ConnectionState.Connecting -> "Connecting..."
                                is ObdConnection.ConnectionState.Error -> "Connection Error"
                                else -> "Not Connected"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (val state = connectionState) {
                                is ObdConnection.ConnectionState.Connected -> state.deviceName
                                is ObdConnection.ConnectionState.Error -> state.message
                                else -> "Tap to connect"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
            
            // Vehicle Info Card (when connected)
            val vehicleInfo = vehicleInfoState
            if (isConnected && vehicleInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsCar, null, tint = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Vehicle Information", fontWeight = FontWeight.Bold)
                            if (vehicleInfo?.isVwGroup == true) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "VW Group",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        vehicleInfo?.vin?.let { vin ->
                            InfoRow("VIN", vin)
                        }
                        vehicleInfo?.protocol?.let { protocol ->
                            InfoRow("Protocol", protocol)
                        }
                        vehicleInfo?.ecuName?.let { ecu ->
                            InfoRow("ECU", ecu)
                        }
                        InfoRow("Supported PIDs", "${vehicleInfo?.supportedPidCount ?: 0}")
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else if (isInitializing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Initializing vehicle communication...")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            // Quick Actions Grid
            Text(
                "Quick Actions",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickActionCard(
                        icon = Icons.Default.Warning,
                        title = "Read DTCs",
                        subtitle = if (dtcList.isNotEmpty()) "${dtcList.size} codes" else "No codes",
                        color = if (dtcList.isNotEmpty()) StatusActive else StatusCleared,
                        onClick = { onNavigate("dtc_list") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Speed,
                        title = "Live Data",
                        subtitle = "Real-time monitoring",
                        color = Primary,
                        onClick = { onNavigate("live_data") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Delete,
                        title = "Clear DTCs",
                        subtitle = "Reset codes",
                        color = StatusPending,
                        onClick = {
                            scope.launch {
                                obdService.clearDtcs()
                            }
                        },
                        enabled = isConnected && dtcList.isNotEmpty()
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.CheckCircle,
                        title = "Readiness",
                        subtitle = "Monitor status",
                        color = StatusCleared,
                        onClick = { onNavigate("readiness") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Memory,
                        title = "ECU Programming",
                        subtitle = "Code & configure ECUs",
                        color = StatusActive,
                        onClick = { onNavigate("ecu_programming") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Key,
                        title = "Key Programming",
                        subtitle = "Program new keys",
                        color = StatusActive,
                        onClick = { onNavigate("key_programming") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.ToggleOn,
                        title = "Bidirectional",
                        subtitle = "Actuator tests",
                        color = StatusPending,
                        onClick = { onNavigate("bidirectional_controls") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.BarChart,
                        title = "Reports",
                        subtitle = "Vehicle diagnostics",
                        color = Primary,
                        onClick = { onNavigate("vehicle_reports") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Build,
                        title = "Maintenance",
                        subtitle = "Service functions",
                        color = StatusCleared,
                        onClick = { onNavigate("maintenance_functions") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Toll,
                        title = "VIN Decode",
                        subtitle = "Vehicle info",
                        color = Primary,
                        onClick = { onNavigate("vin_decoding") }
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Default.Memory,
                        title = "Protocols",
                        subtitle = "Communication",
                        color = StatusCleared,
                        onClick = { onNavigate("protocol_selection") }
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { if (enabled) onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.White else Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (enabled) color else Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.Unspecified else Color.Gray
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}