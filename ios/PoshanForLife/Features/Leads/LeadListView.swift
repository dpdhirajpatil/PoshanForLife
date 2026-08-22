import SwiftUI

/// Reused unmodified for Practitioner and Admin — `GET /leads` is scoped
/// server-side (DOCTOR sees only their own, ADMIN sees all), so there's no
/// role param here at all, same shape as Android reusing one screen across
/// both nav graphs.
struct LeadListView: View {

    @StateObject private var viewModel: LeadsViewModel
    @Environment(\.appTheme) private var theme

    private let repository: LeadsRepository

    init(repository: LeadsRepository) {
        self.repository = repository
        _viewModel = StateObject(wrappedValue: LeadsViewModel(repository: repository))
    }

    var body: some View {
        VStack(spacing: 0) {
            stageChips

            switch viewModel.listState {
            case .loading:
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(0..<5, id: \.self) { _ in SkeletonBlock(height: 76, cornerRadius: 12) }
                    }
                    .padding(16)
                }

            case .failure(let message):
                VStack(spacing: 8) {
                    Spacer(minLength: 0)
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                        .multilineTextAlignment(.center)
                    Spacer(minLength: 0)
                }
                .padding(24)

            case .success(let leads):
                List {
                    if let summary = viewModel.summary {
                        SummaryStatRow(summary: summary)
                            .listRowInsets(EdgeInsets())
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    }

                    if leads.isEmpty {
                        EmptyLeadsView()
                            .listRowInsets(EdgeInsets())
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    } else {
                        ForEach(leads) { lead in
                            NavigationLink(value: lead) {
                                LeadRow(lead: lead)
                            }
                            .listRowBackground(theme.surface)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await viewModel.refreshList() }
            }
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Leads")
        .searchable(text: $viewModel.searchQuery, prompt: "Search leads")
        .task { await viewModel.loadList() }
        .navigationDestination(for: LeadListItem.self) { lead in
            LeadDetailView(leadId: lead.id, leadName: lead.name, repository: repository)
        }
    }

    private var stageChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                StageChip(label: "All", isSelected: viewModel.stageFilter == nil) {
                    viewModel.stageFilter = nil
                }
                ForEach(LeadStage.allCases) { stage in
                    StageChip(label: stage.label, isSelected: viewModel.stageFilter == stage) {
                        viewModel.stageFilter = stage
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }
}

// MARK: - Stage chip

private struct StageChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.displayFont(.semibold, size: 13))
                .foregroundStyle(isSelected ? theme.onPrimary : theme.onSurface.opacity(0.75))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? theme.primary : theme.surface, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("stage-chip-\(label)")
    }
}

// MARK: - Summary

private struct SummaryStatRow: View {
    let summary: LeadSummary
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                StatCard(label: "Follow-ups today", value: "\(summary.followupToday)")
                StatCard(label: "Conversion rate", value: "\(Int(summary.conversionRate))%")
                StatCard(label: "New this week", value: "\(summary.newThisWeek)")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 4)
        }
    }
}

private struct StatCard: View {
    let label: String
    let value: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.displayFont(.heavy, size: 20))
                .foregroundStyle(theme.onSurface)
            Text(label)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.7))
        }
        .frame(width: 128, alignment: .leading)
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Row

private struct LeadRow: View {
    let lead: LeadListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(lead.name)
                    .font(.displayFont(.semibold, size: 16))
                    .foregroundStyle(theme.onSurface)

                Text(lead.phone ?? lead.email ?? "No contact info")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))

                if let programme = lead.interestedProgramme?.name {
                    Text(programme)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.55))
                }
            }

            Spacer(minLength: 8)

            StageBadge(stage: lead.stage)
        }
        .padding(.vertical, 6)
    }
}

struct StageBadge: View {
    let stage: LeadStage
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(stage.label)
            .font(.bodyFont(size: 11))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch stage {
        case .converted: return theme.onPrimary
        case .lost: return theme.error
        case .qualified, .proposed: return theme.onTertiary
        case .new, .contacted: return theme.onSurface.opacity(0.75)
        }
    }

    private var background: Color {
        switch stage {
        case .converted: return theme.primary
        case .lost: return theme.error.opacity(0.15)
        case .qualified, .proposed: return theme.tertiary
        case .new, .contacted: return theme.onSurface.opacity(0.1)
        }
    }
}

// MARK: - Empty state

private struct EmptyLeadsView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "person.crop.circle.badge.questionmark")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))
            Text("No leads yet")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            Text("Leads assigned to you will show up here.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
        .padding(.horizontal, 24)
    }
}
