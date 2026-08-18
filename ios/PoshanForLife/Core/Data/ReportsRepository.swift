import Foundation

protocol ReportsRepository: AnyObject {
    func inbodyReports(patientId: String) async -> Result<[ReportListItem], APIError>
    func report(id: String) async -> Result<ReportDetail, APIError>
    func trendRecords(patientId: String) async -> Result<[HealthRecord], APIError>
    func uploadReport(patientId: String, pdfData: Data) async -> Result<ReportUploadResponse, APIError>
    func updateReport(id: String, title: String, notes: String?, parsedData: InBodyData) async -> Result<ReportDetail, APIError>
}

final class ReportsRepositoryImpl: ReportsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// For a PATIENT caller the backend replaces `patientId` with the caller's
    /// own id regardless of what's sent, so this can't read anyone else's
    /// reports. The parameter is kept so a staff-role screen can reuse this.
    func inbodyReports(patientId: String) async -> Result<[ReportListItem], APIError> {
        let result: Result<ReportListResponse, APIError> = await client.send(
            Endpoint(
                path: "reports",
                method: .get,
                queryItems: [
                    URLQueryItem(name: "patientId", value: patientId),
                    URLQueryItem(name: "type", value: ReportType.inbodyWire),
                    URLQueryItem(name: "limit", value: "50"),
                ]
            )
        )
        return result.map(\.reports)
    }

    func report(id: String) async -> Result<ReportDetail, APIError> {
        await client.send(Endpoint(path: "reports/\(id)", method: .get))
    }

    /// `fields` trims the payload to the four charted metrics — and the names
    /// must be exactly `weight` / `bodyFat` / `bmi` / `skeletalMuscleMass`.
    /// An unrecognised name is **silently dropped**, not rejected, and every
    /// metric not named comes back null, so a typo here yields empty charts
    /// with no error to explain them.
    func trendRecords(patientId: String) async -> Result<[HealthRecord], APIError> {
        await client.send(
            Endpoint(
                path: "health-records/\(patientId)",
                method: .get,
                queryItems: [
                    URLQueryItem(name: "limit", value: "180"),
                    URLQueryItem(
                        name: "fields",
                        value: TrendMetric.allCases.map(\.wireField).joined(separator: ",")
                    ),
                ]
            )
        )
    }

    /// The backend strictly requires `application/pdf` — see `ImageToPDF`
    /// for why the caller has already wrapped the captured photo before
    /// this ever gets called.
    func uploadReport(patientId: String, pdfData: Data) async -> Result<ReportUploadResponse, APIError> {
        await client.send(
            Endpoint.multipart(
                path: "reports/upload",
                fields: ["patientId": patientId],
                fileField: "file",
                fileName: "inbody-report.pdf",
                fileData: pdfData,
                fileContentType: "application/pdf"
            )
        )
    }

    func updateReport(id: String, title: String, notes: String?, parsedData: InBodyData) async -> Result<ReportDetail, APIError> {
        do {
            let endpoint = try Endpoint.json(
                path: "reports/\(id)",
                method: .patch,
                body: UpdateReportRequest(title: title, notes: notes, parsedData: parsedData)
            )
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }
}
