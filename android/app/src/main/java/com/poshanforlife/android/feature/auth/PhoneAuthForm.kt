package com.poshanforlife.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.poshanforlife.android.core.network.OtpPurpose
import com.poshanforlife.android.ui.components.OtpInput

/**
 * The two-step phone/OTP form, shared verbatim by the login screen's Phone tab
 * and Settings' "link phone number" sheet — the only difference between them is
 * the purpose the hosting screen starts the ViewModel with.
 */
@Composable
fun PhoneAuthForm(
    viewModel: PhoneAuthViewModel,
    modifier: Modifier = Modifier,
    /** Login shows the "no account — sign up?" affordance; ADD_PHONE has nothing to sign up for. */
    allowSignupSwitch: Boolean = true,
) {
    val state = viewModel.state

    Column(modifier = modifier.fillMaxWidth()) {
        when (state.step) {
            PhoneAuthStep.PHONE_ENTRY -> PhoneEntry(state, viewModel, allowSignupSwitch)
            PhoneAuthStep.OTP_ENTRY -> OtpEntry(state, viewModel)
        }
    }
}

@Composable
private fun PhoneEntry(
    state: PhoneAuthState,
    viewModel: PhoneAuthViewModel,
    allowSignupSwitch: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Not a fixed +91: an editable code keeps non-Indian numbers usable
        // without pulling in a full country-picker dependency for this pass.
        OutlinedTextField(
            value = state.countryCode,
            onValueChange = viewModel::onCountryCodeChange,
            label = { Text("Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.width(96.dp),
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text("Phone number") },
            singleLine = true,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f),
        )
    }

    if (state.error != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (allowSignupSwitch && state.offerSignup) {
        TextButton(onClick = viewModel::switchToSignup, modifier = Modifier.fillMaxWidth()) {
            Text("No account found — sign up instead?")
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { viewModel.sendOtp() },
        enabled = !state.isLoading && state.phone.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (state.isLoading) LoadingDot() else Text("Send OTP")
    }
}

@Composable
private fun OtpEntry(state: PhoneAuthState, viewModel: PhoneAuthViewModel) {
    Text(
        text = "Enter the 6-digit code sent to ${state.e164}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    // Collected on the same step as the code rather than as a third screen —
    // one submit, one round trip. Required: the backend rejects a SIGNUP
    // verify with no name, and a new account needs something to be called.
    if (state.requiresName) {
        // Only turns red once the code is complete — i.e. once the user has
        // actually tried to submit. Marking it as an error the moment the step
        // opens would scold them before they've had a chance to type.
        val showNameError = !state.isNameValid && state.otp.length == PhoneAuthState.OTP_LENGTH
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Your name *") },
            singleLine = true,
            isError = showNameError,
            // Material 3 already colours supporting text by error state.
            supportingText = {
                Text(
                    when {
                        !showNameError -> "Required"
                        state.name.isBlank() -> "Enter your name to create your account"
                        else -> "Name must be at least ${PhoneAuthState.MIN_NAME_LENGTH} characters"
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }

    OtpInput(
        value = state.otp,
        onValueChange = viewModel::onOtpChange,
        isError = state.error != null,
        enabled = !state.isLoading,
        errorMessage = state.error,
        // Auto-submit only when nothing else is outstanding, so a signup can't
        // fire before a valid name has been typed. When it is outstanding the
        // name field above turns red instead, rather than nothing happening.
        onCompleted = { if (state.isNameValid) viewModel.verify() },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = viewModel::backToPhoneEntry, enabled = !state.isLoading) {
            Text("Change number")
        }
        TextButton(
            onClick = viewModel::resendOtp,
            enabled = state.resendSecondsLeft == 0 && !state.isLoading,
        ) {
            Text(if (state.resendSecondsLeft > 0) "Resend in ${state.resendSecondsLeft}s" else "Resend OTP")
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = viewModel::verify,
        enabled = state.canSubmitOtp,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (state.isLoading) LoadingDot() else Text(if (state.requiresName) "Create account" else "Verify")
    }
}

@Composable
private fun LoadingDot() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.dp,
    )
}
