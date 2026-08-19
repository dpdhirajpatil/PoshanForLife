import SwiftUI

/// System/Light/Dark picker — a set of selectable rows meant to sit inside a
/// `List`'s `Section`, mirroring Android's `AppearanceCard`.
///
/// Three explicit choices rather than a single on/off switch: "dark mode
/// on/off" can't express "follow the device", which is the default and the
/// option most users actually want, and a switch leaves "off" ambiguous
/// between "light" and "following a light device".
struct AppearancePicker: View {
    @ObservedObject var store: ThemePreferenceStore
    @Environment(\.appTheme) private var theme

    var body: some View {
        ForEach(ThemePreference.allCases, id: \.self) { mode in
            Button {
                store.mode = mode
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(mode.label)
                            .font(.bodyFont(size: 16))
                            .foregroundStyle(theme.onSurface)
                        Text(mode.description)
                            .font(.bodyFont(size: 12))
                            .foregroundStyle(theme.onSurface.opacity(0.6))
                    }
                    Spacer()
                    if store.mode == mode {
                        Image(systemName: "checkmark")
                            .foregroundStyle(theme.primary)
                    }
                }
                .padding(.vertical, 4)
            }
            .buttonStyle(.plain)
            .listRowBackground(theme.surface)
            .accessibilityAddTraits(store.mode == mode ? [.isSelected] : [])
        }
    }
}
