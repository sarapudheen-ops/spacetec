package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
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
fun EcuProgrammingScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val vehicleInfoState by obdService.vehicleInfo.collectAsState()

    var ecuList by remember { mutableStateOf<List<EcuInfo>>(emptyList()) }
    var selectedEcu by remember { mutableStateOf<EcuInfo?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var isProgramming by remember { mutableStateOf(false) }
    var programmingStatus by remember { mutableStateOf("") }
    var codingValue by remember { mutableStateOf("") }
    var showCodingDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected
    val vehicleInfo = vehicleInfoState
    val isVwGroup = vehicleInfo?.isVwGroup == true

    // VW-specific ECU list for demonstration
    val vwEcuList = listOf(
        EcuInfo("1", "Engine Control Module", "ECM", "01", "06L906016G", "1037"),
        EcuInfo("2", "Transmission Control Module", "TCM", "02", "09G927158", "1065"),
        EcuInfo("3", "ABS/ESP Control Module", "ABS", "03", "5N0907379", "1045"),
        EcuInfo("4", "Airbag Control Module", "AIRBAG", "15", "5QF959655", "1050"),
        EcuInfo("5", "Steering Angle Sensor", "SAS", "44", "5Q0909143", "1051")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ECU Programming") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isScanning = true
                            // Simulate ECU scan
                            scope.launch {
                                delay(1500)
                                ecuList = if (isVwGroup) vwEcuList else emptyList()
                                isScanning = false
                            }
                        },
                        enabled = isConnected && !isScanning
                    ) {
                        Icon(Icons.Default.Refresh, "Scan ECUs", tint = Color.White)
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
                // Warning Card for Safety
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
                            "ECU programming can damage your vehicle if done incorrectly. Only proceed if you know what you're doing.",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item {
                // Scan Button
                Button(
                    onClick = {
                        isScanning = true
                        // Simulate ECU scan
                        scope.launch {
                            delay(1500)
                            ecuList = if (isVwGroup) vwEcuList else emptyList()
                            isScanning = false
                        }
                    },
                    enabled = isConnected && !isScanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning ECUs...")
                    } else {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Control Modules")
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

            if (isVwGroup) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusCleared.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsCar, null, tint = StatusCleared)
                            Spacer(Modifier.width(16.dp))
                            Text("Volkswagen Group Vehicle Detected", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text(
                    "Available Control Modules",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (ecuList.isEmpty() && !isScanning) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No ECUs scanned yet")
                            Text(
                                "Tap 'Scan Control Modules' to discover available ECUs",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            items(ecuList) { ecu ->
                EcuCard(
                    ecu = ecu,
                    isSelected = selectedEcu?.address == ecu.address,
                    onClick = { selectedEcu = ecu },
                    onProgramClick = { showCodingDialog = true },
                    onReadClick = {
                        // Simulate reading ECU data
                        scope.launch {
                            isProgramming = true
                            programmingStatus = "Reading ECU data..."
                            delay(2000)
                            programmingStatus = "ECU data read successfully"
                            isProgramming = false
                        }
                    }
                )
            }

            if (isProgramming) {
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
                            Text(programmingStatus)
                        }
                    }
                }
            }
        }
    }

    // Coding Dialog
    if (showCodingDialog) {
        EcuCodingDialog(
            onDismiss = { showCodingDialog = false },
            onConfirm = { newCoding ->
                scope.launch {
                    isProgramming = true
                    programmingStatus = "Programming ECU with new coding..."
                    delay(3000) // Simulate programming time
                    programmingStatus = "ECU programmed successfully with coding: $newCoding"
                    isProgramming = false
                    showCodingDialog = false
                }
            },
            currentCoding = selectedEcu?.coding ?: ""
        )
    }
}

@Composable
fun EcuCard(
    ecu: EcuInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onProgramClick: () -> Unit,
    onReadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, null, tint = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ecu.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Address: ${ecu.address}", style = MaterialTheme.typography.bodySmall)
                    Text("Part Number: ${ecu.partNumber}", style = MaterialTheme.typography.bodySmall)
                    Text("Software: ${ecu.softwareVersion}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClick) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (isSelected) Primary else Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCleared),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Read")
                }
                Button(
                    onClick = onProgramClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusActive),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Build, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Program")
                }
            }
        }
    }
}

@Composable
fun EcuCodingDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    currentCoding: String
) {
    var codingValue by remember { mutableStateOf(currentCoding) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ECU Coding") },
        text = {
            Column {
                Text("Enter new ECU coding (hexadecimal):")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = codingValue,
                    onValueChange = { codingValue = it.uppercase().replace(Regex("[^0-9A-F]"), "") },
                    label = { Text("Coding") },
                    singleLine = true,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "WARNING: Incorrect coding can permanently damage the ECU!",
                    color = StatusActive,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(codingValue) },
                enabled = codingValue.length == 8 // Example: 8 hex chars for VW coding
            ) {
                Text("Program", color = StatusActive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class EcuInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val address: String,
    val partNumber: String,
    val softwareVersion: String,
    val coding: String = ""
)