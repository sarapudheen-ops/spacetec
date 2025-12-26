package com.spacetec.obd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.spacetec.obd.obd.PidDecoder
import com.spacetec.obd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDataScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val liveData by obdService.liveData.collectAsState()
    
    var isMonitoring by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("grid") } // grid or list
    
    val isConnected = connectionState is ObdConnection.ConnectionState.Connected
    
    // Start/stop monitoring based on state
    DisposableEffect(isMonitoring) {
        if (isMonitoring && isConnected) {
            obdService.startLiveData(scope)
        }
        onDispose {
            obdService.stopLiveData()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Data") },
                navigationIcon = {
                    IconButton(onClick = {
                        obdService.stopLiveData()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == "grid") "list" else "grid" }) {
                        Icon(
                            if (viewMode == "grid") Icons.Default.ViewList else Icons.Default.GridView,
                            "Toggle View",
                            tint = Color.White
                        )
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
            // Control Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isMonitoring) "Monitoring Active" else "Monitoring Stopped",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${liveData.size} parameters",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    
                    Button(
                        onClick = { isMonitoring = !isMonitoring },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMonitoring) StatusActive else StatusCleared
                        )
                    ) {
                        Icon(
                            if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isMonitoring) "Stop" else "Start")
                    }
                }
            }
            
            if (!isConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = StatusPending)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect to a scanner to view live data")
                    }
                }
            }
            
            // Live Data Display
            if (liveData.isEmpty() && isMonitoring) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Reading vehicle data...")
                    }
                }
            } else if (liveData.isNotEmpty()) {
                if (viewMode == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(liveData.values.toList()) { value ->
                            LiveDataGridCard(value)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(liveData.values.toList().size) { index ->
                            val value = liveData.values.toList()[index]
                            LiveDataListCard(value)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Speed,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Press Start to begin monitoring")
                    }
                }
            }
        }
    }
}

@Composable
fun LiveDataGridCard(value: ObdService.LiveDataValue) {
    val progress = ((value.value - value.minValue) / (value.maxValue - value.minValue)).coerceIn(0.0, 1.0)
    
    val color = when {
        value.name.contains("Temp", ignoreCase = true) -> {
            when {
                value.value > 100 -> StatusActive
                value.value > 90 -> StatusPending
                else -> StatusCleared
            }
        }
        value.name.contains("RPM", ignoreCase = true) -> {
            when {
                value.value > 6000 -> StatusActive
                value.value > 4000 -> StatusPending
                else -> Primary
            }
        }
        else -> Primary
    }
    
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.name.take(20),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value.displayValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.toFloat().toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun LiveDataListCard(value: ObdService.LiveDataValue) {
    val progress = ((value.value - value.minValue) / (value.maxValue - value.minValue)).coerceIn(0.0, 1.0)
    
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(value.name, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress.toFloat().toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Primary,
                    trackColor = Primary.copy(alpha = 0.2f)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                value.displayValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
    }
}
