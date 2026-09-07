import Combine
import Foundation

/// What `RootView` switches on — both which navigation structure to show and
/// which theme wraps it.
enum AuthState: Equatable {
    case loading
    case loggedOut
    case loggedIn(User)

    var user: User? {
        if case .loggedIn(let user) = self { return user }
        return nil
    }
}

@MainActor
final class AuthViewModel: ObservableObject {

    @Published private(set) var state: AuthState = .loading

    // Login form.
    @Published var email = ""
    @Published var password = ""
    @Published private(set) var isSubmitting = false
    /// Non-nil drives the error alert. Cleared on the next edit or attempt.
    @Published var errorMessage: String?

    // Signup form — deliberately separate fields from the login form above
    // rather than reusing `email`/`password`, so switching between the two
    // screens never carries half-typed values from one into the other.
    @Published var signupName = ""
    @Published var signupEmail = ""
    @Published var signupPassword = ""
    @Published var signupPhone = ""
    @Published var signupCity = ""
    @Published var signupHealthGoal = ""
    @Published private(set) var isSigningUp = false
    @Published var signupErrorMessage: String?

    var canSubmit: Bool {
        !email.trimmingCharacters(in: .whitespaces).isEmpty
            && !password.isEmpty
            && !isSubmitting
    }

    /// Mirrors the backend's own `SignupRequest` password pattern
    /// (`^(?=.*\d).{8,}$`) client-side, so a bad password shows its complaint
    /// immediately rather than after a round trip that says the same thing.
    var canSubmitSignup: Bool {
        !signupName.trimmingCharacters(in: .whitespaces).isEmpty
            && !signupEmail.trimmingCharacters(in: .whitespaces).isEmpty
            && Self.isValidSignupPassword(signupPassword)
            && !isSigningUp
    }

    static func isValidSignupPassword(_ password: String) -> Bool {
        password.count >= 8 && password.contains { $0.isNumber }
    }

    private let authRepository: AuthRepository
    private var cancellables = Set<AnyCancellable>()

    init(authRepository: AuthRepository, sessionExpired: AnyPublisher<Void, Never>) {
        self.authRepository = authRepository

        // The client has already cleared the Keychain by the time this fires;
        // all that's left is to move the UI. RootView observes `state`, so no
        // screen needs its own session check.
        sessionExpired
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.state = .loggedOut }
            .store(in: &cancellables)
    }

    /// Called once at launch. A stored token is not proof of a live session —
    /// it may be expired or revoked — so the user is only considered signed in
    /// after the backend confirms it. An expired access token still works here:
    /// APIClient refreshes underneath and this never sees the 401.
    func restoreSession() async {
        state = .loading
        switch await authRepository.loadCurrentUser() {
        case .success(let user):
            state = .loggedIn(user)
        case .failure:
            // Includes being offline. Treating "can't confirm" as signed out is
            // the safe default; a later prompt can soften it with a cached user.
            authRepository.logout()
            state = .loggedOut
        }
    }

    func login() async {
        guard canSubmit else { return }
        isSubmitting = true
        errorMessage = nil

        let result = await authRepository.login(
            email: email.trimmingCharacters(in: .whitespaces),
            password: password
        )
        isSubmitting = false

        switch result {
        case .success(let auth):
            password = ""
            state = .loggedIn(auth.user)
        case .failure(let error):
            errorMessage = Self.loginErrorMessage(for: error)
        }
    }

    func signup() async {
        guard canSubmitSignup else { return }
        isSigningUp = true
        signupErrorMessage = nil

        let request = SignupRequest(
            name: signupName.trimmingCharacters(in: .whitespaces),
            email: signupEmail.trimmingCharacters(in: .whitespaces),
            password: signupPassword,
            phone: Self.nilIfEmpty(signupPhone),
            city: Self.nilIfEmpty(signupCity),
            healthGoal: Self.nilIfEmpty(signupHealthGoal)
        )

        let result = await authRepository.signup(request)
        isSigningUp = false

        switch result {
        case .success(let auth):
            signupPassword = ""
            // Straight off the signup response's own embedded user, same as
            // `login()` — no follow-up `users/me` call needed.
            state = .loggedIn(auth.user)
        case .failure(let error):
            signupErrorMessage = Self.signupErrorMessage(for: error)
        }
    }

    func signOut() {
        authRepository.logout()
        email = ""
        password = ""
        state = .loggedOut
    }

    /// Re-checks the signed-in user's role whenever the app returns to the
    /// foreground — the one way this app learns a practitioner converted a
    /// Lead to a Patient server-side, since a *stored* access token keeps
    /// carrying its original role claim until refreshed (see
    /// `PatientService.promoteExistingUser`'s doc comment on the backend: the
    /// same `User` row's `role` column flips in place, but that's invisible
    /// to a token minted before the flip). A role change flows into `state`
    /// here, and `RootView`'s switch over `user.role` re-navigates on its own
    /// the moment `state` changes — no separate signal needed.
    ///
    /// Unlike `restoreSession()`, a failure here does **not** sign the user
    /// out: this fires on every foreground, and a transient network blip
    /// while merely resuming the app shouldn't punt someone who very likely
    /// still has a perfectly good session to the login screen.
    func refreshUserOnResume() async {
        guard case .loggedIn = state else { return }
        if case .success(let user) = await authRepository.loadCurrentUser() {
            state = .loggedIn(user)
        }
    }

    private static func nilIfEmpty(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Unlike `loginErrorMessage`, there's no account-enumeration concern to
    /// protect here — signup's whole job is telling you whether that email is
    /// taken, so `EMAIL_CONFLICT` gets a specific, actionable message instead
    /// of falling into a generic default.
    private static func signupErrorMessage(for error: APIError) -> String {
        switch error.code {
        case "EMAIL_CONFLICT", "HTTP_409":
            return "An account with this email already exists. Try signing in instead."
        case "VALIDATION_ERROR", "HTTP_422":
            let fields = (error.details ?? [:])
                .sorted { $0.key < $1.key }
                .map(\.value)
            return fields.isEmpty ? "Please check your details and try again." : fields.joined(separator: "\n")
        case "RATE_LIMIT_EXCEEDED", "HTTP_429":
            return "Too many attempts. Try again in a few minutes."
        case "NETWORK_ERROR":
            return "Can't reach the server. Check your connection."
        case "KEYCHAIN_ERROR":
            return error.message
        default:
            return "Something went wrong. Please try again."
        }
    }

    /// Never reveals which field was wrong — that turns the login form into an
    /// account-enumeration oracle. The backend already returns a deliberately
    /// generic "Invalid email or password" for both cases; this keeps it that
    /// way rather than helpfully distinguishing them client-side.
    private static func loginErrorMessage(for error: APIError) -> String {
        switch error.code {
        case "AUTH_REQUIRED", "HTTP_401":
            return "Invalid email or password"
        case "VALIDATION_ERROR", "HTTP_422":
            // Format complaints ("must be a well-formed email address") are
            // safe to show: they describe what was typed, not whether an
            // account exists, so they leak nothing while saving a guess.
            let fields = (error.details ?? [:])
                .sorted { $0.key < $1.key }
                .map(\.value)
            return fields.isEmpty ? "Invalid email or password" : fields.joined(separator: "\n")
        case "RATE_LIMIT_EXCEEDED", "HTTP_429":
            return "Too many attempts. Try again in a few minutes."
        case "NETWORK_ERROR":
            return "Can't reach the server. Check your connection."
        case "KEYCHAIN_ERROR":
            return error.message
        default:
            return "Something went wrong. Please try again."
        }
    }
}
