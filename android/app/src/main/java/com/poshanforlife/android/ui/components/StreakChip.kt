package com.poshanforlife.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poshanforlife.android.ui.theme.streakChipBackground
import com.poshanforlife.android.ui.theme.streakChipText

/**
 * Lead-only "gamified" component (AN-22) — a pill showing the caller's
 * current streak, meant for the top-right of a greeting header. Uses
 * [MaterialTheme.colorScheme]'s streakChip* extensions, which are only
 * meaningful under `PoshanLeadTheme` (Patient/Staff never reference them).
 */
@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.streakChipBackground, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.streakChipText,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = " $days day streak",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.streakChipText,
        )
    }
}
