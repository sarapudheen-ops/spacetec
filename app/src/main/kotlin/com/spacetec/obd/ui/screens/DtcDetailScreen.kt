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
import com.spacetec.obd.obd.DtcDecoder
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcDetailScreen(
    dtcCode: String,
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dtcInfo = remember { DtcDecoder.getDtcInfo(dtcCode) }
    var freezeFrame by remember { mutableStateOf<Map<String, ObdService.LiveDataValue>?>(null) }
    var isLoadingFreezeFrame by remember { mutableStateOf(false) }
    
    val severityColor = when (dtcInfo.severity) {
        DtcDecoder.DtcSeverity.CRITICAL -> StatusActive
        DtcDecoder.DtcSeverity.HIGH -> Color(0xFFFF9800)
        DtcDecoder.DtcSeverity.MEDIUM -> StatusPending
        DtcDecoder.DtcSeverity.LOW -> StatusCleared
        DtcDecoder.DtcSeverity.INFO -> Color.Gray
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dtcCode) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = severityColor.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (dtcInfo.system) {
                                        DtcDecoder.DtcSystem.POWERTRAIN -> Icons.Default.Settings
                                        DtcDecoder.DtcSystem.CHASSIS -> Icons.Default.DirectionsCar
                                        DtcDecoder.DtcSystem.BODY -> Icons.Default.Sensors
                                        DtcDecoder.DtcSystem.NETWORK -> Icons.Default.Wifi
                                    },
                                    null,
                                    tint = severityColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    dtcCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    Surface(
                                        color = severityColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            dtcInfo.severity.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = severityColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = Color.Gray.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            dtcInfo.system.description,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            dtcInfo.description,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            
            // Possible Causes
            if (dtcInfo.possibleCauses.isNotEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, null, tint = Primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Possible Causes", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            dtcInfo.possibleCauses.forEachIndexed { index, cause ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        "${index + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cause)
                                }
                            }
                        }
                    }
                }
            }
            
            // Freeze Frame Data
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Camera, null, tint = Primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Freeze Frame Data", fontWeight = FontWeight.Bold)
                            }
                            
                            if (freezeFrame == null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            isLoadingFreezeFrame = true
                                            freezeFrame = obdService.getFreezeFrame()
                                            isLoadingFreezeFrame = false
                                        }
                                    },
                                    enabled = !isLoadingFreezeFrame
                                ) {
                                    if (isLoadingFreezeFrame) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Load")
                                    }
                                }
                            }
                        }
                        
                        if (freezeFrame != null) {
                            Spacer(Modifier.height(12.dp))
                            freezeFrame?.forEach { (name, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, color = Color.Gray)
                                    Text(value.displayValue, fontWeight = FontWeight.Medium)
                                }
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        } else if (!isLoadingFreezeFrame) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Freeze frame captures vehicle data at the moment the DTC was set.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            
            // Diagnostic Steps
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Checklist, null, tint = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Diagnostic Steps", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        val steps = listOf(
                            "Check for related DTCs",
                            "Inspect wiring and connectors",
                            "Check for TSBs (Technical Service Bulletins)",
                            "Test component with multimeter",
                            "Clear code and verify repair"
                        )
                        
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(step)
                            }
                        }
                    }
                }
            }
            
            // Code Breakdown
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Code Breakdown", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        val firstChar = dtcCode.firstOrNull() ?: 'P'
                        val systemDesc = when (firstChar) {
                            'P' -> "Powertrain (Engine/Transmission)"
                            'C' -> "Chassis (ABS/Steering)"
                            'B' -> "Body (Airbags/AC/Lights)"
                            'U' -> "Network (CAN Bus/Communication)"
                            else -> "Unknown"
                        }
                        
                        val secondChar = dtcCode.getOrNull(1)?.digitToIntOrNull() ?: 0
                        val typeDesc = when (secondChar) {
                            0 -> "Generic (SAE Standard)"
                            1 -> "Manufacturer Specific"
                            2 -> "Generic (SAE Standard)"
                            3 -> "Manufacturer Specific"
                            else -> "Unknown"
                        }
                        
                        CodeBreakdownRow("${dtcCode.firstOrNull()}", "System", systemDesc)
                        CodeBreakdownRow("${dtcCode.getOrNull(1)}", "Type", typeDesc)
                        CodeBreakdownRow("${dtcCode.getOrNull(2)}", "Subsystem", getSubsystemDesc(dtcCode))
                        CodeBreakdownRow(dtcCode.takeLast(2), "Fault", "Specific fault code")
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBreakdownRow(code: String, label: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                code,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

private fun getSubsystemDesc(code: String): String {
    val thirdChar = code.getOrNull(2)?.digitToIntOrNull() ?: return "Unknown"
    val firstChar = code.firstOrNull() ?: 'P'
    
    return when (firstChar) {
        'P' -> when (thirdChar) {
            0 -> "Fuel and Air Metering"
            1 -> "Fuel and Air Metering"
            2 -> "Fuel and Air Metering (Injector)"
            3 -> "Ignition System"
            4 -> "Auxiliary Emission Controls"
            5 -> "Vehicle Speed/Idle Control"
            6 -> "Computer Output Circuit"
            7 -> "Transmission"
            8 -> "Transmission"
            else -> "Unknown"
        }
        else -> "System specific"
    }
}
