import SwiftUI

/// Appointments for whichever role is signed in.
///
/// One view with two variants rather than two views: the data, the grouping and
/// the row layout are identical, and only the affordances differ — a patient
/// reschedules and cancels their own, a practitioner completes theirs and adds
/// notes. Splitting them would duplicate the grouping and the empty/loading
/// states to vary a swipe menu.
struct AppointmentsListView: View {

    @StateObject private var viewModel: AppointmentsViewModel
    @Environment(\.appTheme) private var theme

    private let repository: AppointmentsRepository

    @State private var pendingCancel: Appointment?
    @State private var reschedulingFrom: Appointment?
    @State private var isBooking = false
    @State private var joiningCall: Appointment?

    init(role: AppointmentsRole, repository: AppointmentsRepository) {
        self.repository = repository
        _viewModel = StateObject(wrappedValue: AppointmentsViewModel(role: role, repository: repository))
    }

    var body: some View {
        content
            .background(theme.background.ignoresSafeArea())
            .navigationTitle(viewModel.role == .patient ? "Appointments" : "Schedule")
            .toolbar {
                if viewModel.role == .patient {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button {
                            isBooking = true
                        } label: {
                            Image(systemName: "plus")
                        }
                        .accessibilityLabel("Book appointment")
                        .accessibilityIdentifier("book-appointment")
                    }
                }
            }
            .refreshable { await viewModel.load() }
            .task { await viewModel.load() }
            // Practitioner-only: the patient/notes/complete screen. Registered
            // unconditionally because a destination registered inside an `if`
            // isn't reliably found, but only practitioner rows are links.
            .navigationDestination(for: Appointment.self) { appointment in
                AppointmentDetailView(appointment: appointment, viewModel: viewModel)
            }
            .sheet(isPresented: $isBooking) {
                NavigationStack {
                    BookAppointmentView(repository: repository) {
                        Task { await viewModel.load() }
                    }
                }
            }
            .sheet(item: $reschedulingFrom) { appointment in
                NavigationStack {
                    RescheduleView(appointment: appointment, repository: repository) { newDate in
                        Task { await viewModel.reschedule(appointment, to: newDate) }
                    }
                }
            }
            .fullScreenCover(item: $joiningCall) { appointment in
                LiveSessionFlow(
                    otherPartyName: counterpartName(for: appointment),
                    onDismiss: { joiningCall = nil }
                )
            }
            // Confirmed before it happens: cancelling frees the slot for
            // someone else, so an accidental swipe shouldn't do it silently.
            .confirmationDialog(
                "Cancel this appointment?",
                isPresented: .init(get: { pendingCancel != nil }, set: { if !$0 { pendingCancel = nil } }),
                titleVisibility: .visible
            ) {
                Button("Cancel appointment", role: .destructive) {
                    if let appointment = pendingCancel {
                        Task { await viewModel.cancel(appointment) }
                    }
                    pendingCancel = nil
                }
                Button("Keep it", role: .cancel) { pendingCancel = nil }
            } message: {
                Text("The slot will be released and your practitioner notified.")
            }
            .alert(
                "Couldn't update",
                isPresented: .init(
                    get: { viewModel.actionError != nil },
                    set: { if !$0 { viewModel.actionError = nil } }
                )
            ) {
                Button("OK", role: .cancel) { viewModel.actionError = nil }
            } message: {
                Text(viewModel.actionError ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.days {
        case .loading:
            VStack(spacing: 12) {
                ForEach(0..<4, id: \.self) { _ in SkeletonBlock(height: 64, cornerRadius: 12) }
                Spacer()
            }
            .padding(16)

        case .failure(let message):
            VStack(spacing: 8) {
                Text(message)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .multilineTextAlignment(.center)
            }
            .padding(24)

        case .success(let days):
            if days.isEmpty {
                EmptyAppointmentsView(role: viewModel.role, showsPast: viewModel.showsPast)
            } else {
                List {
                    ForEach(days) { day in
                        Section {
                            ForEach(day.appointments) { appointment in
                                row(appointment)
                            }
                        } header: {
                            Text(day.header)
                                .font(.displayFont(.semibold, size: 13))
                                .foregroundStyle(theme.onBackground.opacity(0.75))
                        }
                    }

                    Toggle("Show past appointments", isOn: $viewModel.showsPast)
                        .font(.bodyFont(size: 14))
                        .tint(theme.primary)
                        .listRowBackground(theme.surface)
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
    }

    @ViewBuilder
    private func row(_ appointment: Appointment) -> some View {
        Group {
            if viewModel.role == .practitioner {
                NavigationLink(value: appointment) {
                    AppointmentRow(
                        appointment: appointment,
                        role: viewModel.role,
                        onJoinCall: { joiningCall = appointment }
                    )
                }
            } else {
                AppointmentRow(
                    appointment: appointment,
                    role: viewModel.role,
                    onJoinCall: { joiningCall = appointment }
                )
            }
        }
        .listRowBackground(theme.surface)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if viewModel.role == .patient, appointment.isPatientEditable {
                Button(role: .destructive) {
                    pendingCancel = appointment
                } label: {
                    Label("Cancel", systemImage: "xmark.circle")
                }

                Button {
                    reschedulingFrom = appointment
                } label: {
                    Label("Reschedule", systemImage: "calendar.badge.clock")
                }
                .tint(theme.primary)
            }
        }
    }

    private func counterpartName(for appointment: Appointment) -> String {
        switch viewModel.role {
        case .patient: return appointment.practitioner?.name ?? "your practitioner"
        case .practitioner: return appointment.patient?.name ?? "the patient"
        }
    }
}

// MARK: - Row

private struct AppointmentRow: View {
    let appointment: Appointment
    let role: AppointmentsRole
    /// Non-nil so both roles can join — `.buttonStyle(.borderless)` below is
    /// what lets this coexist with the practitioner row's enclosing
    /// `NavigationLink` without the two fighting over the tap.
    let onJoinCall: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        if let date = appointment.scheduledAt {
                            Text(AppointmentTime.time(date))
                                .font(.displayFont(.semibold, size: 16))
                                .foregroundStyle(theme.onSurface)
                        }
                        if appointment.isVideo {
                            Image(systemName: "video.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(theme.onSurface.opacity(0.6))
                                .accessibilityLabel("Video call")
                        }
                    }

                    // Each side cares about the other party's name.
                    Text(counterpartName)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))

                    Text("\(appointment.durationMinutes) min")
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.55))
                }

                Spacer(minLength: 8)

                if let status = appointment.parsedStatus, status != .scheduled {
                    AppointmentStatusBadge(status: status)
                }
            }

            if appointment.isVideo, appointment.parsedStatus == .scheduled {
                Button(action: onJoinCall) {
                    Label("Join call", systemImage: "video.fill")
                        .font(.displayFont(.semibold, size: 13))
                        .foregroundStyle(appointment.canJoinVideoCall() ? theme.onPrimary : theme.onSurface.opacity(0.4))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            appointment.canJoinVideoCall() ? theme.primary : theme.onSurface.opacity(0.08),
                            in: Capsule()
                        )
                }
                .buttonStyle(.borderless)
                .disabled(!appointment.canJoinVideoCall())
                .accessibilityIdentifier("join-call")
            }
        }
        .padding(.vertical, 4)
    }

    private var counterpartName: String {
        switch role {
        case .patient: return appointment.practitioner?.name ?? "Your practitioner"
        case .practitioner: return appointment.patient?.name ?? "Patient"
        }
    }
}

struct AppointmentStatusBadge: View {
    let status: AppointmentStatus
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
        case .completed: return theme.onTertiary
        case .cancelled, .noShow: return theme.error
        case .scheduled: return theme.onSurface.opacity(0.75)
        }
    }

    private var background: Color {
        switch status {
        case .completed: return theme.tertiary
        case .cancelled, .noShow: return theme.error.opacity(0.15)
        case .scheduled: return theme.onSurface.opacity(0.1)
        }
    }
}

// MARK: - Empty state

private struct EmptyAppointmentsView: View {
    let role: AppointmentsRole
    let showsPast: Bool

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "calendar")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))

            Text(role == .patient ? "No appointments yet" : "Nothing scheduled")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)

            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
        .padding(.horizontal, 24)
    }

    private var message: String {
        switch role {
        case .patient:
            return "Tap + to book a session with your practitioner."
        case .practitioner:
            return showsPast
                ? "No appointments on record."
                : "Nothing coming up. Past appointments are hidden."
        }
    }
}
