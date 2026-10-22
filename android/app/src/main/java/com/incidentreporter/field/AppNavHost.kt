package com.incidentreporter.field

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.incidentreporter.field.feature.list.IncidentListScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ListRoute) {
        composable<ListRoute> {
            IncidentListScreen(onOpen = {})
        }
    }
}
