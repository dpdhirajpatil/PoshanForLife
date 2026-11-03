import SwiftUI

/// Lead asks for a consultation. Both fields are optional; the backend adds a
/// note to the lead's own CRM record for staff. Mirrors Android's
/// RequestConsultationScreen.
struct RequestConsultationView: View {

    private enum Phase: Equatable { case idle, submitting, submitted, failed(String) }

    let repository: LeadSelfRepository
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var preferredContactTime = ""
    @State private var message = ""
    @State private var phase: Phase = .idle

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if phase == .submitted {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(theme.primary)
                    Text("We'll be in touch soon!")
                        .themedHeading(size: 24)
                        .accessibilityIdentifier("consultationSubmitted")
                    AuthPrimaryButton(title: "Done", isLoading: false, isEnabled: true) { dismiss() }
                } else {
                    Text("Tell us a bit about what you're looking for and a practitioner will reach out.")
                        .font(.bodyFont(size: 15))
                        .foregroundStyle(theme.onBackground.opacity(0.8))

                    VStack(spacing: 0) {
                        TextField("Preferred contact time (optional)", text: $preferredContactTime)
                            .padding(.vertical, 12)
                        AuthFieldDivider()
                        TextField("Message (optional)", text: $message, axis: .vertical)
                            .lineLimit(3...6)
                            .padding(.vertical, 12)
                    }
                    .authFieldCard()

                    if case .failed(let error) = phase {
                        AuthInlineError(message: "Couldn't submit: \(error)")
                    }

                    AuthPrimaryButton(
                        title: "Request consultation",
                        isLoading: phase == .submitting,
                        isEnabled: phase != .submitting,
                        action: submit
                    )
                }
            }
            .padding(20)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Request a consultation")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() {
        phase = .submitting
        let trimmed: (String) -> String? = {
            let t = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }
        Task {
            switch await repository.requestConsultation(
                preferredContactTime: trimmed(preferredContactTime),
                message: trimmed(message)
            ) {
            case .success: phase = .submitted
            case .failure(let error): phase = .failed(error.message)
            }
        }
    }
}
