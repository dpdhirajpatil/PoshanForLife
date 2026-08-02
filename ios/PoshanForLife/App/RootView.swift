import Combine
import SwiftUI

/// Does two things off one piece of state: picks the navigation structure for
/// the signed-in role, and picks the theme that wraps it.
struct RootView: View {

    @StateObject private var authViewModel: AuthViewModel

    init(authRepository: AuthRepository, sessionExpired: AnyPublisher<Void, Never>) {
        _authViewModel = StateObject(
            wrappedValue: AuthViewModel(
                authRepository: authRepository,
                sessionExpired: sessionExpired
            )
        )
    }

    var body: some View {
        content
            .environmentObject(authViewModel)
            .task {
                await authViewModel.restoreSession()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch authViewModel.state {
        case .loading:
            // Staff theme: neutral, and the role isn't known yet anyway.
            LaunchView().appTheme(StaffTheme.self)

        case .loggedOut:
            // Same reasoning — re-themes into the role's own theme the instant
            // login succeeds.
            LoginScreen(viewModel: authViewModel).appTheme(StaffTheme.self)

        case .loggedIn(let user):
            signedIn(user)
        }
    }

    @ViewBuilder
    private func signedIn(_ user: User) -> some View {
        switch user.role {
        case .patient:
            PatientTabView().appTheme(PatientTheme.self)

        case .lead:
            LeadTabView().appTheme(LeadTheme.self)

        case .doctor:
            // Practitioner in every label; DOCTOR on the wire.
            PractitionerTabView().appTheme(StaffTheme.self)

        case .admin:
            // Admin and Practitioner share the one StaffTheme, not two.
            AdminRootView().appTheme(StaffTheme.self)

        case .none:
            // A role this build doesn't know: don't guess at permissions, and
            // don't strand them — signing out is the only safe way forward.
            UnsupportedRoleView().appTheme(StaffTheme.self)
        }
    }
}

private struct LaunchView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: 12) {
                ProgressView().tint(theme.primary)
                Text("Loading…")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onBackground.opacity(0.7))
            }
        }
    }
}

private struct UnsupportedRoleView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: 12) {
                Text("Unsupported account").themedHeading(size: 22)
                Text("This version of the app doesn't recognise your account type. Please update.")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onBackground.opacity(0.7))
                    .multilineTextAlignment(.center)
                SignOutButton().padding(.top, 16)
            }
            .padding(24)
        }
    }
}
