import SwiftUI

/// The slightly slanted parallelogram behind headlines and primary CTAs in the
/// CI guide. Same 6% slant as Android's `TrapeziumShape`.
///
/// Patient and Lead only. Staff-theme screens must never use it — enforced at
/// call sites (check `theme.useTrapeziumMotif`), not by disabling the shape.
struct TrapeziumShape: Shape {
    /// How far each top corner is inset from its bottom counterpart, as a
    /// fraction of the width.
    var slantFraction: CGFloat = 0.06

    func path(in rect: CGRect) -> Path {
        let slant = rect.width * slantFraction
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + slant, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - slant, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// Convenience wrapper matching Android's `TrapeziumBar`.
struct TrapeziumBar<Content: View>: View {
    @Environment(\.appTheme) private var theme
    var color: Color = .brandNavy
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 20)
            .frame(height: 48)
            .background(color, in: TrapeziumShape())
    }
}
