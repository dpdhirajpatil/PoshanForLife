package com.poshanforlife.android.feature.lead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.feature.auth.LinkPhoneCard

@Composable
fun LeadProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: LeadProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val verifiedPhone by viewModel.verifiedPhone.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ALL CAPS per Type.kt's headline contract (Lead theme).
        Text(text = user?.name.orEmpty().uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        // A phone-signup lead has no email at all — show the verified number
        // instead of an empty line.
        val subtitle = user?.email ?: verifiedPhone
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinkPhoneCard(
            verifiedPhone = verifiedPhone,
            onLinked = viewModel::refreshVerifiedPhone,
        )

        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Log out")
        }
    }
}
