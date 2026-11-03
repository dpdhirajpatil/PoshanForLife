import SwiftUI

/// The login screen's Phone tab: number → 6-digit code → signed in. A new
/// number is offered sign-up in place, which also asks for a name.
struct PhoneAuthForm: View {

    @ObservedObject var viewModel: PhoneAuthViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: Field?

    private enum Field { case countryCode, phone, name, otp }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            switch viewModel.step {
            case .phoneEntry: phoneEntry
            case .otpEntry: otpEntry
            }
        }
    }

    private var phoneEntry: some View {
        Group {
            HStack(spacing: 12) {
                TextField("+91", text: $viewModel.countryCode)
                    .keyboardType(.phonePad)
                    .frame(width: 56)
                    .focused($focusedField, equals: .countryCode)
                    .accessibilityLabel("Country code")
                Divider().frame(height: 24)
                TextField("Phone number", text: $viewModel.phone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .focused($focusedField, equals: .phone)
                    .accessibilityIdentifier("phoneField")
            }
            .authFieldCard()

            AuthInlineError(message: viewModel.error)

            if viewModel.offerSignup {
                Button("Create an account with this number") {
                    Task { await viewModel.switchToSignup() }
                }
                .font(.displayFont(.semibold, size: 15))
                .foregroundStyle(theme.primary)
            }

            AuthPrimaryButton(
                title: "Send code",
                isLoading: viewModel.isLoading,
                isEnabled: !viewModel.isLoading && !viewModel.phone.isEmpty
            ) {
                focusedField = nil
                Task { await viewModel.sendOtp(purpose: .login) }
            }
        }
    }

    private var otpEntry: some View {
        Group {
            Button {
                viewModel.backToPhoneEntry()
            } label: {
                Label("Change number", systemImage: "chevron.left")
                    .font(.bodyFont(size: 15))
            }
            .foregroundStyle(theme.primary)

            Text("Enter the 6-digit code sent to \(viewModel.e164)")
                .font(.bodyFont(size: 15))
                .foregroundStyle(theme.onBackground.opacity(0.8))

            VStack(spacing: 0) {
                if viewModel.requiresName {
                    TextField("Your name", text: $viewModel.name)
                        .textContentType(.name)
                        .focused($focusedField, equals: .name)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .otp }
                        .padding(.bottom, 12)
                        .accessibilityIdentifier("nameField")
                    AuthFieldDivider()
                }
                TextField("6-digit code", text: Binding(
                    get: { viewModel.otp },
                    set: { viewModel.otp = String($0.filter(\.isNumber).prefix(PhoneAuthViewModel.otpLength)) }
                ))
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($focusedField, equals: .otp)
                .padding(.top, viewModel.requiresName ? 12 : 0)
                .accessibilityIdentifier("otpField")
            }
            .authFieldCard()

            if viewModel.requiresName && !viewModel.isNameValid && !viewModel.name.isEmpty {
                AuthInlineError(message: "Name must be at least \(PhoneAuthViewModel.minNameLength) characters")
            }
            AuthInlineError(message: viewModel.error)

            AuthPrimaryButton(
                title: viewModel.requiresName ? "Create account" : "Verify and sign in",
                isLoading: viewModel.isLoading,
                isEnabled: viewModel.canSubmitOtp
            ) {
                focusedField = nil
                Task { await viewModel.verify() }
            }

            Button(viewModel.resendSecondsLeft > 0
                   ? "Resend code in \(viewModel.resendSecondsLeft)s"
                   : "Resend code") {
                Task { await viewModel.resendOtp() }
            }
            .disabled(viewModel.resendSecondsLeft > 0 || viewModel.isLoading)
            .font(.bodyFont(size: 15))
            .foregroundStyle(theme.primary)
            .frame(maxWidth: .infinity)
        }
        .onAppear { focusedField = viewModel.requiresName ? .name : .otp }
    }
}
