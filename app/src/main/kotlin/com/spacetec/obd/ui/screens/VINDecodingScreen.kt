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
fun VINDecodingScreen(
    obdService: ObdService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionState by obdService.connection.connectionState.collectAsState()
    val vehicleInfoState by obdService.vehicleInfo.collectAsState()

    var vin by remember { mutableStateOf(vehicleInfoState?.vin ?: "") }
    var decodedInfo by remember { mutableStateOf<VinInfo?>(null) }
    var isDecoding by remember { mutableStateOf(false) }
    var showVinInputDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnection.ConnectionState.Connected

    // Decode VIN when vehicle info is available
    LaunchedEffect(vehicleInfoState?.vin) {
        // Always snapshot nullable state into a local before multiple uses
        val vehicleInfo = vehicleInfoState
        val newVin = vehicleInfo?.vin
        if (newVin != null && newVin != vin) {
            vin = newVin
            decodedInfo = decodeVin(newVin)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIN Decoder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showVinInputDialog = true },
                        enabled = isConnected
                    ) {
                        Icon(Icons.Default.Edit, "Enter VIN", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                isDecoding = true
                                decodedInfo = decodeVin(vin)
                                isDecoding = false
                            }
                        },
                        enabled = vin.length == 17 && !isDecoding
                    ) {
                        Icon(Icons.Default.Refresh, "Decode", tint = Color.White)
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
                // VIN Input Card
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vehicle Identification Number", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vin,
                            onValueChange = { 
                                if (it.length <= 17) {
                                    vin = it.uppercase().replace(Regex("[^A-HJ-NPR-Z0-9]"), "")
                                }
                            },
                            label = { Text("Enter VIN") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { vin = "" },
                                    enabled = vin.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            },
                            isError = vin.isNotEmpty() && vin.length != 17
                        )
                        if (vin.isNotEmpty() && vin.length != 17) {
                            Text(
                                "VIN must be 17 characters",
                                color = StatusActive,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                            Text("Connect to a scanner to read VIN automatically")
                        }
                    }
                }
            }

            if (isDecoding) {
                item {
                    Card {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Decoding VIN...")
                        }
                    }
                }
            }

            decodedInfo?.let { info ->
                item {
                    // Vehicle Info Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vehicle Information", fontWeight = FontWeight.Bold)
                                if (info.isVwGroup) {
                                    Surface(
                                        color = Primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "VW Group",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Make", info.make)
                            InfoRow("Model", info.model)
                            InfoRow("Year", info.year.toString())
                            InfoRow("Engine", info.engine)
                            InfoRow("Transmission", info.transmission)
                            InfoRow("Body Type", info.bodyType)
                            InfoRow("Plant", info.plant)
                        }
                    }
                }

                item {
                    // VIN Breakdown
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("VIN Breakdown", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))

                            VinBreakdownRow("World Manufacturer Identifier (WMI)", vin.take(3), "Manufacturer code")
                            VinBreakdownRow("Vehicle Descriptor Section (VDS)", vin.substring(3, 8), "Vehicle attributes")
                            VinBreakdownRow("Vehicle Identifier Section (VIS)", vin.substring(8), "Sequential number")
                        }
                    }
                }

                item {
                    // WMI Details
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("WMI Details", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("WMI", vin.take(3))
                            InfoRow("Manufacturer", info.wmiDescription)
                            InfoRow("Country", info.country)
                        }
                    }
                }

                item {
                    // Compliance Information
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Compliance Information", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Model Year", info.year.toString())
                            InfoRow("Safety Standard", info.safetyStandard)
                            InfoRow("Vehicle Type", info.vehicleType)
                        }
                    }
                }
            }

            if (decodedInfo == null && vin.isNotEmpty() && vin.length == 17 && !isDecoding) {
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
                            Text("Unable to decode VIN. Check the VIN and try again.")
                        }
                    }
                }
            }
        }
    }

    // VIN Input Dialog
    if (showVinInputDialog) {
        VinInputDialog(
            currentVin = vin,
            onConfirm = { newVin ->
                vin = newVin
                scope.launch {
                    isDecoding = true
                    decodedInfo = decodeVin(vin)
                    isDecoding = false
                }
                showVinInputDialog = false
            },
            onDismiss = { showVinInputDialog = false }
        )
    }
}

@Composable
fun VinBreakdownRow(title: String, value: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value, fontWeight = FontWeight.Bold, color = Primary)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun VinInputDialog(
    currentVin: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var vin by remember { mutableStateOf(currentVin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter VIN") },
        text = {
            Column {
                Text("Enter the 17-character VIN:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vin,
                    onValueChange = { 
                        if (it.length <= 17) {
                            vin = it.uppercase().replace(Regex("[^A-HJ-NPR-Z0-9]"), "")
                        }
                    },
                    label = { Text("VIN") },
                    isError = vin.isNotEmpty() && vin.length != 17
                )
                if (vin.isNotEmpty() && vin.length != 17) {
                    Text(
                        "VIN must be 17 characters",
                        color = StatusActive,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(vin) },
                enabled = vin.length == 17
            ) {
                Text("Decode", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// VIN decoding logic
data class VinInfo(
    val make: String,
    val model: String,
    val year: Int,
    val engine: String,
    val transmission: String,
    val bodyType: String,
    val plant: String,
    val wmiDescription: String,
    val country: String,
    val safetyStandard: String,
    val vehicleType: String,
    val isVwGroup: Boolean
)

fun decodeVin(vin: String): VinInfo? {
    if (vin.length != 17) return null

    // Extract WMI (World Manufacturer Identifier) - first 3 characters
    val wmi = vin.substring(0, 3)
    val wmiInfo = decodeWMI(wmi)

    // Extract model year (10th character)
    val year = decodeYear(vin[9])

    // Extract vehicle attributes (4th-8th characters)
    val vds = vin.substring(3, 8)

    // Extract plant code (11th character)
    val plantCode = vin[10]

    // Extract sequential number (12th-17th characters)
    val serial = vin.substring(11)

    return VinInfo(
        make = wmiInfo.make,
        model = "Model ${vds.substring(0, 2)}", // Simplified model extraction
        year = year,
        engine = decodeEngine(vds[2]), // Simplified engine decoding
        transmission = decodeTransmission(vds[3]), // Simplified transmission decoding
        bodyType = decodeBodyType(vds[0]), // Simplified body type decoding
        plant = decodePlant(plantCode),
        wmiDescription = wmiInfo.description,
        country = wmiInfo.country,
        safetyStandard = "FMVSS", // Simplified safety standard
        vehicleType = decodeVehicleType(vin[0]), // Simplified vehicle type decoding
        isVwGroup = wmi.startsWith("W") // VW Group vehicles start with W
    )
}

// Simplified WMI decoding
data class WMIInfo(
    val make: String,
    val description: String,
    val country: String
)

fun decodeWMI(wmi: String): WMIInfo {
    return when (wmi) {
        "1HG" -> WMIInfo("Honda", "Honda Motor Company", "USA")
        "2HG" -> WMIInfo("Honda", "Honda Motor Company", "Canada")
        "1FT" -> WMIInfo("Ford", "Ford Motor Company", "USA")
        "WBA" -> WMIInfo("BMW", "Bayerische Motoren Werke AG", "Germany")
        "WBS" -> WMIInfo("BMW", "BMW Motorsport", "Germany")
        "WDB" -> WMIInfo("Mercedes-Benz", "Daimler AG", "Germany")
        "WVW" -> WMIInfo("Volkswagen", "Volkswagen AG", "Germany")
        "WDD" -> WMIInfo("Mercedes-Benz", "Daimler AG", "Germany")
        "1N4" -> WMIInfo("Nissan", "Nissan Motor Co., Ltd.", "USA")
        "JH4" -> WMIInfo("Acura", "Honda Motor Co., Ltd.", "Japan")
        "KM8" -> WMIInfo("Hyundai", "Hyundai Motor Company", "South Korea")
        "SAJ" -> WMIInfo("Jaguar", "Jaguar Land Rover", "UK")
        "SCA" -> WMIInfo("Aston Martin", "Aston Martin Lagonda Ltd", "UK")
        "W" -> WMIInfo("Volkswagen Group", "Volkswagen AG & Affiliates", "Germany")
        else -> when {
            wmi.startsWith("W") -> WMIInfo("Volkswagen Group", "Volkswagen AG & Affiliates", "Germany")
            wmi.startsWith("1") || wmi.startsWith("4") -> WMIInfo("USA", "North American Manufacturer", "USA")
            wmi.startsWith("2") -> WMIInfo("Canada", "Canadian Manufacturer", "Canada")
            wmi.startsWith("3") -> WMIInfo("Mexico", "Mexican Manufacturer", "Mexico")
            wmi.startsWith("J") -> WMIInfo("Japan", "Japanese Manufacturer", "Japan")
            wmi.startsWith("K") -> WMIInfo("South Korea", "Korean Manufacturer", "South Korea")
            wmi.startsWith("S") -> WMIInfo("UK", "British Manufacturer", "UK")
            wmi.startsWith("V") -> WMIInfo("Europe", "European Manufacturer", "Europe")
            wmi.startsWith("Z") -> WMIInfo("Italy", "Italian Manufacturer", "Italy")
            wmi.startsWith("1") || wmi.startsWith("4") -> WMIInfo("USA", "North American Manufacturer", "USA")
            else -> WMIInfo("Unknown", "Unknown Manufacturer", "Unknown")
        }
    }
}

fun decodeYear(yearCode: Char): Int {
    val yearMap = mapOf(
        'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
        'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
        'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
        'S' to 2025, 'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029, 'Y' to 2030
    )
    return yearMap[yearCode] ?: 2020 // Default to 2020 if unknown
}

fun decodeEngine(engineCode: Char): String {
    return when (engineCode) {
        'A' -> "1.0L I4"
        'B' -> "1.2L I4"
        'C' -> "1.4L I4"
        'D' -> "1.6L I4"
        'E' -> "1.8L I4"
        'F' -> "2.0L I4"
        'G' -> "2.2L I4"
        'H' -> "2.4L I4"
        'J' -> "3.0L V6"
        'K' -> "3.2L V6"
        'L' -> "3.5L V6"
        'M' -> "4.0L V8"
        'N' -> "4.5L V8"
        'P' -> "5.0L V8"
        else -> "Unknown Engine"
    }
}

fun decodeTransmission(transCode: Char): String {
    return when (transCode) {
        'A' -> "Automatic"
        'M' -> "Manual"
        'S' -> "Semi-automatic"
        else -> "Unknown Transmission"
    }
}

fun decodeBodyType(bodyCode: Char): String {
    return when (bodyCode) {
        '1' -> "2-Door Coupe"
        '2' -> "4-Door Sedan"
        '3' -> "Convertible"
        '4' -> "Wagon"
        '5' -> "Hatchback"
        '6' -> "Pickup"
        '7' -> "SUV"
        '8' -> "Van"
        else -> "Unknown Body Type"
    }
}

fun decodePlant(plantCode: Char): String {
    return when (plantCode) {
        'A' -> "USA Plant A"
        'B' -> "USA Plant B"
        'C' -> "Canada"
        'D' -> "Mexico"
        'F' -> "USA Plant F"
        'G' -> "USA Plant G"
        'H' -> "USA Plant H"
        'J' -> "Japan"
        'K' -> "South Korea"
        'M' -> "Mexico"
        'R' -> "USA Plant R"
        'S' -> "USA Plant S"
        'T' -> "Thailand"
        'V' -> "USA Plant V"
        else -> "Unknown Plant"
    }
}

fun decodeVehicleType(vinFirstChar: Char): String {
    return when (vinFirstChar) {
        '1', '4', '5' -> "Passenger Car"
        '2' -> "Canada Passenger Car"
        '3' -> "Mexico/Other Passenger Car"
        'J' -> "Japan Passenger Car"
        'K' -> "South Korea"
        'L' -> "China"
        'M' -> "India"
        'N' -> "Iran"
        'S' -> "UK"
        'V' -> "France/Spain"
        'W' -> "Germany"
        'Z' -> "Italy"
        else -> "Unknown Vehicle Type"
    }
}