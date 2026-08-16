import Foundation

protocol AppointmentsRepository: AnyObject {
    func appointments(from: Date?, status: AppointmentStatus?) async -> Result<[Appointment], APIError>
    func myPractitioners() async -> Result<[UserRef], APIError>
    func availableSlots(practitionerId: String, date: Date) async -> Result<[AvailableSlot], APIError>
    func book(practitionerId: String, at: Date, isVideo: Bool) async -> Result<Appointment, APIError>
    func reschedule(id: String, to: Date) async -> Result<Appointment, APIError>
    func cancel(id: String) async -> Result<Appointment, APIError>
    func complete(id: String) async -> Result<Appointment, APIError>
    func setNotes(id: String, notes: String) async -> Result<Appointment, APIError>
}

/// `/appointments` for both roles.
///
/// Scoping is entirely server-side and role-shaped: a PATIENT caller's
/// `patientId` is force-overridden to their own id, a DOCTOR's `practitionerId`
/// to theirs, so the same unfiltered GET returns "my appointments" for either.
/// Nothing here needs to pass an id to get that.
///
/// One inconsistency worth knowing: touching someone else's appointment is a
/// **403**, not the 404 that reports and programmes return for the same class
/// of mistake (`findAccessible` throws `AccessDeniedException`).
final class AppointmentsRepositoryImpl: AppointmentsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    func appointments(from: Date? = nil, status: AppointmentStatus? = nil) async -> Result<[Appointment], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "100")]
        if let from { items.append(URLQueryItem(name: "dateFrom", value: Self.dayFormatter.string(from: from))) }
        if let status { items.append(URLQueryItem(name: "status", value: status.rawValue)) }
        return await client.send(Endpoint(path: "appointments", method: .get, queryItems: items))
    }

    /// The pool a patient may book with — their *assigned* practitioners only.
    /// Booking with anyone else is rejected server-side, and there is no other
    /// endpoint that gives a patient this list, so the picker depends on it.
    func myPractitioners() async -> Result<[UserRef], APIError> {
        await client.send(Endpoint(path: "appointments/practitioners", method: .get))
    }

    /// `date` is sent as a plain calendar day. The slots that come back are
    /// **UTC** wall-clock times — see `AppointmentTime`.
    func availableSlots(practitionerId: String, date: Date) async -> Result<[AvailableSlot], APIError> {
        await client.send(
            Endpoint(
                path: "appointments/available-slots",
                method: .get,
                queryItems: [
                    URLQueryItem(name: "practitionerId", value: practitionerId),
                    URLQueryItem(name: "date", value: Self.dayFormatter.string(from: date)),
                ]
            )
        )
    }

    /// `patientId` is deliberately omitted: a PATIENT caller always books for
    /// themselves and any value sent is ignored.
    func book(practitionerId: String, at date: Date, isVideo: Bool) async -> Result<Appointment, APIError> {
        let body: [String: Any] = [
            "practitionerId": practitionerId,
            "scheduledAt": AppointmentTime.wire(date),
            "isVideo": isVideo,
        ]
        return await client.send(
            Endpoint(path: "appointments", method: .post, body: try? JSONSerialization.data(withJSONObject: body))
        )
    }

    func reschedule(id: String, to date: Date) async -> Result<Appointment, APIError> {
        await patch(id: id, body: ["scheduledAt": AppointmentTime.wire(date)])
    }

    /// The patient-facing "delete". A hard `DELETE` exists but is ADMIN-only.
    func cancel(id: String) async -> Result<Appointment, APIError> {
        await patch(id: id, body: ["status": AppointmentStatus.cancelled.rawValue])
    }

    /// DOCTOR/ADMIN only — a PATIENT caller gets `INSUFFICIENT_ROLE`.
    func complete(id: String) async -> Result<Appointment, APIError> {
        await patch(id: id, body: ["status": AppointmentStatus.completed.rawValue])
    }

    /// DOCTOR/ADMIN only. Sending a blank string clears the notes server-side.
    func setNotes(id: String, notes: String) async -> Result<Appointment, APIError> {
        await patch(id: id, body: ["notes": notes])
    }

    private func patch(id: String, body: [String: Any]) async -> Result<Appointment, APIError> {
        await client.send(
            Endpoint(
                path: "appointments/\(id)",
                method: .patch,
                body: try? JSONSerialization.data(withJSONObject: body)
            )
        )
    }
}
