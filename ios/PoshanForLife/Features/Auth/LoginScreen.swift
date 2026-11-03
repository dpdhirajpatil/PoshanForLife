import SwiftUI

/// Wrapped in ``StaffTheme`` by `RootView` — the role isn't known until login
/// succeeds, so the neutral theme is the only honest choice here. It re-themes
/// into the role's own theme the moment `state` becomes `.loggedIn`, the same
/// seam the Android app uses.
///
/// Email (staff, and patients with a password) or Phone (OTP; also how a new
/// person signs up by number), plus email self-signup. Both sign-up paths
/// create a LEAD account, matching Android's LoginScreen.
struct LoginScreen: View {

    @ObservedObject var viewModel: AuthViewModel
    @StateObject private var phoneViewModel: PhoneAuthViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: Field?
    @State private var method: Method = .email
    @State private var showingSignup = false

    private enum Field { case email, password }

    private enum Method: String, CaseIterable, Identifiable {
        case email = "Email"
        case phone = "Phone"
        var id: Self { self }
    }

    init(viewModel: AuthViewModel) {
        self.viewModel = viewModel
        _phoneViewModel = StateObject(wrappedValue: viewModel.makePhoneAuthViewModel())
    }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Sign in")
                        .themedHeading(size: 32)
                        .padding(.bottom, 4)

                    Text("Poshan for Life")
                        .font(.bodyFont(size: 15))
                        .foregroundStyle(theme.onBackground.opacity(0.7))

                    Picker("Sign in with", selection: $method) {
                        ForEach(Method.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("loginMethod")

                    switch method {
                    case .email: emailForm
                    case .phone: PhoneAuthForm(viewModel: phoneViewModel)
                    }

                    Button("Don't have an account? Sign up") { showingSignup = true }
                        .font(.bodyFont(size: 15))
                        .foregroundStyle(theme.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                }
                .padding(24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .sheet(isPresented: $showingSignup) {
            SignupScreen(viewModel: viewModel)
                .appTheme(StaffTheme.self)
        }
        .alert(
            "Couldn't sign in",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            ),
            actions: { Button("OK", role: .cancel) { viewModel.errorMessage = nil } },
            message: { Text(viewModel.errorMessage ?? "") }
        )
    }

    private var emailForm: some View {
        Group {
            VStack(spacing: 0) {
                TextField("Email", text: $viewModel.email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focusedField, equals: .email)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .password }
                    .padding(.bottom, 12)

                AuthFieldDivider()

                SecureField("Password", text: $viewModel.password)
                    .padding(.top, 12)
                    .textContentType(.password)
                    .focused($focusedField, equals: .password)
                    .submitLabel(.go)
                    .onSubmit { submit() }
            }
            .authFieldCard()

            AuthPrimaryButton(
                title: "Sign in",
                isLoading: viewModel.isSubmitting,
                isEnabled: viewModel.canSubmit,
                action: submit
            )
        }
    }

    private func submit() {
        focusedField = nil
        Task { await viewModel.login() }
    }
}
