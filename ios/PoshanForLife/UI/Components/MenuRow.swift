import SwiftUI

/// One row of a List-based menu: leading SF Symbol, name, chevron.
/// Shared by the Admin root menu and the Practitioner More screen.
struct MenuRowItem: Identifiable, Hashable {
    var id: String { title }
    let title: String
    let systemImage: String
}

struct MenuRow: View {
    let item: MenuRowItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        Label {
            Text(item.title)
                .font(.bodyFont(size: 16))
                .foregroundStyle(theme.onSurface)
        } icon: {
            Image(systemName: item.systemImage)
                // The one place Staff's sparing accent shows up — an icon tint,
                // not a filled row.
                .foregroundStyle(theme.primary)
                .frame(width: 24)
        }
        .padding(.vertical, 4)
        // NavigationLink draws its own chevron; adding another would double it.
        .listRowBackground(theme.surface)
    }
}
