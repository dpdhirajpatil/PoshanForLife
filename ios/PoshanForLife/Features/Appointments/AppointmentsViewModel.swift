import Foundation

/// Which side of the appointment the current user is on. The API is the same
/// for both — the backend scopes by role — so this only decides what the UI
/// offers and which name each row shows.
enum AppointmentsRole {
    case patient
    case practitioner
}

@MainActor
final class AppointmentsViewModel: ObservableObject {

    @Published private(set) var days: CardState<[AppointmentDay]> = .loading
    /// Set when an action fails, so a failed cancel says why instead of
    /// silently leaving the row untouched.
    @Published var actionError: String?
    @Published private(set) var busyId: String?

    /// Past appointments are hidden by default: the useful screen is "what's
    /// coming up", and a completed history pushes it below the fold.
    @Published var showsPast = false {
        didSet { rebuild() }
    }

    let role: AppointmentsRole
    private let repository: AppointmentsRepository
    private var all: [Appointment] = []

    init(role: AppointmentsRole, repository: AppointmentsRepository) {
        self.role = role
        self.repository = repository
    }

    func load() async {
        switch await repository.appointments(from: nil, status: nil) {
        case .success(let appointments):
            all = appointments
            rebuild()
        case .failure(let error):
            days = .failure(error.message)
        }
    }

    /// Groups into local calendar days. The backend already sorts by
    /// `scheduledAt` ascending, so within a day the order is already right.
    private func rebuild() {
        let visible = all.filter { appointment in
            guard let date = appointment.scheduledAt else { return false }
            return showsPast || date >= Calendar.current.startOfDay(for: Date())
        }

        let grouped = Dictionary(grouping: visible) { appointment in
            AppointmentTime.day(appointment.scheduledAt ?? .distantPast)
        }
        days = .success(
            grouped.keys.sorted().map { day in
                AppointmentDay(
                    day: day,
                    appointments: (grouped[day] ?? []).sorted {
                        ($0.scheduledAt ?? .distantPast) < ($1.scheduledAt ?? .distantPast)
                    }
                )
            }
        )
    }

    // MARK: - Actions

    func cancel(_ appointment: Appointment) async {
        await perform(appointment) { await self.repository.cancel(id: appointment.id) }
    }

    func reschedule(_ appointment: Appointment, to date: Date) async {
        await perform(appointment) { await self.repository.reschedule(id: appointment.id, to: date) }
    }

    func complete(_ appointment: Appointment) async {
        await perform(appointment) { await self.repository.complete(id: appointment.id) }
    }

    func saveNotes(_ appointment: Appointment, notes: String) async {
        await perform(appointment) { await self.repository.setNotes(id: appointment.id, notes: notes) }
    }

    /// Replaces the one changed appointment in place rather than refetching the
    /// list — the PATCH response is the updated appointment, so a second round
    /// trip would only re-fetch what we already hold.
    private func perform(
        _ appointment: Appointment,
        _ action: @escaping () async -> Result<Appointment, APIError>
    ) async {
        busyId = appointment.id
        actionError = nil

        switch await action() {
        case .success(let updated):
            if let index = all.firstIndex(where: { $0.id == updated.id }) {
                all[index] = updated
            }
            rebuild()
        case .failure(let error):
            actionError = error.message
        }

        busyId = nil
    }

    func appointment(withId id: String) -> Appointment? {
        all.first { $0.id == id }
    }
}

/// Booking is a separate flow with its own state, so it gets its own model
/// rather than swelling the list's.
@MainActor
final class BookAppointmentViewModel: ObservableObject {

    @Published private(set) var practitioners: CardState<[UserRef]> = .loading
    @Published var selectedPractitionerId: String?
    @Published var date = Date() {
        didSet {
            if !Calendar.current.isDate(date, inSameDayAs: oldValue) {
                Task { await loadSlots() }
            }
        }
    }
    @Published private(set) var slots: CardState<[AvailableSlot]> = .loading
    @Published var selectedSlot: AvailableSlot?
    @Published var isVideo = false
    @Published private(set) var isBooking = false
    @Published var errorMessage: String?
    @Published private(set) var booked: Appointment?

    private let repository: AppointmentsRepository

    init(repository: AppointmentsRepository) {
        self.repository = repository
    }

    func load() async {
        practitioners = .loading
        switch await repository.myPractitioners() {
        case .success(let list):
            practitioners = .success(list)
            // One assigned practitioner is the common case — preselecting it
            // removes a pointless tap.
            if selectedPractitionerId == nil { selectedPractitionerId = list.first?.id }
            await loadSlots()
        case .failure(let error):
            practitioners = .failure(error.message)
        }
    }

    func loadSlots() async {
        guard let practitionerId = selectedPractitionerId else {
            slots = .success([])
            return
        }
        selectedSlot = nil
        slots = .loading
        switch await repository.availableSlots(practitionerId: practitionerId, date: date) {
        case .success(let list): slots = .success(list)
        case .failure(let error): slots = .failure(error.message)
        }
    }

    var canBook: Bool { selectedPractitionerId != nil && selectedSlot != nil && !isBooking }

    func book() async {
        guard let practitionerId = selectedPractitionerId,
              let slot = selectedSlot,
              let at = AppointmentTime.instant(forSlot: slot, on: date)
        else { return }

        isBooking = true
        errorMessage = nil

        switch await repository.book(practitionerId: practitionerId, at: at, isVideo: isVideo) {
        case .success(let appointment):
            booked = appointment
        case .failure(let error):
            // The likely one is "This slot is no longer available" — someone
            // took it between the slot list loading and this tap. Refresh so
            // the taken slot visibly greys out rather than staying selectable.
            errorMessage = error.message
            await loadSlots()
        }

        isBooking = false
    }
}
