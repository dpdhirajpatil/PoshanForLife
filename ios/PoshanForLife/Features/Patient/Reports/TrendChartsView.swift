import Charts
import SwiftUI

/// Trend lines over the patient's health records. Swift Charts is native from
/// iOS 16, so this needs no dependency.
struct TrendChartsView: View {

    @ObservedObject var viewModel: ReportsViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Picker("Window", selection: $viewModel.window) {
                ForEach(TrendWindow.allCases) { window in
                    Text(window.rawValue).tag(window)
                }
            }
            .pickerStyle(.segmented)

            switch viewModel.records {
            case .loading:
                ForEach(0..<2, id: \.self) { _ in
                    SkeletonBlock(height: 160, cornerRadius: 16)
                }

            case .failure(let message):
                Text(message)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))

            case .success:
                let metrics = viewModel.plottableMetrics()
                if metrics.isEmpty {
                    // Distinguishes "nothing recorded yet" from "request failed"
                    // — an empty chart frame alone reads as a bug.
                    Text("Not enough readings in this window to chart a trend yet.")
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                } else {
                    ForEach(metrics) { metric in
                        MetricChartCard(metric: metric, records: viewModel.windowedRecords())
                    }
                }
            }
        }
    }
}

private struct MetricChartCard: View {
    let metric: TrendMetric
    let records: [HealthRecord]

    @Environment(\.appTheme) private var theme
    @State private var selectedID: String?

    private var points: [(id: String, date: Date, value: Double, delta: Double?)] {
        records.compactMap { record in
            guard let date = record.date, let value = record.value(for: metric) else { return nil }
            return (record.id, date, value, record.delta(for: metric))
        }
    }

    private var selected: (id: String, date: Date, value: Double, delta: Double?)? {
        guard let selectedID else { return points.last }
        return points.first { $0.id == selectedID } ?? points.last
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(metric.title)
                    .font(.displayFont(.semibold, size: 17))
                    .foregroundStyle(theme.onSurface)
                Spacer()
                if let selected {
                    Text(Self.format(selected.value) + (metric.unit.isEmpty ? "" : " \(metric.unit)"))
                        .font(.displayFont(.heavy, size: 17))
                        .foregroundStyle(theme.onSurface)
                    DeltaBadge(delta: selected.delta, metric: metric)
                }
            }

            if let selected {
                Text(selected.date, style: .date)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            Chart {
                ForEach(points, id: \.id) { point in
                    LineMark(
                        x: .value("Date", point.date),
                        y: .value(metric.title, point.value)
                    )
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(theme.primary)

                    PointMark(
                        x: .value("Date", point.date),
                        y: .value(metric.title, point.value)
                    )
                    .foregroundStyle(theme.primary)
                    .symbolSize(point.id == selected?.id ? 90 : 24)
                }

                if let selected {
                    RuleMark(x: .value("Date", selected.date))
                        .foregroundStyle(theme.onSurface.opacity(0.25))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))
                }
            }
            // Never auto-scale from zero: body metrics move by a few percent, and
            // a zero-based axis flattens a real trend into a straight line.
            .chartYScale(domain: .automatic(includesZero: false))
            .chartXAxis { AxisMarks(values: .automatic(desiredCount: 4)) }
            .frame(height: 150)
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { drag in select(at: drag.location, proxy: proxy, geometry: geometry) }
                        )
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// Maps the touch x to the nearest data point, rather than requiring the
    /// user to hit a point exactly.
    private func select(at location: CGPoint, proxy: ChartProxy, geometry: GeometryProxy) {
        // `proxy.plotFrame` is iOS 17+; this app targets 16, so it uses the
        // older `plotAreaFrame` anchor. Swap when the minimum target rises.
        let plotOrigin = geometry[proxy.plotAreaFrame].origin
        let xInPlot = location.x - plotOrigin.x
        guard let touchedDate: Date = proxy.value(atX: xInPlot) else { return }

        let nearest = points.min {
            abs($0.date.timeIntervalSince(touchedDate)) < abs($1.date.timeIntervalSince(touchedDate))
        }
        selectedID = nearest?.id
    }

    static func format(_ value: Double) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }
}

/// Change since the previous reading, coloured by whether it's the direction
/// the patient wants — down for weight/fat, up for muscle.
private struct DeltaBadge: View {
    let delta: Double?
    let metric: TrendMetric

    @Environment(\.appTheme) private var theme

    var body: some View {
        if let delta, abs(delta) > 0.0001 {
            let improving = metric.lowerIsBetter ? delta < 0 : delta > 0
            Text("\(delta > 0 ? "+" : "")\(MetricChartCard.format(delta))")
                .font(.bodyFont(size: 12))
                .foregroundStyle(improving ? theme.onTertiary : theme.error)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(
                    (improving ? theme.tertiary : theme.error.opacity(0.15)),
                    in: Capsule()
                )
        }
    }
}
