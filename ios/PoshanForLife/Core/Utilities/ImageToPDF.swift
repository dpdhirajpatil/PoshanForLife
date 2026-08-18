import UIKit

/// The backend's upload endpoint strictly requires `application/pdf` — its
/// extraction pipeline is PDFBox-based and rejects any other content type,
/// including `image/jpeg` — so a camera-captured photo can't be sent as-is.
/// Mirrors Android's `wrapImageAsPdf`: a single-page PDF sized to the image's
/// own pixel dimensions, not a fixed Letter/A4 page.
enum ImageToPDF {
    static func wrap(_ image: UIImage) -> Data? {
        let pageRect = CGRect(origin: .zero, size: image.size)
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        return renderer.pdfData { context in
            context.beginPage()
            image.draw(in: pageRect)
        }
    }
}
