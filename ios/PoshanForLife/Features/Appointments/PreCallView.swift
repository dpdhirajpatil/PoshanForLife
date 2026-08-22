import AVFoundation
import SwiftUI

/// IOS-13's pre-call screen: camera/mic permissions plus a front-camera
/// self-preview, then "Join now". Deliberately real and provider-agnostic —
/// whichever video SDK is eventually chosen (Agora/Twilio/Daily.co/raw
/// WebRTC), this screen is reusable as-is, which is the whole point of
/// building the scaffold before picking one.
///
/// Permissions follow IOS-10's `CameraPermissionGate` convention (re-check on
/// every entry, offer a retry plus a Settings escape hatch) rather than
/// IOS-08's ask-once-ever notification shape — camera and mic are both
/// required for the screen to function at all.
struct PreCallView: View {

    let otherPartyName: String
    let onJoin: () -> Void
    let onCancel: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 16) {
            Text("Call with \(otherPartyName)")
                .font(.displayFont(.semibold, size: 20))
                .foregroundStyle(theme.onBackground)
                .padding(.top, 8)

            CallPermissionsGate { hasPermissions, requestAgain, openSettings in
                if hasPermissions {
                    VStack(spacing: 12) {
                        SelfPreviewView()
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                        Text("Check your camera and microphone before joining.")
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onBackground.opacity(0.7))
                    }
                } else {
                    PermissionsNeededView(onRequest: requestAgain, onOpenSettings: openSettings)
                }

                Button(action: onJoin) {
                    Text("Join now")
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .disabled(!hasPermissions)
                .opacity(hasPermissions ? 1 : 0.5)
                .accessibilityIdentifier("join-now")

                Button(action: onCancel) {
                    Text("Cancel")
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onSurface)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}

// MARK: - Permissions

/// Camera and microphone are both required; the gate only reports "granted"
/// once both are — a caller who can see themselves but can't be heard (or
/// vice versa) shouldn't be waved into a call.
private struct CallPermissionsGate<Content: View>: View {
    @ViewBuilder let content: (_ hasPermissions: Bool, _ requestAgain: @escaping () -> Void, _ openSettings: @escaping () -> Void) -> Content

    @State private var cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var micStatus = AVCaptureDevice.authorizationStatus(for: .audio)

    private var hasPermissions: Bool { cameraStatus == .authorized && micStatus == .authorized }

    var body: some View {
        content(hasPermissions, requestPermissions, openSettings)
            .task { await requestUndeterminedPermissions() }
    }

    private func requestPermissions() {
        Task { await requestUndeterminedPermissions() }
    }

    private func requestUndeterminedPermissions() async {
        if cameraStatus == .notDetermined {
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            cameraStatus = granted ? .authorized : .denied
        }
        if micStatus == .notDetermined {
            let granted = await AVCaptureDevice.requestAccess(for: .audio)
            micStatus = granted ? .authorized : .denied
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct PermissionsNeededView: View {
    let onRequest: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "video.fill")
                .font(.system(size: 44))
                .foregroundStyle(theme.primary)
            Text("Camera and microphone access needed")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onBackground)
                .multilineTextAlignment(.center)
            Text("Poshan for Life needs both to start your video consultation.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onBackground.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)

            Button(action: onRequest) {
                Text("Grant access")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onPrimary)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(theme.primary, in: Capsule())
            }
            Button(action: onOpenSettings) {
                Text("Open app settings")
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.primary)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Self preview

/// Front camera, preview only — no photo/video output, nothing is captured
/// or recorded, matching Android's `SelfPreview` (a CameraX `Preview` use
/// case with no `ImageCapture`/`VideoCapture`).
private struct SelfPreviewView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> SelfPreviewController {
        SelfPreviewController()
    }

    func updateUIViewController(_ uiViewController: SelfPreviewController, context: Context) {}
}

private final class SelfPreviewController: UIViewController {
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "com.poshanforlife.ios.precall-preview")
    private var previewLayer: AVCaptureVideoPreviewLayer?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer

        sessionQueue.async { [weak self] in
            self?.configureSession()
            self?.session.startRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .medium
        defer { session.commitConfiguration() }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            return
        }
        session.addInput(input)
    }

    deinit {
        session.stopRunning()
    }
}
