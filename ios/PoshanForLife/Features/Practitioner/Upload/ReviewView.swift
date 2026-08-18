import SwiftUI

struct ReviewView: View {

    @ObservedObject var viewModel: ReportUploadViewModel
    let onSaved: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ConfidenceBanner(
                    tier: viewModel.confidenceTier,
                    extractedFieldCount: viewModel.extractedFieldCount,
                    onRetake: viewModel.retake
                )
                ExtractionTag()

                TextField("Report title", text: $viewModel.title)
                    .font(.bodyFont(size: 15))
                    .padding(12)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 6) {
                    Text("Practitioner notes")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                    TextEditor(text: $viewModel.notes)
                        .font(.bodyFont(size: 15))
                        .frame(minHeight: 100)
                        .padding(8)
                        .scrollContentBackground(.hidden)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                ForEach(ReportFieldGroups.all) { group in
                    FieldGroupCard(group: group, data: $viewModel.editedData)
                }

                confirmSection
            }
            .padding(16)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Review report")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private var confirmSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                Task {
                    if await viewModel.confirm() { onSaved() }
                }
            } label: {
                HStack {
                    if viewModel.confirmState == .saving {
                        ProgressView().tint(theme.onPrimary)
                    } else {
                        Text("Confirm and save")
                    }
                }
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .disabled(viewModel.confirmState == .saving)

            if case .failure(let message) = viewModel.confirmState {
                Text(message)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }
        }
    }
}

// MARK: - Confidence banner

private struct ConfidenceBanner: View {
    let tier: ReportConfidenceTier
    let extractedFieldCount: Int
    let onRetake: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(headline)
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(contentColor)
            Text("\(extractedFieldCount) of \(InBodyGroup.totalFieldCount) fields extracted")
                .font(.bodyFont(size: 13))
                .foregroundStyle(contentColor.opacity(0.85))

            if tier == .low {
                Button(action: onRetake) {
                    Text("Retake photo")
                        .font(.displayFont(.semibold, size: 14))
                        .foregroundStyle(contentColor)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(contentColor.opacity(0.15), in: Capsule())
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(containerColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var containerColor: Color {
        switch tier {
        case .high: return .brandGreenLightest
        case .medium: return .brandGold300
        case .low: return theme.error.opacity(0.15)
        }
    }

    private var contentColor: Color {
        switch tier {
        case .high: return .brandGreenDarkest
        case .medium: return .brandNavyDarkest
        case .low: return theme.error
        }
    }

    private var headline: String {
        switch tier {
        case .high: return "Ready to save"
        case .medium: return "Please review the values below"
        case .low: return "Low-confidence extraction"
        }
    }
}

// MARK: - Extraction tag

private struct ExtractionTag: View {
    @State private var showInfo = false
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            Text("Extracted using Claude Haiku AI")
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.6))
            Button {
                showInfo = true
            } label: {
                Image(systemName: "info.circle")
                    .font(.system(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }
            .popover(isPresented: $showInfo) {
                Text("Values are extracted automatically by AI from the photo and may need correction.")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface)
                    .padding(16)
                    .frame(width: 240)
            }
        }
    }
}

// MARK: - Editable field groups

private struct FieldGroupCard: View {
    let group: FieldGroupSpec
    @Binding var data: InBodyData
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(group.title)
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
                .padding(.bottom, 6)

            ForEach(group.fields) { field in
                FieldRow(spec: field, data: $data)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct FieldRow: View {
    let spec: FieldSpec
    @Binding var data: InBodyData

    @Environment(\.appTheme) private var theme
    @FocusState private var focused: Bool
    @State private var isEditing = false
    @State private var text = ""

    var body: some View {
        HStack {
            Text(spec.label)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.75))
            Spacer()
            if isEditing {
                TextField(spec.unit, text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .focused($focused)
                    .frame(width: 110)
                    .onSubmit(commit)
                    .onChange(of: focused) { isFocused in
                        if !isFocused { commit() }
                    }
            } else {
                Button {
                    text = data.value(for: spec.key).map(formatted) ?? ""
                    isEditing = true
                    focused = true
                } label: {
                    Text(displayValue)
                        .font(.displayFont(.semibold, size: 15))
                        .foregroundStyle(theme.onSurface)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
    }

    private var displayValue: String {
        guard let value = data.value(for: spec.key) else { return "—" }
        let text = formatted(value)
        return spec.unit.isEmpty ? text : "\(text) \(spec.unit)"
    }

    private func formatted(_ value: Double) -> String {
        spec.isInt
            ? String(Int(value))
            : String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private func commit() {
        isEditing = false
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        data.setValue(trimmed.isEmpty ? nil : Double(trimmed), for: spec.key)
    }
}
