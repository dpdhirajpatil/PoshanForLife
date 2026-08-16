import SwiftUI

/// Pick a practitioner, a day, and a slot.
///
/// Every time shown here is in the device's own time zone, including the slot
/// buttons — see `AppointmentTime` for why that conversion is load-bearing
/// rather than cosmetic.
struct BookAppointmentView: View {

    @StateObject private var viewModel: BookAppointmentViewModel
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let onBooked: () -> Void

    init(repository: AppointmentsRepository, onBooked: @escaping () -> Void) {
        self.onBooked = onBooked
        _viewModel = StateObject(wrappedValue: BookAppointmentViewModel(repository: repository))
    }

    var body: some View {
        Form {
            practitionerSection

            Section("Day") {
                DatePicker(
                    "Date",
                    selection: $viewModel.date,
                    in: Date()...,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .tint(theme.primary)
            }

            slotsSection

            Section {
                Toggle("Video call", isOn: $viewModel.isVideo)
                    .tint(theme.primary)
            } footer: {
                Text("Your practitioner will send a link before the appointment.")
            }

            if let error = viewModel.errorMessage {
                Section {
                    Text(error)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.error)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Book appointment")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(viewModel.isBooking ? "Booking…" : "Book") {
                    Task {
                        await viewModel.book()
                        if viewModel.booked != nil {
                            onBooked()
                            dismiss()
                        }
                    }
                }
                .disabled(!viewModel.canBook)
                .accessibilityIdentifier("confirm-booking")
            }
        }
        .task { await viewModel.load() }
    }

    @ViewBuilder
    private var practitionerSection: some View {
        Section("Practitioner") {
            switch viewModel.practitioners {
            case .loading:
                ProgressView()

            case .failure(let message):
                Text(message)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)

            case .success(let list):
                if list.isEmpty {
                    // Booking is only ever with an already-assigned
                    // practitioner, so an unassigned patient genuinely has
                    // nobody to book with — say so rather than showing an
                    // empty picker.
                    Text("You don't have a practitioner assigned yet. Your clinic will assign one before you can book.")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                } else {
                    Picker("Practitioner", selection: $viewModel.selectedPractitionerId) {
                        ForEach(list, id: \.id) { practitioner in
                            Text(practitioner.name).tag(Optional(practitioner.id))
                        }
                    }
                    .onChange(of: viewModel.selectedPractitionerId) { _ in
                        Task { await viewModel.loadSlots() }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var slotsSection: some View {
        Section("Time") {
            switch viewModel.slots {
            case .loading:
                ProgressView()

            case .failure(let message):
                Text(message)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)

            case .success(let slots):
                let bookable = slots.filter(\.available)
                if bookable.isEmpty {
                    Text("No free slots on this day. Try another date.")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                } else {
                    // A wrapped grid rather than a List of rows: sixteen
                    // half-hour slots as full-width rows is a lot of scrolling
                    // for what is really a set of small choices.
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], spacing: 8) {
                        ForEach(bookable) { slot in
                            SlotButton(
                                slot: slot,
                                date: viewModel.date,
                                isSelected: viewModel.selectedSlot?.time == slot.time
                            ) {
                                viewModel.selectedSlot = slot
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }
}

private struct SlotButton: View {
    let slot: AvailableSlot
    let date: Date
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.bodyFont(size: 14))
                .foregroundStyle(isSelected ? theme.onPrimary : theme.onSurface)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    isSelected ? theme.primary : theme.onSurface.opacity(0.08),
                    in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("slot-\(slot.time)")
    }

    /// Local time, so this button agrees with what the appointment list will
    /// show after booking.
    private var label: String {
        guard let instant = AppointmentTime.instant(forSlot: slot, on: date) else { return slot.time }
        return AppointmentTime.time(instant)
    }
}

// MARK: - Reschedule

/// Same slot picker, minus the practitioner choice — a reschedule keeps the
/// practitioner (the backend has no "move to a different practitioner" path;
/// it would be a cancel plus a new booking).
struct RescheduleView: View {

    let appointment: Appointment
    @StateObject private var viewModel: BookAppointmentViewModel
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let onPick: (Date) -> Void

    init(appointment: Appointment, repository: AppointmentsRepository, onPick: @escaping (Date) -> Void) {
        self.appointment = appointment
        self.onPick = onPick
        let model = BookAppointmentViewModel(repository: repository)
        model.selectedPractitionerId = appointment.practitioner?.id
        _viewModel = StateObject(wrappedValue: model)
    }

    var body: some View {
        Form {
            Section("Currently") {
                if let date = appointment.scheduledAt {
                    Text("\(AppointmentTime.dayHeader(AppointmentTime.day(date))) at \(AppointmentTime.time(date))")
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.75))
                }
            }

            Section("New day") {
                DatePicker("Date", selection: $viewModel.date, in: Date()..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(theme.primary)
            }

            Section("New time") {
                switch viewModel.slots {
                case .loading:
                    ProgressView()
                case .failure(let message):
                    Text(message).font(.bodyFont(size: 13)).foregroundStyle(theme.error)
                case .success(let slots):
                    let bookable = slots.filter(\.available)
                    if bookable.isEmpty {
                        Text("No free slots on this day. Try another date.")
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], spacing: 8) {
                            ForEach(bookable) { slot in
                                SlotButton(
                                    slot: slot,
                                    date: viewModel.date,
                                    isSelected: viewModel.selectedSlot?.time == slot.time
                                ) {
                                    viewModel.selectedSlot = slot
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Reschedule")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Move") {
                    if let slot = viewModel.selectedSlot,
                       let at = AppointmentTime.instant(forSlot: slot, on: viewModel.date) {
                        onPick(at)
                        dismiss()
                    }
                }
                .disabled(viewModel.selectedSlot == nil)
            }
        }
        .task { await viewModel.loadSlots() }
    }
}
