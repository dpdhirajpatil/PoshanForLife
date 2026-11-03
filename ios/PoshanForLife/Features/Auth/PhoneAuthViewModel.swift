import Foundation

/// Drives phone + OTP sign-in and sign-up. A port of Android's
/// `PhoneAuthViewModel` (minus ADD_PHONE), so both apps behave the same:
/// enter number → code is sent → enter the 6 digits (plus a name when the
/// number is new) → signed in.
///
/// The resend countdown is UX only. The real limit is the backend's per-phone
/// send cap, reported as RATE_LIMIT_EXCEEDED.
@MainActor
final class PhoneAuthViewModel: ObservableObject {

    enum Step { case phoneEntry, otpEntry }

    static let defaultCountryCode = "+91"
    static let otpLength = 6
    static let minNameLength = 2
    private static let resendSeconds = 30

    @Published private(set) var step: Step = .phoneEntry
    @Published var countryCode = defaultCountryCode
    @Published var phone = "" { didSet { if phone != oldValue { error = nil; offerSignup = false } } }
    @Published var otp = "" { didSet { if otp != oldValue { error = nil } } }
    @Published var name = "" { didSet { if name != oldValue { error = nil } } }
    @Published private(set) var purpose: OtpPurpose = .login
    @Published private(set) var isLoading = false
    /// Shown under the phone field on step 1, or under the code on step 2.
    @Published private(set) var error: String?
    /// Set when a LOGIN finds no account, so the form can offer to sign up instead.
    @Published private(set) var offerSignup = false
    @Published private(set) var resendSecondsLeft = 0

    private let authRepository: AuthRepository
    private let onSignedIn: (User) -> Void
    private var countdownTask: Task<Void, Never>?

    init(authRepository: AuthRepository, onSignedIn: @escaping (User) -> Void) {
        self.authRepository = authRepository
        self.onSignedIn = onSignedIn
    }

    deinit { countdownTask?.cancel() }

    var requiresName: Bool { purpose == .signup }

    /// Mirrors the backend's `@Size(min = 2)` on the signup name, so a
    /// one-letter name gets a useful message instead of "Validation failed".
    var isNameValid: Bool {
        !requiresName || name.trimmingCharacters(in: .whitespaces).count >= Self.minNameLength
    }

    var canSubmitOtp: Bool { otp.count == Self.otpLength && !isLoading && isNameValid }

    /// E.164, which is what the backend stores and compares.
    var e164: String {
        let code = "+" + countryCode.filter(\.isNumber).prefix(4)
        return code + phone.filter(\.isNumber).prefix(15)
    }

    func sendOtp(purpose requested: OtpPurpose? = nil) async {
        let purpose = requested ?? self.purpose
        guard !phone.filter(\.isNumber).isEmpty else {
            error = "Enter your phone number"
            return
        }
        self.purpose = purpose
        isLoading = true
        error = nil
        offerSignup = false

        let result = await authRepository.requestOtp(phone: e164, purpose: purpose)
        isLoading = false
        switch result {
        case .success:
            otp = ""
            step = .otpEntry
            startResendCountdown()
        case .failure(let apiError):
            error = Self.requestErrorMessage(apiError, purpose: purpose)
            // An unknown number on LOGIN is the cue to offer signup instead.
            offerSignup = purpose == .login && Self.isNoAccount(apiError)
        }
    }

    func resendOtp() async {
        guard resendSecondsLeft == 0, !isLoading else { return }
        isLoading = true
        error = nil
        otp = ""
        let result = await authRepository.requestOtp(phone: e164, purpose: purpose)
        isLoading = false
        switch result {
        case .success: startResendCountdown()
        case .failure(let apiError): error = Self.requestErrorMessage(apiError, purpose: purpose)
        }
    }

    /// Turns a failed LOGIN into a SIGNUP without retyping the number.
    func switchToSignup() async {
        offerSignup = false
        await sendOtp(purpose: .signup)
    }

    func verify() async {
        guard canSubmitOtp else { return }
        isLoading = true
        error = nil
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        let result = await authRepository.verifyOtp(
            phone: e164,
            otp: otp,
            purpose: purpose,
            name: requiresName && !trimmedName.isEmpty ? trimmedName : nil
        )
        isLoading = false
        switch result {
        case .success(let auth):
            countdownTask?.cancel()
            onSignedIn(auth.user)
        case .failure(let apiError):
            otp = ""
            error = Self.verifyErrorMessage(apiError)
        }
    }

    /// Back from the code step, keeping the number.
    func backToPhoneEntry() {
        countdownTask?.cancel()
        step = .phoneEntry
        otp = ""
        error = nil
        resendSecondsLeft = 0
        purpose = .login
    }

    private func startResendCountdown() {
        countdownTask?.cancel()
        resendSecondsLeft = Self.resendSeconds
        countdownTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self, !Task.isCancelled else { return }
                if self.resendSecondsLeft <= 1 {
                    self.resendSecondsLeft = 0
                    return
                }
                self.resendSecondsLeft -= 1
            }
        }
    }

    /// The backend answers an unknown number on LOGIN with AUTH_REQUIRED (401),
    /// the same opaque code as a failed password login.
    private static func isNoAccount(_ error: APIError) -> Bool {
        ["AUTH_REQUIRED", "HTTP_401", "HTTP_404"].contains(error.code)
    }

    private static func requestErrorMessage(_ error: APIError, purpose: OtpPurpose) -> String {
        switch error.code {
        case "RATE_LIMIT_EXCEEDED", "HTTP_429":
            return "Too many attempts, try again in a few minutes"
        case "PHONE_CONFLICT" where purpose == .signup:
            return "An account already exists for this number. Sign in instead."
        case "PHONE_CONFLICT":
            return "That phone number is already in use"
        case _ where purpose == .login && isNoAccount(error):
            return "No account found for this number"
        case "VALIDATION_ERROR":
            return error.message
        case "NETWORK_ERROR":
            return "Can't reach the server. Check your connection."
        default:
            return "Couldn't send the code. Please try again."
        }
    }

    private static func verifyErrorMessage(_ error: APIError) -> String {
        switch error.code {
        case "RATE_LIMIT_EXCEEDED", "HTTP_429":
            return "Too many attempts, try again in a few minutes"
        case "PHONE_CONFLICT":
            return "That phone number is already in use"
        // Wrong, expired and used-up codes all come back as VALIDATION_ERROR
        // with a message written for the user; pass it through.
        case "VALIDATION_ERROR":
            return error.message
        case "NETWORK_ERROR":
            return "Can't reach the server. Check your connection."
        default:
            return "Couldn't verify the code. Please try again."
        }
    }
}
