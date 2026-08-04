import Foundation

/// Elapsed fraction and days left for a programme, from its ISO `yyyy-MM-dd`
/// bounds. Mirrors the Android dashboard's `programmeProgress`.
struct ProgrammeProgress {
    let fraction: Double
    let daysLeft: Int?

    init(startDate: String?, endDate: String?) {
        guard let start = Self.parse(startDate),
              let end = Self.parse(endDate),
              end >= start
        else {
            self.fraction = 0
            self.daysLeft = nil
            return
        }

        let today = Calendar.current.startOfDay(for: Date())
        let total = max(Self.days(from: start, to: end), 1)
        let elapsed = min(max(Self.days(from: start, to: today), 0), total)

        self.fraction = Double(elapsed) / Double(total)
        self.daysLeft = max(Self.days(from: today, to: end), 0)
    }

    var remainingLabel: String {
        guard let daysLeft else { return "Ongoing" }
        return daysLeft == 1 ? "1 day remaining" : "\(daysLeft) days remaining"
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        // POSIX locale and UTC so parsing doesn't shift by a day for users in
        // negative-offset time zones.
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    private static func parse(_ value: String?) -> Date? {
        guard let value else { return nil }
        return formatter.date(from: String(value.prefix(10)))
    }

    private static func days(from: Date, to: Date) -> Int {
        Calendar.current.dateComponents([.day], from: from, to: to).day ?? 0
    }
}
