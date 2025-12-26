package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun KeyProgrammingScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()

    var keyCount by remember { mutableStateOf(0) }
    var newKeyId by remember { mutableStateOf("") }
    var isProgramming by remember { mutableStateOf(false) }
    var programmingStatus by remember { mutableStateOf("") }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Key Programming") },
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
                            "Key programming is a critical operation that can lock you out of your vehicle. Ensure you have a working key before proceeding.",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item {
                // Vehicle Status Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, null, tint = Color.White)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Immobilizer System Status", fontWeight = FontWeight.Bold)
                            Text("Ready for key programming", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                // Current Key Information
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Keys", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Registered Keys:")
                            Text("$keyCount", fontWeight = FontWeight.Bold, color = Primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Maximum Keys: 8", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = (keyCount.toFloat() / 8f).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
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
                    "Key Operations",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                // Programming Options
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { showAddKeyDialog = true },
                            enabled = isConnected && keyCount < 8,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add New Key")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { 
                                scope.launch {
                                    isProgramming = true
                                    programmingStatus = "Learning existing key..."
                                    delay(2000)
                                    programmingStatus = "Key learned successfully"
                                    isProgramming = false
                                }
                            },
                            enabled = isConnected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Learn Existing Key")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showClearAllDialog = true },
                            enabled = isConnected && keyCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusActive)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear All Keys")
                        }
                    }
                }
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

            item {
                // Programming Steps
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Programming Steps", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        
                        ProgrammingStep(1, "Ensure vehicle is in programming mode")
                        ProgrammingStep(2, "Insert existing working key")
                        ProgrammingStep(3, "Follow on-screen instructions")
                        ProgrammingStep(4, "Test new key functionality")
                    }
                }
            }
        }
    }

    // Add Key Dialog
    if (showAddKeyDialog) {
        AddKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onConfirm = { keyId ->
                scope.launch {
                    isProgramming = true
                    programmingStatus = "Programming new key..."
                    delay(3000) // Simulate programming time
                    keyCount++
                    programmingStatus = "Key programmed successfully"
                    isProgramming = false
                    showAddKeyDialog = false
                }
            }
        )
    }

    // Clear All Keys Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Keys?") },
            text = {
                Column {
                    Text("This will remove ALL programmed keys from the immobilizer system.")
                    Spacer(Modifier.height(8.dp))
                    Text("You MUST have a working key or programming tool to regain access to the vehicle.", color = StatusActive)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isProgramming = true
                            programmingStatus = "Clearing all keys..."
                            delay(2000)
                            keyCount = 0
                            programmingStatus = "All keys cleared"
                            isProgramming = false
                            showClearAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusActive)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProgrammingStep(step: Int, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$step",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(description)
    }
}

@Composable
fun AddKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var keyId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Key") },
        text = {
            Column {
                Text("Enter new key identifier (optional):")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = keyId,
                    onValueChange = { keyId = it.take(20) },
                    label = { Text("Key ID") },
                    singleLine = true,
                    maxLines = 1
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Insert the new key into the ignition and follow the prompts.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keyId.ifEmpty { "KEY${(1000..9999).random()}" }) },
                enabled = true
            ) {
                Text("Start Programming", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}