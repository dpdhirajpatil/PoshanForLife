import SwiftUI

/// The solid navy block that tops Patient/Lead's brand-forward screens, per the
/// CI guide. Bleeds into the safe area so it reads as part of the status bar
/// rather than a floating band.
///
/// Patient/Lead only. Guard call sites with `theme.useTrapeziumMotif` (the same
/// flag that separates the brand-forward themes from the calm one) — a Staff
/// screen must never show this.
struct NavyHeaderBlock<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color.brandNavyDarkest
                    // Extends upward under the status bar without pushing the
                    // content up with it.
                    .ignoresSafeArea(edges: .top)
            )
    }
}

extension View {
    /// Foreground treatment for anything sitting on the navy block. The block is
    /// always navy in both appearances, so its contents can't use the theme's
    /// `onBackground` — that's near-navy in light mode and would vanish.
    func onNavyBlock() -> some View {
        foregroundStyle(Color.brandOffWhite)
    }
}
