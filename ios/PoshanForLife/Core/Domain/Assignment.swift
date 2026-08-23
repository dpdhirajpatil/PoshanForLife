import Foundation

/// The three things a practitioner can assign. Wire values are lowercase
/// (`CatalogueItemType`'s `@JsonValue` label), same convention as `ReportType`.
enum ServiceType: String, Decodable, CaseIterable {
    case programme, session, challenge

    var label: String {
        switch self {
        case .programme: return "Programme"
        case .session: return "Session"
        case .challenge: return "Challenge"
        }
    }

    /// Distinct silhouettes rather than three variations of a document icon —
    /// the type is the fastest way to read a row, so it has to survive a glance.
    var icon: String {
        switch self {
        case .programme: return "book.closed.fill"
        case .session: return "calendar.badge.clock"
        case .challenge: return "flame.fill"
        }
    }

    /// The plural URL segment (`CatalogueItemType.pathSegment` on the
    /// backend) — distinct from `rawValue`, which is the singular wire label
    /// used everywhere a service is *referenced* (e.g. `serviceType` on an
    /// assignment or lead-conversion request).
    var pathSegment: String {
        switch self {
        case .programme: return "programmes"
        case .session: return "sessions"
        case .challenge: return "challenges"
        }
    }
}

/// Assignment lifecycle — `PatientProgrammeStatus` on the wire, lowercase.
/// Note this is the *assignment's* status, which is not the same thing as
/// whether a session has happened yet: a session next month is still "active".
enum AssignmentStatus: String, Decodable {
    case active, completed, cancelled

    var label: String {
        switch self {
        case .active: return "Active"
        case .completed: return "Completed"
        case .cancelled: return "Cancelled"
        }
    }
}

/// Where a single-day session sits relative to today. Sessions are
/// point-in-time, so "60% elapsed" is meaningless for them — they're either
/// still ahead of you, happening today, or done.
enum SessionState {
    case upcoming(daysAway: Int)
    case today
    case past

    var label: String {
        switch self {
        case .today: return "Today"
        case .past: return "Completed"
        case .upcoming(let days):
            if days == 1 { return "Tomorrow" }
            return "In \(days) days"
        }
    }

    init(startDate: String?) {
        guard let day = LocalDay.date(from: startDate) else {
            self = .upcoming(daysAway: 0)
            return
        }
        let today = LocalDay.startOfToday()
        if day == today {
            self = .today
        } else if day < today {
            self = .past
        } else {
            self = .upcoming(daysAway: LocalDay.days(from: today, to: day))
        }
    }
}

/// Server-computed check-in progress for a challenge assignment.
///
/// `currentStreak` / `longestStreak` / `percentComplete` are all derived
/// backend-side from `lastLoggedDate` and never trusted from the client, so
/// there is nothing to recompute here.
struct ChallengeProgress: Decodable, Equatable {
    let currentStreak: Int
    let longestStreak: Int
    let lastLoggedDate: String?
    let percentComplete: Int

    /// Compared in the device's own time zone, because the backend stamps
    /// `lastLoggedDate` with `LocalDate.now()` — a local calendar day, not a
    /// UTC one. Formatting "today" as UTC would make the button flip back to
    /// "Check in today" during the evening for anyone east of Greenwich.
    var checkedInToday: Bool {
        guard let lastLoggedDate else { return false }
        return lastLoggedDate == LocalDay.todayString()
    }

    var fraction: Double { Double(max(0, min(percentComplete, 100))) / 100 }
}

/// Body for `PATCH …/progress`. Only `true` is accepted — the backend rejects
/// `false` with a validation error rather than treating it as an undo.
struct CheckInRequest: Encodable {
    let checkedInToday: Bool
}

/// Day-granularity helpers for the `yyyy-MM-dd` values the backend sends for
/// `startDate` / `endDate` / `lastLoggedDate`.
///
/// These are calendar days with no time zone attached, so they're parsed with a
/// fixed UTC calendar to keep the arithmetic stable, while "today" is taken
/// from the device's own calendar — that pairing is what makes "3 days left"
/// agree with what the user's phone says the date is.
enum LocalDay {
    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    private static var utcCalendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC") ?? .current
        return c
    }()

    static func date(from value: String?) -> Date? {
        guard let value else { return nil }
        return formatter.date(from: String(value.prefix(10)))
    }

    /// Today as a UTC-midnight `Date`, so it's directly comparable with
    /// anything `date(from:)` returned.
    static func startOfToday() -> Date {
        let now = Date()
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: now)
        return utcCalendar.date(from: parts) ?? now
    }

    /// The device's current calendar day as `yyyy-MM-dd`.
    static func todayString() -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        guard let day = utcCalendar.date(from: parts) else { return formatter.string(from: Date()) }
        return formatter.string(from: day)
    }

    static func days(from: Date, to: Date) -> Int {
        utcCalendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// "15 Jun 2026" — for detail rows, where the raw ISO string reads like a
    /// database dump.
    static func display(_ value: String?) -> String? {
        guard let date = date(from: value) else { return nil }
        let out = DateFormatter()
        out.dateFormat = "d MMM yyyy"
        out.locale = Locale.current
        out.timeZone = TimeZone(identifier: "UTC")
        return out.string(from: date)
    }
}
