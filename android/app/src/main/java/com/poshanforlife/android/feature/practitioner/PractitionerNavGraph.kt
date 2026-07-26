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
import com.poshanforlife.android.feature.patient.programmes.ProgrammeDetailScreen
import com.poshanforlife.android.feature.patient.reports.ReportDetailScreen
import com.poshanforlife.android.feature.practitioner.patients.PatientDetailScreen
import com.poshanforlife.android.feature.practitioner.patients.PatientListScreen
import com.poshanforlife.android.feature.practitioner.schedule.ScheduleScreen
import com.poshanforlife.android.feature.practitioner.upload.CaptureScreen
import com.poshanforlife.android.feature.practitioner.upload.ReviewScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "practitioner/root"
private const val SCHEDULE_ROUTE = "practitioner/schedule"
private const val PATIENTS_ROUTE = "practitioner/patients"
private const val UPLOAD_ROUTE = "practitioner/upload"
private const val PATIENT_DETAIL_ROUTE = "practitioner/patients/{patientId}"
private const val PATIENT_REPORT_DETAIL_ROUTE = "practitioner/patients/{patientId}/reports/{reportId}"
private const val PATIENT_PROGRAMME_DETAIL_ROUTE = "practitioner/patients/{patientId}/programmes/{programmeId}"
private const val CAPTURE_ROUTE = "practitioner/upload/capture/{patientId}"
private const val REVIEW_ROUTE = "practitioner/upload/review/{patientId}/{reportId}"

// Role value on the wire stays DOCTOR (see Role.kt) — only user-facing text says "Practitioner".
private val items = listOf(
    BottomNavItem(PATIENTS_ROUTE, "Patients", Icons.Filled.People),
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
                    PATIENTS_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/patients/$patientId") },
                    )
                    // Reuses AN-09's patient picker verbatim — tapping a patient here starts a
                    // report capture for them instead of opening their detail page.
                    UPLOAD_ROUTE -> PatientListScreen(
                        onOpenPatient = { patientId -> navController.navigate("practitioner/upload/capture/$patientId") },
                    )
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        // Opens as a full screen above the bottom-nav shell, same convention as the patient graph's sibling detail routes.
        composable(PATIENT_DETAIL_ROUTE) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId").orEmpty()
            PatientDetailScreen(
                onOpenReport = { reportId -> navController.navigate("practitioner/patients/$patientId/reports/$reportId") },
                onOpenProgramme = { programmeId ->
                    navController.navigate("practitioner/patients/$patientId/programmes/$programmeId")
                },
                onBack = { navController.popBackStack() },
            )
        }
        // Reuses AN-05's/AN-06's own detail screens verbatim — their ViewModels read
        // reportId/patientId/programmeId from SavedStateHandle by key name, which
        // this route's placeholders satisfy regardless of which graph declares it.
        composable(PATIENT_REPORT_DETAIL_ROUTE) {
            ReportDetailScreen()
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
    }
}
