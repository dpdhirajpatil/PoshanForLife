import SwiftUI

/// Where tapping a dashboard card leads. Values rather than inline destinations
/// so the enclosing NavigationStack owns the routing.
enum DashboardRoute: Hashable {
    case fullReport
    case invoice(id: String)
}

struct DashboardView: View {

    @StateObject private var viewModel: DashboardViewModel
    @Environment(\.appTheme) private var theme

    @ObservedObject private var reminders: ReminderScheduler
    @EnvironmentObject private var container: AppContainer

    init(repository: DashboardRepository, reminders: ReminderScheduler) {
        _viewModel = StateObject(wrappedValue: DashboardViewModel(repository: repository))
        self.reminders = reminders
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                GreetingHeader(state: viewModel.profile)

                Group {
                    InBodyScoreCard(state: viewModel.healthRecord)
                    ActiveProgrammeCard(state: viewModel.programme)
                    OutstandingBalanceCard(state: viewModel.invoices)
                    UpcomingRemindersCard(scheduler: reminders)
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 24)
        }
        .background(theme.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NotificationBellButton(
                    repository: container.notificationsRepository,
                    deepLinkRouter: container.deepLinkRouter
                )
            }
        }
        .refreshable { await viewModel.load() }
        .task {
            await viewModel.load()
            // Reminders live on disk, not the network — the dashboard needs
            // them loaded even if the user hasn't opened Track yet.
            await reminders.load()
        }
        .navigationDestination(for: DashboardRoute.self) { route in
            switch route {
            case .fullReport:
                // IOS-05 landed, so this is a real destination now.
                ReportsListView(
                    repository: container.reportsRepository,
                    profile: container.dashboardRepository
                )
            case .invoice(let id):
                PlaceholderScreen(title: "Invoice", detail: "Invoice \(id) opens in IOS-16.")
            }
        }
    }
}

// MARK: - Greeting

private struct GreetingHeader: View {
    let state: CardState<UserDetail>

    var body: some View {
        // The navy header block treatment, per the theme system — this is one of
        // the screens the CI guide applies it to.
        NavyHeaderBlock {
            HStack(alignment: .center, spacing: 12) {
                switch state {
                case .loading:
                    VStack(alignment: .leading, spacing: 6) {
                        SkeletonBlock(width: 190, height: 26)
                    }
                    Spacer()
                    Circle()
                        .fill(Color.brandOffWhite.opacity(0.18))
                        .frame(width: 48, height: 48)
                        .modifier(Shimmer())

                case .success(let user):
                    Text("\(Self.greeting()), \(user.firstName)")
                        .font(.displayFont(.heavy, size: 24))
                        .textCase(.uppercase)
                        .onNavyBlock()
                    Spacer()
                    Avatar(name: user.name, avatarUrl: user.avatarUrl)

                case .failure:
                    // No name to greet with, but the block still anchors the
                    // screen — a bare error where the header belongs looks broken.
                    Text("Welcome back")
                        .font(.displayFont(.heavy, size: 24))
                        .textCase(.uppercase)
                        .onNavyBlock()
                    Spacer()
                }
            }
        }
    }

    private static func greeting() -> String {
        switch Calendar.current.component(.hour, from: Date()) {
        case ..<12: return "Good morning"
        case ..<17: return "Good afternoon"
        default: return "Good evening"
        }
    }
}

// MARK: - Cards

/// Shared chrome so every card sits on the same surface, radius and padding.
private struct DashboardCard<Content: View>: View {
    var fill: Color?
    @ViewBuilder var content: () -> Content

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(fill ?? theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct CardTitle: View {
    let text: String
    var color: Color?

    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(text)
            .font(.displayFont(.semibold, size: 20))
            .foregroundStyle(color ?? theme.onSurface)
    }
}

private struct CardMessage: View {
    let text: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(text)
            .font(.bodyFont(size: 14))
            .foregroundStyle(theme.onSurface.opacity(0.7))
    }
}

private struct InBodyScoreCard: View {
    let state: CardState<HealthRecord?>
    @Environment(\.appTheme) private var theme

    var body: some View {
        DashboardCard {
            CardTitle(text: "InBody score")

            switch state {
            case .loading:
                HStack(spacing: 24) {
                    ForEach(0..<3, id: \.self) { _ in
                        VStack(alignment: .leading, spacing: 6) {
                            SkeletonBlock(width: 64, height: 22)
                            SkeletonBlock(width: 44, height: 12)
                        }
                    }
                }

            case .failure:
                CardMessage(text: "Couldn't load your latest stats")

            case .success(let record):
                if let record {
                    HStack {
                        StatColumn(label: "Weight", value: record.weightKg.map { "\(Self.oneDecimal($0)) kg" })
                        Spacer()
                        StatColumn(label: "BMI", value: record.bmi.map(Self.oneDecimal))
                        Spacer()
                        StatColumn(label: "Body fat", value: record.bodyFatPct.map { "\(Self.oneDecimal($0))%" })
                    }
                } else {
                    CardMessage(text: "No InBody data yet")
                }
            }

            NavigationLink(value: DashboardRoute.fullReport) {
                Text("View full report →")
                    .font(.bodyFont(size: 15))
                    .foregroundStyle(theme.primary)
            }
        }
    }

    /// Fixed locale: these are numbers, not prose, and a comma decimal
    /// separator alongside "kg" reads as a thousands separator.
    private static func oneDecimal(_ value: Double) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }
}

private struct StatColumn: View {
    let label: String
    let value: String?

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value ?? "—")
                .font(.displayFont(.heavy, size: 22))
                .foregroundStyle(theme.onSurface)
            Text(label)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.7))
        }
    }
}

private struct ActiveProgrammeCard: View {
    let state: CardState<PatientProgramme?>
    @Environment(\.appTheme) private var theme

    var body: some View {
        switch state {
        case .loading:
            DashboardCard {
                SkeletonBlock(width: 200, height: 22)
                SkeletonBlock(width: 130, height: 14)
                SkeletonBlock(height: 8, cornerRadius: 4)
            }

        case .failure(let message):
            DashboardCard {
                CardTitle(text: "Active programme")
                CardMessage(text: message)
            }

        case .success(let programme):
            // "Only if present" — a patient with no active programme gets no
            // card at all, rather than an empty one.
            if let programme {
                content(for: programme)
            }
        }
    }

    @ViewBuilder
    private func content(for programme: PatientProgramme) -> some View {
        let progress = ProgrammeProgress(startDate: programme.startDate, endDate: programme.endDate)

        VStack(alignment: .leading, spacing: 8) {
            // The trapezium label chip above the card, matching the Android
            // dashboard's Direction A treatment.
            TrapeziumSectionLabel("Active programme")

            DashboardCard {
                CardTitle(text: programme.catalogueItem?.name ?? "Programme")

                if let doctor = programme.assignedDoctor {
                    CardMessage(text: "with \(doctor.name)")
                }

                ProgressView(value: progress.fraction)
                    .progressViewStyle(.linear)
                    .tint(theme.primary)

                Text(progress.remainingLabel)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
        }
    }
}

private struct OutstandingBalanceCard: View {
    let state: CardState<[DocumentListItem]>
    @Environment(\.appTheme) private var theme

    var body: some View {
        switch state {
        case .loading:
            DashboardCard {
                SkeletonBlock(width: 170, height: 22)
                SkeletonBlock(width: 110, height: 16)
            }

        case .failure:
            // Silent: a balance card that can't load its amount has nothing
            // useful to say, and an error where money should be is alarming out
            // of proportion to a transient fetch failure. The Invoices screen
            // is the source of truth.
            EmptyView()

        case .success(let invoices):
            if !invoices.isEmpty {
                content(for: invoices)
            }
        }
    }

    @ViewBuilder
    private func content(for invoices: [DocumentListItem]) -> some View {
        let total = invoices.reduce(0) { $0 + $1.total }

        // theme.tertiary, not a hard-coded amber — Staff's tertiary is olive,
        // and this card would be wrong there.
        DashboardCard(fill: theme.tertiary) {
            CardTitle(text: "Outstanding balance", color: theme.onTertiary)

            Text("\(CurrencyFormatter.inr(total)) due")
                .font(.bodyFont(size: 16))
                .foregroundStyle(theme.onTertiary)

            if invoices.count > 1 {
                Text("across \(invoices.count) invoices")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onTertiary.opacity(0.8))
            }

            if let first = invoices.first {
                NavigationLink(value: DashboardRoute.invoice(id: first.id)) {
                    Text("Pay")
                        .font(.displayFont(.semibold, size: 15))
                        .foregroundStyle(theme.tertiary)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(theme.onTertiary, in: Capsule())
                }
            }
        }
    }
}

/// The next three reminders, soonest first, from IOS-04's scheduler. Ordered by
/// next occurrence rather than clock time, so a 22:00 reminder tonight sorts
/// ahead of a 07:00 one tomorrow.
private struct UpcomingRemindersCard: View {
    @ObservedObject var scheduler: ReminderScheduler
    @Environment(\.appTheme) private var theme

    private var upcoming: [(reminder: MedicationReminder, date: Date)] {
        scheduler.reminders
            .filter(\.enabled)
            .compactMap { reminder in
                reminder.nextOccurrence().map { (reminder, $0) }
            }
            .sorted { $0.1 < $1.1 }
            .prefix(3)
            .map { ($0.0, $0.1) }
    }

    var body: some View {
        DashboardCard {
            CardTitle(text: "Upcoming reminders")

            if upcoming.isEmpty {
                CardMessage(text: "No reminders yet")
            } else {
                ForEach(upcoming, id: \.reminder.id) { item in
                    HStack(spacing: 10) {
                        Image(systemName: "bell")
                            .foregroundStyle(theme.primary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.reminder.label)
                                .font(.bodyFont(size: 15))
                                .foregroundStyle(theme.onSurface)
                            Text("\(item.reminder.timeLabel) · \(item.reminder.daysLabel)")
                                .font(.bodyFont(size: 12))
                                .foregroundStyle(theme.onSurface.opacity(0.7))
                        }
                        Spacer()
                    }
                }
            }
        }
    }
}
