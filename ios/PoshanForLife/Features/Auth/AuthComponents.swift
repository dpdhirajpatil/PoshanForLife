import SwiftUI

/// Patient/Lead use generous rounding; Staff is slightly tighter. Matching
/// Android's PoshanRoundedShapes / PoshanStaffShapes split.
struct RoundedCornerShape: Shape {
    func path(in rect: CGRect) -> Path {
        RoundedRectangle(cornerRadius: 12, style: .continuous).path(in: rect)
    }
}

/// The bordered card the sign-in, sign-up and phone forms put their fields in.
struct AuthFieldCard: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .font(.bodyFont(size: 16))
            .padding(14)
            .background(theme.surface, in: RoundedCornerShape())
            .overlay(
                RoundedCornerShape()
                    .stroke(theme.onBackground.opacity(0.12), lineWidth: 1)
            )
    }
}

extension View {
    func authFieldCard() -> some View { modifier(AuthFieldCard()) }
}

/// A rule between stacked fields. Without it two fields in one border read as
/// a single control, and iOS blanks secure text in screenshots.
struct AuthFieldDivider: View {
    @Environment(\.appTheme) private var theme
    var body: some View { Divider().overlay(theme.onBackground.opacity(0.12)) }
}

/// Full-width primary action. The spinner replaces the label rather than
/// sitting beside it, so the button can't change width mid-tap.
struct AuthPrimaryButton: View {
    let title: String
    let isLoading: Bool
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView().tint(theme.onPrimary)
                } else {
                    Text(title).font(.displayFont(.semibold, size: 16))
                }
            }
            .frame(maxWidth: .infinity, minHeight: 50)
        }
        .background(isEnabled ? theme.primary : theme.primary.opacity(0.4), in: RoundedCornerShape())
        .foregroundStyle(theme.onPrimary)
        .disabled(!isEnabled)
    }
}

/// Inline error text under a form, in the theme's error colour.
struct AuthInlineError: View {
    let message: String?
    @Environment(\.appTheme) private var theme

    var body: some View {
        if let message, !message.isEmpty {
            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.error)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("authError")
        }
    }
}
