package com.poshanforlife.android.feature.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "admin/root"

private val items = listOf(
    BottomNavItem("admin/dashboard", "Dashboard", Icons.Filled.Dashboard),
    BottomNavItem("admin/patients", "Patients", Icons.Filled.People),
    BottomNavItem("admin/leads", "Leads", Icons.Filled.ContactPhone),
    BottomNavItem("admin/orders", "Orders", Icons.Filled.ShoppingCart),
    BottomNavItem("admin/transactions", "Transactions", Icons.Filled.Receipt),
    BottomNavItem("admin/settings", "Settings", Icons.Filled.Settings),
)

fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.ADMIN_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items)
        }
    }
}
