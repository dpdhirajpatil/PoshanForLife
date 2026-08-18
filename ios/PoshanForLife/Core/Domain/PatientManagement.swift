import Foundation

/// Row shape for `GET /patients` — a practitioner's own caseload, scoped
/// server-side (`PatientService.list` hard-limits a DOCTOR caller to their
/// assigned patients no matter what's asked for; verified live). `lastReportDate`
/// is nil until the patient's first report exists.
struct PatientSummary: Decodable, Equatable, Hashable, Identifiable {
    let id: String
    let name: String
    let email: String?
    let phone: String?
    let dateOfBirth: String?
    let isActive: Bool
    let assignedDoctors: [UserRef]
    let lastReportDateRaw: String?

    var lastReportDate: Date? { ISO8601.date(from: lastReportDateRaw) }
    var age: Int? { PatientAge.years(from: dateOfBirth) }

    private enum CodingKeys: String, CodingKey {
        case id, name, email, phone, dateOfBirth, isActive, assignedDoctors
        case lastReportDateRaw = "lastReportDate"
    }
}

/// Full record for `GET /patients/{id}` / `PATCH /patients/{id}`.
///
/// `doctorNotes` is practitioner-only clinical notes — the UI label reads
/// "Notes" / "Practitioner notes", but the field name stays `doctorNotes` on
/// the wire and in the DB end to end (matches the backend DTO and Android
/// verbatim; only the label translates, same pattern as `UserRole.doctor`).
///
/// `healthRecords` arrives **newest first** here — the backend explicitly
/// reverses its usual oldest-first order for this embedded list (verified
/// live), so `.first` is the latest reading. That's the opposite of
/// `ReportsRepository.trendRecords`, where the newest is `.last` — the two
/// endpoints disagree on order and both are correct for their own contract.
///
/// `reports` is deliberately not modeled: the backend always sends `[]` for it
/// ("stays empty until the reports feature prompt lands", per its own doc
/// comment), so reports are fetched separately via
/// `ReportsRepository.inbodyReports(patientId:)` — the same call the patient's
/// own Reports tab makes.
struct PatientDetail: Decodable, Equatable, Identifiable {
    let id: String
    let name: String
    let email: String?
    let phone: String?
    let dateOfBirth: String?
    let isActive: Bool
    let gender: String?
    let bloodGroup: String?
    let heightCm: Double?
    let emergencyContact: String?
    let medicalHistory: String?
    let doctorNotes: String?
    let assignedDoctors: [UserRef]
    let healthRecords: [HealthRecord]

    var age: Int? { PatientAge.years(from: dateOfBirth) }
    var latestVitals: HealthRecord? { healthRecords.first }
}

enum PatientAge {
    /// Whole years between a `yyyy-MM-dd` date of birth and today. Uses the
    /// device calendar for "today", same as Android's `Period.between` — the
    /// day-level imprecision from parsing `dateOfBirth` at UTC midnight can
    /// only ever be off by a day right at a birthday, never off by a year.
    static func years(from dateOfBirth: String?) -> Int? {
        guard let dob = ISO8601.date(from: dateOfBirth) else { return nil }
        return Calendar.current.dateComponents([.year], from: dob, to: Date()).year
    }
}
