package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
fun BidirectionalControlsScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()

    var activeTests by remember { mutableStateOf<List<ActuatorTest>>(emptyList()) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("") }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    // Sample actuator tests
    val actuatorTests = listOf(
        ActuatorTest("1", "Fuel Pump", "0x2F", "ON/OFF"),
        ActuatorTest("2", "Fuel Injector 1", "0x30", "ON/OFF"),
        ActuatorTest("3", "Fuel Injector 2", "0x31", "ON/OFF"),
        ActuatorTest("4", "Fuel Injector 3", "0x32", "ON/OFF"),
        ActuatorTest("5", "Fuel Injector 4", "0x33", "ON/OFF"),
        ActuatorTest("6", "EVAP Purge Valve", "0x32", "ON/OFF"),
        ActuatorTest("7", "EVAP Vent Valve", "0x32", "ON/OFF"),
        ActuatorTest("8", "Canister Purge", "0x4E", "ON/OFF"),
        ActuatorTest("9", "EVAP Leak Monitor Pump", "0x5E", "ON/OFF"),
        ActuatorTest("10", "Secondary Air Injection", "0x3E", "ON/OFF"),
        ActuatorTest("11", "A/C Compressor", "0x5E", "ON/OFF"),
        ActuatorTest("12", "Radiator Fan", "0x5E", "ON/OFF")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bidirectional Controls") },
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
            // Warning Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = StatusActive.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = StatusActive)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Actuator tests can cause engine damage if run incorrectly. Only test when safe to do so.",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Active Tests Header
            if (activeTests.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${activeTests.size} Active Test${if (activeTests.size > 1) "s" else ""}",
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isTesting = true
                                    testStatus = "Stopping all tests..."
                                    delay(1000)
                                    activeTests = emptyList()
                                    testStatus = "All tests stopped"
                                    isTesting = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusActive)
                        ) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop All")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
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

            // Test Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(actuatorTests) { test ->
                    ActuatorTestCard(
                        test = test,
                        isActive = activeTests.any { it.id == test.id },
                        onToggle = { isActive ->
                            scope.launch {
                                if (isActive) {
                                    isTesting = true
                                    testStatus = "Starting ${test.name} test..."
                                    delay(800)
                                    activeTests = activeTests + test
                                    testStatus = "${test.name} test started"
                                    isTesting = false
                                } else {
                                    isTesting = true
                                    testStatus = "Stopping ${test.name} test..."
                                    delay(500)
                                    activeTests = activeTests.filter { it.id != test.id }
                                    testStatus = "${test.name} test stopped"
                                    isTesting = false
                                }
                            }
                        },
                        enabled = isConnected
                    )
                }
            }

            // Status Bar
            if (isTesting || testStatus.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(testStatus)
                    }
                }
            }
        }
    }
}

@Composable
fun ActuatorTestCard(
    test: ActuatorTest,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isActive) Modifier.background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isActive) StatusActive.copy(alpha = 0.2f) else Primary.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                    null,
                    tint = if (isActive) StatusActive else Primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                test.name,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "UDS: ${test.service}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Switch(
                checked = isActive,
                onCheckedChange = { onToggle(!isActive) },
                enabled = enabled
            )
        }
    }
}

data class ActuatorTest(
    val id: String,
    val name: String,
    val service: String,
    val controlType: String
)