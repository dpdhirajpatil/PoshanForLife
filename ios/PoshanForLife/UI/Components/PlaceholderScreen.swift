import SwiftUI

/// Themed stand-in for every destination whose real screen belongs to a later
/// prompt. Present so the navigation structure is fully walkable now — replace
/// them one at a time, no structural change needed.
struct PlaceholderScreen: View {
    let title: String
    var detail: String = "Coming in a later prompt."
    /// Profile/Settings show it; nothing else needs to.
    var showsSignOut: Bool = false

    @Environment(\.appTheme) private var theme

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                // No title Text here: `.navigationTitle` below already renders
                // it, and having both showed the name twice on every screen.
                Text(detail)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onBackground.opacity(0.7))
                    .multilineTextAlignment(.center)

                if showsSignOut {
                    SignOutButton().padding(.top, 24)
                }
            }
            .padding(24)
        }
        .navigationTitle(title)
    }
}

struct SignOutButton: View {
    @EnvironmentObject private var authViewModel: AuthViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(role: .destructive) {
            authViewModel.signOut()
        } label: {
            Text("Sign out")
                .font(.displayFont(.semibold, size: 15))
                .foregroundStyle(theme.error)
        }
    }
}
