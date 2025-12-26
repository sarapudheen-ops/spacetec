package com.spacetec.obd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spacetec.obd.obd.ObdService
import com.spacetec.obd.ui.screens.*
import com.spacetec.obd.ui.theme.SpaceTecTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpaceTecTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpaceTecApp()
                }
            }
        }
    }
}

@Composable
fun SpaceTecApp() {
    val context = LocalContext.current
    val obdService = remember { ObdService(context) }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                obdService = obdService,
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable("dtc_list") {
            DtcListScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() },
                onDtcClick = { code -> navController.navigate("dtc_detail/$code") }
            )
        }

        composable(
            route = "dtc_detail/{code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: ""
            DtcDetailScreen(
                dtcCode = code,
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("live_data") {
            LiveDataScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("connection") {
            ConnectionScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("readiness") {
            ReadinessScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("ecu_programming") {
            EcuProgrammingScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("key_programming") {
            KeyProgrammingScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bidirectional_controls") {
            BidirectionalControlsScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("vehicle_reports") {
            VehicleReportsScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("maintenance_functions") {
            MaintenanceFunctionsScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("vin_decoding") {
            VINDecodingScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("protocol_selection") {
            ProtocolSelectionScreen(
                obdService = obdService,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
