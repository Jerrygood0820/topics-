package com.example.taoyuantrafficgps.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.taoyuantrafficgps.ui.screen.EventDetailScreen
import com.example.taoyuantrafficgps.ui.screen.MapScreen
import com.example.taoyuantrafficgps.ui.screen.ReportScreen

object Routes {
    const val MAP = "map"
    const val EVENT_DETAIL = "eventDetail"
    const val REPORT = "report"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onOpenReport = { lat, lng ->
                    nav.navigate("${Routes.REPORT}?lat=$lat&lng=$lng")
                },
                onOpenEventDetail = { eventId ->
                    nav.navigate("${Routes.EVENT_DETAIL}/$eventId")
                }
            )
        }

        composable(
            route = "${Routes.EVENT_DETAIL}/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId").orEmpty()
            EventDetailScreen(eventId = eventId, onBack = { nav.popBackStack() })
        }

        composable(
            route = "${Routes.REPORT}?lat={lat}&lng={lng}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStack ->
            val latStr = backStack.arguments?.getString("lat").orEmpty()
            val lngStr = backStack.arguments?.getString("lng").orEmpty()
            ReportScreen(
                initialLat = latStr.toDoubleOrNull(),
                initialLng = lngStr.toDoubleOrNull(),
                onDone = { nav.popBackStack() },
            )
        }
    }
}
