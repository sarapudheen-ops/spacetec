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
fun ProtocolSelectionScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()

    // Get the actual current protocol from the connection
    val currentProtocol by obdService.currentProtocol.collectAsState()

    var selectedProtocol by remember { mutableStateOf(currentProtocol) }
    var availableProtocols by remember { mutableStateOf<List<Protocol>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionStatus by remember { mutableStateOf("") }
    var showProtocolDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    // Available protocols
    val protocols = listOf(
        Protocol("AUTO", "Automatic Detection", "Let scanner auto-detect protocol", true),
        Protocol("ISO_9141_2", "ISO 9141-2", "Slow but compatible with older vehicles", true),
        Protocol("ISO_14230_4", "ISO 14230-4 (KWP2000)", "Enhanced keyword protocol", true),
        Protocol("ISO_15765_4", "ISO 15765-4 (CAN)", "Controller Area Network (11-bit)", true),
        Protocol("ISO_15765_4_29", "ISO 15765-4 (CAN 29-bit)", "Controller Area Network (29-bit)", true),
        Protocol("SAE_J1850_PWM", "SAE J1850 PWM", "Pulse Width Modulation", true),
        Protocol("SAE_J1850_VPW", "SAE J1850 VPW", "Variable Pulse Width", true),
        Protocol("SAE_J1939", "SAE J1939", "Commercial vehicle protocol", false)
    )

    // Auto-detect protocols when connected and update selected protocol
    LaunchedEffect(isConnected) {
        if (isConnected) {
            availableProtocols = protocols.filter { it.isSupported }
        }
    }

    // Update selected protocol when current protocol changes
    LaunchedEffect(currentProtocol) {
        selectedProtocol = currentProtocol
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protocol Selection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isDetecting = true
                                detectionStatus = "Detecting supported protocols..."
                                delay(2000)
                                availableProtocols = protocols.filter { it.isSupported }
                                detectionStatus = "Protocol detection complete"
                                isDetecting = false
                            }
                        },
                        enabled = isConnected && !isDetecting
                    ) {
                        Icon(Icons.Default.Refresh, "Detect", tint = Color.White)
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
                // Current Protocol Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, null, tint = Color.White)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Current Protocol", fontWeight = FontWeight.Bold)
                            Text(
                                when (currentProtocol) {
                                    "AUTO" -> "Automatic Detection"
                                    "ISO_9141_2" -> "ISO 9141-2 (KWP)"
                                    "ISO_14230_4" -> "ISO 14230-4 (KWP2000)"
                                    "ISO_15765_4" -> "ISO 15765-4 (CAN 11-bit)"
                                    "ISO_15765_4_29" -> "ISO 15765-4 (CAN 29-bit)"
                                    "SAE_J1850_PWM" -> "SAE J1850 PWM"
                                    "SAE_J1850_VPW" -> "SAE J1850 VPW"
                                    "SAE_J1939" -> "SAE J1939"
                                    else -> "Unknown Protocol"
                                },
                                color = Color.White
                            )
                        }
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
                            Text("Connect to a scanner to configure protocols")
                        }
                    }
                }
            }

            item {
                // Protocol Selection Info
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Protocol Selection", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select the communication protocol to use with your vehicle. " +
                            "Automatic detection is recommended for most vehicles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                // Protocol Selection Options
                Card {
                    LazyColumn {
                        items(availableProtocols) { protocol ->
                            ProtocolOption(
                                protocol = protocol,
                                isSelected = selectedProtocol == protocol.id,
                                onClick = {
                                    if (protocol.isSupported) {
                                        selectedProtocol = protocol.id
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                // Apply Button
                Button(
                    onClick = { showProtocolDialog = true },
                    enabled = isConnected && selectedProtocol != currentProtocol,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply Protocol Change")
                }
            }

            if (isDetecting) {
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
                            Text(detectionStatus)
                        }
                    }
                }
            }

            item {
                // Protocol Information
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Protocol Information", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        ProtocolInfoItem(
                            "ISO 9141-2",
                            "K-Line protocol for older vehicles",
                            "5 baud init, 10.4 kbps"
                        )
                        ProtocolInfoItem(
                            "ISO 14230-4",
                            "Enhanced KWP2000",
                            "Fast init, up to 10.4 kbps"
                        )
                        ProtocolInfoItem(
                            "ISO 15765-4",
                            "CAN protocol (most common)",
                            "11-bit or 29-bit identifiers"
                        )
                        ProtocolInfoItem(
                            "SAE J1850",
                            "PWM or VPW for Ford/GM",
                            "41.6 kbps (PWM) or 10.4 kbps (VPW)"
                        )
                    }
                }
            }
        }
    }

    // Protocol Change Confirmation Dialog
    if (showProtocolDialog) {
        ProtocolChangeDialog(
            onDismiss = { showProtocolDialog = false },
            onConfirm = {
                scope.launch {
                    isDetecting = true
                    detectionStatus = "Changing protocol to ${getProtocolName(selectedProtocol)}..."
                    // Actually change the protocol on the connection
                    val success = obdService.connection.setProtocol(selectedProtocol)
                    if (success) {
                        detectionStatus = "Protocol changed successfully"
                    } else {
                        detectionStatus = "Failed to change protocol"
                    }
                    isDetecting = false
                    showProtocolDialog = false
                }
            },
            protocolName = getProtocolName(selectedProtocol)
        )
    }
}

@Composable
fun ProtocolOption(
    protocol: Protocol,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = protocol.isSupported) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { if (protocol.isSupported) onClick() },
            enabled = protocol.isSupported
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (protocol.id) {
                        "AUTO" -> "Automatic Detection"
                        "ISO_9141_2" -> "ISO 9141-2 (KWP)"
                        "ISO_14230_4" -> "ISO 14230-4 (KWP2000)"
                        "ISO_15765_4" -> "ISO 15765-4 (CAN 11-bit)"
                        "ISO_15765_4_29" -> "ISO 15765-4 (CAN 29-bit)"
                        "SAE_J1850_PWM" -> "SAE J1850 PWM"
                        "SAE_J1850_VPW" -> "SAE J1850 VPW"
                        "SAE_J1939" -> "SAE J1939"
                        else -> protocol.name
                    },
                    fontWeight = FontWeight.Medium,
                    color = if (protocol.isSupported) Color.Unspecified else Color.Gray
                )
                if (!protocol.isSupported) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color.Gray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Not Supported",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Text(
                protocol.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (protocol.isSupported) Color.Gray else Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ProtocolInfoItem(title: String, description: String, details: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(details, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun ProtocolChangeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    protocolName: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Protocol") },
        text = {
            Column {
                Text("Are you sure you want to change the communication protocol?")
                Spacer(Modifier.height(8.dp))
                Text("New protocol: $protocolName", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This may require reconnection to the vehicle. " +
                    "Some protocols may not work with your vehicle.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Primary)
            ) {
                Text("Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getProtocolName(protocolId: String): String {
    return when (protocolId) {
        "AUTO" -> "Automatic Detection"
        "ISO_9141_2" -> "ISO 9141-2 (KWP)"
        "ISO_14230_4" -> "ISO 14230-4 (KWP2000)"
        "ISO_15765_4" -> "ISO 15765-4 (CAN 11-bit)"
        "ISO_15765_4_29" -> "ISO 15765-4 (CAN 29-bit)"
        "SAE_J1850_PWM" -> "SAE J1850 PWM"
        "SAE_J1850_VPW" -> "SAE J1850 VPW"
        "SAE_J1939" -> "SAE J1939"
        else -> "Unknown Protocol"
    }
}

data class Protocol(
    val id: String,
    val name: String,
    val description: String,
    val isSupported: Boolean
)