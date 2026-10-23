package com.incidentreporter.field

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.incidentreporter.field.feature.capture.CaptureScreen
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

/** No draft yet means the first photo creates one. */
@Serializable
data class CaptureRoute(val draftId: String? = null)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ListRoute) {
        composable<ListRoute> {
            IncidentListScreen(
                // launchSingleTop: a gloved double tap opens one detail screen, not two.
                onOpen = { id ->
                    navController.navigate(DetailRoute(id)) {
                        launchSingleTop = true
                    }
                },
                onReport = { navController.navigate(CaptureRoute()) { launchSingleTop = true } },
            )
        }
        composable<DetailRoute> {
            // navigateUp, not popBackStack: a double tap on Back can't pop the list and leave a blank screen.
            IncidentDetailScreen(
                onBack = { navController.navigateUp() },
                onEdit = { id -> navController.navigate(FormRoute(id)) { launchSingleTop = true } },
            )
        }
        composable<FormRoute> { entry ->
            val route = entry.toRoute<FormRoute>()
            IncidentFormScreen(
                // Submitted, saved or discarded, the report is done with: back to the list, past the detail screen after an edit.
                onDone = { navController.popBackStack<ListRoute>(inclusive = false) },
                onBack = { navController.navigateUp() },
                onTakePhoto = {
                    navController.navigate(CaptureRoute(draftId = route.incidentId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<CaptureRoute> {
            CaptureScreen(
                // The camera leaves the back stack, so Back from the new report's form goes to the list.
                onOpenForm = { id ->
                    navController.navigate(FormRoute(id)) {
                        popUpTo<CaptureRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.navigateUp() },
            )
        }
    }
}
