package com.spacetec.features.dtc.presentation.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTCListScreen(
    onDTCClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit = {},
    onClearClick: () -> Unit = {},
    viewModel: DTCListViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsState().value
    var searchQuery by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.searchDTCs(it)
            },
            label = { Text("Search DTCs") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Scan DTCs")
            }
            
            OutlinedButton(
                onClick = onClearClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear DTCs")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (uiState) {
            is DTCListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is DTCListUiState.Success -> {
                if (uiState.dtcs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No DTCs found" else "No DTCs match your search",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.dtcs) { dtc ->
                            DTCListItem(
                                dtc = dtc,
                                onClick = { onDTCClick(dtc.code) }
                            )
                        }
                    }
                }
            }
            
            is DTCListUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading DTCs",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadDTCs() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DTCListItem(
    dtc: DTCListItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = dtc.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                SeverityChip(severity = dtc.severity)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = dtc.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            
            if (dtc.category.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Category: ${dtc.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: String) {
    val (color, text) = when (severity.uppercase()) {
        "CRITICAL" -> MaterialTheme.colorScheme.error to "Critical"
        "HIGH" -> MaterialTheme.colorScheme.errorContainer to "High"
        "MEDIUM" -> MaterialTheme.colorScheme.tertiary to "Medium"
        "LOW" -> MaterialTheme.colorScheme.secondary to "Low"
        else -> MaterialTheme.colorScheme.outline to "Info"
    }
    
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError
        )
    }
}
