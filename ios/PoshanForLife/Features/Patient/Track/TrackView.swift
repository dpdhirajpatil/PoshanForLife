import SwiftUI

struct TrackView: View {

    /// Which numeric field is up, so one shared keyboard toolbar can dismiss it.
    enum Field: Hashable { case calories, weight }

    @StateObject private var viewModel: TrackViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: Field?

    init(repository: HealthTrackingRepository, goalsStore: GoalsStore, reminders: ReminderScheduler, healthKit: HealthKitManager) {
        _viewModel = StateObject(
            wrappedValue: TrackViewModel(
                repository: repository,
                goalsStore: goalsStore,
                reminders: reminders,
                healthKit: healthKit
            )
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                WaterCard(viewModel: viewModel)
                NutritionCard(viewModel: viewModel, focusedField: $focusedField)
                SleepCard(viewModel: viewModel)
                WeightCard(viewModel: viewModel, focusedField: $focusedField)
                if HealthKitManager.isAvailable {
                    AppleHealthCard(viewModel: viewModel)
                }
                RemindersCard(scheduler: viewModel.reminders)
            }
            .padding(16)
        }
        // The number and decimal pads have NO return key, so without an
        // explicit Done the keyboard can't be dismissed at all — and while it's
        // up it covers the Log button the user is trying to reach.
        .scrollDismissesKeyboard(.interactively)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
            }
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Track")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink {
                    GoalsView(store: viewModel.goalsStore)
                } label: {
                    Image(systemName: "target")
                }
                .accessibilityLabel("Goals")
            }
        }
        .task { await viewModel.load() }
        // Leaving for Goals and coming back otherwise restores whatever
        // @FocusState held when we left, popping the keyboard open unprompted.
        // The number pad then covers the tab bar, so the next tap on Reports or
        // Profile lands on a number key instead of the tab — it silently types
        // into the weight field rather than navigating.
        //
        // Returning from Goals brings the keyboard back up on its own, and its
        // Done toolbar renders *over* the tab bar — so the next tap on Reports
        // or Profile lands on the keyboard and silently types a digit into the
        // weight field instead of switching tabs. Reproduced and captured.
        //
        // Clearing @FocusState alone does not fix it (tried on disappear, on
        // appear, and on the next runloop pass — the keyboard stayed up every
        // time), so the responder is dismissed directly as well.
        .onAppear {
            DispatchQueue.main.async {
                focusedField = nil
                UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
                )
            }
        }
    }
}

// MARK: - Shared chrome

private struct TrackCard<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.displayFont(.semibold, size: 20))
                .foregroundStyle(theme.onSurface)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct QuickAddButton: View {
    let title: String
    let action: () async -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button {
            Task { await action() }
        } label: {
            Text(title)
                .font(.displayFont(.semibold, size: 14))
                .foregroundStyle(theme.onPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(theme.primary, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Water

private struct WaterCard: View {
    @ObservedObject var viewModel: TrackViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        TrackCard(title: "Water") {
            HStack(spacing: 16) {
                CircularProgressRing(progress: viewModel.waterProgress) {
                    VStack(spacing: 0) {
                        Text("\(viewModel.waterMlToday)")
                            .font(.displayFont(.heavy, size: 20))
                            .foregroundStyle(theme.onSurface)
                        Text("ml")
                            .font(.bodyFont(size: 11))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                }
                .accessibilityIdentifier("water-total")
                .accessibilityLabel("Water logged today")
                .accessibilityValue("\(viewModel.waterMlToday) ml")

                VStack(alignment: .leading, spacing: 8) {
                    Text("Goal \(viewModel.goals.waterMlPerDay) ml")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                    HStack(spacing: 8) {
                        QuickAddButton(title: "+250 ml") { await viewModel.logWater(ml: 250) }
                        QuickAddButton(title: "+500 ml") { await viewModel.logWater(ml: 500) }
                    }
                }
            }
        }
    }
}

// MARK: - Nutrition

private struct NutritionCard: View {
    @ObservedObject var viewModel: TrackViewModel
    @FocusState.Binding var focusedField: TrackView.Field?
    @Environment(\.appTheme) private var theme

    private let meals = ["Breakfast", "Lunch", "Dinner", "Snack"]

    var body: some View {
        TrackCard(title: "Nutrition") {
            TextField("Calories (optional)", text: $viewModel.calorieText)
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .calories)
                .font(.bodyFont(size: 15))
                .padding(10)
                .background(theme.background, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            // Wraps to a second row on narrower devices rather than clipping.
            FlowRow(spacing: 8) {
                ForEach(meals, id: \.self) { meal in
                    QuickAddButton(title: meal) {
                        focusedField = nil
                        await viewModel.logNutrition(mealType: meal.lowercased())
                    }
                }
            }

            if viewModel.caloriesToday > 0 {
                Text("\(viewModel.caloriesToday) kcal logged today")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            ForEach(viewModel.nutritionToday) { entry in
                HStack {
                    Text((entry.mealType ?? "Meal").capitalized)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface)
                    Spacer()
                    Text(entry.value > 0 ? "\(Int(entry.value)) kcal" : "logged")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
            }
        }
    }
}

// MARK: - Sleep

private struct SleepCard: View {
    @ObservedObject var viewModel: TrackViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        TrackCard(title: "Sleep") {
            DatePicker("Bed time", selection: $viewModel.bedTime, displayedComponents: .hourAndMinute)
                .font(.bodyFont(size: 15))
            DatePicker("Wake time", selection: $viewModel.wakeTime, displayedComponents: .hourAndMinute)
                .font(.bodyFont(size: 15))

            HStack {
                Text(String(format: "%.1f hours", TrackViewModel.hoursBetween(viewModel.bedTime, viewModel.wakeTime)))
                    .font(.displayFont(.heavy, size: 18))
                    .foregroundStyle(theme.onSurface)
                Spacer()
                QuickAddButton(title: "Log sleep") { await viewModel.logSleep() }
            }

            if let hours = viewModel.sleepHoursToday {
                Text(String(format: "Logged today: %.1f h · goal %.1f h", hours, viewModel.goals.sleepHoursPerNight))
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
        }
    }
}

// MARK: - Weight

private struct WeightCard: View {
    @ObservedObject var viewModel: TrackViewModel
    @FocusState.Binding var focusedField: TrackView.Field?
    @Environment(\.appTheme) private var theme

    var body: some View {
        TrackCard(title: "Weight") {
            HStack(spacing: 12) {
                TextField("kg", text: $viewModel.weightText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .weight)
                    .font(.bodyFont(size: 15))
                    .padding(10)
                    .background(theme.background, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Button {
                    focusedField = nil
                    Task { await viewModel.logWeight() }
                } label: {
                    Text("Log")
                        .font(.displayFont(.semibold, size: 14))
                        .foregroundStyle(theme.onPrimary)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(
                            viewModel.canLogWeight ? theme.primary : theme.primary.opacity(0.4),
                            in: Capsule()
                        )
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.canLogWeight)
            }

            if let weight = viewModel.latestWeightKg {
                Text(String(format: "Last logged: %.1f kg", weight))
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
            // The only metric on this screen with a backend home, so the only
            // one where saying "synced" means anything.
            Text("Weight syncs to your practitioner. Water, nutrition and sleep stay on this device.")
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.6))
        }
    }
}

// MARK: - Apple Health (steps, heart rate)

/// Steps and heart rate are both read-only — HealthKit is their only source,
/// so unlike Water/Nutrition/Weight there's no manual-entry half to this
/// card, just a connect step and whatever the last sync brought in.
private struct AppleHealthCard: View {
    @ObservedObject var viewModel: TrackViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        TrackCard(title: "Apple Health") {
            switch viewModel.healthKit.connectionStatus {
            case .unavailable:
                EmptyView()
            case .notConnected:
                notConnected
            case .connected:
                connected
            }
        }
    }

    private var notConnected: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Connect Apple Health to bring in steps and heart rate automatically.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
            Button {
                Task { await viewModel.connectHealthKit() }
            } label: {
                Text("Connect Apple Health")
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.onPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(theme.primary, in: Capsule())
            }
            .buttonStyle(.plain)
            if let error = viewModel.healthKit.lastSyncError {
                Text(error)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.error)
            }
        }
    }

    private var connected: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 24) {
                if let steps = viewModel.stepsToday {
                    HStack(spacing: 12) {
                        CircularProgressRing(progress: viewModel.stepsProgress) {
                            Text("\(steps)")
                                .font(.displayFont(.heavy, size: 16))
                                .foregroundStyle(theme.onSurface)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Steps")
                                .font(.bodyFont(size: 12))
                                .foregroundStyle(theme.onSurface.opacity(0.6))
                            Text("Goal \(viewModel.goals.stepsPerDay)")
                                .font(.bodyFont(size: 11))
                                .foregroundStyle(theme.onSurface.opacity(0.5))
                        }
                    }
                }
                if let heartRate = viewModel.heartRateToday {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(heartRate) bpm")
                            .font(.displayFont(.heavy, size: 16))
                            .foregroundStyle(theme.onSurface)
                        Text("Avg heart rate")
                            .font(.bodyFont(size: 12))
                            .foregroundStyle(theme.onSurface.opacity(0.6))
                    }
                }
            }

            if viewModel.stepsToday == nil && viewModel.heartRateToday == nil {
                Text("Connected — no data yet today.")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }

            HStack {
                if let lastSyncedAt = viewModel.healthKit.lastSyncedAt {
                    (Text("Last synced ") + Text(lastSyncedAt, style: .relative) + Text(" ago"))
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.5))
                }
                Spacer()
                Button {
                    Task { await viewModel.syncHealthKit() }
                } label: {
                    if viewModel.healthKit.isSyncing {
                        ProgressView()
                    } else {
                        Text("Sync now")
                            .font(.displayFont(.semibold, size: 13))
                            .foregroundStyle(theme.primary)
                    }
                }
                .buttonStyle(.plain)
                .disabled(viewModel.healthKit.isSyncing)
            }
        }
    }
}

/// Minimal wrapping HStack — `Layout` is iOS 16+, which this app targets, and
/// it saves pulling in a flow-layout dependency for one row of chips.
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var totalHeight: CGFloat = 0
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth > 0, rowWidth + spacing + size.width > maxWidth {
                totalHeight += rowHeight + spacing
                rowWidth = size.width
                rowHeight = size.height
            } else {
                rowWidth += (rowWidth > 0 ? spacing : 0) + size.width
                rowHeight = max(rowHeight, size.height)
            }
        }
        return CGSize(width: maxWidth == .infinity ? rowWidth : maxWidth, height: totalHeight + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
