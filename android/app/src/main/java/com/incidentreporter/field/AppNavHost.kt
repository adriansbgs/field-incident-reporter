package com.incidentreporter.field

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.incidentreporter.field.feature.detail.IncidentDetailScreen
import com.incidentreporter.field.feature.form.IncidentFormScreen
import com.incidentreporter.field.feature.list.IncidentListScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Serializable
data class DetailRoute(val id: String)

@Serializable
data class FormRoute(val incidentId: String)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ListRoute) {
        composable<ListRoute> {
            IncidentListScreen(
                // launchSingleTop: a gloved double tap opens one detail screen, not two.
                onOpen = { id -> navController.navigate(DetailRoute(id)) { launchSingleTop = true } },
                onOpenForm = { id -> navController.navigate(FormRoute(id)) { launchSingleTop = true } },
            )
        }
        composable<DetailRoute> {
            // navigateUp, not popBackStack: a double tap on Back can't pop the list and leave a blank screen.
            IncidentDetailScreen(
                onBack = { navController.navigateUp() },
                onEdit = { id -> navController.navigate(FormRoute(id)) { launchSingleTop = true } },
            )
        }
        composable<FormRoute> {
            IncidentFormScreen(
                // Submitted, saved or discarded, the report is done with: back to the list, past the detail screen after an edit.
                onDone = { navController.popBackStack<ListRoute>(inclusive = false) },
                onBack = { navController.navigateUp() },
            )
        }
    }
}
