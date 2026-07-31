package com.poshanforlife.android.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.SignupRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Root-level auth state — one instance shared by AppNavGraph (to pick a graph) and LoginScreen (to trigger login). */
sealed class AuthUiState {
    data object Loading : AuthUiState()
    data object LoggedOut : AuthUiState()
    data class LoggedIn(val role: Role, val userId: String) : AuthUiState()
}

data class LoginFormState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SignupFormState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Derived straight from AuthRepository.currentUser(), which is backed by
     * DataStore — a failed token refresh (TokenAuthenticator) clears that
     * store, which flows through here into LoggedOut automatically. Nothing
     * else needs to poll or explicitly detect an expired session.
     */
    val uiState: StateFlow<AuthUiState> = authRepository.currentUser()
        .map { user -> if (user == null) AuthUiState.LoggedOut else AuthUiState.LoggedIn(Role.fromWire(user.role), user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    var loginForm by mutableStateOf(LoginFormState())
        private set

    var signupForm by mutableStateOf(SignupFormState())
        private set

    fun login(email: String, password: String) {
        loginForm = loginForm.copy(isLoading = true, error = null)
        viewModelScope.launch {
            loginForm = when (val result = authRepository.login(email, password)) {
                is Result.Success -> loginForm.copy(isLoading = false)
                is Result.Error -> loginForm.copy(isLoading = false, error = mapError(result))
                Result.Loading -> loginForm
            }
        }
    }

    fun signup(request: SignupRequest) {
        signupForm = signupForm.copy(isLoading = true, error = null)
        viewModelScope.launch {
            signupForm = when (val result = authRepository.signup(request)) {
                is Result.Success -> signupForm.copy(isLoading = false)
                is Result.Error -> signupForm.copy(isLoading = false, error = result.message)
                Result.Loading -> signupForm
            }
        }
    }

    fun consumeError() {
        loginForm = loginForm.copy(error = null)
    }

    fun consumeSignupError() {
        signupForm = signupForm.copy(error = null)
    }

    /** Detects a staff-side LEAD->PATIENT conversion — see AuthRepository.refreshCurrentUser's kdoc. Fire-and-forget. */
    fun refreshUser() {
        viewModelScope.launch { authRepository.refreshCurrentUser() }
    }

    // Never reveals which field was wrong — any 401 from /auth/login means bad credentials.
    // The backend's login 401 body always carries its own "code":"AUTH_REQUIRED" (decoded by
    // safeApiCall into Result.Error.code), so "HTTP_401" alone never matched a real login
    // failure — every bad-password attempt fell through to the generic fallback message instead.
    private fun mapError(error: Result.Error): String =
        if (error.code == "HTTP_401" || error.code == "AUTH_REQUIRED") "Invalid email or password" else "Something went wrong. Please try again."
}
