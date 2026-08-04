import Foundation

/// A repeating local reminder. Purely on-device — no backend row, no push
/// token, nothing to sync. (IOS-08's FCM notifications are the opposite case.)
struct MedicationReminder: Codable, Identifiable, Equatable {
    let id: UUID
    var label: String
    var hour: Int
    var minute: Int
    /// 1 = Sunday … 7 = Saturday, matching `DateComponents.weekday`, which is
    /// what `UNCalendarNotificationTrigger` consumes. Empty means every day.
    var daysOfWeek: Set<Int>
    var enabled: Bool

    init(
        id: UUID = UUID(),
        label: String,
        hour: Int,
        minute: Int,
        daysOfWeek: Set<Int> = [],
        enabled: Bool = true
    ) {
        self.id = id
        self.label = label
        self.hour = hour
        self.minute = minute
        self.daysOfWeek = daysOfWeek
        self.enabled = enabled
    }

    var timeLabel: String {
        var components = DateComponents()
        components.hour = hour
        components.minute = minute
        let date = Calendar.current.date(from: components) ?? Date()
        return MedicationReminder.timeFormatter.string(from: date)
    }

    var daysLabel: String {
        guard !daysOfWeek.isEmpty else { return "Every day" }
        let symbols = Calendar.current.shortWeekdaySymbols
        return daysOfWeek.sorted()
            .compactMap { symbols.indices.contains($0 - 1) ? symbols[$0 - 1] : nil }
            .joined(separator: " ")
    }

    /// When this next fires, so a list of reminders can be ordered by
    /// imminence rather than by clock time (a 07:00 reminder is *tomorrow* if
    /// it's already 20:00 today, and so comes after tonight's 22:00 one).
    func nextOccurrence(after now: Date = Date(), calendar: Calendar = .current) -> Date? {
        var components = DateComponents()
        components.hour = hour
        components.minute = minute

        if daysOfWeek.isEmpty {
            return calendar.nextDate(after: now, matching: components, matchingPolicy: .nextTime)
        }
        return daysOfWeek.compactMap { weekday -> Date? in
            var dayComponents = components
            dayComponents.weekday = weekday
            return calendar.nextDate(after: now, matching: dayComponents, matchingPolicy: .nextTime)
        }.min()
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return f
    }()
}
