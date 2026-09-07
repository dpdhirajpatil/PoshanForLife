import SwiftUI

/// Public self-signup — lands the new account in the LEAD role. Pushed from
/// `LoginScreen`'s "Create an account" link, so it inherits the same
/// ``StaffTheme`` wrapper `RootView` applies pre-login; the moment signup
/// succeeds, `viewModel.state` becomes `.loggedIn` and `RootView` re-themes
/// into `LeadTheme` around `LeadTabView`, same seam `LoginScreen` uses.
///
/// Mirrors `LoginScreen`'s exact form chrome (`RoundedCornerShape` card,
/// spinner-replaces-label submit button, disabled-until-valid) rather than
/// inventing a second visual language for the one other pre-auth screen.
struct SignupView: View {

    @ObservedObject var viewModel: AuthViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: Field?

    private enum Field { case name, email, password, phone, city, healthGoal }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Create account")
                        .themedHeading(size: 32)
                        .padding(.bottom, 4)

                    Text("Tell us a little about yourself to get started.")
                        .font(.bodyFont(size: 15))
                        .foregroundStyle(theme.onBackground.opacity(0.7))

                    card

                    Text("Password needs at least 8 characters, including a number.")
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onBackground.opacity(0.6))

                    Button(action: submit) {
                        Group {
                            if viewModel.isSigningUp {
                                ProgressView().tint(theme.onPrimary)
                            } else {
                                Text("Create account")
                                    .font(.displayFont(.semibold, size: 16))
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 50)
                    }
                    .background(
                        (viewModel.canSubmitSignup ? theme.primary : theme.primary.opacity(0.4)),
                        in: RoundedCornerShape()
                    )
                    .foregroundStyle(theme.onPrimary)
                    .disabled(!viewModel.canSubmitSignup)
                }
                .padding(24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationBarTitleDisplayMode(.inline)
        .alert(
            "Couldn't create account",
            isPresented: Binding(
                get: { viewModel.signupErrorMessage != nil },
                set: { if !$0 { viewModel.signupErrorMessage = nil } }
            ),
            actions: { Button("OK", role: .cancel) { viewModel.signupErrorMessage = nil } },
            message: { Text(viewModel.signupErrorMessage ?? "") }
        )
    }

    private var card: some View {
        VStack(spacing: 0) {
            TextField("Full name", text: $viewModel.signupName)
                .textContentType(.name)
                .focused($focusedField, equals: .name)
                .submitLabel(.next)
                .onSubmit { focusedField = .email }
                .padding(.bottom, 12)

            Divider().overlay(theme.onBackground.opacity(0.12))

            TextField("Email", text: $viewModel.signupEmail)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focusedField, equals: .email)
                .submitLabel(.next)
                .onSubmit { focusedField = .password }
                .padding(.vertical, 12)

            Divider().overlay(theme.onBackground.opacity(0.12))

            SecureField("Password", text: $viewModel.signupPassword)
                // Not `.newPassword` — that content type triggers iOS's
                // "Automatic Strong Password" QuickType suggestion, which
                // steals keystrokes typed right after it appears (and is one
                // more thing standing between a prospect and finishing this
                // form). `.password`, same as `LoginScreen`'s field.
                .textContentType(.password)
                .focused($focusedField, equals: .password)
                .submitLabel(.next)
                .onSubmit { focusedField = .phone }
                .padding(.vertical, 12)

            Divider().overlay(theme.onBackground.opacity(0.12))

            TextField("Phone (optional)", text: $viewModel.signupPhone)
                .textContentType(.telephoneNumber)
                .keyboardType(.phonePad)
                .focused($focusedField, equals: .phone)
                .submitLabel(.next)
                .onSubmit { focusedField = .city }
                .padding(.vertical, 12)

            Divider().overlay(theme.onBackground.opacity(0.12))

            TextField("City (optional)", text: $viewModel.signupCity)
                .textContentType(.addressCity)
                .focused($focusedField, equals: .city)
                .submitLabel(.next)
                .onSubmit { focusedField = .healthGoal }
                .padding(.vertical, 12)

            Divider().overlay(theme.onBackground.opacity(0.12))

            TextField("Health goal (optional)", text: $viewModel.signupHealthGoal, axis: .vertical)
                .focused($focusedField, equals: .healthGoal)
                .submitLabel(.done)
                .onSubmit { submit() }
                .padding(.top, 12)
        }
        .font(.bodyFont(size: 16))
        .padding(14)
        .background(theme.surface, in: RoundedCornerShape())
        .overlay(
            RoundedCornerShape()
                .stroke(theme.onBackground.opacity(0.12), lineWidth: 1)
        )
    }

    private func submit() {
        focusedField = nil
        Task {
            await viewModel.signup()
            // A failed attempt leaves `state` as `.loggedOut` and surfaces
            // `signupErrorMessage` instead — nothing to dismiss back to.
        }
    }
}
