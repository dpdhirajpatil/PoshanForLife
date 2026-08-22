import SwiftUI

/// IOS-13 is a scaffold, not a working call: everything around the call (the
/// join window, permissions, self-preview) is real, but no video provider
/// has been chosen yet — that's a deliberate open decision (pricing,
/// iOS/Android SDK parity, self-hosted vs. managed) rather than something to
/// settle implicitly.
///
/// When a provider is picked, this screen is where its SDK's call view goes;
/// nothing else in the flow should need to change.
struct LiveSessionView: View {

    let otherPartyName: String
    let onLeave: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "video.fill")
                .font(.system(size: 56))
                .foregroundStyle(theme.primary)
            Text("Video calling coming soon")
                .font(.displayFont(.semibold, size: 22))
                .foregroundStyle(theme.onBackground)
            Text("Your consultation with \(otherPartyName) will happen here once video calling is switched on.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onBackground.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Spacer()

            Button(action: onLeave) {
                Text("Back to appointments")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}
