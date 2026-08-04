import Foundation

enum CurrencyFormatter {
    private static let formatter: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "INR"
        f.currencySymbol = "₹"
        // en_IN groups as 1,23,456 — the convention these amounts are read in.
        f.locale = Locale(identifier: "en_IN")
        return f
    }()

    static func inr(_ value: Double) -> String {
        formatter.string(from: NSNumber(value: value)) ?? "₹\(value)"
    }
}
