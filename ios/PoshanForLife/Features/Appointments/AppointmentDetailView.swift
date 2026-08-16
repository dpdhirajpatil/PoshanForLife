import SwiftUI

/// Practitioner-only: the patient's details, mark-completed, and post-appointment
/// notes.
///
/// Patients never reach this screen — `notes` and `status=completed` are both
/// `INSUFFICIENT_ROLE` for a PATIENT caller (verified live), so showing them the
/// controls would only produce a 403 they can't act on.
struct AppointmentDetailView: View {

    let appointment: Appointment
    @ObservedObject var viewModel: AppointmentsViewModel

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var notes: String = ""
    @State private var didLoadNotes = false
    @FocusState private var notesFocused: Bool
    @State private var confirmingComplete = false

    /// Re-read from the view model so the screen reflects an update made here
    /// (or a refresh) rather than the snapshot it was pushed with.
    private var current: Appointment {
        viewModel.appointment(withId: appointment.id) ?? appointment
    }

    var body: some View {
        Form {
            Section("Patient") {
                LabeledContent("Name", value: current.patient?.name ?? "—")
                if let date = current.scheduledAt {
                    LabeledContent("When", value: "\(AppointmentTime.dayHeader(AppointmentTime.day(date))) at \(AppointmentTime.time(date))")
                }
                LabeledContent("Duration", value: "\(current.durationMinutes) min")
                LabeledContent("Type", value: current.isVideo ? "Video call" : "In person")
                if let status = current.parsedStatus {
                    LabeledContent("Status", value: status.label)
                }
            }

            Section("Notes") {
                TextEditor(text: $notes)
                    .frame(minHeight: 120)
                    .focused($notesFocused)
                    .font(.bodyFont(size: 14))
                    .accessibilityIdentifier("appointment-notes")

                Button("Save notes") {
                    notesFocused = false
                    Task { await viewModel.saveNotes(current, notes: notes) }
                }
                .disabled(viewModel.busyId == current.id || notes == (current.notes ?? ""))
            }

            if current.parsedStatus == .scheduled {
                Section {
                    Button("Mark completed") { confirmingComplete = true }
                        .accessibilityIdentifier("mark-completed")
                } footer: {
                    Text("Marking it completed is how the appointment leaves your upcoming list.")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Appointment")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // TextEditor has no return-key affordance for dismissal, same
            // problem the Track tab's number pads had.
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { notesFocused = false }
            }
        }
        // Both actions here can fail server-side (a stale session, a race with
        // another device). The list screen has its own alert; without this one
        // a failed "Save notes" on this screen would look exactly like a
        // successful one.
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
        .confirmationDialog(
            "Mark this appointment completed?",
            isPresented: $confirmingComplete,
            titleVisibility: .visible
        ) {
            Button("Mark completed") {
                Task { await viewModel.complete(current) }
            }
            Button("Not yet", role: .cancel) {}
        }
        .task {
            // Only seed once: re-seeding on every appearance would discard
            // edits in progress whenever the view model publishes.
            if !didLoadNotes {
                notes = current.notes ?? ""
                didLoadNotes = true
            }
        }
    }
}
