import Foundation

/// A trimmed `CatalogueItemDto` — just enough for the convert-to-patient
/// flow's inline service picker. Not IOS-15 (the full catalogue browse/edit
/// feature, which hasn't landed yet): this mirrors Android's own convert
/// screen, which also queries the catalogue endpoints directly rather than
/// routing through a dedicated picker screen.
struct CatalogueItem: Decodable, Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    let priceInr: Double?
}
