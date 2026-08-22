import SwiftUI

struct LeadDetailView: View {

    let leadId: String
    let leadName: String
    let repository: LeadsRepository

    @StateObject private var viewModel: LeadsViewModel
    @EnvironmentObject private var container: AppContainer
    @Environment(\.appTheme) private var theme

    @State private var showLogActivity = false
    @State private var showScheduleFollowup = false
    @State private var showConvert = false
    @State private var convertedPatient: ConvertedPatientRef?

    init(leadId: String, leadName: String, repository: LeadsRepository) {
        self.leadId = leadId
        self.leadName = leadName
        self.repository = repository
        _viewModel = StateObject(wrappedValue: LeadsViewModel(leadId: leadId, repository: repository))
    }

    var body: some View {
        content
            .background(theme.background.ignoresSafeArea())
            .navigationTitle(leadName)
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.loadDetail() }
            .sheet(isPresented: $showLogActivity) {
                LogActivitySheet(viewModel: viewModel, isPresented: $showLogActivity)
            }
            .sheet(isPresented: $showScheduleFollowup) {
                ScheduleFollowupSheet(viewModel: viewModel, isPresented: $showScheduleFollowup)
            }
            .fullScreenCover(isPresented: $showConvert) {
                if case .success(let lead) = viewModel.detailState {
                    ConvertToPatientView(lead: lead, repository: repository) { patientId, patientName in
                        showConvert = false
                        convertedPatient = ConvertedPatientRef(id: patientId, name: patientName)
                    }
                }
            }
            // iOS 16-compatible equivalent of `.navigationDestination(item:)`
            // (introduced iOS 17) — same shape as `CaptureView`'s push to
            // `ReviewView`.
            .navigationDestination(
                isPresented: Binding(
                    get: { convertedPatient != nil },
                    set: { presented in if !presented { convertedPatient = nil } }
                )
            ) {
                if let convertedPatient {
                    PatientDetailView(
                        patientId: convertedPatient.id,
                        patientName: convertedPatient.name,
                        patientsRepository: container.patientsRepository,
                        reportsRepository: container.reportsRepository,
                        programmesRepository: container.programmesRepository
                    )
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.detailState {
        case .loading:
            ScrollView {
                VStack(spacing: 12) {
                    SkeletonBlock(height: 140, cornerRadius: 16)
                    SkeletonBlock(height: 60, cornerRadius: 16)
                    SkeletonBlock(height: 200, cornerRadius: 16)
                }
                .padding(16)
            }

        case .failure(let message):
            VStack {
                Spacer(minLength: 0)
                Text(message)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .multilineTextAlignment(.center)
                Spacer(minLength: 0)
            }
            .padding(24)

        case .success(let lead):
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ContactInfoCard(lead: lead)
                    StageSection(lead: lead, viewModel: viewModel)

                    if lead.stage != .converted {
                        actionRow
                    }

                    ActivityTimelineSection(
                        activities: lead.activities,
                        onLogActivity: { showLogActivity = true }
                    )
                }
                .padding(16)
            }
        }
    }

    private var actionRow: some View {
        HStack(spacing: 10) {
            Button {
                showScheduleFollowup = true
            } label: {
                Text("Schedule follow-up")
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.onSurface)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            Button {
                showConvert = true
            } label: {
                Text("Convert to patient")
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .accessibilityIdentifier("convert-to-patient")
        }
    }
}

private struct ConvertedPatientRef: Equatable {
    let id: String
    let name: String
}

// MARK: - Contact info

private struct ContactInfoCard: View {
    let lead: LeadDetail
    @Environment(\.appTheme) private var theme

    var body: some View {
        DetailCard {
            if let phone = lead.phone { DetailRow(label: "Phone", value: phone) }
            if let email = lead.email { DetailRow(label: "Email", value: email) }
            if let city = lead.city { DetailRow(label: "City", value: city) }
            if let age = lead.age { DetailRow(label: "Age", value: "\(age)") }
            if let source = lead.source { DetailRow(label: "Source", value: source.label) }
            if let programme = lead.interestedProgramme?.name { DetailRow(label: "Interested in", value: programme) }
            if let goal = lead.healthGoal, !goal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                DetailRow(label: "Health goal", value: goal)
            }
            if let notes = lead.notes, !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                DetailRow(label: "Notes", value: notes)
            }
        }
    }
}

// MARK: - Stage

private struct StageSection: View {
    let lead: LeadDetail
    @ObservedObject var viewModel: LeadsViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        DetailCard {
            Text("Stage")
                .font(.displayFont(.semibold, size: 15))
                .foregroundStyle(theme.onSurface)

            Picker("Stage", selection: Binding(
                get: { lead.stage },
                set: { newStage in Task { await viewModel.changeStage(to: newStage) } }
            )) {
                ForEach(LeadStage.allCases) { stage in
                    Text(stage.label).tag(stage)
                }
            }
            .pickerStyle(.menu)
            .disabled(lead.stage == .converted || viewModel.stageChangeState == .inFlight)
            .tint(theme.primary)
            .accessibilityIdentifier("stage-picker")

            if case .failure(let message) = viewModel.stageChangeState {
                Text(message)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.error)
            }
        }
    }
}

// MARK: - Activity timeline

private struct ActivityTimelineSection: View {
    let activities: [LeadActivity]
    let onLogActivity: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Activity timeline")
                    .font(.displayFont(.semibold, size: 17))
                    .foregroundStyle(theme.onSurface)
                Spacer()
                Button("Log activity", action: onLogActivity)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.primary)
                    .accessibilityIdentifier("log-activity")
            }

            if activities.isEmpty {
                Text("No activity yet")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            } else {
                // Newest first — the backend already orders chronologically
                // ascending by `createdAt`, so this is the one reversal.
                ForEach(activities.reversed(), id: \.id) { activity in
                    ActivityRow(activity: activity)
                }
            }
        }
    }
}

private struct ActivityRow: View {
    let activity: LeadActivity
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: activity.activityType.symbolName)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.onPrimary)
                .frame(width: 30, height: 30)
                .background(theme.primary, in: Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(activity.description)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface)
                Text(subtitle)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 6)
    }

    private var subtitle: String {
        let who = activity.createdBy?.name ?? "Someone"
        guard let date = activity.createdAt else { return who }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return "\(who) · \(formatter.string(from: date))"
    }
}

// MARK: - Log activity sheet

private struct LogActivitySheet: View {
    @ObservedObject var viewModel: LeadsViewModel
    @Binding var isPresented: Bool

    @State private var activityType: LeadActivityType = .note
    @State private var description: String = ""
    @Environment(\.appTheme) private var theme

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: $activityType) {
                        ForEach(LeadActivityType.loggable) { type in
                            Text(type.label).tag(type)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Description") {
                    TextField("What happened?", text: $description, axis: .vertical)
                        .lineLimit(3...6)
                        .accessibilityIdentifier("activity-description")
                }

                if case .failure(let message) = viewModel.activityState {
                    Text(message)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.error)
                }
            }
            .navigationTitle("Log activity")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        Task {
                            let ok = await viewModel.logActivity(type: activityType, description: description)
                            if ok { isPresented = false }
                        }
                    }
                    .disabled(description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || viewModel.activityState == .inFlight)
                }
            }
        }
    }
}

// MARK: - Schedule follow-up sheet

private struct ScheduleFollowupSheet: View {
    @ObservedObject var viewModel: LeadsViewModel
    @Binding var isPresented: Bool

    @State private var date = Date().addingTimeInterval(3600)
    @State private var message: String = ""
    @Environment(\.appTheme) private var theme

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("When", selection: $date, in: Date()...)
                }

                Section("Message (optional)") {
                    TextField("Reminder note", text: $message, axis: .vertical)
                        .lineLimit(2...4)
                }

                if case .failure(let msg) = viewModel.followupState {
                    Text(msg)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.error)
                }
            }
            .navigationTitle("Schedule follow-up")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        Task {
                            let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
                            let ok = await viewModel.scheduleFollowup(at: date, message: trimmed.isEmpty ? nil : trimmed)
                            if ok { isPresented = false }
                        }
                    }
                    .disabled(viewModel.followupState == .inFlight)
                }
            }
        }
    }
}

// MARK: - Shared building blocks

private struct DetailCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct DetailRow: View {
    let label: String
    let value: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
            Spacer(minLength: 12)
            Text(value)
                .font(.displayFont(.semibold, size: 14))
                .foregroundStyle(theme.onSurface)
                .multilineTextAlignment(.trailing)
        }
    }
}
