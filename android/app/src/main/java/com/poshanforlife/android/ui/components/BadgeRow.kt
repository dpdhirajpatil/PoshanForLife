package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.ui.theme.badgeEarnedBackground

/**
 * Lead-only "gamified" component (AN-22) — a horizontal row of badge chips.
 * Earned badges rotate through the theme's plum/indigo tint extensions per
 * index; locked badges are dimmed with a lock icon overlay.
 */
@Composable
fun BadgeRow(badges: List<PatientBadgeStatusDto>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(badges, key = { _, badge -> badge.id }) { index, badge ->
            BadgeChip(badge = badge, index = index)
        }
    }
}

@Composable
private fun BadgeChip(badge: PatientBadgeStatusDto, index: Int) {
    val background = if (badge.earned) {
        MaterialTheme.colorScheme.badgeEarnedBackground(index)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = background,
                modifier = Modifier
                    .size(56.dp)
                    .alpha(if (badge.earned) 1f else 0.4f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = badge.name)
                }
            }
            if (!badge.earned) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
