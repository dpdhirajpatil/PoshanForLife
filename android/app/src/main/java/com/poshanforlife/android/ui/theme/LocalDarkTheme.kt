package com.poshanforlife.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's resolved light/dark state — the user's [com.poshanforlife.android.core.datastore.ThemeMode]
 * already reconciled against the device setting. Provided once in MainActivity;
 * every theme composable reads it as its `darkTheme` default.
 *
 * A CompositionLocal rather than a parameter threaded down because Navigation
 * Compose swaps content per destination instead of nesting it under a parent
 * composable, so `PoshanPatientTheme`/`PoshanLeadTheme` are re-applied deep
 * inside the nav graphs (see `ThemedComposable.kt`). Passing a flag to each of
 * those call sites would mean every future screen has to remember to forward
 * it, and the one that forgets silently ignores the user's choice.
 *
 * `null` means "nobody provided it" — previews and tests that render a theme
 * directly then fall back to the system setting, which is the old behaviour.
 * `static` because this changes only when the user picks a different mode, and
 * when it does the entire tree genuinely must re-theme.
 */
val LocalDarkTheme = staticCompositionLocalOf<Boolean?> { null }
