package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/**
 * A one-shot confetti burst overlay, shown while [trigger] is true. Callers
 * own the trigger's lifetime (flip it back to false once the burst has had
 * time to play, e.g. after ~1.5s) — this composable doesn't self-dismiss.
 */
@Composable
fun BadgeCelebrationOverlay(trigger: Boolean, modifier: Modifier = Modifier) {
    if (!trigger) return

    val parties = remember {
        listOf(
            Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                spread = 360,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x29cdff, 0x78ff44).map { it.toInt() },
                emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(120),
                position = Position.Relative(0.5, 0.3),
            ),
        )
    }

    KonfettiView(modifier = modifier.fillMaxSize(), parties = parties)
}
