package com.poshanforlife.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private enum class LoginMethod(val label: String) { EMAIL("Email"), PHONE("Phone") }

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onSignUp: () -> Unit = {},
    phoneAuthViewModel: PhoneAuthViewModel = hiltViewModel(),
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(LoginMethod.EMAIL) }
    val formState = authViewModel.loginForm
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(formState.error) {
        val error = formState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            authViewModel.consumeError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Poshan for Life", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Swaps the form in place rather than navigating — role still isn't
            // known pre-auth, so both methods stay inside this one
            // StaffTheme-wrapped screen (same reasoning as AN-02's original choice).
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LoginMethod.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = method == entry,
                        onClick = { method = entry },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LoginMethod.entries.size),
                    ) { Text(entry.label) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            when (method) {
                LoginMethod.EMAIL -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { authViewModel.login(email.trim(), password) },
                        enabled = !formState.isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (formState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Sign in")
                        }
                    }

                    TextButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth()) {
                        Text("Don't have an account? Sign up")
                    }
                }

                // No separate sign-up link needed: an unknown number offers to
                // sign up inline, so one flow covers both.
                LoginMethod.PHONE -> PhoneAuthForm(viewModel = phoneAuthViewModel)
            }
        }
    }
}
