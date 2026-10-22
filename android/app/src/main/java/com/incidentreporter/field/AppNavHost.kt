package com.incidentreporter.field

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.incidentreporter.field.feature.detail.IncidentDetailScreen
import com.incidentreporter.field.feature.list.IncidentListScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class DetailRoute(val id: String)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ListRoute) {
        composable<ListRoute> {
            IncidentListScreen(
                // launchSingleTop: a gloved double tap opens one detail screen, not two.
                onOpen = { id -> navController.navigate(DetailRoute(id)) { launchSingleTop = true } },
            )
        }
        composable<DetailRoute> {
            // navigateUp, not popBackStack: a double tap on Back can't pop the list and leave a blank screen.
            IncidentDetailScreen(onBack = { navController.navigateUp() })
        }
    }
}
