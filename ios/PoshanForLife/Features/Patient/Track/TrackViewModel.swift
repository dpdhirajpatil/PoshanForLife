import Combine
import Foundation

@MainActor
final class TrackViewModel: ObservableObject {

    let repository: HealthTrackingRepository
    let goalsStore: GoalsStore
    let reminders: ReminderScheduler

    // Form state for the cards that take input.
    @Published var calorieText: String = ""
    @Published var weightText: String = ""
    @Published var bedTime: Date = TrackViewModel.defaultTime(hour: 23, minute: 0)
    @Published var wakeTime: Date = TrackViewModel.defaultTime(hour: 7, minute: 0)

    private var cancellables = Set<AnyCancellable>()

    init(repository: HealthTrackingRepository, goalsStore: GoalsStore, reminders: ReminderScheduler) {
        self.repository = repository
        self.goalsStore = goalsStore
        self.reminders = reminders

        // These are separate ObservableObjects, so their changes don't reach a
        // view observing only this one. Re-publish them.
        repository.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        goalsStore.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    func load() async {
        await repository.loadFromDisk()
        await reminders.load()
    }

    // MARK: - Derived state

    var goals: Goals { goalsStore.goals }

    var waterMlToday: Int { Int(repository.total(.water)) }

    var waterProgress: Double {
        guard goals.waterMlPerDay > 0 else { return 0 }
        return min(Double(waterMlToday) / Double(goals.waterMlPerDay), 1)
    }

    var nutritionToday: [HealthEntry] {
        repository.entries().filter { $0.type == .nutrition }.sorted { $0.loggedAt > $1.loggedAt }
    }

    var caloriesToday: Int { Int(nutritionToday.reduce(0) { $0 + $1.value }) }

    var sleepHoursToday: Double? { repository.latestToday(.sleep)?.value }

    var latestWeightKg: Double? { repository.latest(.weight)?.value }

    /// Nil until HealthKit is wired up in IOS-11 — there is no manual entry for
    /// steps, so nil means "not connected" rather than "zero steps".
    var stepsToday: Int? { repository.latestToday(.steps).map { Int($0.value) } }

    var stepsProgress: Double {
        guard let steps = stepsToday, goals.stepsPerDay > 0 else { return 0 }
        return min(Double(steps) / Double(goals.stepsPerDay), 1)
    }

    // MARK: - Logging

    func logWater(ml: Int) async {
        await repository.log(.water, value: Double(ml), unit: "ml")
    }

    func logNutrition(mealType: String) async {
        let calories = Double(calorieText.trimmingCharacters(in: .whitespaces)) ?? 0
        await repository.log(
            .nutrition,
            value: calories,
            unit: HealthEntry.nutritionUnit(mealType: mealType)
        )
        calorieText = ""
    }

    func logSleep() async {
        await repository.log(.sleep, value: Self.hoursBetween(bedTime, wakeTime), unit: "hours")
    }

    var canLogWeight: Bool {
        guard let kg = Double(weightText.trimmingCharacters(in: .whitespaces)) else { return false }
        // The backend rejects non-positive weights outright (@DecimalMin 0,
        // exclusive); catching it here avoids a round trip to be told so.
        return kg > 0 && kg < 500
    }

    func logWeight() async {
        guard canLogWeight, let kg = Double(weightText.trimmingCharacters(in: .whitespaces)) else { return }
        await repository.log(.weight, value: kg, unit: "kg")
        weightText = ""
    }

    // MARK: - Helpers

    /// Handles the normal case of going to bed before midnight and waking after
    /// it — a naive subtraction would report a negative night's sleep.
    nonisolated static func hoursBetween(_ bed: Date, _ wake: Date) -> Double {
        let calendar = Calendar.current
        let bedMinutes = minutesOfDay(bed, calendar)
        let wakeMinutes = minutesOfDay(wake, calendar)
        let delta = wakeMinutes >= bedMinutes
            ? wakeMinutes - bedMinutes
            : (24 * 60 - bedMinutes) + wakeMinutes
        return Double(delta) / 60
    }

    nonisolated private static func minutesOfDay(_ date: Date, _ calendar: Calendar) -> Int {
        let components = calendar.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }

    nonisolated private static func defaultTime(hour: Int, minute: Int) -> Date {
        Calendar.current.date(
            bySettingHour: hour, minute: minute, second: 0, of: Date()
        ) ?? Date()
    }
}
