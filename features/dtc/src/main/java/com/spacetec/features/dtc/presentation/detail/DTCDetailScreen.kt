package com.spacetec.features.dtc.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTCDetailScreen(
    dtcCode: String,
    onBackClick: () -> Unit,
    viewModel: DTCDetailViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsState().value

    LaunchedEffect(dtcCode) {
        viewModel.loadDTC(dtcCode)
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("DTC Details") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        when (uiState) {
            is DTCDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is DTCDetailUiState.Success -> {
                DTCDetailContent(
                    dtc = uiState.dtc,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is DTCDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "DTC not found",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DTCDetailContent(
    dtc: DTCDetail,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header Card
        Card(
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    SeverityChip(severity = dtc.severity)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = dtc.description,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(label = "Category", value = dtc.category)
                    InfoChip(label = "System", value = dtc.system)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Explanation
        DetailSection(
            title = "Explanation",
            content = dtc.explanation
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Possible Causes
        if (dtc.possibleCauses.isNotEmpty()) {
            DetailSection(
                title = "Possible Causes",
                items = dtc.possibleCauses
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Symptoms
        if (dtc.symptoms.isNotEmpty()) {
            DetailSection(
                title = "Symptoms",
                items = dtc.symptoms
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Diagnostic Steps
        if (dtc.diagnosticSteps.isNotEmpty()) {
            DetailSection(
                title = "Diagnostic Steps",
                items = dtc.diagnosticSteps
            )
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
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onError
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: String? = null,
    items: List<String>? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            content?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            items?.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
