package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.poshanforlife.android.core.datastore.ThemeMode

/**
 * Light/dark/system picker, shared by every role's settings surface (Patient and
 * Lead embed it in Profile; Practitioner and Admin reach it as its own screen).
 *
 * A radio group rather than a two-state switch on purpose: "Dark mode on/off"
 * cannot express "follow the device", which is the default and the option most
 * users actually want. Three explicit choices also make the current state
 * readable at a glance, where a switch leaves "off" ambiguous between "light"
 * and "following a light device".
 */
@Composable
fun AppearanceCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = "Choose how Poshan for Life looks on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // selectableGroup() so TalkBack announces this as one "N of 3" set
            // rather than three unrelated toggles.
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(mode.label) },
                        supportingContent = { Text(mode.description) },
                        leadingContent = {
                            // null onClick: the whole row carries the selectable
                            // semantics, so the button must not be separately
                            // focusable or it reads twice.
                            RadioButton(selected = mode == selected, onClick = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                onClick = { onSelect(mode) },
                                role = Role.RadioButton,
                            ),
                    )
                }
            }
        }
    }
}
