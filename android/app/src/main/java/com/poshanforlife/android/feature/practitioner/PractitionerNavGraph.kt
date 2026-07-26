package com.poshanforlife.android.feature.practitioner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.practitioner.schedule.ScheduleScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "practitioner/root"
private const val SCHEDULE_ROUTE = "practitioner/schedule"

// Role value on the wire stays DOCTOR (see Role.kt) — only user-facing text says "Practitioner".
private val items = listOf(
    BottomNavItem("practitioner/patients", "Patients", Icons.Filled.People),
    BottomNavItem("practitioner/leads", "Leads", Icons.Filled.ContactPhone),
    BottomNavItem("practitioner/upload", "Upload", Icons.Filled.CloudUpload),
    BottomNavItem(SCHEDULE_ROUTE, "Schedule", Icons.Filled.Schedule),
    BottomNavItem("practitioner/orders", "Orders", Icons.Filled.ShoppingCart),
    BottomNavItem("practitioner/profile", "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.practitionerGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PRACTITIONER_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    SCHEDULE_ROUTE -> ScheduleScreen()
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
    }
}
