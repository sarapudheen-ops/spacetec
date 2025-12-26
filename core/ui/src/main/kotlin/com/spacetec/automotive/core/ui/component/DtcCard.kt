// core/ui/src/main/kotlin/com/spacetec/automotive/core/ui/component/DtcCard.kt
package com.spacetec.obd.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spacetec.obd.core.domain.model.dtc.DtcCode
import com.spacetec.obd.core.domain.model.dtc.DtcDefinition
import com.spacetec.obd.core.domain.model.dtc.DtcSeverity
import com.spacetec.obd.core.domain.model.dtc.DtcStatus
import com.spacetec.obd.core.domain.model.dtc.DtcSystem
import com.spacetec.obd.core.ui.theme.SpaceTecColors
import com.spacetec.obd.core.ui.theme.SpaceTecTextStyles
import com.spacetec.obd.core.ui.theme.SpaceTecTheme
import com.spacetec.obd.core.ui.theme.severityColor

/**
 * Card component for displaying a DTC with its definition.
 * 
 * @param dtc The DTC code to display
 * @param definition Optional definition for the DTC
 * @param onClick Callback when the card is clicked
 * @param onInfoClick Callback when the info button is clicked
 * @param modifier Modifier for the card
 * @param expanded Whether the card is expanded to show details
 */
@Composable
fun DtcCard(
    dtc: DtcCode,
    definition: DtcDefinition?,
    onClick: () -> Unit,
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(expanded) }
    
    val severity = definition?.severity ?: DtcSeverity.INFO
    val severityColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.severityColor(severity),
        label = "severityColor"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Severity indicator strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(severityColor)
            )
            
            // Main content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Severity icon
                SeverityIcon(severity = severity)
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // DTC info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // DTC Code and Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dtc.code,
                            style = SpaceTecTextStyles.dtcCode,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        StatusChip(status = dtc.status)
                        
                        if (dtc.milStatus) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MilIndicator()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Description
                    Text(
                        text = definition?.shortDescription ?: "Unknown DTC",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // System and ECU info
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SystemChip(system = dtc.system)
                        
                        if (dtc.ecuAddress.isNotEmpty()) {
                            EcuChip(ecuAddress = dtc.ecuAddress)
                        }
                    }
                }
                
                // Expand/Info buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess 
                                         else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                    
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "More info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DtcExpandedContent(
                    dtc = dtc,
                    definition = definition,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SeverityIcon(severity: DtcSeverity) {
    val (color, icon) = when (severity) {
        DtcSeverity.CRITICAL -> SpaceTecColors.Critical to Icons.Default.Warning
        DtcSeverity.HIGH -> SpaceTecColors.Critical to Icons.Default.Warning
        DtcSeverity.MAJOR -> SpaceTecColors.Major to Icons.Default.Warning
        DtcSeverity.MEDIUM -> SpaceTecColors.Major to Icons.Default.Warning
        DtcSeverity.MINOR -> SpaceTecColors.Minor to Icons.Default.Info
        DtcSeverity.LOW -> SpaceTecColors.Minor to Icons.Default.Info
        DtcSeverity.INFO -> SpaceTecColors.Info to Icons.Default.Info
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = severity.displayName,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun StatusChip(status: DtcStatus) {
    val (color, text) = when (status) {
        DtcStatus.CONFIRMED, DtcStatus.ACTIVE -> SpaceTecColors.Critical to "Active"
        DtcStatus.PENDING -> SpaceTecColors.Major to "Pending"
        DtcStatus.PERMANENT -> SpaceTecColors.Critical to "Permanent"
        DtcStatus.HISTORICAL, DtcStatus.INACTIVE -> SpaceTecColors.Info to "Inactive"
        DtcStatus.UNKNOWN -> Color.Gray to "Unknown"
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SystemChip(system: DtcSystem) {
    val color = when (system) {
        DtcSystem.POWERTRAIN -> SpaceTecColors.Powertrain
        DtcSystem.BODY -> SpaceTecColors.Body
        DtcSystem.CHASSIS -> SpaceTecColors.Chassis
        DtcSystem.NETWORK -> SpaceTecColors.Network
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = system.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EcuChip(ecuAddress: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "ECU: $ecuAddress",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MilIndicator() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = SpaceTecColors.MilOn.copy(alpha = 0.1f)
    ) {
        Text(
            text = "MIL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SpaceTecColors.MilOn,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DtcExpandedContent(
    dtc: DtcCode,
    definition: DtcDefinition?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Possible causes
        if (definition?.possibleCauses?.isNotEmpty() == true) {
            ExpandedSection(
                title = "Possible Causes",
                icon = Icons.Outlined.CarRepair
            ) {
                definition.possibleCauses.take(3).forEach { cause ->
                    Text(
                        text = "• $cause",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
        
        // Symptoms
        if (definition?.symptoms?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(8.dp))
            ExpandedSection(
                title = "Symptoms",
                icon = Icons.Outlined.Description
            ) {
                definition.symptoms.take(3).forEach { symptom ->
                    Text(
                        text = "• $symptom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
        
        // Occurrence info
        if (dtc.occurrenceCount > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Occurred ${dtc.occurrenceCount} times",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ExpandedSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DtcCardPreview() {
    SpaceTecTheme {
        DtcCard(
            dtc = DtcCode(
                code = "P0301",
                status = DtcStatus.CONFIRMED,
                milStatus = true,
                ecuAddress = "7E8"
            ),
            definition = DtcDefinition(
                code = "P0301",
                shortDescription = "Cylinder 1 Misfire Detected",
                possibleCauses = listOf(
                    "Faulty spark plug",
                    "Faulty ignition coil",
                    "Fuel injector problem"
                ),
                symptoms = listOf(
                    "Rough idle",
                    "Engine vibration",
                    "Reduced power"
                ),
                severity = DtcSeverity.MAJOR
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}