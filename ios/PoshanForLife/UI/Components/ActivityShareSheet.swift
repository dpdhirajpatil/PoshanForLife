import SwiftUI
import UIKit

/// Thin wrapper over `UIActivityViewController` — SwiftUI's `ShareLink` needs
/// its `item` known up front, but a document's PDF URL is a signed link
/// minted by an async call (`DocumentsRepository.pdfUrl`), not something the
/// view has when it's built. Matches the Android build's generic-share-sheet
/// approach.
struct ActivityShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
