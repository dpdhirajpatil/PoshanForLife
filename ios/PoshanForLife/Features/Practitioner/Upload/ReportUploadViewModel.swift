import Foundation
import UIKit

enum ReportCaptureState: Equatable {
    case ready
    case captured(URL)
    case uploading
    case uploadError(String)
}

enum ReportConfirmState: Equatable {
    case idle, saving, saved
    case failure(String)
}

enum ReportConfidenceTier {
    case high, medium, low
}

/// Thresholds scaled to this app's real 20-field schema — mirrors Android's
/// `confidenceTierFor`, which adapted the original Expo/web spec's raw
/// ">30 / 15-30 / <15" field-count thresholds (tuned for a much larger field
/// set that doesn't exist in this backend) into fractions of
/// `InBodyGroup.totalFieldCount`.
func confidenceTierFor(_ extractedFieldCount: Int) -> ReportConfidenceTier {
    let total = Double(InBodyGroup.totalFieldCount)
    if Double(extractedFieldCount) > total * 0.7 { return .high }
    if Double(extractedFieldCount) >= total * 0.35 { return .medium }
    return .low
}

/// Backs both `CaptureView` and `ReviewView` — one instance per capture
/// session, created with a `patientId` and carried across the push from
/// capture to review so the review screen never has to re-fetch what the
/// upload response already returned.
@MainActor
final class ReportUploadViewModel: ObservableObject {

    let patientId: String
    private let repository: ReportsRepository

    @Published private(set) var captureState: ReportCaptureState = .ready
    /// Set once upload+extraction succeeds — `CaptureView` treats a non-nil
    /// id as "push to review".
    @Published private(set) var reportId: String?
    @Published private(set) var extractedFieldCount: Int = 0
    @Published var editedData = InBodyData()
    @Published var title: String = ""
    @Published var notes: String = ""
    @Published private(set) var confirmState: ReportConfirmState = .idle

    /// Survives the `.uploading` / `.uploadError` states (unlike
    /// `.captured`'s associated URL) so a failed upload can be retried
    /// without re-capturing.
    private var pendingFileURL: URL?

    var confidenceTier: ReportConfidenceTier { confidenceTierFor(extractedFieldCount) }

    init(patientId: String, repository: ReportsRepository) {
        self.patientId = patientId
        self.repository = repository
    }

    func onCaptured(_ url: URL) {
        pendingFileURL = url
        captureState = .captured(url)
    }

    /// Also used to leave the review screen from a low-confidence banner's
    /// "Retake photo" button — nilling `reportId` is what pops `ReviewView`,
    /// since `CaptureView` pushes it via `isPresented: reportId != nil`.
    func retake() {
        pendingFileURL = nil
        reportId = nil
        extractedFieldCount = 0
        editedData = InBodyData()
        confirmState = .idle
        captureState = .ready
    }

    func usePhoto() async {
        guard let url = pendingFileURL,
              let data = try? Data(contentsOf: url),
              let image = UIImage(data: data) else {
            captureState = .uploadError("Couldn't read the captured photo.")
            return
        }
        captureState = .uploading
        guard let pdfData = ImageToPDF.wrap(image) else {
            captureState = .uploadError("Couldn't prepare this photo for upload.")
            return
        }
        switch await repository.uploadReport(patientId: patientId, pdfData: pdfData) {
        case .success(let response):
            reportId = response.reportId
            editedData = response.parsedData ?? InBodyData()
            extractedFieldCount = response.extractedFieldCount
            title = "InBody Report – \(Self.titleDateFormatter.string(from: Date()))"
            captureState = .ready
        case .failure(let error):
            captureState = .uploadError(error.message)
        }
    }

    func onFieldChange(_ key: String, value: Double?) {
        editedData.setValue(value, for: key)
    }

    @discardableResult
    func confirm() async -> Bool {
        guard let reportId else { return false }
        confirmState = .saving
        let trimmedNotes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        switch await repository.updateReport(
            id: reportId,
            title: title,
            notes: trimmedNotes.isEmpty ? nil : trimmedNotes,
            parsedData: editedData
        ) {
        case .success:
            confirmState = .saved
            return true
        case .failure(let error):
            confirmState = .failure(error.message)
            return false
        }
    }

    private static let titleDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter
    }()
}
