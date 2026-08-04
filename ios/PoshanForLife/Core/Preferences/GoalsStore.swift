import Combine
import Foundation

/// Daily targets the Track screen measures progress against.
struct Goals: Equatable {
    var stepsPerDay: Int = 8_000
    var waterMlPerDay: Int = 2_000
    var sleepHoursPerNight: Double = 8
    var targetWeightKg: Double?
}

/// `UserDefaults` is the right home for these: a handful of plain preference
/// values, no secrecy requirement, and they should survive reinstall-free
/// upgrades without ceremony. Tokens are the opposite case and live in the
/// Keychain — see ``KeychainTokenStore``.
@MainActor
final class GoalsStore: ObservableObject {

    @Published private(set) var goals: Goals

    private let defaults: UserDefaults

    private enum Key {
        static let steps = "goal.stepsPerDay"
        static let water = "goal.waterMlPerDay"
        static let sleep = "goal.sleepHoursPerNight"
        static let weight = "goal.targetWeightKg"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var loaded = Goals()
        // `object(forKey:)` rather than `integer(forKey:)`: the latter returns 0
        // for a missing key, which would silently replace the default goal with
        // an unreachable zero target.
        if let steps = defaults.object(forKey: Key.steps) as? Int { loaded.stepsPerDay = steps }
        if let water = defaults.object(forKey: Key.water) as? Int { loaded.waterMlPerDay = water }
        if let sleep = defaults.object(forKey: Key.sleep) as? Double { loaded.sleepHoursPerNight = sleep }
        loaded.targetWeightKg = defaults.object(forKey: Key.weight) as? Double
        self.goals = loaded
    }

    func update(_ goals: Goals) {
        self.goals = goals
        defaults.set(goals.stepsPerDay, forKey: Key.steps)
        defaults.set(goals.waterMlPerDay, forKey: Key.water)
        defaults.set(goals.sleepHoursPerNight, forKey: Key.sleep)
        if let weight = goals.targetWeightKg {
            defaults.set(weight, forKey: Key.weight)
        } else {
            defaults.removeObject(forKey: Key.weight)
        }
    }
}
