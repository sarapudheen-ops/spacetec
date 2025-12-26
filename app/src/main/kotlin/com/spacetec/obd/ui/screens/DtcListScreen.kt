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
import com.spacetec.obd.obd.DtcDecoder
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcListScreen(
    obdService: ObdService,
    onBack: () -> Unit,
    onDtcClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val dtcList by obdService.dtcList.collectAsState()
    
    var isScanning by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf("all") }
    var showClearDialog by remember { mutableStateOf(false) }
    
    val isConnected = connectionState is com.spacetec.obd.obd.ObdConnection.ConnectionState.Connected
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Trouble Codes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (dtcList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, "Clear DTCs", tint = Color.White)
                        }
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
            // Scan Type Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = scanType == "all",
                    onClick = { scanType = "all" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = scanType == "stored",
                    onClick = { scanType = "stored" },
                    label = { Text("Stored") }
                )
                FilterChip(
                    selected = scanType == "pending",
                    onClick = { scanType = "pending" },
                    label = { Text("Pending") }
                )
                FilterChip(
                    selected = scanType == "permanent",
                    onClick = { scanType = "permanent" },
                    label = { Text("Permanent") }
                )
            }
            
            // Scan Button
            Button(
                onClick = {
                    scope.launch {
                        isScanning = true
                        when (scanType) {
                            "all" -> obdService.readAllDtcs()
                            "stored" -> obdService.readStoredDtcs()
                            "pending" -> obdService.readPendingDtcs()
                            "permanent" -> obdService.readPermanentDtcs()
                        }
                        isScanning = false
                    }
                },
                enabled = isConnected && !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan for DTCs")
                }
            }
            
            if (!isConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
            
            Spacer(Modifier.height(8.dp))
            
            // Results
            if (dtcList.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = StatusCleared
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No DTCs Found", style = MaterialTheme.typography.titleMedium)
                        Text("Vehicle is healthy", color = Color.Gray)
                    }
                }
            } else {
                // DTC Count Header
                if (dtcList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${dtcList.size} DTC${if (dtcList.size > 1) "s" else ""} found",
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Severity summary
                        val critical = dtcList.count { it.severity == DtcDecoder.DtcSeverity.CRITICAL }
                        val high = dtcList.count { it.severity == DtcDecoder.DtcSeverity.HIGH }
                        if (critical > 0 || high > 0) {
                            Text(
                                "${critical + high} critical/high",
                                color = StatusActive
                            )
                        }
                    }
                }
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(dtcList) { dtc ->
                        DtcCard(dtc = dtc, onClick = { onDtcClick(dtc.code) })
                    }
                }
            }
        }
    }
    
    // Clear DTCs Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear DTCs?") },
            text = { 
                Text("This will clear all stored DTCs and reset monitors. The MIL (Check Engine Light) will turn off. Are you sure?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            obdService.clearDtcs()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text("Clear", color = StatusActive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DtcCard(dtc: DtcDecoder.DtcInfo, onClick: () -> Unit) {
    val severityColor = when (dtc.severity) {
        DtcDecoder.DtcSeverity.CRITICAL -> StatusActive
        DtcDecoder.DtcSeverity.HIGH -> Color(0xFFFF9800)
        DtcDecoder.DtcSeverity.MEDIUM -> StatusPending
        DtcDecoder.DtcSeverity.LOW -> StatusCleared
        DtcDecoder.DtcSeverity.INFO -> Color.Gray
    }
    
    val systemIcon = when (dtc.system) {
        DtcDecoder.DtcSystem.POWERTRAIN -> Icons.Default.Settings
        DtcDecoder.DtcSystem.CHASSIS -> Icons.Default.DirectionsCar
        DtcDecoder.DtcSystem.BODY -> Icons.Default.Sensors
        DtcDecoder.DtcSystem.NETWORK -> Icons.Default.Wifi
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(severityColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(systemIcon, null, tint = severityColor)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dtc.code,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = severityColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            dtc.severity.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    dtc.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    dtc.system.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}
