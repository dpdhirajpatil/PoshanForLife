import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Admin-only create/edit form for one catalogue item. Owns its own local
/// state and talks to the repository directly rather than through
/// `CatalogueViewModel` — see that class's doc comment for why.
///
/// Presented as a `.sheet` from `CatalogueView`, wrapped in its own
/// `NavigationStack` by the presenter (this view assumes it's already inside
/// one, same as `LeadDetailView`'s sheets).
struct CatalogueItemFormView: View {

    let type: ServiceType
    let existingItem: CatalogueItem?
    let repository: CatalogueRepository
    /// Fired after a successful save, before `dismiss()` — the presenter
    /// reloads its list.
    let onSaved: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var serviceCode: String
    /// The free-text sub-category (`type` on the wire) — create-only on the
    /// backend, so this field is disabled once editing an existing item.
    @State private var subType: String
    @State private var priceInr: String
    @State private var description: String
    @State private var status: CatalogueStatus
    @State private var durationWeeks: String
    @State private var durationMinutes: String
    @State private var durationDays: String
    @State private var goalDescription: String

    @State private var coverImageUrl: String?
    @State private var photosPickerItem: PhotosPickerItem?
    @State private var uploadingImage = false
    @State private var uploadErrorMessage: String?

    @State private var saving = false
    @State private var errorMessage: String?

    init(type: ServiceType, existingItem: CatalogueItem?, repository: CatalogueRepository, onSaved: @escaping () -> Void) {
        self.type = type
        self.existingItem = existingItem
        self.repository = repository
        self.onSaved = onSaved
        _name = State(initialValue: existingItem?.name ?? "")
        _serviceCode = State(initialValue: existingItem?.serviceCode ?? "")
        _subType = State(initialValue: existingItem?.type ?? "")
        _priceInr = State(initialValue: existingItem?.priceInr.map { String(format: "%.0f", $0) } ?? "")
        _description = State(initialValue: existingItem?.description ?? "")
        _status = State(initialValue: existingItem?.status ?? .draft)
        _durationWeeks = State(initialValue: existingItem?.durationWeeks.map(String.init) ?? "")
        _durationMinutes = State(initialValue: existingItem?.durationMinutes.map(String.init) ?? "")
        _durationDays = State(initialValue: existingItem?.durationDays.map(String.init) ?? "")
        _goalDescription = State(initialValue: existingItem?.goalDescription ?? "")
        _coverImageUrl = State(initialValue: existingItem?.coverImageUrl)
    }

    private var isEditing: Bool { existingItem != nil }

    var body: some View {
        Form {
            Section("Cover image") {
                coverImageSection
            }

            Section("Details") {
                TextField("Name", text: $name)
                TextField("Service code", text: $serviceCode)
                    .textInputAutocapitalization(.characters)
                TextField("Category (e.g. Weight loss)", text: $subType)
                    .disabled(isEditing)
                TextField("Price (₹)", text: $priceInr)
                    .keyboardType(.decimalPad)
                TextField("Description (optional)", text: $description, axis: .vertical)
                Picker("Status", selection: $status) {
                    ForEach(CatalogueStatus.allCases) { option in
                        Text(option.label).tag(option)
                    }
                }
            }

            Section(type.label) {
                typeSpecificFields
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }
        }
        .navigationTitle(isEditing ? "Edit \(type.label.lowercased())" : "New \(type.label.lowercased())")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(saving ? "Saving…" : "Save") {
                    Task { await save() }
                }
                .disabled(!canSave || saving)
                .accessibilityIdentifier("save-catalogue-item")
            }
        }
    }

    // MARK: - Type-specific fields

    @ViewBuilder
    private var typeSpecificFields: some View {
        switch type {
        case .programme:
            TextField("Duration (weeks)", text: $durationWeeks).keyboardType(.numberPad)
        case .session:
            TextField("Duration (minutes)", text: $durationMinutes).keyboardType(.numberPad)
        case .challenge:
            TextField("Duration (days)", text: $durationDays).keyboardType(.numberPad)
            TextField("Goal description", text: $goalDescription, axis: .vertical)
        }
    }

    // MARK: - Cover image

    private var coverImageSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Group {
                if let coverImageUrl, let url = URL(string: coverImageUrl) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Color.clear
                        }
                    }
                } else {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(theme.onSurface.opacity(0.08))
                        .overlay(
                            Image(systemName: "photo")
                                .font(.system(size: 28))
                                .foregroundStyle(theme.onSurface.opacity(0.3))
                        )
                }
            }
            .frame(height: 140)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            PhotosPicker(selection: $photosPickerItem, matching: .images) {
                HStack {
                    if uploadingImage { ProgressView() }
                    Text(coverImageUrl == nil ? "Add cover image" : "Change cover image")
                }
            }
            .disabled(uploadingImage)
            .onChange(of: photosPickerItem) { newItem in
                Task { await uploadPickedImage(newItem) }
            }

            if let uploadErrorMessage {
                Text(uploadErrorMessage)
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.error)
            }
        }
        .listRowInsets(EdgeInsets())
        .padding(.vertical, 8)
    }

    private func uploadPickedImage(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        uploadErrorMessage = nil
        uploadingImage = true
        defer { uploadingImage = false }

        guard let data = try? await item.loadTransferable(type: Data.self) else {
            uploadErrorMessage = "Couldn't read that image."
            return
        }
        // The backend rejects a wildcard content type and only accepts these
        // four exact ones — resolve the picked item's concrete type rather
        // than guessing from a file extension.
        guard let (mime, ext) = Self.mimeAndExtension(for: item.supportedContentTypes.first) else {
            uploadErrorMessage = "Only JPEG, PNG, WebP, or GIF images are supported."
            return
        }

        switch await repository.uploadCoverImage(type: type, fileName: "cover.\(ext)", contentType: mime, data: data) {
        case .success(let response):
            coverImageUrl = response.url
        case .failure(let error):
            uploadErrorMessage = error.message
        }
    }

    private static func mimeAndExtension(for contentType: UTType?) -> (mime: String, ext: String)? {
        guard let contentType else { return nil }
        if contentType.conforms(to: .jpeg) { return ("image/jpeg", "jpg") }
        if contentType.conforms(to: .png) { return ("image/png", "png") }
        if contentType.conforms(to: .webP) { return ("image/webp", "webp") }
        if contentType.conforms(to: .gif) { return ("image/gif", "gif") }
        return nil
    }

    // MARK: - Save

    private var priceValue: Double? { Double(priceInr) }

    private var typeSpecificValid: Bool {
        switch type {
        case .programme: return Int(durationWeeks) != nil
        case .session: return Int(durationMinutes) != nil
        case .challenge:
            return Int(durationDays) != nil && !goalDescription.trimmingCharacters(in: .whitespaces).isEmpty
        }
    }

    private var canSave: Bool {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        guard !serviceCode.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        guard priceValue != nil, typeSpecificValid else { return false }
        if !isEditing, subType.trimmingCharacters(in: .whitespaces).isEmpty { return false }
        return true
    }

    private func save() async {
        guard let price = priceValue else { return }
        saving = true
        errorMessage = nil

        let durWeeks = type == .programme ? Int(durationWeeks) : nil
        let durMinutes = type == .session ? Int(durationMinutes) : nil
        let durDays = type == .challenge ? Int(durationDays) : nil
        let goal = type == .challenge ? goalDescription.trimmingCharacters(in: .whitespaces) : nil
        let trimmedDescription = description.trimmingCharacters(in: .whitespaces)

        let result: Result<CatalogueItem, APIError>
        if let existingItem {
            result = await repository.update(
                type: type,
                id: existingItem.id,
                request: UpdateCatalogueItemRequest(
                    name: name.trimmingCharacters(in: .whitespaces),
                    serviceCode: serviceCode.trimmingCharacters(in: .whitespaces),
                    priceInr: price,
                    description: trimmedDescription.isEmpty ? nil : trimmedDescription,
                    coverImageUrl: coverImageUrl,
                    status: status,
                    durationWeeks: durWeeks,
                    durationMinutes: durMinutes,
                    durationDays: durDays,
                    goalDescription: goal
                )
            )
        } else {
            result = await repository.create(
                type: type,
                request: CreateCatalogueItemRequest(
                    name: name.trimmingCharacters(in: .whitespaces),
                    serviceCode: serviceCode.trimmingCharacters(in: .whitespaces),
                    type: subType.trimmingCharacters(in: .whitespaces),
                    priceInr: price,
                    description: trimmedDescription.isEmpty ? nil : trimmedDescription,
                    coverImageUrl: coverImageUrl,
                    status: status,
                    durationWeeks: durWeeks,
                    durationMinutes: durMinutes,
                    durationDays: durDays,
                    goalDescription: goal
                )
            )
        }

        saving = false
        switch result {
        case .success:
            onSaved()
            dismiss()
        case .failure(let error):
            errorMessage = error.message
        }
    }
}
