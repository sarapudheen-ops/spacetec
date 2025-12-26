package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacetec.obd.obd.ObdConnection
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    
    var monitorStatus by remember { mutableStateOf<ObdService.MonitorStatus?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val isConnected = connectionState is ObdConnection.ConnectionState.Connected
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Readiness Monitors") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                monitorStatus = obdService.getMonitorStatus()
                                isLoading = false
                            }
                        },
                        enabled = isConnected && !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // MIL Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (monitorStatus?.milOn == true) 
                            StatusActive.copy(alpha = 0.1f) 
                        else 
                            StatusCleared.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (monitorStatus?.milOn == true) StatusActive else StatusCleared,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Check Engine Light",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (monitorStatus?.milOn == true) "ON - ${monitorStatus?.dtcCount ?: 0} DTCs" else "OFF",
                                color = if (monitorStatus?.milOn == true) StatusActive else StatusCleared
                            )
                        }
                    }
                }
            }
            
            // Scan Button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            monitorStatus = obdService.getMonitorStatus()
                            isLoading = false
                        }
                    },
                    enabled = isConnected && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reading...")
                    } else {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Read Monitor Status")
                    }
                }
            }
            
            if (!isConnected) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = StatusPending)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to a scanner first")
                        }
                    }
                }
            }
            
            // Continuous Monitors
            val localMonitorStatus = monitorStatus
            if (localMonitorStatus != null) {
                item {
                    Text(
                        "Continuous Monitors",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                item {
                    MonitorCard("Misfire", localMonitorStatus.misfire)
                }
                item {
                    MonitorCard("Fuel System", localMonitorStatus.fuelSystem)
                }
                item {
                    MonitorCard("Components", localMonitorStatus.components)
                }
                
                // Non-Continuous Monitors
                item {
                    Text(
                        "Non-Continuous Monitors",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                item {
                    MonitorCard("Catalyst", localMonitorStatus.catalyst)
                }
                item {
                    MonitorCard("Heated Catalyst", localMonitorStatus.heatedCatalyst)
                }
                item {
                    MonitorCard("Evaporative System", localMonitorStatus.evapSystem)
                }
                item {
                    MonitorCard("Secondary Air", localMonitorStatus.secondaryAir)
                }
                item {
                    MonitorCard("Oxygen Sensor", localMonitorStatus.oxygenSensor)
                }
                item {
                    MonitorCard("O2 Sensor Heater", localMonitorStatus.oxygenSensorHeater)
                }
                item {
                    MonitorCard("EGR System", localMonitorStatus.egr)
                }
                
                // Summary
                item {
                    val totalAvailable = listOf(
                        localMonitorStatus.misfire,
                        localMonitorStatus.fuelSystem,
                        localMonitorStatus.components,
                        localMonitorStatus.catalyst,
                        localMonitorStatus.heatedCatalyst,
                        localMonitorStatus.evapSystem,
                        localMonitorStatus.secondaryAir,
                        localMonitorStatus.oxygenSensor,
                        localMonitorStatus.oxygenSensorHeater,
                        localMonitorStatus.egr
                    ).count { it.available }
                    
                    val totalComplete = listOf(
                        localMonitorStatus.misfire,
                        localMonitorStatus.fuelSystem,
                        localMonitorStatus.components,
                        localMonitorStatus.catalyst,
                        localMonitorStatus.heatedCatalyst,
                        localMonitorStatus.evapSystem,
                        localMonitorStatus.secondaryAir,
                        localMonitorStatus.oxygenSensor,
                        localMonitorStatus.oxygenSensorHeater,
                        localMonitorStatus.egr
                    ).count { it.available && it.complete }
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (totalComplete == totalAvailable) 
                                StatusCleared.copy(alpha = 0.1f) 
                            else 
                                StatusPending.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("$totalComplete of $totalAvailable monitors complete")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = if (totalAvailable > 0) totalComplete.toFloat() / totalAvailable else 0f.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (totalComplete == totalAvailable) StatusCleared else StatusPending
                            )
                            if (totalComplete < totalAvailable) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Drive the vehicle to complete remaining monitors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorCard(name: String, result: ObdService.TestResult) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when {
                            !result.available -> Color.Gray.copy(alpha = 0.2f)
                            result.complete -> StatusCleared.copy(alpha = 0.2f)
                            else -> StatusPending.copy(alpha = 0.2f)
                        },
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        !result.available -> Icons.Default.Remove
                        result.complete -> Icons.Default.Check
                        else -> Icons.Default.Schedule
                    },
                    null,
                    tint = when {
                        !result.available -> Color.Gray
                        result.complete -> StatusCleared
                        else -> StatusPending
                    }
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        !result.available -> "Not Supported"
                        result.complete -> "Complete"
                        else -> "Incomplete"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Surface(
                color = when {
                    !result.available -> Color.Gray.copy(alpha = 0.1f)
                    result.complete -> StatusCleared.copy(alpha = 0.1f)
                    else -> StatusPending.copy(alpha = 0.1f)
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    when {
                        !result.available -> "N/A"
                        result.complete -> "PASS"
                        else -> "INC"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !result.available -> Color.Gray
                        result.complete -> StatusCleared
                        else -> StatusPending
                    }
                )
            }
        }
    }
}