import SwiftUI

/// Email self-signup, the same fields as Android's SignupScreen. The account
/// is created as a LEAD; on success `AuthViewModel.state` flips to
/// `.loggedIn` and RootView swaps to LeadTabView, which dismisses this sheet.
struct SignupScreen: View {

    @ObservedObject var viewModel: AuthViewModel
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: Field?

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var phone = ""
    @State private var city = ""
    @State private var healthGoal = ""

    private enum Field { case name, email, password, phone, city, healthGoal }

    /// Mirrors the backend's SignupRequest rules, so mistakes show here rather
    /// than as a server error after the tap.
    private var nameValid: Bool { name.trimmingCharacters(in: .whitespaces).count >= 2 }
    private var emailValid: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.contains("@") && trimmed.contains(".") && !trimmed.contains(" ")
    }
    private var passwordValid: Bool { password.count >= 8 && password.contains(where: \.isNumber) }
    private var canSubmit: Bool { nameValid && emailValid && passwordValid && !viewModel.isSigningUp }

    var body: some View {
        NavigationStack {
            ZStack {
                theme.background.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Create account")
                            .themedHeading(size: 28)
                        Text("Start your Poshan for Life journey")
                            .font(.bodyFont(size: 15))
                            .foregroundStyle(theme.onBackground.opacity(0.7))

                        VStack(spacing: 0) {
                            field("Name", text: $name, focus: .name, next: .email)
                                .textContentType(.name)
                            AuthFieldDivider()
                            field("Email", text: $email, focus: .email, next: .password)
                                .textContentType(.emailAddress)
                                .keyboardType(.emailAddress)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            AuthFieldDivider()
                            SecureField("Password", text: $password)
                                .textContentType(.newPassword)
                                .focused($focusedField, equals: .password)
                                .submitLabel(.next)
                                .onSubmit { focusedField = .phone }
                                .padding(.vertical, 12)
                                .accessibilityIdentifier("signupPassword")
                        }
                        .authFieldCard()

                        Text("Password: at least 8 characters, with a number")
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(password.isEmpty || passwordValid
                                             ? theme.onBackground.opacity(0.6) : theme.error)

                        VStack(spacing: 0) {
                            field("Phone (optional)", text: $phone, focus: .phone, next: .city)
                                .keyboardType(.phonePad)
                                .textContentType(.telephoneNumber)
                            AuthFieldDivider()
                            field("City (optional)", text: $city, focus: .city, next: .healthGoal)
                                .textContentType(.addressCity)
                            AuthFieldDivider()
                            field("What's your health goal? (optional)", text: $healthGoal, focus: .healthGoal, next: nil)
                        }
                        .authFieldCard()

                        AuthInlineError(message: viewModel.signupErrorMessage)

                        AuthPrimaryButton(
                            title: "Create account",
                            isLoading: viewModel.isSigningUp,
                            isEnabled: canSubmit,
                            action: submit
                        )

                        Button("Already have an account? Sign in") { dismiss() }
                            .font(.bodyFont(size: 15))
                            .foregroundStyle(theme.primary)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(24)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .onAppear { viewModel.signupErrorMessage = nil }
    }

    private func field(_ title: String, text: Binding<String>, focus: Field, next: Field?) -> some View {
        TextField(title, text: text)
            .focused($focusedField, equals: focus)
            .submitLabel(next == nil ? .done : .next)
            .onSubmit { if let next { focusedField = next } else { submit() } }
            .padding(.vertical, 12)
            .accessibilityIdentifier("signup.\(focus)")
    }

    private func submit() {
        guard canSubmit else { return }
        focusedField = nil
        let optional: (String) -> String? = { value in
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        let request = SignupRequest(
            name: name.trimmingCharacters(in: .whitespaces),
            email: email.trimmingCharacters(in: .whitespaces).lowercased(),
            password: password,
            phone: optional(phone),
            city: optional(city),
            healthGoal: optional(healthGoal)
        )
        Task { await viewModel.signup(request) }
    }
}
