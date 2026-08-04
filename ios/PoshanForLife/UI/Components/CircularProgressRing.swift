import SwiftUI

/// Progress ring with a label in the middle. Generic — the water card uses it
/// now, Lead's streak ring can use it later.
struct CircularProgressRing<Label: View>: View {
    /// 0…1. Clamped, so an over-target day draws a full ring rather than
    /// wrapping past the start.
    let progress: Double
    var size: CGFloat = 92
    var lineWidth: CGFloat = 10
    @ViewBuilder var label: () -> Label

    @Environment(\.appTheme) private var theme

    var body: some View {
        ZStack {
            Circle()
                .stroke(theme.onSurface.opacity(0.12), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0, min(progress, 1)))
                .stroke(theme.primary, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                // Start at 12 o'clock rather than 3 o'clock.
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.35), value: progress)
            label()
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text("\(Int(max(0, min(progress, 1)) * 100)) percent of goal"))
    }
}
