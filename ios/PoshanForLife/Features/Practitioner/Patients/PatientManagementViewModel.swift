import Foundation

/// Backs both `PatientListView` and `PatientDetailView` — one class, mode
/// decided by whether a `patientId` was given, the same shape as Android's
/// `PatientManagementViewModel` for this exact feature. A list screen builds
/// one with `patientId: nil`; pushing to detail builds a fresh instance for
/// the tapped patient.
@MainActor
final class PatientManagementViewModel: ObservableObject {

    enum NotesSaveState: Equatable {
        case idle, saving, saved
        case failure(String)
    }

    let patientId: String?

    // MARK: List

    @Published private(set) var listState: CardState<[PatientSummary]> = .loading
    @Published var searchQuery: String = "" {
        didSet { scheduleSearch() }
    }

    // MARK: Detail

    @Published private(set) var detailState: CardState<PatientDetail> = .loading
    @Published private(set) var reportsState: CardState<[ReportListItem]> = .loading
    @Published private(set) var programmesState: CardState<[PatientProgramme]> = .loading
    @Published var notesText: String = ""
    @Published private(set) var notesSaveState: NotesSaveState = .idle

    private let patientsRepository: PatientsRepository
    private let reportsRepository: ReportsRepository
    private let programmesRepository: ProgrammesRepository
    private var searchTask: Task<Void, Never>?

    init(
        patientId: String? = nil,
        patientsRepository: PatientsRepository,
        reportsRepository: ReportsRepository,
        programmesRepository: ProgrammesRepository
    ) {
        self.patientId = patientId
        self.patientsRepository = patientsRepository
        self.reportsRepository = reportsRepository
        self.programmesRepository = programmesRepository
    }

    // MARK: - List

    func loadList() async {
        listState = .loading
        await performListLoad()
    }

    func refreshList() async {
        await performListLoad()
    }

    /// Debounced: cancels any in-flight search so a slow response to a stale
    /// keystroke can't land after — and overwrite — a newer one. Mirrors
    /// Android's 300ms debounce on the same screen.
    private func scheduleSearch() {
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await self?.performListLoad()
        }
    }

    private func performListLoad() async {
        let result = await patientsRepository.list(search: searchQuery)
        listState = result.cardState
    }

    // MARK: - Detail

    func loadDetail() async {
        guard let patientId else { return }
        detailState = .loading
        let result = await patientsRepository.detail(id: patientId)
        detailState = result.cardState
        if case .success(let patient) = result {
            notesText = patient.doctorNotes ?? ""
        }

        async let reportsResult = reportsRepository.inbodyReports(patientId: patientId)
        async let programmesResult = programmesRepository.assignments(patientId: patientId)
        reportsState = (await reportsResult).cardState
        programmesState = (await programmesResult).cardState
    }

    func onNotesChange(_ text: String) {
        notesText = text
        // Editing after a save/error invalidates that indicator — back to
        // idle until the next save.
        notesSaveState = .idle
    }

    func saveNotes() async {
        guard let patientId else { return }
        notesSaveState = .saving
        switch await patientsRepository.updateNotes(id: patientId, doctorNotes: notesText) {
        case .success:
            notesSaveState = .saved
        case .failure(let error):
            notesSaveState = .failure(error.message)
        }
    }
}

private extension Result where Failure == APIError {
    var cardState: CardState<Success> {
        switch self {
        case .success(let value): return .success(value)
        case .failure(let error): return .failure(error.message)
        }
    }
}
