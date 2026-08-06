import SwiftUI

struct ReportsListView: View {

    @StateObject private var viewModel: ReportsViewModel
    @Environment(\.appTheme) private var theme

    /// Held alongside the view model so the pushed detail screen can be built
    /// without routing every call back through it.
    private let repository: ReportsRepository

    init(repository: ReportsRepository, profile: DashboardRepository) {
        self.repository = repository
        _viewModel = StateObject(
            wrappedValue: ReportsViewModel(repository: repository, profile: profile)
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TrendChartsView(viewModel: viewModel)

                Text("Past reports")
                    .font(.displayFont(.semibold, size: 20))
                    .foregroundStyle(theme.onSurface)

                switch viewModel.reports {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(height: 64, cornerRadius: 12)
                    }

                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))

                case .success(let reports):
                    if reports.isEmpty {
                        Text("No InBody reports yet. Your practitioner uploads these after a scan.")
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    } else {
                        ForEach(reports) { report in
                            NavigationLink(value: report.id) {
                                ReportRow(report: report)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Reports")
        .refreshable { await viewModel.load() }
        .task { await viewModel.load() }
        .navigationDestination(for: String.self) { reportId in
            ReportDetailView(reportId: reportId, repository: repository)
        }
    }
}

private struct ReportRow: View {
    let report: ReportListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(report.title)
                    .font(.bodyFont(size: 16))
                    .foregroundStyle(theme.onSurface)
                if let date = report.createdAt {
                    Text(date, style: .date)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
            }
            Spacer()
            if let status = report.parsedStatus {
                StatusBadge(status: status)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.onSurface.opacity(0.35))
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct StatusBadge: View {
    let status: ReportStatus
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(status.label)
            .font(.bodyFont(size: 11))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch status {
        case .done: return theme.onTertiary
        case .error: return theme.error
        default: return theme.onSurface.opacity(0.75)
        }
    }

    private var background: Color {
        switch status {
        case .done: return theme.tertiary
        case .error: return theme.error.opacity(0.15)
        default: return theme.onSurface.opacity(0.1)
        }
    }
}
