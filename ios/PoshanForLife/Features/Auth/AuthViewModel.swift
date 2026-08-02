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

    var canSubmit: Bool {
        !email.trimmingCharacters(in: .whitespaces).isEmpty
            && !password.isEmpty
            && !isSubmitting
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

    func signOut() {
        authRepository.logout()
        email = ""
        password = ""
        state = .loggedOut
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
