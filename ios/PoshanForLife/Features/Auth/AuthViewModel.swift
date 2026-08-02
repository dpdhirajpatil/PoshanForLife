import Foundation

/// What `RootView` switches on. Kept deliberately small — anything richer
/// (per-role state, onboarding) belongs to the role's own ViewModel.
enum AuthState: Equatable {
    case loading
    case signedOut
    case signedIn(User)
}

/// IOS-01 provides only the seam: `restoreSession()` is what decides between
/// the placeholder and a role graph. Login, signup, and the OTP flow are IOS-02.
@MainActor
final class AuthViewModel: ObservableObject {

    @Published private(set) var state: AuthState = .loading

    private let authRepository: AuthRepository

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    /// Called once at launch. A stored token is not proof of a live session —
    /// it may be expired or revoked — so the user is only considered signed in
    /// after the backend confirms it.
    func restoreSession() async {
        state = .loading
        switch await authRepository.currentUser() {
        case .success(let user):
            state = .signedIn(user)
        case .failure:
            // Includes the offline case. Treating "can't confirm" as signed out
            // is the safe default; IOS-02 can soften it with a cached user once
            // there's a real session to cache.
            authRepository.logout()
            state = .signedOut
        }
    }

    func signOut() {
        authRepository.logout()
        state = .signedOut
    }
}
