import Foundation
import UserNotifications

/// Owns the reminder list and its `UNUserNotificationCenter` registrations,
/// keeping the two in step: the stored list is the source of truth, and every
/// mutation re-syncs the scheduled notifications for that reminder.
@MainActor
final class ReminderScheduler: ObservableObject {

    @Published private(set) var reminders: [MedicationReminder] = []
    /// Nil until asked. Drives whether the UI shows an "allow notifications"
    /// prompt or the reminder list.
    @Published private(set) var authorizationStatus: UNAuthorizationStatus?

    private let store: JSONFileStore<MedicationReminder>
    private let center: UNUserNotificationCenter

    init(
        store: JSONFileStore<MedicationReminder> = JSONFileStore(filename: "medication-reminders.json"),
        center: UNUserNotificationCenter = .current()
    ) {
        self.store = store
        self.center = center
    }

    func load() async {
        reminders = await store.load()
        authorizationStatus = await center.notificationSettings().authorizationStatus
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        authorizationStatus = await center.notificationSettings().authorizationStatus
        if granted {
            // Anything added before permission was granted has no pending
            // notification — register them now rather than silently never firing.
            await rescheduleAll()
        }
        return granted
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorized || authorizationStatus == .provisional
    }

    // MARK: - Mutations

    func add(_ reminder: MedicationReminder) async {
        reminders.append(reminder)
        await persist()
        await schedule(reminder)
    }

    func update(_ reminder: MedicationReminder) async {
        guard let index = reminders.firstIndex(where: { $0.id == reminder.id }) else { return }
        reminders[index] = reminder
        await persist()
        cancel(reminder)
        await schedule(reminder)
    }

    func setEnabled(_ reminder: MedicationReminder, enabled: Bool) async {
        var updated = reminder
        updated.enabled = enabled
        await update(updated)
    }

    func delete(_ reminder: MedicationReminder) async {
        reminders.removeAll { $0.id == reminder.id }
        await persist()
        cancel(reminder)
    }

    // MARK: - Notification centre

    /// One request per selected weekday. `UNCalendarNotificationTrigger` takes a
    /// single `DateComponents`, so a Mon/Wed/Fri reminder is three repeating
    /// registrations rather than one — hence the indexed identifiers, which are
    /// what `cancel` matches on.
    private func schedule(_ reminder: MedicationReminder) async {
        guard reminder.enabled, isAuthorized else { return }

        let content = UNMutableNotificationContent()
        content.title = "Medication reminder"
        content.body = reminder.label
        content.sound = .default

        let weekdays: [Int?] = reminder.daysOfWeek.isEmpty
            ? [nil]                       // nil weekday = repeats daily
            : reminder.daysOfWeek.sorted()

        for weekday in weekdays {
            var components = DateComponents()
            components.hour = reminder.hour
            components.minute = reminder.minute
            components.weekday = weekday

            let request = UNNotificationRequest(
                identifier: Self.identifier(for: reminder, weekday: weekday),
                content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            )
            try? await center.add(request)
        }
    }

    private func cancel(_ reminder: MedicationReminder) {
        // Covers every weekday slot plus the daily one, so switching a reminder
        // from Mon/Wed to daily can't leave the old Wednesday firing.
        let identifiers = (1...7).map { Self.identifier(for: reminder, weekday: $0) }
            + [Self.identifier(for: reminder, weekday: nil)]
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    private func rescheduleAll() async {
        for reminder in reminders {
            cancel(reminder)
            await schedule(reminder)
        }
    }

    private static func identifier(for reminder: MedicationReminder, weekday: Int?) -> String {
        "medication.\(reminder.id.uuidString).\(weekday.map(String.init) ?? "daily")"
    }

    private func persist() async {
        await store.save(reminders)
    }
}
