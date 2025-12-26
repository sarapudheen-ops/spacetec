package com.spacetec.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.spacetec.features.dtc.presentation.list.DTCListScreen
import com.spacetec.features.dtc.presentation.detail.DTCDetailScreen
import com.spacetec.features.dtc.presentation.scan.DTCScanScreen
import com.spacetec.features.dtc.presentation.clear.DTCClearScreen
import com.spacetec.features.livedata.presentation.LiveDataScreen
import com.spacetec.features.dashboard.DashboardScreen
import com.spacetec.features.connection.ConnectionScreen
import com.spacetec.features.reports.presentation.ReportsScreen
import com.spacetec.app.settings.SettingsScreen

@Composable
fun SpaceTecNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToDTCList = {
                    navController.navigate("dtc_list")
                },
                onNavigateToLiveData = {
                    navController.navigate("live_data")
                },
                onNavigateToConnection = {
                    navController.navigate("connection")
                },
                onNavigateToReports = {
                    navController.navigate("reports")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        
        composable("dtc_list") {
            DTCListScreen(
                onDTCClick = { dtcCode ->
                    navController.navigate("dtc_detail/$dtcCode")
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onScanClick = {
                    navController.navigate("dtc_scan")
                },
                onClearClick = {
                    navController.navigate("dtc_clear")
                }
            )
        }
        
        composable("dtc_detail/{dtcCode}") { backStackEntry ->
            val dtcCode = backStackEntry.arguments?.getString("dtcCode") ?: ""
            DTCDetailScreen(
                dtcCode = dtcCode,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("dtc_scan") {
            DTCScanScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onDTCsFound = { dtcCodes ->
                    navController.popBackStack()
                }
            )
        }
        
        composable("dtc_clear") {
            DTCClearScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("live_data") {
            LiveDataScreen()
        }
        
        composable("connection") {
            ConnectionScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("reports") {
            ReportsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
