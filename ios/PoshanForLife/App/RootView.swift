import SwiftUI

/// Picks which role's navigation stack to show. Each role's view is wrapped in
/// its own theme — that wrapping is the IOS-02/IOS-03 seam and is marked below.
struct RootView: View {

    @EnvironmentObject private var container: AppContainer
    @StateObject private var authViewModel: AuthViewModel

    init(authRepository: AuthRepository) {
        _authViewModel = StateObject(wrappedValue: AuthViewModel(authRepository: authRepository))
    }

    var body: some View {
        Group {
            switch authViewModel.state {
            case .loading:
                LoadingView()

            case .signedOut:
                // IOS-02 replaces this with LoginView.
                PlaceholderView(title: "Signed out", detail: "Login arrives in IOS-02.")

            case .signedIn(let user):
                signedIn(user)
            }
        }
        .task {
            await authViewModel.restoreSession()
        }
    }

    /// SEAM — IOS-02 wires navigation and IOS-03's themes in here:
    ///   PatientNavigationView().environment(\.appTheme, PatientTheme())
    ///   LeadNavigationView().environment(\.appTheme, LeadTheme())
    ///   StaffNavigationView(role:).environment(\.appTheme, StaffTheme())
    /// Admin and Practitioner (wire role DOCTOR) share the one StaffTheme.
    @ViewBuilder
    private func signedIn(_ user: User) -> some View {
        switch user.role {
        case .patient:
            PlaceholderView(title: "Patient", detail: "Navigation arrives in IOS-02.")
        case .lead:
            PlaceholderView(title: "Lead", detail: "Navigation arrives in IOS-02.")
        case .doctor:
            PlaceholderView(title: "Practitioner", detail: "Navigation arrives in IOS-02.")
        case .admin:
            PlaceholderView(title: "Admin", detail: "Navigation arrives in IOS-02.")
        case .none:
            // A role this build doesn't know about: don't guess at permissions.
            PlaceholderView(
                title: "Unsupported account",
                detail: "This app version doesn't recognise your account type. Please update."
            )
        }
    }
}

struct LoadingView: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Loading…")
                .font(.body)
        }
    }
}

/// Temporary stand-in so IOS-01 has something that runs. Deleted by IOS-02.
struct PlaceholderView: View {
    let title: String
    let detail: String

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.title2)
            Text(detail)
                .font(.footnote)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}
