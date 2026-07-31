package com.poshanforlife.android.feature.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.reports.CreateReportScreen
import com.poshanforlife.android.feature.patient.reports.EditReportScreen
import com.poshanforlife.android.feature.patient.reports.ReportDetailScreen
import com.poshanforlife.android.feature.practitioner.catalogue.CatalogueItemFormScreen
import com.poshanforlife.android.feature.practitioner.catalogue.CatalogueScreen
import com.poshanforlife.android.feature.practitioner.documents.CreateEstimateScreen
import com.poshanforlife.android.feature.practitioner.documents.DocumentDetailScreen
import com.poshanforlife.android.feature.practitioner.documents.DocumentsListScreen
import com.poshanforlife.android.feature.practitioner.leads.ConvertToPatientScreen
import com.poshanforlife.android.feature.practitioner.leads.LeadDetailScreen
import com.poshanforlife.android.feature.practitioner.leads.LeadListScreen
import com.poshanforlife.android.feature.practitioner.orders.OrderDetailScreen
import com.poshanforlife.android.feature.practitioner.orders.OrdersScreen
import com.poshanforlife.android.feature.practitioner.orders.TransactionsScreen
import com.poshanforlife.android.feature.practitioner.patients.AssignServiceScreen
import com.poshanforlife.android.feature.practitioner.patients.PatientDetailScreen
import com.poshanforlife.android.feature.products.ProductDetailScreen
import com.poshanforlife.android.feature.products.ProductFormScreen
import com.poshanforlife.android.feature.products.ProductsScreen
import com.poshanforlife.android.feature.products.SegmentManagementScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "admin/root"
private const val DOCUMENTS_ROUTE = "admin/documents"
private const val DOCUMENT_DETAIL_ROUTE = "admin/documents/{documentId}"
private const val CREATE_ESTIMATE_ROUTE = "admin/documents/create-estimate"
private const val ORDERS_ROUTE = "admin/orders"
private const val TRANSACTIONS_ROUTE = "admin/transactions"
private const val ORDER_DETAIL_ROUTE = "admin/orders/{orderId}"
private const val LEADS_ROUTE = "admin/leads"
private const val LEAD_DETAIL_ROUTE = "admin/leads/{leadId}"
private const val CONVERT_LEAD_ROUTE = "admin/leads/{leadId}/convert"
private const val PATIENT_DETAIL_ROUTE = "admin/patients/{patientId}"
private const val PATIENT_REPORT_DETAIL_ROUTE = "admin/patients/{patientId}/reports/{reportId}"
private const val EDIT_REPORT_ROUTE = "admin/patients/{patientId}/reports/{reportId}/edit"
private const val CREATE_REPORT_ROUTE = "admin/patients/{patientId}/reports/new"
private const val ASSIGN_SERVICE_PICKER_ROUTE = "admin/patients/{patientId}/assign"
private const val ASSIGN_SERVICE_DETAILS_ROUTE = "admin/patients/{patientId}/assign/{type}/{itemId}"
private const val SETTINGS_ROUTE = "admin/settings"
private const val CATALOGUE_NEW_ROUTE = "admin/catalogue/{type}/new"
private const val CATALOGUE_EDIT_ROUTE = "admin/catalogue/{type}/{itemId}/edit"
private const val PRODUCTS_ROUTE = "admin/products"
private const val PRODUCT_DETAIL_ROUTE = "admin/products/{productId}"
private const val PRODUCT_NEW_ROUTE = "admin/products/new"
private const val PRODUCT_EDIT_ROUTE = "admin/products/{productId}/edit"
private const val MANAGE_SEGMENTS_ROUTE = "admin/products/segments"

private val items = listOf(
    BottomNavItem("admin/dashboard", "Dashboard", Icons.Filled.Dashboard),
    BottomNavItem("admin/patients", "Patients", Icons.Filled.People),
    BottomNavItem(LEADS_ROUTE, "Leads", Icons.Filled.ContactPhone),
    BottomNavItem(ORDERS_ROUTE, "Orders", Icons.Filled.ShoppingCart),
    BottomNavItem(TRANSACTIONS_ROUTE, "Transactions", Icons.Filled.Receipt),
    BottomNavItem(DOCUMENTS_ROUTE, "Invoices", Icons.Filled.Description),
    BottomNavItem(PRODUCTS_ROUTE, "Products", Icons.Filled.ShoppingBag),
    BottomNavItem("admin/settings", "Settings", Icons.Filled.Settings),
)

fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.ADMIN_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    DOCUMENTS_ROUTE -> DocumentsListScreen(
                        onOpenDocument = { documentId -> navController.navigate("admin/documents/$documentId") },
                        onCreateEstimate = { navController.navigate(CREATE_ESTIMATE_ROUTE) },
                    )
                    ORDERS_ROUTE -> OrdersScreen(
                        onOpenOrder = { orderId -> navController.navigate("admin/orders/$orderId") },
                    )
                    TRANSACTIONS_ROUTE -> TransactionsScreen()
                    LEADS_ROUTE -> LeadListScreen(
                        onOpenLead = { leadId -> navController.navigate("admin/leads/$leadId") },
                    )
                    // Settings doesn't have a real screen of its own yet (a future prompt's
                    // job) — Service Catalogue management is the one concrete admin-only
                    // surface this prompt asks for, so it's the tab's first real content.
                    SETTINGS_ROUTE -> CatalogueScreen(
                        isAdmin = true,
                        onCreateItem = { type -> navController.navigate("admin/catalogue/${type.pathSegment}/new") },
                        onEditItem = { type, itemId ->
                            navController.navigate("admin/catalogue/${type.pathSegment}/$itemId/edit")
                        },
                    )
                    PRODUCTS_ROUTE -> ProductsScreen(
                        isAdmin = true,
                        onOpenProduct = { productId -> navController.navigate("admin/products/$productId") },
                        onCreateProduct = { navController.navigate(PRODUCT_NEW_ROUTE) },
                        onEditProduct = { productId -> navController.navigate("admin/products/$productId/edit") },
                        onManageSegments = { navController.navigate(MANAGE_SEGMENTS_ROUTE) },
                    )
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        composable(PRODUCT_DETAIL_ROUTE) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(PRODUCT_NEW_ROUTE) {
            ProductFormScreen(onBack = { navController.popBackStack() })
        }
        composable(PRODUCT_EDIT_ROUTE) {
            ProductFormScreen(onBack = { navController.popBackStack() })
        }
        composable(MANAGE_SEGMENTS_ROUTE) {
            SegmentManagementScreen(onBack = { navController.popBackStack() })
        }
        composable(DOCUMENT_DETAIL_ROUTE) {
            DocumentDetailScreen()
        }
        composable(CREATE_ESTIMATE_ROUTE) {
            CreateEstimateScreen(
                onBack = { navController.popBackStack() },
                onSaved = { documentId ->
                    navController.navigate("admin/documents/$documentId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
        composable(ORDER_DETAIL_ROUTE) {
            OrderDetailScreen()
        }
        composable(LEAD_DETAIL_ROUTE) {
            LeadDetailScreen(
                onBack = { navController.popBackStack() },
                onConvert = {
                    val leadId = navController.currentBackStackEntry?.arguments?.getString("leadId").orEmpty()
                    navController.navigate("admin/leads/$leadId/convert")
                },
            )
        }
        composable(CONVERT_LEAD_ROUTE) {
            ConvertToPatientScreen(
                onBack = { navController.popBackStack() },
                onConverted = { patientId ->
                    navController.navigate("admin/patients/$patientId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
        // No admin patient list screen exists yet (AN-09 only built the practitioner
        // one) — this sibling route exists purely so the convert flow has somewhere
        // to deep-link to, same PatientDetailScreen reused verbatim.
        composable(PATIENT_DETAIL_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            PatientDetailScreen(
                isAdmin = true,
                onOpenReport = { reportId -> navController.navigate("admin/patients/$patientId/reports/$reportId") },
                onCreateReport = { navController.navigate("admin/patients/$patientId/reports/new") },
                onAssignService = { navController.navigate("admin/patients/$patientId/assign") },
                onBack = { navController.popBackStack() },
            )
        }
        // AN-20: reuses AN-15's CatalogueScreen itself as the type-tabs + item-picker
        // step (pickerMode=true) rather than rebuilding that browse UI.
        composable(ASSIGN_SERVICE_PICKER_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            CatalogueScreen(
                pickerMode = true,
                onItemSelected = { item ->
                    val type = CatalogueType.entries.first { it.wireLabel == item.type }
                    navController.navigate("admin/patients/$patientId/assign/${type.pathSegment}/${item.id}")
                },
            )
        }
        composable(ASSIGN_SERVICE_DETAILS_ROUTE) { backStackEntry ->
            AssignServiceScreen(
                onBack = { navController.popBackStack() },
                onAssigned = {
                    val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
                    navController.navigate("admin/patients/$patientId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
        composable(PATIENT_REPORT_DETAIL_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            ReportDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("admin/patients/$patientId/reports/$reportId/edit") },
            )
        }
        composable(EDIT_REPORT_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            EditReportScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate("admin/patients/$patientId/reports/$reportId") {
                        popUpTo(PATIENT_DETAIL_ROUTE) { inclusive = false }
                    }
                },
            )
        }
        composable(CREATE_REPORT_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            CreateReportScreen(
                onBack = { navController.popBackStack() },
                onSaved = { reportId ->
                    navController.navigate("admin/patients/$patientId/reports/$reportId") {
                        popUpTo(PATIENT_DETAIL_ROUTE) { inclusive = false }
                    }
                },
            )
        }
        composable(CATALOGUE_NEW_ROUTE) {
            CatalogueItemFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
        composable(CATALOGUE_EDIT_ROUTE) {
            CatalogueItemFormScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
    }
}
