import SwiftUI

/// A "from"/"to" date-range filter, shared by Orders and Transactions —
/// there's no dual-date-picker component anywhere else in the app to reuse,
/// so this is the first one, built generically (two optional `Date`
/// bindings) rather than tied to either screen.
///
/// Each bound is independently toggleable: a caller can filter by "from
/// only", "to only", both, or neither. Draft state is local so backing out
/// via "Cancel" (the system swipe-to-dismiss, effectively) never mutates the
/// bindings — only "Apply" commits, and "Clear" resets both to nil immediately.
struct DateRangeFilterSheet: View {

    @Binding var dateFrom: Date?
    @Binding var dateTo: Date?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    @State private var fromEnabled: Bool
    @State private var toEnabled: Bool
    @State private var draftFrom: Date
    @State private var draftTo: Date

    init(dateFrom: Binding<Date?>, dateTo: Binding<Date?>) {
        _dateFrom = dateFrom
        _dateTo = dateTo
        _fromEnabled = State(initialValue: dateFrom.wrappedValue != nil)
        _toEnabled = State(initialValue: dateTo.wrappedValue != nil)
        _draftFrom = State(initialValue: dateFrom.wrappedValue ?? Date())
        _draftTo = State(initialValue: dateTo.wrappedValue ?? Date())
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("From", isOn: $fromEnabled.animation())
                        .tint(theme.primary)
                    if fromEnabled {
                        DatePicker("Start date", selection: $draftFrom, displayedComponents: .date)
                    }
                }
                Section {
                    Toggle("To", isOn: $toEnabled.animation())
                        .tint(theme.primary)
                    if toEnabled {
                        DatePicker("End date", selection: $draftTo, displayedComponents: .date)
                    }
                }
            }
            .navigationTitle("Date range")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Clear") {
                        dateFrom = nil
                        dateTo = nil
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Apply") {
                        dateFrom = fromEnabled ? draftFrom : nil
                        dateTo = toEnabled ? draftTo : nil
                        dismiss()
                    }
                    .accessibilityIdentifier("apply-date-range")
                }
            }
        }
    }
}
