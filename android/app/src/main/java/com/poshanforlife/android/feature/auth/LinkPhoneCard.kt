package com.poshanforlife.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * "Link phone number" for an account that signed up by email, so it can also
 * sign in by OTP later. Shared by the patient and lead profile screens.
 *
 * <p>Collapsed to a single row until tapped — this is a secondary action on a
 * screen that already has a primary one, and the two-step OTP form would
 * otherwise dominate it.
 *
 * @param verifiedPhone the already-linked number, if any; when set the card
 *   just confirms it and offers nothing to do
 */
@Composable
fun LinkPhoneCard(
    verifiedPhone: String?,
    modifier: Modifier = Modifier,
    onLinked: () -> Unit = {},
    viewModel: PhoneAuthViewModel = hiltViewModel(),
) {
    var expanded by remember { mutableStateOf(false) }
    val state = viewModel.state

    // ADD_PHONE returns no tokens, so nothing in the auth state changes to
    // signal success — the ViewModel's own `linked` flag is the only signal.
    LaunchedEffect(state.linked) {
        if (state.linked) {
            expanded = false
            viewModel.reset()
            onLinked()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (verifiedPhone != null) Icons.Filled.CheckCircle else Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.fillMaxWidth(0.04f))
                Column {
                    Text("Phone number", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = verifiedPhone ?: "Not linked yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (verifiedPhone != null) {
                Text(
                    text = "You can sign in with this number using a one-time code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = "Link a number to sign in with a one-time code instead of your password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (!expanded) {
                OutlinedButton(
                    onClick = {
                        viewModel.startAddPhone()
                        expanded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Link phone number") }
            } else {
                PhoneAuthForm(
                    viewModel = viewModel,
                    // Nothing to sign up for — this account already exists.
                    allowSignupSwitch = false,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.reset()
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel") }
            }
        }
    }
}
