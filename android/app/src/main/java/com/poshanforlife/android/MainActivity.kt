package com.poshanforlife.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.poshanforlife.android.core.fcm.DeepLinkEvents
import com.poshanforlife.android.feature.AppNavGraph
import com.poshanforlife.android.ui.theme.PoshanStaffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkEvents: DeepLinkEvents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Role isn't known until login succeeds, and Practitioner/Admin both use Staff too —
            // this is the correct default for Loading/Auth/Practitioner/Admin. Patient/Lead
            // override it explicitly per-destination inside their own nav graphs (see
            // patientGraph/leadGraph) since Navigation Compose swaps out content per route
            // rather than nesting under this wrapper.
            PoshanStaffTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
        emitDeepLinkFrom(intent)
    }

    /** launchMode="singleTask" (see manifest) routes a tapped-notification Intent here instead of a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        emitDeepLinkFrom(intent)
    }

    private fun emitDeepLinkFrom(intent: Intent?) {
        val relatedEntityType = intent?.getStringExtra(EXTRA_RELATED_ENTITY_TYPE)
        val relatedEntityId = intent?.getStringExtra(EXTRA_RELATED_ENTITY_ID)
        if (relatedEntityType != null || relatedEntityId != null) {
            deepLinkEvents.emit(relatedEntityType, relatedEntityId)
        }
    }

    companion object {
        const val EXTRA_RELATED_ENTITY_TYPE = "relatedEntityType"
        const val EXTRA_RELATED_ENTITY_ID = "relatedEntityId"
    }
}
