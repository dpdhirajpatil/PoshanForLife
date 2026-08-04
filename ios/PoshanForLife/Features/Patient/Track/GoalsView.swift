import SwiftUI

/// Daily targets the Track screen measures against. Stored in UserDefaults —
/// see ``GoalsStore`` for why that's the right home for these.
struct GoalsView: View {

    @ObservedObject var store: GoalsStore
    @Environment(\.appTheme) private var theme

    @State private var steps: Double = 8_000
    @State private var water: Double = 2_000
    @State private var sleep: Double = 8
    @State private var targetWeight: String = ""

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    stepper(
                        title: "Daily steps",
                        value: "\(Int(steps))",
                        binding: $steps,
                        range: 1_000...30_000,
                        step: 500
                    )
                    stepper(
                        title: "Daily water",
                        value: "\(Int(water)) ml",
                        binding: $water,
                        range: 500...6_000,
                        step: 250
                    )
                    stepper(
                        title: "Sleep per night",
                        value: String(format: "%.1f h", sleep),
                        binding: $sleep,
                        range: 4...12,
                        step: 0.5
                    )

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Target weight")
                            .font(.displayFont(.semibold, size: 16))
                            .foregroundStyle(theme.onSurface)
                        TextField("Optional", text: $targetWeight)
                            .keyboardType(.decimalPad)
                            .font(.bodyFont(size: 16))
                            .padding(12)
                            .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        Text("Leave blank if you'd rather not set one.")
                            .font(.bodyFont(size: 12))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                }
                .padding(16)
            }
        }
        .navigationTitle("Goals")
        .onAppear(perform: loadFromStore)
        // Saved on the way out rather than behind a Save button: these are
        // preferences, and there's nothing to validate or fail.
        .onDisappear(perform: save)
    }

    private func stepper(
        title: String,
        value: String,
        binding: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.displayFont(.semibold, size: 16))
                    .foregroundStyle(theme.onSurface)
                Spacer()
                Text(value)
                    .font(.displayFont(.heavy, size: 18))
                    .foregroundStyle(theme.onSurface)
            }
            Stepper(title, value: binding, in: range, step: step)
                .labelsHidden()
            Slider(value: binding, in: range, step: step)
                .tint(theme.primary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func loadFromStore() {
        steps = Double(store.goals.stepsPerDay)
        water = Double(store.goals.waterMlPerDay)
        sleep = store.goals.sleepHoursPerNight
        targetWeight = store.goals.targetWeightKg.map { String(format: "%g", $0) } ?? ""
    }

    private func save() {
        store.update(
            Goals(
                stepsPerDay: Int(steps),
                waterMlPerDay: Int(water),
                sleepHoursPerNight: sleep,
                targetWeightKg: Double(targetWeight.trimmingCharacters(in: .whitespaces))
            )
        )
    }
}
