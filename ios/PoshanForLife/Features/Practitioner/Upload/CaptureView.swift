import AVFoundation
import SwiftUI

/// Entry point for the practitioner report-capture flow. Presented modally
/// (`.fullScreenCover`) from `PatientDetailView`'s Reports tab; owns its own
/// `NavigationStack` so it can push to `ReviewView` once upload succeeds,
/// independent of whatever navigation the presenting screen is using.
struct CaptureView: View {

    let patientId: String
    let repository: ReportsRepository
    /// Fired after a successful confirm — the presenter dismisses this cover
    /// and refreshes its own report list from here.
    let onUploaded: () -> Void

    @StateObject private var viewModel: ReportUploadViewModel
    @Environment(\.dismiss) private var dismiss

    init(patientId: String, repository: ReportsRepository, onUploaded: @escaping () -> Void) {
        self.patientId = patientId
        self.repository = repository
        self.onUploaded = onUploaded
        _viewModel = StateObject(
            wrappedValue: ReportUploadViewModel(patientId: patientId, repository: repository)
        )
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Capture InBody report")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                        }
                    }
                }
                .navigationDestination(
                    isPresented: Binding(
                        get: { viewModel.reportId != nil },
                        set: { presented in if !presented { viewModel.retake() } }
                    )
                ) {
                    ReviewView(viewModel: viewModel) {
                        onUploaded()
                        dismiss()
                    }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.captureState {
        case .ready:
            CameraPermissionGate {
                ZStack {
                    CameraCaptureView(onCaptured: viewModel.onCaptured)
                    CaptureGuideOverlay()
                }
                .ignoresSafeArea()
            }
        case .captured(let url):
            CapturedPhotoReview(
                url: url,
                onRetake: viewModel.retake,
                onUsePhoto: { Task { await viewModel.usePhoto() } }
            )
        case .uploading:
            UploadProgressView()
        case .uploadError(let message):
            UploadErrorView(
                message: message,
                onRetry: { Task { await viewModel.usePhoto() } },
                onRetake: viewModel.retake
            )
        }
    }
}

// MARK: - Camera permission

/// Camera access is required for this screen to function at all, so unlike
/// IOS-08's once-ever notification-permission ask there's no "asked before"
/// bookkeeping — just "granted or not", re-checked every time this appears,
/// with a button the user can retry as often as they land here.
private struct CameraPermissionGate<Content: View>: View {
    @ViewBuilder let content: () -> Content

    @State private var status = AVCaptureDevice.authorizationStatus(for: .video)
    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            if status == .authorized {
                content()
            } else {
                deniedView
            }
        }
        .task {
            if status == .notDetermined {
                let granted = await AVCaptureDevice.requestAccess(for: .video)
                status = granted ? .authorized : .denied
            }
        }
    }

    private var deniedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.system(size: 44))
                .foregroundStyle(theme.primary)
            Text("Camera access needed")
                .font(.displayFont(.semibold, size: 20))
                .foregroundStyle(theme.onSurface)
            Text("Poshan for Life needs your camera to capture the InBody report photo.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Button {
                if status == .notDetermined {
                    Task {
                        let granted = await AVCaptureDevice.requestAccess(for: .video)
                        status = granted ? .authorized : .denied
                    }
                } else if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text(status == .notDetermined ? "Grant camera access" : "Open Settings")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onPrimary)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(theme.primary, in: Capsule())
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
    }
}

// MARK: - Live capture

/// Wraps an `AVCaptureSession` + `AVCapturePhotoOutput` — SwiftUI has no
/// native camera capture view.
private struct CameraCaptureView: UIViewControllerRepresentable {
    let onCaptured: (URL) -> Void

    func makeUIViewController(context: Context) -> CameraCaptureController {
        let controller = CameraCaptureController()
        controller.onCaptured = onCaptured
        return controller
    }

    func updateUIViewController(_ uiViewController: CameraCaptureController, context: Context) {}
}

private final class CameraCaptureController: UIViewController {
    var onCaptured: ((URL) -> Void)?

    private let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "com.poshanforlife.ios.camera-session")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let delegate = PhotoCaptureDelegate()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        delegate.onCaptured = { [weak self] url in
            DispatchQueue.main.async { self?.onCaptured?(url) }
        }

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer

        setupCaptureButton()

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
        session.sessionPreset = .photo
        defer { session.commitConfiguration() }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            return
        }
        session.addInput(input)
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }
    }

    private func setupCaptureButton() {
        let button = UIButton(type: .system)
        button.backgroundColor = .white
        button.layer.cornerRadius = 36
        button.addTarget(self, action: #selector(capturePhoto), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(button)
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 72),
            button.heightAnchor.constraint(equalToConstant: 72),
            button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            button.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32),
        ])
    }

    @objc private func capturePhoto() {
        let settings = AVCapturePhotoSettings()
        sessionQueue.async { [photoOutput, delegate] in
            photoOutput.capturePhoto(with: settings, delegate: delegate)
        }
    }

    deinit {
        session.stopRunning()
    }
}

/// A dedicated delegate object rather than the view controller itself — the
/// controller is deallocated the instant the SwiftUI view it backs goes
/// away, but a photo write can still be in flight, so this outlives it for
/// as long as the capture takes.
private final class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    var onCaptured: ((URL) -> Void)?

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation() else { return }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("inbody-report-\(Int(Date().timeIntervalSince1970 * 1000)).jpg")
        do {
            try data.write(to: url)
            onCaptured?(url)
        } catch {
            // Nothing sensible to surface here — the user just sees the
            // capture button do nothing and can try again.
        }
    }
}

/// Rectangle outline suggesting the report should fill the frame.
private struct CaptureGuideOverlay: View {
    var body: some View {
        GeometryReader { geometry in
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color.white.opacity(0.85), lineWidth: 3)
                .padding(.horizontal, geometry.size.width * 0.08)
                .padding(.top, geometry.size.height * 0.12)
                .padding(.bottom, geometry.size.height * 0.23)
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Review captured photo

private struct CapturedPhotoReview: View {
    let url: URL
    let onRetake: () -> Void
    let onUsePhoto: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            if let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black)
            }

            VStack(spacing: 8) {
                Button(action: onUsePhoto) {
                    Text("Use photo")
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                Button(action: onRetake) {
                    Text("Retake")
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onSurface)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(16)
            .background(theme.background)
        }
        .background(theme.background.ignoresSafeArea())
    }
}

// MARK: - Upload progress

private struct UploadProgressView: View {
    private static let messages = [
        "Uploading…",
        "Extracting health data with AI…",
        "Analysing fields…",
    ]

    @State private var messageIndex = 0
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(Self.messages[messageIndex])
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)
                .contentTransition(.opacity)
                .multilineTextAlignment(.center)
            Text("Powered by Claude AI")
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.6))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background.ignoresSafeArea())
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_800_000_000)
                guard !Task.isCancelled else { return }
                withAnimation { messageIndex = (messageIndex + 1) % Self.messages.count }
            }
        }
    }
}

// MARK: - Upload error

private struct UploadErrorView: View {
    let message: String
    let onRetry: () -> Void
    let onRetake: () -> Void
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 16) {
            Text("Upload failed")
                .font(.displayFont(.semibold, size: 20))
                .foregroundStyle(theme.error)
            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)

            Button(action: onRetry) {
                Text("Try again")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(theme.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            Button(action: onRetake) {
                Text("Retake photo")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background.ignoresSafeArea())
    }
}
