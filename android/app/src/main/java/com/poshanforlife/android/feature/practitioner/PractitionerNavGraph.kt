package com.poshanforlife.android.feature.practitioner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.appointments.PreCallScreen
import com.poshanforlife.android.feature.patient.appointments.VideoCallScreen
import com.poshanforlife.android.feature.patient.appointments.VideoCallViewModel
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
import com.poshanforlife.android.feature.settings.AppearanceScreen
import com.poshanforlife.android.ui.components.MoreMenuItem
import com.poshanforlife.android.ui.components.MoreMenuScreen
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
private const val SETTINGS_ROUTE = "practitioner/settings"
private const val APPEARANCE_ROUTE = "practitioner/appearance"

// AN-13 telemedicine scaffold — the practitioner half of the same flow the patient
// graph declares; both reuse the shared PreCallScreen/VideoCallScreen composables.
private const val PRE_CALL_ROUTE = "practitioner/appointments/{appointmentId}/pre-call"
private const val VIDEO_CALL_ROUTE = "practitioner/appointments/{appointmentId}/call"
private const val MORE_ROUTE = "practitioner/more"

// Role value on the wire stays DOCTOR (see Role.kt) — only user-facing text says "Practitioner".
// PoshanStaffTheme's nav shape: 4 primary bottom tabs + a "More" overflow tab (standard Android
// overflow pattern) rather than the flat 8-tab bar this graph used to have — Orders/Products/
// Invoices/Settings moved to full-screen routes reached from MoreMenuScreen instead.
private val items = listOf(
    BottomNavItem(PATIENTS_ROUTE, "Patients", Icons.Filled.People),
    BottomNavItem(LEADS_ROUTE, "Leads", Icons.Filled.ContactPhone),
    BottomNavItem("practitioner/upload", "Upload", Icons.Filled.CloudUpload),
    BottomNavItem(SCHEDULE_ROUTE, "Schedule", Icons.Filled.Schedule),
    BottomNavItem(MORE_ROUTE, "More", Icons.Filled.MoreHoriz),
)

// The prompt's own "More" list only names Orders/Products/Settings — Invoices (AN-16, already
// shipped before this nav restructure) isn't mentioned there, but dropping a built feature
// silently would be worse than a literal reading; kept alongside the other three.
private val moreMenuItems = listOf(
    MoreMenuItem("Orders", Icons.Filled.ShoppingCart, ORDERS_ROUTE),
    MoreMenuItem("Products", Icons.Filled.ShoppingBag, PRODUCTS_ROUTE),
    MoreMenuItem("Invoices", Icons.Filled.Receipt, DOCUMENTS_ROUTE),
    MoreMenuItem("Settings", Icons.Filled.Settings, SETTINGS_ROUTE),
    MoreMenuItem("Appearance", Icons.Filled.DarkMode, APPEARANCE_ROUTE),
)

fun NavGraphBuilder.practitionerGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PRACTITIONER_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    SCHEDULE_ROUTE -> ScheduleScreen(
                        onJoinCall = { appointmentId ->
                            navController.navigate("practitioner/appointments/$appointmentId/pre-call")
                        },
                    )
                    PATIENTS_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/patients/$patientId") },
                    )
                    // Reuses AN-09's patient picker verbatim — tapping a patient here starts a
                    // report capture for them instead of opening their detail page.
                    UPLOAD_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/upload/capture/$patientId") },
                    )
                    LEADS_ROUTE -> LeadListScreen(
                        onOpenLead = { leadId -> navController.navigate("practitioner/leads/$leadId") },
                    )
                    MORE_ROUTE -> MoreMenuScreen(
                        items = moreMenuItems,
                        onSelect = { menuRoute -> navController.navigate(menuRoute) },
                    )
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        composable(PRE_CALL_ROUTE) { backStackEntry ->
            val appointmentId = backStackEntry.arguments?.getString("appointmentId").orEmpty()
            val viewModel: VideoCallViewModel = hiltViewModel()
            val appointment by viewModel.appointment.collectAsStateWithLifecycle()
            PreCallScreen(
                // Mirror image of the patient side: each party sees the other's name.
                otherPartyName = appointment?.patient?.name.orEmpty(),
                onJoin = {
                    navController.navigate("practitioner/appointments/$appointmentId/call") {
                        popUpTo(PRE_CALL_ROUTE) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(VIDEO_CALL_ROUTE) {
            val viewModel: VideoCallViewModel = hiltViewModel()
            val appointment by viewModel.appointment.collectAsStateWithLifecycle()
            VideoCallScreen(
                otherPartyName = appointment?.patient?.name.orEmpty(),
                onLeave = { navController.popBackStack() },
            )
        }
        composable(PRODUCT_DETAIL_ROUTE) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
        // Reached from the "More" tab (MoreMenuScreen) rather than tab-rendered inside
        // RoleScaffold now — same "full-screen sibling route, no visible back button, relies on
        // system back gesture" convention already used by ORDER_DETAIL_ROUTE/PATIENT_PROGRAMME_DETAIL_ROUTE below.
        composable(ORDERS_ROUTE) {
            OrdersAndTransactionsScreen(
                onOpenOrder = { orderId -> navController.navigate("practitioner/orders/$orderId") },
            )
        }
        composable(PRODUCTS_ROUTE) {
            // Read-only for DOCTOR — see AdminNavGraph for the admin-mode instance of this same screen.
            ProductsScreen(
                isAdmin = false,
                onOpenProduct = { productId -> navController.navigate("practitioner/products/$productId") },
            )
        }
        composable(DOCUMENTS_ROUTE) {
            DocumentsListScreen(
                onOpenDocument = { documentId -> navController.navigate("practitioner/documents/$documentId") },
                onCreateEstimate = { navController.navigate(CREATE_ESTIMATE_ROUTE) },
            )
        }
        composable(APPEARANCE_ROUTE) {
            // Light/dark/system. Same full-screen-sibling convention as the other
            // "More" destinations — no back button, system gesture returns.
            AppearanceScreen()
        }
        composable(SETTINGS_ROUTE) {
            // Doesn't have a real screen of its own yet (a future prompt's job) — read-only
            // Service Catalogue browse is the one concrete practitioner-facing surface built so far.
            CatalogueScreen(isAdmin = false)
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
