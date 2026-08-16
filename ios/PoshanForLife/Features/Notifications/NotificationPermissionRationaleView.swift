import SwiftUI

/// Shown once, before the real system permission prompt.
///
/// **Why this exists at all**: unlike Android, iOS shows its own notification
/// prompt exactly once per install — there is no "ask again" short of sending
/// the user to Settings. So the first ask has to make its case; this sheet is
/// that case, presented on top of the app rather than as the very first thing
/// a new user sees, so there's context (they've already seen what the app is)
/// before being asked to decide.
struct NotificationPermissionRationaleView: View {

    let onDecide: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var isRequesting = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "bell.badge.fill")
                .font(.system(size: 52, weight: .light))
                .foregroundStyle(theme.primary)

            VStack(spacing: 10) {
                Text("Stay in the loop")
                    .themedHeading(size: 22)

                Text("Get notified when your practitioner books an appointment, your InBody report is ready, or you earn a badge — right when it happens, not just when you open the app.")
                    .font(.bodyFont(size: 15))
                    .foregroundStyle(theme.onBackground.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 24)

            Spacer()

            VStack(spacing: 12) {
                Button {
                    Task {
                        isRequesting = true
                        await NotificationPermissionGate.requestAuthorization()
                        isRequesting = false
                        onDecide()
                        dismiss()
                    }
                } label: {
                    Text(isRequesting ? "Requesting…" : "Turn on notifications")
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.primary, in: Capsule())
                }
                .disabled(isRequesting)
                .accessibilityIdentifier("enable-notifications")

                Button {
                    NotificationPermissionGate.declineWithoutAsking()
                    onDecide()
                    dismiss()
                } label: {
                    Text("Not now")
                        .font(.bodyFont(size: 15))
                        .foregroundStyle(theme.onBackground.opacity(0.6))
                }
                .accessibilityIdentifier("skip-notifications")
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 8)
        }
        .padding(.top, 40)
        .background(theme.background.ignoresSafeArea())
        .interactiveDismissDisabled(isRequesting)
    }
}
