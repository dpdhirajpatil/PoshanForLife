import SwiftUI

/// Practitioner/admin-only manual estimate builder: pick a patient or lead,
/// build line items, apply a discount, preview the GST totals, then save as
/// a draft or save-and-send.
///
/// `POST /documents` has no `status` field — every document is born `DRAFT`
/// server-side — so "save & send" is a follow-up `PATCH` to `sent` right
/// after create, same two-step shape `CatalogueItemFormView` would use if it
/// ever needed a second call after its own save.
///
/// Owns its own local state and talks to the repositories directly, same
/// relationship `CatalogueItemFormView` has to `CatalogueViewModel` —
/// presented as a `.sheet` from `DocumentsListView`, wrapped in its own
/// `NavigationStack` by the presenter.
struct CreateEstimateView: View {

    let repository: DocumentsRepository
    let patientsRepository: PatientsRepository
    let leadsRepository: LeadsRepository
    let onSaved: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var party: DocumentPartySelection?
    @State private var showPartyPicker = false
    @State private var items: [DraftLineItem] = [DraftLineItem()]
    @State private var discountInr: String = ""

    @State private var saving = false
    @State private var errorMessage: String?

    struct DraftLineItem: Identifiable, Equatable {
        let id = UUID()
        var itemName: String = ""
        var hsnSac: String = ""
        var quantity: String = "1"
        var rateInr: String = ""
    }

    var body: some View {
        Form {
            Section("Patient or lead") {
                partySection
            }

            Section("Line items") {
                ForEach($items) { $item in
                    lineItemRow($item)
                }
                .onDelete { items.remove(atOffsets: $0) }

                Button {
                    items.append(DraftLineItem())
                } label: {
                    Label("Add item", systemImage: "plus.circle")
                }
                .accessibilityIdentifier("add-line-item")
            }

            Section("Discount") {
                TextField("Discount (₹, optional)", text: $discountInr)
                    .keyboardType(.decimalPad)
            }

            Section("Preview") {
                previewTotals
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }

            Section {
                Button {
                    Task { await save(sendAfter: false) }
                } label: {
                    HStack {
                        Spacer()
                        Text(saving ? "Saving…" : "Save as draft")
                        Spacer()
                    }
                }
                .disabled(!canSave || saving)
                .accessibilityIdentifier("save-estimate-draft")

                Button {
                    Task { await save(sendAfter: true) }
                } label: {
                    HStack {
                        Spacer()
                        Text(saving ? "Saving…" : "Save & send")
                            .foregroundStyle(theme.primary)
                        Spacer()
                    }
                }
                .disabled(!canSave || saving)
                .accessibilityIdentifier("save-estimate-send")
            }
        }
        .navigationTitle("New estimate")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Cancel") { dismiss() }
            }
        }
        .sheet(isPresented: $showPartyPicker) {
            NavigationStack {
                DocumentPartyPickerView(patientsRepository: patientsRepository, leadsRepository: leadsRepository) { selection in
                    party = selection
                }
            }
        }
    }

    // MARK: - Patient/lead

    @ViewBuilder
    private var partySection: some View {
        if let party {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(party.name)
                        .font(.displayFont(.semibold, size: 15))
                        .foregroundStyle(theme.onSurface)
                    Text(party.isLead ? "Lead" : "Patient")
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                }
                Spacer()
                Button("Change") { showPartyPicker = true }
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.primary)
            }
        } else {
            Button {
                showPartyPicker = true
            } label: {
                Label("Choose patient or lead", systemImage: "person.crop.circle.badge.plus")
            }
            .accessibilityIdentifier("choose-document-party")
        }
    }

    // MARK: - Line items

    private func lineItemRow(_ item: Binding<DraftLineItem>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField("Item name", text: item.itemName)
            TextField("HSN/SAC (optional)", text: item.hsnSac)
                .textInputAutocapitalization(.characters)
            HStack {
                TextField("Qty", text: item.quantity)
                    .keyboardType(.numberPad)
                    .frame(width: 60)
                TextField("Rate (₹)", text: item.rateInr)
                    .keyboardType(.decimalPad)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Totals preview

    /// Mirrors the backend's `DocumentTotals` (2.5% CGST + 2.5% SGST on the
    /// discounted subtotal) closely enough for a live preview — the server
    /// remains the source of truth on the saved document.
    private var lineTotals: [Double] {
        items.compactMap { item in
            guard let qty = Int(item.quantity), let rate = Double(item.rateInr) else { return nil }
            return Double(qty) * rate
        }
    }

    private var subtotal: Double { max(0, lineTotals.reduce(0, +) - (Double(discountInr) ?? 0)) }
    private var cgst: Double { subtotal * 0.025 }
    private var sgst: Double { subtotal * 0.025 }
    private var total: Double { subtotal + cgst + sgst }

    private var previewTotals: some View {
        VStack(spacing: 6) {
            totalsRow("Subtotal", subtotal)
            totalsRow("CGST (2.5%)", cgst)
            totalsRow("SGST (2.5%)", sgst)
            Divider()
            totalsRow("Total", total, emphasized: true)
        }
    }

    private func totalsRow(_ label: String, _ value: Double, emphasized: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(emphasized ? .displayFont(.semibold, size: 15) : .bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(emphasized ? 1 : 0.7))
            Spacer()
            Text(CurrencyFormatter.inr(value))
                .font(emphasized ? .displayFont(.semibold, size: 15) : .bodyFont(size: 14))
                .foregroundStyle(theme.onSurface)
        }
    }

    // MARK: - Save

    private var validItems: [CreateDocumentItemRequest]? {
        var result: [CreateDocumentItemRequest] = []
        for item in items {
            let name = item.itemName.trimmingCharacters(in: .whitespaces)
            guard !name.isEmpty,
                  let quantity = Int(item.quantity), quantity > 0,
                  let rate = Double(item.rateInr), rate > 0
            else { return nil }
            let hsnSac = item.hsnSac.trimmingCharacters(in: .whitespaces)
            result.append(CreateDocumentItemRequest(
                itemName: name,
                description: nil,
                hsnSac: hsnSac.isEmpty ? nil : hsnSac,
                quantity: quantity,
                rateInr: rate
            ))
        }
        return result.isEmpty ? nil : result
    }

    private var canSave: Bool {
        party != nil && validItems != nil
    }

    private func save(sendAfter: Bool) async {
        guard let party, let validItems else { return }
        saving = true
        errorMessage = nil

        let request = CreateDocumentRequest(
            documentType: .estimate,
            leadId: party.isLead ? party.id : nil,
            patientId: party.isLead ? nil : party.id,
            items: validItems,
            notes: nil,
            discountInr: Double(discountInr),
            validForDays: nil
        )

        switch await repository.create(request) {
        case .success(let document):
            if sendAfter {
                _ = await repository.updateStatus(id: document.id, status: .sent)
            }
            saving = false
            onSaved()
            dismiss()
        case .failure(let error):
            saving = false
            errorMessage = error.message
        }
    }
}
