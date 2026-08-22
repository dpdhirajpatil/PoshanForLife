import Foundation

/// Wire values are lowercase. Note there are **four**, not the three the
/// IOS-07 prompt implies — `no_show` exists and a practitioner can set it.
enum AppointmentStatus: String, Decodable, CaseIterable {
    case scheduled
    case completed
    case cancelled
    case noShow = "no_show"

    var label: String {
        switch self {
        case .scheduled: return "Scheduled"
        case .completed: return "Completed"
        case .cancelled: return "Cancelled"
        case .noShow: return "No show"
        }
    }
}

struct Appointment: Decodable, Identifiable, Equatable, Hashable {
    let id: String
    let patient: UserRef?
    let practitioner: UserRef?
    let scheduledAtRaw: String?
    let durationMinutes: Int
    let status: String
    let notes: String?
    let isVideo: Bool
    let videoRoomId: String?

    private enum CodingKeys: String, CodingKey {
        case id, patient, practitioner, durationMinutes, status, notes, isVideo, videoRoomId
        case scheduledAtRaw = "scheduledAt"
    }

    /// A true instant. Everything user-facing renders it in the device's own
    /// time zone — see `AppointmentTime` for why that matters here.
    var scheduledAt: Date? { ISO8601.date(from: scheduledAtRaw) }
    var parsedStatus: AppointmentStatus? { AppointmentStatus(rawValue: status) }

    var isPast: Bool { (scheduledAt ?? .distantFuture) < Date() }

    /// Only a scheduled appointment can be rescheduled or cancelled.
    ///
    /// Cancelled ones are deliberately excluded from *both*: `PATCH` with a
    /// `scheduledAt` on a cancelled appointment silently flips it back to
    /// scheduled (verified against the backend), so offering "Reschedule" on a
    /// cancelled row would quietly un-cancel it.
    var isPatientEditable: Bool { parsedStatus == .scheduled && !isPast }

    /// IOS-13's join window: a video appointment's "Join call" is live from 5
    /// minutes before its scheduled time until 30 minutes after. Kept in one
    /// place so the patient's row and the practitioner's agree exactly — a
    /// call one side can join and the other can't is the obvious failure mode
    /// here. Mirrors Android's `canJoinVideoCall`.
    private static let joinOpensBefore: TimeInterval = 5 * 60
    private static let joinClosesAfter: TimeInterval = 30 * 60

    func canJoinVideoCall(now: Date = Date()) -> Bool {
        guard isVideo, parsedStatus == .scheduled, let startsAt = scheduledAt else { return false }
        return now >= startsAt.addingTimeInterval(-Self.joinOpensBefore)
            && now <= startsAt.addingTimeInterval(Self.joinClosesAfter)
    }
}

/// One bookable slot. `time` is a wall-clock time **in UTC**, serialised as
/// `"HH:mm:ss"` — despite `AvailableSlotDto`'s comment claiming `"HH:mm"`.
struct AvailableSlot: Decodable, Identifiable, Equatable {
    let time: String
    let available: Bool

    var id: String { time }
}

/// Converts between the backend's UTC wall-clock slot times and real instants,
/// and formats everything for display in the device's own time zone.
///
/// **The trap this exists for:** `AppointmentService` hardcodes working hours
/// as 09:00–17:00 and builds every slot with `ZoneOffset.UTC`, so a slot
/// labelled `09:00:00` is 09:00 *UTC*. Rendering that string as-is while the
/// appointment list renders `scheduledAt` in local time makes the app
/// contradict itself: you pick "09:00" and the booking then shows as 2:30 PM
/// in IST. (The Android client does exactly this today — `BookAppointmentScreen`
/// prints the raw UTC `LocalTime` while `AppointmentsListScreen` converts to
/// `ZoneId.systemDefault()`.)
///
/// So slot times are converted to an instant first and formatted locally
/// everywhere, which keeps the picker and the list agreeing. The side effect
/// worth knowing: in IST the bookable window reads 2:30 PM–10:00 PM, because
/// that is genuinely what 09:00–17:00 UTC is.
enum AppointmentTime {

    private static let utc = TimeZone(identifier: "UTC") ?? .current

    private static let slotParser: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = utc
        return f
    }()

    /// The instant a slot on `date` refers to. `date` is a local calendar day
    /// as chosen in the DatePicker; the slot's clock time is UTC.
    static func instant(forSlot slot: AvailableSlot, on date: Date) -> Date? {
        guard let parsed = slotParser.date(from: slot.time) else { return nil }

        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = utc

        let day = Calendar.current.dateComponents([.year, .month, .day], from: date)
        let clock = utcCalendar.dateComponents([.hour, .minute], from: parsed)

        var combined = DateComponents()
        combined.year = day.year
        combined.month = day.month
        combined.day = day.day
        combined.hour = clock.hour
        combined.minute = clock.minute
        return utcCalendar.date(from: combined)
    }

    /// `scheduledAt` for the create/update body — the backend rejects a
    /// non-`Z` offset shape less predictably than it accepts this one.
    static func wire(_ date: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        f.timeZone = utc
        return f.string(from: date)
    }

    // MARK: - Display (always the device's own time zone)

    static func time(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return f.string(from: date)
    }

    /// "Today" / "Tomorrow" / "Fri 14 Aug 2026" — the section header.
    static func dayHeader(_ date: Date) -> String {
        let cal = Calendar.current
        if cal.isDateInToday(date) { return "Today" }
        if cal.isDateInTomorrow(date) { return "Tomorrow" }
        if cal.isDateInYesterday(date) { return "Yesterday" }
        let f = DateFormatter()
        f.dateFormat = "EEE d MMM yyyy"
        return f.string(from: date)
    }

    /// Local calendar day, used to group rows into sections.
    static func day(_ date: Date) -> Date {
        Calendar.current.startOfDay(for: date)
    }
}

/// A day's worth of appointments, for a `List` section.
struct AppointmentDay: Identifiable {
    let day: Date
    let appointments: [Appointment]

    var id: Date { day }
    var header: String { AppointmentTime.dayHeader(day) }
}
