import SwiftUI

/// Lead's Home tab: greeting, a prominent "Request Consultation" CTA, a
/// compact today's-health summary reusing IOS-04's `TrackViewModel`/
/// `CircularProgressRing`, an InBody upload nudge (points at the same
/// consultation flow — a Lead has no InBody data until staff convert them),
/// and a rotating health tip.
///
/// Deliberately **not** `DashboardView` reused with a role flag:
/// `DashboardViewModel` calls `activeProgramme(patientId:)`/
/// `unpaidInvoices(patientId:)` — patient-programme/invoice-scoped reads a
/// pre-conversion Lead has nothing behind anyway. Gamification (streak chip,
/// badge row, progress ring) is IOS-22's addition to this screen, not built
/// here.
struct LeadHomeView: View {

    let leadSelfRepository: LeadSelfRepository
    @StateObject private var trackViewModel: TrackViewModel

    @EnvironmentObject private var authViewModel: AuthViewModel
    @Environment(\.appTheme) private var theme
    @State private var showingRequestConsultation = false

    /// Takes the raw dependencies rather than `AppContainer` itself — same
    /// reason `TrackView`'s own initializer does, since `@StateObject` can't
    /// read an `@EnvironmentObject` this early. `LeadTabView.body` (where
    /// `container` *is* available) passes these straight through.
    init(
        leadSelfRepository: LeadSelfRepository,
        healthTracking: HealthTrackingRepository,
        goalsStore: GoalsStore,
        reminders: ReminderScheduler,
        healthKit: HealthKitManager
    ) {
        self.leadSelfRepository = leadSelfRepository
        _trackViewModel = StateObject(
            wrappedValue: TrackViewModel(
                repository: healthTracking,
                goalsStore: goalsStore,
                reminders: reminders,
                healthKit: healthKit
            )
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                LeadGreetingHeader(name: authViewModel.state.user?.name)

                Group {
                    RequestConsultationCard { showingRequestConsultation = true }
                    TodaysSummaryCard(trackViewModel: trackViewModel)
                    InBodyUploadNudgeCard { showingRequestConsultation = true }
                    HealthTipCard()
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 24)
        }
        .background(theme.background.ignoresSafeArea())
        .task { await trackViewModel.load() }
        .sheet(isPresented: $showingRequestConsultation) {
            NavigationStack {
                RequestConsultationView(repository: leadSelfRepository)
            }
        }
    }
}

// MARK: - Greeting

private struct LeadGreetingHeader: View {
    let name: String?

    var body: some View {
        NavyHeaderBlock {
            HStack(alignment: .center, spacing: 12) {
                Text("\(Self.greeting()), \(firstName)")
                    .font(.displayFont(.heavy, size: 24))
                    .textCase(.uppercase)
                    .onNavyBlock()
                Spacer()
                Avatar(name: name ?? "?", avatarUrl: nil)
            }
        }
    }

    private var firstName: String {
        guard let name, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return "there" }
        return name.split(separator: " ").first.map(String.init) ?? name
    }

    private static func greeting() -> String {
        switch Calendar.current.component(.hour, from: Date()) {
        case ..<12: return "Good morning"
        case ..<17: return "Good afternoon"
        default: return "Good evening"
        }
    }
}

// MARK: - Request consultation CTA

private struct RequestConsultationCard: View {
    let action: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: "stethoscope")
                    .font(.system(size: 28))
                    .foregroundStyle(theme.onPrimary)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Request a consultation")
                        .font(.displayFont(.semibold, size: 17))
                        .foregroundStyle(theme.onPrimary)
                    Text("Talk to a practitioner about your health goals.")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onPrimary.opacity(0.85))
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .foregroundStyle(theme.onPrimary.opacity(0.7))
            }
            .padding(16)
            .background(theme.primary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("request-consultation-cta")
    }
}

// MARK: - Today's summary (reuses IOS-04's tracking data + ring component)

private struct TodaysSummaryCard: View {
    @ObservedObject var trackViewModel: TrackViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Today")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)

            HStack(spacing: 20) {
                CircularProgressRing(progress: trackViewModel.waterProgress, size: 64, lineWidth: 7) {
                    VStack(spacing: 0) {
                        Text("\(trackViewModel.waterMlToday)")
                            .font(.displayFont(.heavy, size: 14))
                            .foregroundStyle(theme.onSurface)
                        Text("ml")
                            .font(.bodyFont(size: 9))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Water")
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                    Text("Goal \(trackViewModel.goals.waterMlPerDay) ml")
                        .font(.bodyFont(size: 11))
                        .foregroundStyle(theme.onSurface.opacity(0.5))
                }

                if let steps = trackViewModel.stepsToday {
                    Spacer(minLength: 0)
                    CircularProgressRing(progress: trackViewModel.stepsProgress, size: 64, lineWidth: 7) {
                        Text("\(steps)")
                            .font(.displayFont(.heavy, size: 13))
                            .foregroundStyle(theme.onSurface)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Steps")
                            .font(.bodyFont(size: 12))
                            .foregroundStyle(theme.onSurface.opacity(0.6))
                        Text("Goal \(trackViewModel.goals.stepsPerDay)")
                            .font(.bodyFont(size: 11))
                            .foregroundStyle(theme.onSurface.opacity(0.5))
                    }
                }
            }

            if let sleepHours = trackViewModel.sleepHoursToday {
                Text(String(format: "Slept %.1f h last night · goal %.1f h", sleepHours, trackViewModel.goals.sleepHoursPerNight))
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - InBody nudge

private struct InBodyUploadNudgeCard: View {
    let action: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "figure.arms.open")
                    .font(.system(size: 22))
                    .foregroundStyle(theme.primary)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Unlock full body composition tracking")
                        .font(.displayFont(.semibold, size: 14))
                        .foregroundStyle(theme.onSurface)
                    Text("Request a consultation to get your first InBody scan.")
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.onSurface.opacity(0.35))
            }
            .padding(14)
            .background(theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("inbody-upload-nudge")
    }
}

// MARK: - Health tip

private struct HealthTipCard: View {
    @Environment(\.appTheme) private var theme

    /// Rotates daily (deterministic by day-of-year) rather than on a timer —
    /// a tip that changes mid-read would be a distraction, not a feature.
    private static let tips: [String] = [
        "Drink a glass of water before every meal to help manage portions.",
        "Aim for 7–8 hours of sleep — recovery is part of nutrition too.",
        "A 10-minute walk after eating can help steady your blood sugar.",
        "Protein at breakfast keeps hunger in check through the morning.",
        "Small, consistent changes beat drastic short-term diets.",
        "Fibre-rich foods keep you fuller for longer on fewer calories.",
        "Consistent meal timing helps regulate hunger hormones.",
    ]

    private var todaysTip: String {
        let dayOfYear = Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 0
        return Self.tips[dayOfYear % Self.tips.count]
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "lightbulb.fill")
                .font(.system(size: 20))
                .foregroundStyle(theme.tertiary)
            VStack(alignment: .leading, spacing: 3) {
                Text("Health tip")
                    .font(.displayFont(.semibold, size: 13))
                    .foregroundStyle(theme.onSurface)
                Text(todaysTip)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.75))
            }
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
