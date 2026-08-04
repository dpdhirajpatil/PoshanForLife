import SwiftUI

/// Medication reminders — entirely local. Nothing here touches the backend.
struct RemindersCard: View {

    @ObservedObject var scheduler: ReminderScheduler
    @Environment(\.appTheme) private var theme

    @State private var showingEditor = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Medication reminders")
                    .font(.displayFont(.semibold, size: 20))
                    .foregroundStyle(theme.onSurface)
                Spacer()
                Button {
                    showingEditor = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(theme.primary)
                        .font(.system(size: 22))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add reminder")
            }

            if !scheduler.isAuthorized {
                // Reminders can be created before permission exists; they just
                // won't fire. Say so rather than letting the user add three and
                // wonder why nothing happens.
                Text("Allow notifications so reminders can actually reach you.")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                Button {
                    Task { await scheduler.requestAuthorization() }
                } label: {
                    Text("Allow notifications")
                        .font(.displayFont(.semibold, size: 14))
                        .foregroundStyle(theme.onPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(theme.primary, in: Capsule())
                }
                .buttonStyle(.plain)
            }

            if scheduler.reminders.isEmpty {
                Text("No reminders yet.")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            } else {
                ForEach(scheduler.reminders) { reminder in
                    row(for: reminder)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .sheet(isPresented: $showingEditor) {
            ReminderEditor { reminder in
                Task { await scheduler.add(reminder) }
            }
        }
    }

    private func row(for reminder: MedicationReminder) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(reminder.label)
                    .font(.bodyFont(size: 15))
                    .foregroundStyle(theme.onSurface)
                Text("\(reminder.timeLabel) · \(reminder.daysLabel)")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { reminder.enabled },
                set: { enabled in Task { await scheduler.setEnabled(reminder, enabled: enabled) } }
            ))
            .labelsHidden()
            .tint(theme.primary)

            Button {
                Task { await scheduler.delete(reminder) }
            } label: {
                Image(systemName: "trash")
                    .foregroundStyle(theme.error)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete \(reminder.label)")
        }
        .padding(.vertical, 4)
    }
}

private struct ReminderEditor: View {
    let onSave: (MedicationReminder) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var label = ""
    @State private var time = Date()
    @State private var selectedDays: Set<Int> = []

    private let weekdaySymbols = Calendar.current.shortWeekdaySymbols

    var body: some View {
        NavigationStack {
            Form {
                Section("Medication") {
                    TextField("e.g. Vitamin D", text: $label)
                    DatePicker("Time", selection: $time, displayedComponents: .hourAndMinute)
                }

                Section {
                    ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { index, symbol in
                        let weekday = index + 1
                        Button {
                            if selectedDays.contains(weekday) {
                                selectedDays.remove(weekday)
                            } else {
                                selectedDays.insert(weekday)
                            }
                        } label: {
                            HStack {
                                Text(symbol)
                                Spacer()
                                if selectedDays.contains(weekday) {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } header: {
                    Text("Repeat")
                } footer: {
                    Text(selectedDays.isEmpty ? "No days selected — repeats every day." : "")
                }
            }
            .navigationTitle("New reminder")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let components = Calendar.current.dateComponents([.hour, .minute], from: time)
                        onSave(
                            MedicationReminder(
                                label: label.isEmpty ? "Medication" : label,
                                hour: components.hour ?? 9,
                                minute: components.minute ?? 0,
                                daysOfWeek: selectedDays
                            )
                        )
                        dismiss()
                    }
                }
            }
        }
    }
}
