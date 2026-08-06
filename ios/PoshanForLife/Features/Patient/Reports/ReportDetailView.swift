import SwiftUI

struct ReportDetailView: View {

    let reportId: String
    let repository: ReportsRepository

    @Environment(\.appTheme) private var theme
    @State private var state: CardState<ReportDetail> = .loading
    @State private var pdfURL: URL?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                switch state {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(height: 120, cornerRadius: 16)
                    }

                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))

                case .success(let report):
                    header(report)

                    if let data = report.parsedData {
                        ForEach(InBodyGroup.groups(for: data)) { group in
                            GroupCard(group: group)
                        }
                    } else {
                        Text(emptyMessage(for: report))
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }

                    if let fileUrl = report.fileUrl, let url = URL(string: fileUrl) {
                        Button {
                            pdfURL = url
                        } label: {
                            Label("View original PDF", systemImage: "doc.text")
                                .font(.displayFont(.semibold, size: 15))
                                .foregroundStyle(theme.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Report")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .sheet(item: $pdfURL) { url in
            SafariView(url: url).ignoresSafeArea()
        }
    }

    @ViewBuilder
    private func header(_ report: ReportDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(report.title)
                .font(.displayFont(.heavy, size: 22))
                .textCase(.uppercase)
                .foregroundStyle(theme.onSurface)

            HStack(spacing: 8) {
                if let date = report.createdAt {
                    Text(date, style: .date)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
                if let status = report.parsedStatus {
                    StatusBadge(status: status)
                }
            }

            // Confidence lives only on the detail response, never on the list —
            // showing it in the list would mean fetching every report's detail
            // just to draw a badge.
            if report.isLowConfidence {
                Label(
                    "Some values may be misread. Check against the original PDF.",
                    systemImage: "exclamationmark.triangle.fill"
                )
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onTertiary)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.tertiary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            if let data = report.parsedData {
                Text("\(data.populatedFieldCount) of \(InBodyGroup.totalFieldCount) values read from the scan")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }
        }
    }

    private func emptyMessage(for report: ReportDetail) -> String {
        switch report.parsedStatus {
        case .pending, .processing:
            return "This report is still being read. Check back shortly."
        case .error:
            return "We couldn't read this report automatically. The original PDF is still available."
        default:
            return "No values were extracted from this report."
        }
    }

    private func load() async {
        state = .loading
        switch await repository.report(id: reportId) {
        case .success(let report): state = .success(report)
        case .failure(let error): state = .failure(error.message)
        }
    }
}

/// `sheet(item:)` needs Identifiable; URL isn't.
extension URL: Identifiable {
    public var id: String { absoluteString }
}

private struct GroupCard: View {
    let group: InBodyGroup
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(group.title)
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)

            ForEach(Array(group.rows.enumerated()), id: \.offset) { _, row in
                HStack {
                    Text(row.label)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.75))
                    Spacer()
                    Text(row.value)
                        .font(.displayFont(.semibold, size: 15))
                        .foregroundStyle(theme.onSurface)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
