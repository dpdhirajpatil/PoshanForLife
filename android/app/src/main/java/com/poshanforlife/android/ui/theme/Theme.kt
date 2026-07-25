package com.poshanforlife.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PoshanGreen80,
    secondary = PoshanGreenGrey80,
    tertiary = PoshanAmber80,
    error = PoshanError80,
)

private val LightColorScheme = lightColorScheme(
    primary = PoshanGreen40,
    secondary = PoshanGreenGrey40,
    tertiary = PoshanAmber40,
    error = PoshanError40,
)

/**
 * Dynamic color (Material You) is preferred on API 31+; below that we fall
 * back to the fixed Poshan palette above rather than AOSP defaults.
 */
@Composable
fun PoshanForLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
