import SwiftUI

/// A simple form → `POST leads/me/request-consultation`. Presented as a sheet
/// from `LeadHomeView`'s CTA card (and its InBody nudge, which points at the
/// same flow) — there's no list this fire-and-forget POST refreshes into, so
/// success is its own brief confirmation view rather than the dismiss-and-
/// silently-reload pattern the rest of the app uses for a submitted form.
struct RequestConsultationView: View {

    let repository: LeadSelfRepository

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var preferredContactTime = ""
    @State private var message = ""
    @State private var submitting = false
    @State private var submitted = false
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if submitted {
                confirmation
            } else {
                form
            }
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Request consultation")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !submitted {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var form: some View {
        Form {
            Section {
                TextField("Preferred contact time (optional)", text: $preferredContactTime)
                TextField("Message (optional)", text: $message, axis: .vertical)
            } footer: {
                Text("A practitioner will reach out to you soon.")
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }

            Section {
                Button {
                    Task { await submit() }
                } label: {
                    HStack {
                        Spacer()
                        Text(submitting ? "Sending…" : "Send request")
                        Spacer()
                    }
                }
                .disabled(submitting)
                .accessibilityIdentifier("send-consultation-request")
            }
        }
    }

    private var confirmation: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 0)
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(theme.primary)
            Text("We'll be in touch soon!")
                .font(.displayFont(.semibold, size: 20))
                .foregroundStyle(theme.onBackground)
                .multilineTextAlignment(.center)
            Text("A practitioner will reach out about your consultation request.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onBackground.opacity(0.7))
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
            Button("Done") { dismiss() }
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onPrimary)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(theme.primary, in: RoundedCornerShape())
                .accessibilityIdentifier("consultation-confirmation-done")
        }
        .padding(24)
    }

    private func submit() async {
        submitting = true
        errorMessage = nil

        let contactTime = preferredContactTime.trimmingCharacters(in: .whitespaces)
        let trimmedMessage = message.trimmingCharacters(in: .whitespaces)

        switch await repository.requestConsultation(
            preferredContactTime: contactTime.isEmpty ? nil : contactTime,
            message: trimmedMessage.isEmpty ? nil : trimmedMessage
        ) {
        case .success:
            submitting = false
            submitted = true
        case .failure(let error):
            submitting = false
            errorMessage = error.message
        }
    }
}
