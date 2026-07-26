package com.poshanforlife.android.core.util

import java.time.Instant
import java.time.temporal.ChronoUnit

/** "5m ago" / "3h ago" / "2d ago", falling back to the raw ISO string if it can't be parsed. */
fun relativeTime(isoInstant: String): String {
    val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return isoInstant
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(then, now)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
