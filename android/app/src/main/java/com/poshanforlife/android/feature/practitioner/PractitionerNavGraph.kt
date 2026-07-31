package com.poshanforlife.android.feature.practitioner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.programmes.ProgrammeDetailScreen
import com.poshanforlife.android.feature.patient.reports.CreateReportScreen
import com.poshanforlife.android.feature.patient.reports.EditReportScreen
import com.poshanforlife.android.feature.patient.reports.ReportDetailScreen
import com.poshanforlife.android.feature.practitioner.catalogue.CatalogueScreen
import com.poshanforlife.android.feature.practitioner.documents.CreateEstimateScreen
import com.poshanforlife.android.feature.practitioner.documents.DocumentDetailScreen
import com.poshanforlife.android.feature.practitioner.documents.DocumentsListScreen
import com.poshanforlife.android.feature.practitioner.leads.ConvertToPatientScreen
import com.poshanforlife.android.feature.practitioner.leads.LeadDetailScreen
import com.poshanforlife.android.feature.practitioner.leads.LeadListScreen
import com.poshanforlife.android.feature.practitioner.orders.OrderDetailScreen
import com.poshanforlife.android.feature.practitioner.orders.OrdersAndTransactionsScreen
import com.poshanforlife.android.feature.practitioner.patients.AssignServiceScreen
import com.poshanforlife.android.feature.practitioner.patients.PatientDetailScreen
import com.poshanforlife.android.feature.practitioner.patients.PatientListScreen
import com.poshanforlife.android.feature.practitioner.schedule.ScheduleScreen
import com.poshanforlife.android.feature.practitioner.upload.CaptureScreen
import com.poshanforlife.android.feature.practitioner.upload.ReviewScreen
import com.poshanforlife.android.feature.products.ProductDetailScreen
import com.poshanforlife.android.feature.products.ProductsScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "practitioner/root"
private const val SCHEDULE_ROUTE = "practitioner/schedule"
private const val PATIENTS_ROUTE = "practitioner/patients"
private const val UPLOAD_ROUTE = "practitioner/upload"
private const val DOCUMENTS_ROUTE = "practitioner/documents"
private const val PATIENT_DETAIL_ROUTE = "practitioner/patients/{patientId}"
private const val PATIENT_REPORT_DETAIL_ROUTE = "practitioner/patients/{patientId}/reports/{reportId}"
private const val EDIT_REPORT_ROUTE = "practitioner/patients/{patientId}/reports/{reportId}/edit"
private const val CREATE_REPORT_ROUTE = "practitioner/patients/{patientId}/reports/new"
private const val PATIENT_PROGRAMME_DETAIL_ROUTE = "practitioner/patients/{patientId}/programmes/{programmeId}"
private const val ASSIGN_SERVICE_PICKER_ROUTE = "practitioner/patients/{patientId}/assign"
private const val ASSIGN_SERVICE_DETAILS_ROUTE = "practitioner/patients/{patientId}/assign/{type}/{itemId}"
private const val CAPTURE_ROUTE = "practitioner/upload/capture/{patientId}"
private const val REVIEW_ROUTE = "practitioner/upload/review/{patientId}/{reportId}"
private const val DOCUMENT_DETAIL_ROUTE = "practitioner/documents/{documentId}"
private const val CREATE_ESTIMATE_ROUTE = "practitioner/documents/create-estimate"
private const val ORDERS_ROUTE = "practitioner/orders"
private const val ORDER_DETAIL_ROUTE = "practitioner/orders/{orderId}"
private const val LEADS_ROUTE = "practitioner/leads"
private const val LEAD_DETAIL_ROUTE = "practitioner/leads/{leadId}"
private const val CONVERT_LEAD_ROUTE = "practitioner/leads/{leadId}/convert"
private const val PRODUCTS_ROUTE = "practitioner/products"
private const val PRODUCT_DETAIL_ROUTE = "practitioner/products/{productId}"
private const val PROFILE_ROUTE = "practitioner/profile"

// Role value on the wire stays DOCTOR (see Role.kt) — only user-facing text says "Practitioner".
private val items = listOf(
    BottomNavItem(PATIENTS_ROUTE, "Patients", Icons.Filled.People),
    BottomNavItem(LEADS_ROUTE, "Leads", Icons.Filled.ContactPhone),
    BottomNavItem("practitioner/upload", "Upload", Icons.Filled.CloudUpload),
    BottomNavItem(SCHEDULE_ROUTE, "Schedule", Icons.Filled.Schedule),
    BottomNavItem(ORDERS_ROUTE, "Orders", Icons.Filled.ShoppingCart),
    BottomNavItem(DOCUMENTS_ROUTE, "Invoices", Icons.Filled.Receipt),
    BottomNavItem(PRODUCTS_ROUTE, "Products", Icons.Filled.ShoppingBag),
    BottomNavItem("practitioner/profile", "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.practitionerGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PRACTITIONER_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    SCHEDULE_ROUTE -> ScheduleScreen()
                    PATIENTS_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/patients/$patientId") },
                    )
                    // Reuses AN-09's patient picker verbatim — tapping a patient here starts a
                    // report capture for them instead of opening their detail page.
                    UPLOAD_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/upload/capture/$patientId") },
                    )
                    DOCUMENTS_ROUTE -> DocumentsListScreen(
                        onOpenDocument = { documentId -> navController.navigate("practitioner/documents/$documentId") },
                        onCreateEstimate = { navController.navigate(CREATE_ESTIMATE_ROUTE) },
                    )
                    ORDERS_ROUTE -> OrdersAndTransactionsScreen(
                        onOpenOrder = { orderId -> navController.navigate("practitioner/orders/$orderId") },
                    )
                    LEADS_ROUTE -> LeadListScreen(
                        onOpenLead = { leadId -> navController.navigate("practitioner/leads/$leadId") },
                    )
                    // Read-only for DOCTOR — see AdminNavGraph for the admin-mode instance of this same screen.
                    PRODUCTS_ROUTE -> ProductsScreen(
                        isAdmin = false,
                        onOpenProduct = { productId -> navController.navigate("practitioner/products/$productId") },
                    )
                    // Profile doesn't have a real screen of its own yet (a future prompt's
                    // job) — read-only Service Catalogue browse is the one concrete
                    // practitioner-facing surface this prompt asks for.
                    PROFILE_ROUTE -> CatalogueScreen(isAdmin = false)
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        composable(PRODUCT_DETAIL_ROUTE) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
        // Opens as a full screen above the bottom-nav shell, same convention as the patient graph's sibling detail routes.
        composable(PATIENT_DETAIL_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            PatientDetailScreen(
                isAdmin = false,
                onOpenReport = { reportId -> navController.navigate("practitioner/patients/$patientId/reports/$reportId") },
                onCreateReport = { navController.navigate("practitioner/patients/$patientId/reports/new") },
                onOpenProgramme = { programmeId ->
                    navController.navigate("practitioner/patients/$patientId/programmes/$programmeId")
                },
                onAssignService = { navController.navigate("practitioner/patients/$patientId/assign") },
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
                    navController.navigate("practitioner/patients/$patientId/assign/${type.pathSegment}/${item.id}")
                },
            )
        }
        composable(ASSIGN_SERVICE_DETAILS_ROUTE) { backStackEntry ->
            AssignServiceScreen(
                onBack = { navController.popBackStack() },
                onAssigned = {
                    val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
                    navController.navigate("practitioner/patients/$patientId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
        // Reuses AN-05's/AN-06's own detail screens verbatim — their ViewModels read
        // reportId/patientId/programmeId from SavedStateHandle by key name, which
        // this route's placeholders satisfy regardless of which graph declares it.
        composable(PATIENT_REPORT_DETAIL_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            ReportDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("practitioner/patients/$patientId/reports/$reportId/edit") },
            )
        }
        composable(EDIT_REPORT_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            EditReportScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate("practitioner/patients/$patientId/reports/$reportId") {
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
                    navController.navigate("practitioner/patients/$patientId/reports/$reportId") {
                        popUpTo(PATIENT_DETAIL_ROUTE) { inclusive = false }
                    }
                },
            )
        }
        composable(PATIENT_PROGRAMME_DETAIL_ROUTE) {
            ProgrammeDetailScreen()
        }
        // AN-10: capture -> upload -> review -> confirm. patientId travels through both
        // routes (CaptureScreen's ViewModel only reads it, ReviewScreen's route also
        // carries it so onSaved can land back on that exact patient's detail page).
        composable(CAPTURE_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onUploaded = { reportId ->
                    navController.navigate("practitioner/upload/review/$patientId/$reportId")
                },
            )
        }
        composable(REVIEW_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate("practitioner/patients/$patientId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
        composable(DOCUMENT_DETAIL_ROUTE) {
            DocumentDetailScreen()
        }
        composable(CREATE_ESTIMATE_ROUTE) {
            CreateEstimateScreen(
                onBack = { navController.popBackStack() },
                onSaved = { documentId ->
                    navController.navigate("practitioner/documents/$documentId") {
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
                    val backStackEntry = navController.currentBackStackEntry
                    val leadId = backStackEntry?.arguments?.getString("leadId").orEmpty()
                    navController.navigate("practitioner/leads/$leadId/convert")
                },
            )
        }
        composable(CONVERT_LEAD_ROUTE) {
            ConvertToPatientScreen(
                onBack = { navController.popBackStack() },
                onConverted = { patientId ->
                    navController.navigate("practitioner/patients/$patientId") {
                        popUpTo(ROOT) { inclusive = false }
                    }
                },
            )
        }
    }
}
