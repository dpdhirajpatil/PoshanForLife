package com.poshanforlife.android.feature.patient.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.ui.components.BadgeCelebrationOverlay
import kotlinx.coroutines.delay

@Composable
fun BadgesScreen(
    modifier: Modifier = Modifier,
    viewModel: BadgesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val celebratingBadge by viewModel.celebratingBadge.collectAsStateWithLifecycle()
    var selectedBadge by remember { mutableStateOf<PatientBadgeStatusDto?>(null) }

    LaunchedEffect(celebratingBadge) {
        if (celebratingBadge != null) {
            delay(1_800)
            viewModel.consumeCelebration()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            BadgesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is BadgesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load badges: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            is BadgesUiState.Success -> {
                // An admin who hasn't defined any badges yet leaves this list genuinely
                // empty, which would otherwise render as a blank screen with no explanation.
                if (state.badges.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No badges to earn yet — check back soon.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.badges, key = { it.id }) { badge ->
                            BadgeTile(badge = badge, onClick = { selectedBadge = badge })
                        }
                    }
                }
            }
        }

        BadgeCelebrationOverlay(trigger = celebratingBadge != null, modifier = Modifier.fillMaxSize())
    }

    selectedBadge?.let { badge ->
        BadgeDetailDialog(badge = badge, onDismiss = { selectedBadge = null })
    }
}

@Composable
private fun BadgeTile(badge: PatientBadgeStatusDto, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .background(
                    color = if (badge.earned) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = iconFor(badge),
                contentDescription = badge.name,
                tint = if (badge.earned) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(40.dp),
            )
            if (!badge.earned) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp),
                )
            }
        }
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (badge.earned) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun BadgeDetailDialog(badge: PatientBadgeStatusDto, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        icon = {
            Icon(
                imageVector = iconFor(badge),
                contentDescription = null,
                tint = if (badge.earned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        title = { Text(badge.name) },
        text = {
            Column {
                Text(
                    text = badge.description ?: "No description yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (badge.earned) "Earned" else "Not yet earned",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

/**
 * iconKey is a free-form string typed by an admin in the web console (e.g.
 * "trophy_gold", "streak_7") — there's no fixed enum on the backend, so this
 * is a best-effort keyword match with criteriaType as a secondary fallback.
 */
private fun iconFor(badge: PatientBadgeStatusDto): ImageVector {
    val key = badge.iconKey.lowercase()
    return when {
        "streak" in key || "fire" in key -> Icons.Filled.LocalFireDepartment
        "star" in key -> Icons.Filled.Star
        "medal" in key -> Icons.Filled.MilitaryTech
        "trophy" in key || "cup" in key -> Icons.Filled.EmojiEvents
        "book" in key || "programme" in key || "program" in key -> Icons.AutoMirrored.Filled.MenuBook
        "premium" in key || "crown" in key -> Icons.Filled.WorkspacePremium
        else -> when (badge.criteriaType) {
            "streak_days" -> Icons.Filled.LocalFireDepartment
            "programme_count" -> Icons.AutoMirrored.Filled.MenuBook
            "challenge_completed" -> Icons.Filled.MilitaryTech
            else -> Icons.Filled.EmojiEvents
        }
    }
}
