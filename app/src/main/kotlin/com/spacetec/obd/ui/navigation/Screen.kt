package com.spacetec.obd.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object DtcList : Screen("dtc_list")
    object DtcDetail : Screen("dtc_detail/{code}") {
        fun createRoute(code: String) = "dtc_detail/$code"
    }
    object LiveData : Screen("live_data")
    object Connection : Screen("connection")
    object Settings : Screen("settings")
}