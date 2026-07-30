package com.poshanforlife.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.poshanforlife.android.core.network.InBodyDataDto
import com.poshanforlife.android.feature.practitioner.upload.FieldGroupSpec
import com.poshanforlife.android.feature.practitioner.upload.FieldSpec
import com.poshanforlife.android.feature.practitioner.upload.REPORT_FIELD_GROUPS
import com.poshanforlife.android.feature.practitioner.upload.valueFor
import java.util.Locale

/**
 * Shared by AN-10's ReviewScreen (upload confirm) and AN-19's EditReportScreen
 * (post-hoc correction) — the same tap-to-edit grouped InBody field list, added
 * as LazyColumn items so callers can interleave their own items around it.
 */
fun LazyListScope.editableParsedDataList(
    groups: List<FieldGroupSpec> = REPORT_FIELD_GROUPS,
    data: InBodyDataDto,
    onFieldChange: (String, Double?) -> Unit,
) {
    groups.forEach { group ->
        item(key = group.title) {
            FieldGroupCard(group = group, data = data, onFieldChange = onFieldChange)
        }
    }
}

@Composable
private fun FieldGroupCard(group: FieldGroupSpec, data: InBodyDataDto, onFieldChange: (String, Double?) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = group.title, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                group.fields.forEach { spec ->
                    EditableFieldRow(
                        spec = spec,
                        value = data.valueFor(spec.key),
                        onValueChange = { onFieldChange(spec.key, it) },
                    )
                }
            }
        }
    }
}

private fun formatValue(value: Double?, spec: FieldSpec): String {
    if (value == null) return ""
    return if (spec.isInt) value.toInt().toString() else "%.1f".format(Locale.US, value)
}

@Composable
private fun EditableFieldRow(spec: FieldSpec, value: Double?, onValueChange: (Double?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value, editing) { mutableStateOf(formatValue(value, spec)) }
    val focusRequester = remember { FocusRequester() }
    // onFocusChanged fires once on initial placement with isFocused=false, before
    // requestFocus() below ever runs — without this guard that spurious first
    // callback looks identical to "focus lost" and instantly reverts the field
    // to read-only before the user can type anything. Keyed on `editing` so it
    // resets at the start of every edit session, not just the first one ever.
    var hasBeenFocused by remember(editing) { mutableStateOf(false) }
    val label = if (spec.unit.isNotEmpty()) "${spec.label} (${spec.unit})" else spec.label

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(120.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused) {
                            onValueChange(text.toDoubleOrNull())
                            editing = false
                        }
                    },
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = value?.let { formatValue(it, spec) } ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { editing = true },
            )
        }
    }
}
