package com.poshanforlife.android.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.network.OtpPurpose
import com.poshanforlife.android.core.network.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which half of the two-step flow the phone form is showing. */
enum class PhoneAuthStep { PHONE_ENTRY, OTP_ENTRY }

data class PhoneAuthState(
    val step: PhoneAuthStep = PhoneAuthStep.PHONE_ENTRY,
    val countryCode: String = DEFAULT_COUNTRY_CODE,
    val phone: String = "",
    val otp: String = "",
    val name: String = "",
    val purpose: OtpPurpose = OtpPurpose.LOGIN,
    val isLoading: Boolean = false,
    /** Shown against the phone field on step 1, or under the OTP boxes on step 2. */
    val error: String? = null,
    /** Set when a LOGIN attempt finds no account, so the UI can offer to sign up instead. */
    val offerSignup: Boolean = false,
    val resendSecondsLeft: Int = 0,
    /** ADD_PHONE only — no tokens come back, so the screen needs its own success signal. */
    val linked: Boolean = false,
) {
    val requiresName: Boolean get() = purpose == OtpPurpose.SIGNUP

    /**
     * Mirrors the backend's own `@Size(min = 2)` on the signup name. Without
     * this the server rejects a one-character name with a bare "Validation
     * failed", which tells the user nothing about what to fix.
     */
    val isNameValid: Boolean get() = !requiresName || name.trim().length >= MIN_NAME_LENGTH

    val canSubmitOtp: Boolean
        get() = otp.length == OTP_LENGTH && !isLoading && isNameValid

    /** E.164, which is what the backend stores and compares — it also normalises, but sending it canonical keeps the two in step. */
    val e164: String get() = countryCode + phone.filter(Char::isDigit)

    companion object {
        const val DEFAULT_COUNTRY_CODE = "+91"
        const val OTP_LENGTH = 6
        const val MIN_NAME_LENGTH = 2
    }
}

/**
 * Drives the phone+OTP flow for all three purposes. One ViewModel backs both
 * the login screen's phone tab and Settings' "link phone number", since the
 * two-step shape is identical and only [PhoneAuthState.purpose] differs.
 *
 * <p>The resend countdown here is UX only. The real limit is the backend's
 * per-phone send cap, which is enforced server-side and reported as
 * RATE_LIMIT_EXCEEDED — a client timer can always be bypassed by reinstalling.
 */
@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var state by mutableStateOf(PhoneAuthState())
        private set

    private var countdownJob: Job? = null

    fun onCountryCodeChange(code: String) {
        // Keep a single leading '+' and digits only, so "+91"/"91" both work.
        val digits = code.filter(Char::isDigit).take(4)
        state = state.copy(countryCode = "+$digits", error = null)
    }

    fun onPhoneChange(value: String) {
        state = state.copy(phone = value.filter(Char::isDigit).take(15), error = null, offerSignup = false)
    }

    fun onOtpChange(value: String) {
        state = state.copy(otp = value, error = null)
    }

    fun onNameChange(value: String) {
        state = state.copy(name = value, error = null)
    }

    /** Entry point for Settings — the caller is signed in and attaching a number. */
    fun startAddPhone() {
        state = PhoneAuthState(purpose = OtpPurpose.ADD_PHONE)
    }

    fun sendOtp(purpose: OtpPurpose = state.purpose) {
        if (state.phone.isBlank()) {
            state = state.copy(error = "Enter your phone number")
            return
        }
        state = state.copy(isLoading = true, error = null, offerSignup = false, purpose = purpose)
        viewModelScope.launch {
            when (val result = authRepository.requestOtp(state.e164, purpose)) {
                is Result.Success -> {
                    state = state.copy(isLoading = false, step = PhoneAuthStep.OTP_ENTRY, otp = "")
                    startResendCountdown()
                }
                is Result.Error -> state = state.copy(
                    isLoading = false,
                    error = requestErrorMessage(result, purpose),
                    // An unknown number on LOGIN is the cue to offer signup instead.
                    offerSignup = purpose == OtpPurpose.LOGIN && isNoAccount(result),
                )
                Result.Loading -> Unit
            }
        }
    }

    /** Re-sends for the current purpose, keeping the user on the OTP step. */
    fun resendOtp() {
        if (state.resendSecondsLeft > 0 || state.isLoading) return
        state = state.copy(isLoading = true, error = null, otp = "")
        viewModelScope.launch {
            when (val result = authRepository.requestOtp(state.e164, state.purpose)) {
                is Result.Success -> {
                    state = state.copy(isLoading = false)
                    startResendCountdown()
                }
                is Result.Error -> state = state.copy(isLoading = false, error = requestErrorMessage(result, state.purpose))
                Result.Loading -> Unit
            }
        }
    }

    /** Switches a failed LOGIN into a SIGNUP without making the user retype the number. */
    fun switchToSignup() {
        state = state.copy(purpose = OtpPurpose.SIGNUP, offerSignup = false, error = null)
        sendOtp(OtpPurpose.SIGNUP)
    }

    fun verify() {
        if (!state.canSubmitOtp) return
        state = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = authRepository.verifyOtp(
                phone = state.e164,
                otp = state.otp,
                purpose = state.purpose,
                name = state.name.trim().takeIf { state.requiresName && it.isNotBlank() },
            )
            state = when (result) {
                // SIGNUP/LOGIN need no further action here: saving the tokens
                // updates TokenDataStore, which AuthViewModel observes, so the
                // root graph swaps to the right role graph on its own.
                is Result.Success -> state.copy(
                    isLoading = false,
                    linked = state.purpose == OtpPurpose.ADD_PHONE,
                )
                is Result.Error -> state.copy(isLoading = false, otp = "", error = verifyErrorMessage(result))
                Result.Loading -> state
            }
        }
    }

    /** Back arrow on the OTP step — returns to phone entry without losing the number. */
    fun backToPhoneEntry() {
        countdownJob?.cancel()
        state = state.copy(step = PhoneAuthStep.PHONE_ENTRY, otp = "", error = null, resendSecondsLeft = 0)
    }

    fun reset() {
        countdownJob?.cancel()
        state = PhoneAuthState()
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            state = state.copy(resendSecondsLeft = RESEND_SECONDS)
            while (state.resendSecondsLeft > 0) {
                delay(1_000)
                state = state.copy(resendSecondsLeft = state.resendSecondsLeft - 1)
            }
        }
    }

    /**
     * The backend answers an unknown number on LOGIN with AUTH_REQUIRED (401),
     * not the 404 the prompt assumes — see PhoneOtpService, which deliberately
     * reuses the same opaque code as a failed password login.
     */
    private fun isNoAccount(error: Result.Error): Boolean =
        error.code == "AUTH_REQUIRED" || error.code == "HTTP_401" || error.code == "HTTP_404"

    private fun requestErrorMessage(error: Result.Error, purpose: OtpPurpose): String = when {
        error.code == "RATE_LIMIT_EXCEEDED" || error.code == "HTTP_429" ->
            "Too many attempts, try again in a few minutes"
        error.code == "PHONE_CONFLICT" && purpose == OtpPurpose.SIGNUP ->
            "An account already exists for this number — sign in instead"
        error.code == "PHONE_CONFLICT" -> "That phone number is already in use"
        purpose == OtpPurpose.LOGIN && isNoAccount(error) -> "No account found for this number"
        error.code == "VALIDATION_ERROR" -> error.message
        error.code == "NETWORK_ERROR" -> "Can't reach the server. Check your connection."
        else -> "Couldn't send the code. Please try again."
    }

    private fun verifyErrorMessage(error: Result.Error): String = when {
        error.code == "RATE_LIMIT_EXCEEDED" || error.code == "HTTP_429" ->
            "Too many attempts, try again in a few minutes"
        error.code == "PHONE_CONFLICT" -> "That phone number is already in use"
        // Wrong/expired/spent codes all arrive as VALIDATION_ERROR with a
        // message written for the user ("That code isn't correct", "That code
        // has expired. Request a new one.") — pass it through rather than
        // flattening three distinct recovery paths into one generic string.
        error.code == "VALIDATION_ERROR" -> error.message
        error.code == "NETWORK_ERROR" -> "Can't reach the server. Check your connection."
        else -> "Couldn't verify the code. Please try again."
    }

    private companion object {
        const val RESEND_SECONDS = 30
    }
}

private const val OTP_LENGTH = PhoneAuthState.OTP_LENGTH
