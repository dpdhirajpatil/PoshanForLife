import SwiftUI

/// Home for a self-signup account (LEAD): a greeting, a daily check-in streak,
/// streak badges, and the path to becoming a patient (request a consultation).
/// Mirrors Android's LeadHomeScreen.
struct LeadHomeView: View {

    @StateObject private var viewModel: LeadHomeViewModel
    private let repository: LeadSelfRepository
    @EnvironmentObject private var authViewModel: AuthViewModel
    @Environment(\.appTheme) private var theme

    init(repository: LeadSelfRepository) {
        self.repository = repository
        _viewModel = StateObject(wrappedValue: LeadHomeViewModel(repository: repository))
    }

    private var firstName: String {
        authViewModel.state.user?.name.split(separator: " ").first.map(String.init) ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(firstName.isEmpty ? "Welcome" : "Hi, \(firstName)")
                    .themedHeading(size: 28)
                    .accessibilityIdentifier("leadGreeting")

                streakCard
                if !viewModel.badges.isEmpty { badgesRow }
                consultationCard
            }
            .padding(20)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Home")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }

    private var streakCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 16) {
                ZStack {
                    Circle().stroke(theme.onSurface.opacity(0.12), lineWidth: 8)
                    Circle()
                        .trim(from: 0, to: CGFloat(viewModel.streak?.percentComplete ?? 0) / 100)
                        .stroke(theme.primary, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(viewModel.streak?.currentStreak ?? 0)")
                        .font(.displayFont(.bold, size: 22))
                        .foregroundStyle(theme.onSurface)
                }
                .frame(width: 64, height: 64)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Day streak")
                        .font(.displayFont(.semibold, size: 17))
                        .foregroundStyle(theme.onSurface)
                    Text("Best: \(viewModel.streak?.longestStreak ?? 0) days")
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
                Spacer()
            }

            Text("Log your health data daily to build your streak.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.8))

            AuthPrimaryButton(
                title: viewModel.checkedInToday ? "Checked in today" : "Check in today",
                isLoading: viewModel.isCheckingIn,
                isEnabled: !viewModel.checkedInToday && !viewModel.isCheckingIn && !viewModel.isLoading
            ) {
                Task { await viewModel.checkIn() }
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var badgesRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Badges")
                .font(.displayFont(.semibold, size: 17))
                .foregroundStyle(theme.onBackground)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(viewModel.badges) { badge in
                        VStack(spacing: 6) {
                            Image(systemName: badge.earned ? "rosette" : "lock.fill")
                                .font(.system(size: 26))
                                .foregroundStyle(badge.earned ? theme.primary : theme.onSurface.opacity(0.35))
                            Text(badge.name)
                                .font(.bodyFont(size: 12))
                                .multilineTextAlignment(.center)
                                .foregroundStyle(theme.onSurface.opacity(badge.earned ? 1 : 0.6))
                        }
                        .frame(width: 88, height: 88)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("\(badge.name), \(badge.earned ? "earned" : "not yet earned")")
                    }
                }
            }
        }
    }

    private var consultationCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Unlock full body composition tracking")
                .font(.displayFont(.semibold, size: 17))
                .foregroundStyle(theme.onSurface)
            Text("Request a consultation to get InBody reports and a practitioner-guided programme.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.8))
            NavigationLink {
                RequestConsultationView(repository: repository)
            } label: {
                Text("Request consultation")
                    .font(.displayFont(.semibold, size: 16))
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(theme.primary, in: RoundedCornerShape())
                    .foregroundStyle(theme.onPrimary)
            }
            .accessibilityIdentifier("requestConsultation")
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
