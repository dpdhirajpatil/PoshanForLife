import SwiftUI

/// Confirm/override contact details → optionally assign a catalogue item →
/// confirm. One scrollable form rather than a literal multi-step wizard —
/// same shape as Android's `ConvertToPatientScreen`, which folds the same
/// steps into one screen behind an "Assign a service now" toggle.
///
/// Owns its own `NavigationStack`, presented `.fullScreenCover` from
/// `LeadDetailView` — same pattern as IOS-10's `CaptureView` and IOS-13's
/// `LiveSessionFlow`.
struct ConvertToPatientView: View {

    let lead: LeadDetail
    let repository: LeadsRepository
    /// Fired after a successful convert with the new patient's id and the
    /// name this form submitted (which may differ from `lead.name` if it was
    /// edited) — the presenter deep-links to `PatientDetailView` from here.
    let onConverted: (_ patientId: String, _ patientName: String) -> Void

    @EnvironmentObject private var container: AppContainer
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var phone: String
    @State private var email: String
    @State private var bloodGroup: String = ""
    @State private var heightCm: String = ""

    @State private var assignService = false
    @State private var serviceType: ServiceType = .programme
    @State private var selectedItem: CatalogueItem?
    @State private var showCataloguePicker = false
    @State private var price: String = ""
    @State private var startDate = Date()

    @State private var submitting = false
    @State private var errorMessage: String?

    init(lead: LeadDetail, repository: LeadsRepository, onConverted: @escaping (String, String) -> Void) {
        self.lead = lead
        self.repository = repository
        self.onConverted = onConverted
        _name = State(initialValue: lead.name)
        _phone = State(initialValue: lead.phone ?? "")
        _email = State(initialValue: lead.email ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Contact & medical details") {
                    TextField("Name", text: $name)
                    TextField("Phone", text: $phone).keyboardType(.phonePad)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    if (lead.email ?? "").isEmpty {
                        Text("Required — this lead has no email on file yet.")
                            .font(.bodyFont(size: 12))
                            .foregroundStyle(theme.onSurface.opacity(0.6))
                    }
                    TextField("Blood group (optional)", text: $bloodGroup)
                    TextField("Height in cm (optional)", text: $heightCm)
                        .keyboardType(.decimalPad)
                }

                Section {
                    Toggle("Assign a service now", isOn: $assignService.animation())
                        .tint(theme.primary)
                } footer: {
                    Text("You can always assign a programme, session, or challenge later from the patient's profile.")
                }

                if assignService {
                    assignmentSection
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.error)
                }
            }
            .navigationTitle("Convert to patient")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(submitting ? "Converting…" : "Convert") {
                        Task { await submit() }
                    }
                    .disabled(!canSubmit || submitting)
                    .accessibilityIdentifier("confirm-convert")
                }
            }
            .sheet(isPresented: $showCataloguePicker) {
                NavigationStack {
                    CatalogueView(repository: container.catalogueRepository, initialType: serviceType) { item in
                        selectedItem = item
                        price = item.priceInr.map { String(format: "%.0f", $0) } ?? ""
                    }
                }
            }
        }
    }

    // MARK: - Service assignment

    /// Picking a service delegates entirely to IOS-15's `CatalogueView` in
    /// selection mode rather than this screen rolling its own search/list —
    /// the "price"/"start date" fields below stay local since they're
    /// assignment-specific, not catalogue-item fields.
    @ViewBuilder
    private var assignmentSection: some View {
        Section("Service") {
            Picker("Type", selection: $serviceType) {
                ForEach(ServiceType.allCases, id: \.self) { type in
                    Text(type.label).tag(type)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: serviceType) { _ in selectedItem = nil }

            if let selectedItem {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(selectedItem.name)
                            .font(.displayFont(.semibold, size: 15))
                            .foregroundStyle(theme.onSurface)
                        if let priceInr = selectedItem.priceInr {
                            Text(CurrencyFormatter.inr(priceInr))
                                .font(.bodyFont(size: 12))
                                .foregroundStyle(theme.onSurface.opacity(0.7))
                        }
                    }
                    Spacer()
                    Button("Change") { showCataloguePicker = true }
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.primary)
                }

                TextField("Price (₹)", text: $price)
                    .keyboardType(.decimalPad)
                DatePicker("Start date", selection: $startDate, displayedComponents: .date)
            } else {
                Button {
                    showCataloguePicker = true
                } label: {
                    Label("Choose a \(serviceType.label.lowercased())", systemImage: "magnifyingglass")
                }
                .accessibilityIdentifier("choose-catalogue-item")
            }
        }
    }

    // MARK: - Submit

    private var canSubmit: Bool {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        if (lead.email ?? "").isEmpty, email.trimmingCharacters(in: .whitespaces).isEmpty { return false }
        return true
    }

    private func submit() async {
        submitting = true
        errorMessage = nil

        let request = ConvertLeadRequest(
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? nil : name,
            phone: phone.trimmingCharacters(in: .whitespaces).isEmpty ? nil : phone,
            email: email.trimmingCharacters(in: .whitespaces).isEmpty ? nil : email,
            dateOfBirth: nil,
            bloodGroup: bloodGroup.trimmingCharacters(in: .whitespaces).isEmpty ? nil : bloodGroup,
            heightCm: Double(heightCm),
            assignServiceId: assignService ? selectedItem?.id : nil,
            serviceType: assignService ? selectedItem.map { _ in serviceType.rawValue } : nil,
            startDate: assignService && selectedItem != nil ? LeadDateFormat.localDate(startDate) : nil,
            price: assignService ? Double(price) : nil
        )

        switch await repository.convert(id: lead.id, request: request) {
        case .success(let response):
            submitting = false
            onConverted(response.patientId, name)
        case .failure(let error):
            submitting = false
            errorMessage = error.message
        }
    }
}
