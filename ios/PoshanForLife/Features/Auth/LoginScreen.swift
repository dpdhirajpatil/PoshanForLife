import SwiftUI

/// Wrapped in ``StaffTheme`` by `RootView` — the role isn't known until login
/// succeeds, so the neutral theme is the only honest choice here. It re-themes
/// into the role's own theme the moment `state` becomes `.loggedIn`, the same
/// seam the Android app uses.
struct LoginScreen: View {

    @ObservedObject var viewModel: AuthViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: Field?

    private enum Field { case email, password }

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

                        // Without a rule between them the two fields read as one
                        // control inside a single border — and iOS excludes
                        // secure text from screenshots, so the lower row can look
                        // empty with no cue that it's a separate field.
                        Divider().overlay(theme.onBackground.opacity(0.12))

                        SecureField("Password", text: $viewModel.password)
                            .padding(.top, 12)
                            .textContentType(.password)
                            .focused($focusedField, equals: .password)
                            .submitLabel(.go)
                            .onSubmit { submit() }
                    }
                    .font(.bodyFont(size: 16))
                    .padding(14)
                    .background(theme.surface, in: RoundedCornerShape())
                    .overlay(
                        RoundedCornerShape()
                            .stroke(theme.onBackground.opacity(0.12), lineWidth: 1)
                    )

                    Button(action: submit) {
                        // The spinner replaces the label rather than sitting
                        // beside it, so the button can't change width mid-tap.
                        Group {
                            if viewModel.isSubmitting {
                                ProgressView().tint(theme.onPrimary)
                            } else {
                                Text("Sign in")
                                    .font(.displayFont(.semibold, size: 16))
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 50)
                    }
                    .background(
                        (viewModel.canSubmit ? theme.primary : theme.primary.opacity(0.4)),
                        in: RoundedCornerShape()
                    )
                    .foregroundStyle(theme.onPrimary)
                    .disabled(!viewModel.canSubmit)
                }
                .padding(24)
            }
            .scrollDismissesKeyboard(.interactively)
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

    private func submit() {
        focusedField = nil
        Task { await viewModel.login() }
    }
}

/// Patient/Lead use generous rounding; Staff is slightly tighter. Matching
/// Android's PoshanRoundedShapes / PoshanStaffShapes split.
private struct RoundedCornerShape: Shape {
    func path(in rect: CGRect) -> Path {
        RoundedRectangle(cornerRadius: 12, style: .continuous).path(in: rect)
    }
}
