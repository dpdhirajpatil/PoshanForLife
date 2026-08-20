import SwiftUI

/// The patient's full badge catalog — earned badges in full color, locked
/// ones dimmed with a lock overlay. Tapping any tile opens a popover with
/// the badge's description; a newly-earned badge (per `SeenBadgesStore`)
/// plays a one-shot celebration on top.
struct BadgesView: View {

    @StateObject private var viewModel: BadgesViewModel
    @Environment(\.appTheme) private var theme
    @State private var selectedBadge: PatientBadgeStatus?

    private static let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    init(repository: BadgesRepository, profile: DashboardRepository) {
        _viewModel = StateObject(wrappedValue: BadgesViewModel(repository: repository, profile: profile))
    }

    var body: some View {
        ZStack {
            ScrollView {
                content
                    .padding(16)
            }
            .background(theme.background.ignoresSafeArea())
            .refreshable { await viewModel.load() }

            if let celebratingBadge = viewModel.celebratingBadge {
                BadgeCelebrationOverlay(badge: celebratingBadge)
                    .transition(.opacity)
            }
        }
        .navigationTitle("Badges")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
        .popover(item: $selectedBadge) { badge in
            BadgeDetailPopover(badge: badge, onDismiss: { selectedBadge = nil })
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.celebratingBadge)
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.badges {
        case .loading:
            LazyVGrid(columns: Self.columns, spacing: 20) {
                ForEach(0..<9, id: \.self) { _ in
                    VStack(spacing: 6) {
                        Circle()
                            .fill(theme.onSurface.opacity(0.1))
                            .aspectRatio(1, contentMode: .fit)
                            .modifier(Shimmer())
                        SkeletonBlock(width: 50, height: 10)
                    }
                }
            }

        case .failure(let message):
            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .padding(.top, 24)

        case .success(let list):
            // An admin who hasn't defined any badges yet leaves this list
            // genuinely empty — say so rather than showing a blank grid.
            if list.isEmpty {
                Text("No badges to earn yet — check back soon.")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .padding(.top, 24)
            } else {
                LazyVGrid(columns: Self.columns, spacing: 20) {
                    ForEach(list) { badge in
                        BadgeTile(badge: badge)
                            .onTapGesture { selectedBadge = badge }
                    }
                }
            }
        }
    }
}

// MARK: - Tile

private struct BadgeTile: View {
    let badge: PatientBadgeStatus
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .fill(badge.earned ? theme.primary.opacity(0.15) : theme.onSurface.opacity(0.06))
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        Image(systemName: badge.symbolName)
                            .font(.system(size: 26, weight: .semibold))
                            .foregroundStyle(badge.earned ? theme.primary : theme.onSurface.opacity(0.3))
                    )

                if !badge.earned {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(theme.onSurface)
                        .padding(5)
                        .background(theme.surface, in: Circle())
                }
            }

            Text(badge.name)
                .font(.bodyFont(size: 12))
                .foregroundStyle(badge.earned ? theme.onSurface : theme.onSurface.opacity(0.5))
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Detail popover

private struct BadgeDetailPopover: View {
    let badge: PatientBadgeStatus
    let onDismiss: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: badge.symbolName)
                    .font(.system(size: 22))
                    .foregroundStyle(badge.earned ? theme.primary : theme.onSurface.opacity(0.5))
                Text(badge.name)
                    .font(.displayFont(.semibold, size: 17))
                    .foregroundStyle(theme.onSurface)
            }

            Text((badge.description?.isEmpty == false ? badge.description : nil) ?? "No description yet.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.8))
                .fixedSize(horizontal: false, vertical: true)

            Text(badge.earned ? "Earned" : "Not yet earned")
                .font(.displayFont(.semibold, size: 13))
                .foregroundStyle(badge.earned ? theme.primary : theme.onSurface.opacity(0.6))

            // `.popover` adapts to a full-screen sheet on a compact width
            // (iPhone), where there's no "outside" left to tap-to-dismiss —
            // an explicit close mirrors Android's dialog, which has the same
            // "Close" button for the same reason.
            Button("Close", action: onDismiss)
                .font(.displayFont(.semibold, size: 14))
                .foregroundStyle(theme.primary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(16)
        .frame(width: 260, alignment: .leading)
        .background(theme.surface)
    }
}

// MARK: - Celebration

/// A one-shot celebration for a newly-earned badge: small pieces burst
/// outward from the center while the badge itself pops in, all pure SwiftUI
/// (`.scaleEffect` + `.opacity`, no confetti SPM package) — no new package
/// resolution needed to ship it.
struct BadgeCelebrationOverlay: View {
    let badge: PatientBadgeStatus

    @Environment(\.appTheme) private var theme
    @State private var animate = false

    private static let pieceColors: [Color] = [.yellow, .orange, .pink, .purple, .mint, .cyan]

    private let pieces: [(angle: Double, distance: CGFloat, color: Color)] = (0..<24).map { i in
        let angle = Double(i) / 24 * 2 * .pi
        let distance = CGFloat(90 + (i * 37) % 90)
        return (angle, distance, pieceColors[i % pieceColors.count])
    }

    var body: some View {
        ZStack {
            Color.black.opacity(animate ? 0.35 : 0)

            ForEach(0..<pieces.count, id: \.self) { i in
                ConfettiPiece(piece: pieces[i], animate: animate)
            }

            VStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(theme.primary)
                        .frame(width: 96, height: 96)
                    Image(systemName: badge.symbolName)
                        .font(.system(size: 40, weight: .bold))
                        .foregroundStyle(theme.onPrimary)
                }
                .scaleEffect(animate ? 1 : 0.4)
                .opacity(animate ? 1 : 0)

                Text("Badge earned!")
                    .font(.displayFont(.heavy, size: 20))
                    .foregroundStyle(.white)
                Text(badge.name)
                    .font(.displayFont(.semibold, size: 16))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .opacity(animate ? 1 : 0)
            .scaleEffect(animate ? 1 : 0.7)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) { animate = true }
        }
    }
}

private struct ConfettiPiece: View {
    let piece: (angle: Double, distance: CGFloat, color: Color)
    let animate: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: 1.5)
            .fill(piece.color)
            .frame(width: 6, height: 10)
            .rotationEffect(.degrees(animate ? Double(piece.distance) * 2 : 0))
            .offset(
                x: animate ? cos(piece.angle) * piece.distance : 0,
                y: animate ? sin(piece.angle) * piece.distance : 0
            )
            .opacity(animate ? 0 : 1)
            .animation(.easeOut(duration: 0.9), value: animate)
    }
}
