package com.poshanforlife.android.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.network.Result
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
    data class LoggedIn(val role: Role) : AuthUiState()
}

data class LoginFormState(
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
        .map { user -> if (user == null) AuthUiState.LoggedOut else AuthUiState.LoggedIn(Role.fromWire(user.role)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    var loginForm by mutableStateOf(LoginFormState())
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

    fun consumeError() {
        loginForm = loginForm.copy(error = null)
    }

    // Never reveals which field was wrong — any 401 from /auth/login means bad credentials.
    private fun mapError(error: Result.Error): String =
        if (error.code == "HTTP_401") "Invalid email or password" else "Something went wrong. Please try again."
}
