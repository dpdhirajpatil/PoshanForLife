package com.poshanforlife.android.feature

/** Root-level destinations — the Loading screen and each role's nested graph. */
object RootRoutes {
    const val LOADING = "loading"
    const val AUTH_GRAPH = "auth_graph"
    const val PATIENT_GRAPH = "patient_graph"
    const val PRACTITIONER_GRAPH = "practitioner_graph"
    const val ADMIN_GRAPH = "admin_graph"
    const val LEAD_GRAPH = "lead_graph"

    /** AN-22: the one-time LEAD->PATIENT re-theme moment — a sibling destination, not part of any role graph. */
    const val CONVERSION_WELCOME = "conversion_welcome"
}
