package com.spacetec.features.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.spacetec.domain.repository.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onBackClick: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Connection") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = when (connectionState) {
                                is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "Connected"
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Disconnected -> "Not Connected"
                                is ConnectionState.Initializing -> "Initializing..."
                                is ConnectionState.Scanning -> "Scanning..."
                                is ConnectionState.Error -> "Error"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (connectionState) {
                                is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            // Available Adapters
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Available Adapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startBleScan() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning
                    ) {
                        Text("Scan for BLE Adapters")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.startClassicBluetoothScan() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning
                    ) {
                        Text("Scan for Classic Bluetooth Adapters")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.startWiFiScan() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan for WiFi Adapters")
                    }
                }
            }

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Make sure your OBD adapter is plugged into the vehicle's OBD port\n" +
                              "2. Turn on the vehicle's ignition\n" +
                              "3. Enable Bluetooth on your device\n" +
                              "4. Tap 'Scan for BLE Adapters' or 'Scan for Classic Bluetooth Adapters' to find your adapter",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
