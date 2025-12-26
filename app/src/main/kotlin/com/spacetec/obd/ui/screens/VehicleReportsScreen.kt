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
fun VehicleReportsScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val dtcList by obdService.dtcList.collectAsState()
    val vehicleInfo by obdService.vehicleInfo.collectAsState()

    var reports by remember { mutableStateOf<List<VehicleReport>>(emptyList()) }
    var selectedReport by remember { mutableStateOf<VehicleReport?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    // Sample reports
    val availableReports = listOf(
        VehicleReport(
            "1", "Full Diagnostic Report", "Complete vehicle diagnostic summary", 
            "2024-12-25 10:30", "Complete"
        ),
        VehicleReport(
            "2", "DTC History Report", "All stored and pending DTCs", 
            "2024-12-24 14:20", "Complete"
        ),
        VehicleReport(
            "3", "Emission Readiness", "Monitor status and compliance", 
            "2024-12-23 09:15", "Complete"
        ),
        VehicleReport(
            "4", "Service History", "Maintenance and service records", 
            "2024-12-22 16:45", "Complete"
        ),
        VehicleReport(
            "5", "Performance Analysis", "Engine and transmission performance", 
            "2024-12-21 11:30", "Complete"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isGenerating = true
                                // Simulate report generation
                                delay(1500)
                                reports = availableReports
                                isGenerating = false
                            }
                        },
                        enabled = isConnected && !isGenerating
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
            item {
                // Summary Card
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Vehicle Diagnostic Summary", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "DTCs: ${dtcList.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Connected: ${if (isConnected) "Yes" else "No"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Default.Analytics,
                            null,
                            tint = if (dtcList.isEmpty()) StatusCleared else StatusActive
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
                            Text("Connect to a scanner for detailed reports")
                        }
                    }
                }
            }

            item {
                // Generate Report Button
                Button(
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            // Simulate report generation
                            delay(2000)
                            reports = availableReports
                            isGenerating = false
                        }
                    },
                    enabled = isConnected && !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Icon(Icons.Default.BarChart, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate New Report")
                    }
                }
            }

            item {
                Text(
                    "Available Reports",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (reports.isEmpty() && !isGenerating) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No reports generated yet")
                            Text(
                                "Tap 'Generate New Report' to create diagnostic reports",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            items(reports) { report ->
                ReportCard(
                    report = report,
                    onClick = { 
                        selectedReport = report
                        showReportDialog = true
                    }
                )
            }

            item {
                // Report Templates
                Text(
                    "Report Templates",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(availableReports) { template ->
                if (!reports.any { it.id == template.id }) {
                    ReportTemplateCard(
                        template = template,
                        onUse = {
                            scope.launch {
                                isGenerating = true
                                // Simulate report generation
                                delay(1500)
                                reports = reports + template.copy(
                                    id = "${reports.size + 1}",
                                    timestamp = "Just now",
                                    status = "Complete"
                                )
                                isGenerating = false
                            }
                        }
                    )
                }
            }
        }
    }

    // Report Detail Dialog
    if (showReportDialog && selectedReport != null) {
        selectedReport?.let { report ->
            ReportDetailDialog(
                report = report,
                onDismiss = { showReportDialog = false },
                onExport = {
                    // Simulate export
                    scope.launch {
                        isGenerating = true
                        delay(1000)
                        isGenerating = false
                    }
                }
            )
        }
    }
}

@Composable
fun ReportCard(
    report: VehicleReport,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(report.name, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(report.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Generated: ${report.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@Composable
fun ReportTemplateCard(
    template: VehicleReport,
    onUse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(template.name, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                Spacer(Modifier.height(4.dp))
                Text(template.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Button(
                onClick = onUse,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Use", color = Color.White)
            }
        }
    }
}

@Composable
fun ReportDetailDialog(
    report: VehicleReport,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.name) },
        text = {
            LazyColumn {
                item {
                    Text("Report Details:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Status", report.status)
                    InfoRow("Generated", report.timestamp)
                    InfoRow("Type", report.name)
                }
                
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Sample Content:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This is a sample diagnostic report showing vehicle health and diagnostic information. " +
                        "Actual reports would contain detailed diagnostic data, DTC information, " +
                        "readiness monitors, and other vehicle diagnostic parameters.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExport) {
                    Text("Export")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

data class VehicleReport(
    val id: String,
    val name: String,
    val description: String,
    val timestamp: String,
    val status: String
)