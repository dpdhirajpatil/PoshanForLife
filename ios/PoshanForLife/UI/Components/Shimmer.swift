import SwiftUI

/// A travelling highlight over whatever it's applied to. Generic on purpose —
/// nothing here knows about the dashboard; later modules apply it to their own
/// placeholders.
///
/// Applied as a mask rather than an overlay so it works on any content shape,
/// and honours Reduce Motion by holding still instead of animating.
struct Shimmer: ViewModifier {
    var active: Bool = true

    @State private var phase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let duration: Double = 1.2

    func body(content: Content) -> some View {
        if active && !reduceMotion {
            content
                .mask(
                    GeometryReader { geometry in
                        LinearGradient(
                            colors: [.black.opacity(0.45), .black, .black.opacity(0.45)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                        .frame(width: geometry.size.width * 1.5)
                        .offset(x: phase * geometry.size.width * 1.5)
                    }
                )
                .onAppear {
                    withAnimation(.linear(duration: duration).repeatForever(autoreverses: false)) {
                        phase = 1
                    }
                }
        } else {
            content
        }
    }
}

/// A grey block standing in for text or a value while it loads. Sized by the
/// caller, because a skeleton should occupy roughly the space its real content
/// will — that's what stops the layout jumping when the data lands.
struct SkeletonBlock: View {
    var width: CGFloat?
    var height: CGFloat = 16
    var cornerRadius: CGFloat = 8

    @Environment(\.appTheme) private var theme

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(theme.onSurface.opacity(0.12))
            .frame(width: width, height: height)
            .modifier(Shimmer())
            .accessibilityHidden(true)
    }
}

extension View {
    /// Shimmer an existing view — use when real content is already laid out and
    /// only needs redacting, e.g. `.redacted(reason: .placeholder).shimmering()`.
    func shimmering(active: Bool = true) -> some View {
        modifier(Shimmer(active: active))
    }
}
