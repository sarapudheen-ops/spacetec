package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceFunctionsScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()

    var isPerforming by remember { mutableStateOf(false) }
    var operationStatus by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetType by remember { mutableStateOf("") }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    // Maintenance functions
    val maintenanceFunctions = listOf(
        MaintenanceFunction(
            "1", 
            "Service Reset", 
            "Reset service interval", 
            Icons.Default.Build,
            "service"
        ),
        MaintenanceFunction(
            "2", 
            "TPMS Reset", 
            "Reset tire pressure monitoring", 
            Icons.Default.Air,
            "tpms"
        ),
        MaintenanceFunction(
            "3", 
            "Throttle Adaptation", 
            "Reset throttle position", 
            Icons.Default.Speed,
            "throttle"
        ),
        MaintenanceFunction(
            "4", 
            "DPF Regeneration", 
            "Force diesel particulate filter regeneration", 
            Icons.Default.LocalFireDepartment,
            "dpf"
        ),
        MaintenanceFunction(
            "5", 
            "Battery Registration", 
            "Register new battery", 
            Icons.Default.BatteryFull,
            "battery"
        ),
        MaintenanceFunction(
            "6", 
            "Steering Angle Reset", 
            "Reset steering angle sensor", 
            Icons.Default.Navigation,
            "steering"
        ),
        MaintenanceFunction(
            "7", 
            "Brake Pad Wear Reset", 
            "Reset brake pad wear indicators", 
            Icons.Default.Brush,
            "brake"
        ),
        MaintenanceFunction(
            "8", 
            "Oil Change Reset", 
            "Reset oil change interval", 
            Icons.Default.LocalGasStation,
            "oil"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance Functions") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Warning Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusActive.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = StatusActive)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Maintenance functions can affect vehicle operation. Follow manufacturer procedures carefully.",
                            fontWeight = FontWeight.Medium
                        )
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

            item {
                Text(
                    "Maintenance Operations",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(maintenanceFunctions) { function ->
                MaintenanceFunctionCard(
                    function = function,
                    onExecute = { 
                        resetType = function.type
                        showResetDialog = true
                    },
                    enabled = isConnected
                )
            }

            if (isPerforming) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(operationStatus)
                        }
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        ResetConfirmationDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                scope.launch {
                    isPerforming = true
                    operationStatus = "Performing ${getMaintenanceName(resetType)}..."
                    delay(2500) // Simulate maintenance operation
                    operationStatus = "${getMaintenanceName(resetType)} completed successfully"
                    isPerforming = false
                    showResetDialog = false
                }
            },
            operationName = getMaintenanceName(resetType)
        )
    }
}

@Composable
fun MaintenanceFunctionCard(
    function: MaintenanceFunction,
    onExecute: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { if (enabled) onExecute() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(function.icon, null, tint = Primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    function.name,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.Unspecified else Color.Gray
                )
                Text(
                    function.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.5f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ResetConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    operationName: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm $operationName") },
        text = {
            Column {
                Text("Are you sure you want to perform this maintenance operation?")
                Spacer(Modifier.height(8.dp))
                Text(
                    "This operation cannot be undone. Make sure you have followed all required procedures.",
                    color = StatusActive,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = StatusActive)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getMaintenanceName(type: String): String {
    return when (type) {
        "service" -> "Service Reset"
        "tpms" -> "TPMS Reset"
        "throttle" -> "Throttle Adaptation"
        "dpf" -> "DPF Regeneration"
        "battery" -> "Battery Registration"
        "steering" -> "Steering Angle Reset"
        "brake" -> "Brake Pad Wear Reset"
        "oil" -> "Oil Change Reset"
        else -> "Maintenance Operation"
    }
}

data class MaintenanceFunction(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val type: String
)