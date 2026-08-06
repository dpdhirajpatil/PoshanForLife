import SafariServices
import SwiftUI

/// `SFSafariViewController` in SwiftUI clothing.
///
/// Chosen over an in-app `PDFKit` renderer deliberately, matching the Android
/// build's `CustomTabsIntent`: the file URLs are short-lived signed links to a
/// private bucket, and Safari already handles PDF rendering, pinch-zoom,
/// sharing and printing for free.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.barCollapsingEnabled = true
        return SFSafariViewController(url: url, configuration: config)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
