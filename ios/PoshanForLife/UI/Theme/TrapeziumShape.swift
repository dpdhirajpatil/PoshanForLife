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
///
/// A minimum height rather than a fixed one, so a long label at a large font
/// scale grows the bar instead of being clipped inside it.
struct TrapeziumBar<Content: View>: View {
    var color: Color = .brandNavy
    var minHeight: CGFloat = 48
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .frame(minHeight: minHeight, alignment: .leading)
            .background(color, in: TrapeziumShape())
    }
}

/// The canonical use of the motif: a short ALL-CAPS label chip sitting directly
/// above the card it introduces — the "ACTIVE PROGRAMME" pattern. A section
/// header accent, NOT a container style; don't wrap ordinary cards in it.
///
/// Hard-codes uppercase and the off-white foreground because the component is
/// Patient/Lead-only by contract and the bar is always navy in both
/// appearances — the theme's `onSurface` would vanish against it in light mode.
struct TrapeziumSectionLabel: View {
    let text: String
    var color: Color = .brandNavy

    init(_ text: String, color: Color = .brandNavy) {
        self.text = text
        self.color = color
    }

    var body: some View {
        TrapeziumBar(color: color, minHeight: 40) {
            Text(text.uppercased())
                .font(.displayFont(.heavy, size: 13))
                .foregroundStyle(Color.brandOffWhite)
        }
    }
}
