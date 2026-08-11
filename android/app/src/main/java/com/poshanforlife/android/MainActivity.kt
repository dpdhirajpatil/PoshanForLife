package com.poshanforlife.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.datastore.ThemeMode
import com.poshanforlife.android.core.datastore.ThemePreferenceDataStore
import com.poshanforlife.android.core.fcm.DeepLinkEvents
import com.poshanforlife.android.feature.AppNavGraph
import com.poshanforlife.android.ui.theme.LocalDarkTheme
import com.poshanforlife.android.ui.theme.PoshanStaffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkEvents: DeepLinkEvents

    @Inject
    lateinit var themePreference: ThemePreferenceDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Resolve the user's Appearance choice against the device setting once, here at the
            // root, and publish it — every theme call site (including the per-destination
            // Patient/Lead wraps deep inside the nav graphs) reads it from LocalDarkTheme.
            //
            // Seeded with the system value rather than a hardcoded false: DataStore's first
            // emission is asynchronous, and defaulting to light for that frame makes a dark-mode
            // device flash white on every cold start.
            val systemDark = isSystemInDarkTheme()
            val themeMode by themePreference.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // enableEdgeToEdge() was called before the preference was known, so the status/nav
            // bar icon polarity has to be re-applied whenever the resolved theme changes —
            // otherwise picking Dark on a light-mode device leaves dark icons on a dark bar.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { darkTheme },
                )
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !darkTheme
            }

            CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
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
