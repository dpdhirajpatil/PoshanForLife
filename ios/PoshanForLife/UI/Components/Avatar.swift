import SwiftUI

/// Remote avatar with an initials fallback. The fallback is not just for a nil
/// URL — `AsyncImage`'s failure phase lands there too, so a broken or expired
/// image URL degrades to initials rather than an empty circle.
struct Avatar: View {
    let name: String
    let avatarUrl: String?
    var size: CGFloat = 48

    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            if let avatarUrl, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .empty:
                        initialsCircle.modifier(Shimmer())
                    case .failure:
                        initialsCircle
                    @unknown default:
                        initialsCircle
                    }
                }
            } else {
                initialsCircle
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(Text(name))
    }

    private var initialsCircle: some View {
        ZStack {
            theme.primary
            Text(initials)
                .font(.displayFont(.semibold, size: size * 0.36))
                .foregroundStyle(theme.onPrimary)
        }
    }

    private var initials: String {
        name.trimmingCharacters(in: .whitespaces)
            .split(separator: " ")
            .prefix(2)
            .compactMap { $0.first.map { String($0).uppercased() } }
            .joined()
    }
}
