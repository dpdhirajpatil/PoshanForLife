import Foundation

/// Lenient ISO-8601 parsing.
///
/// `JSONDecoder.DateDecodingStrategy.iso8601` rejects fractional seconds, and
/// the backend's `Instant` fields serialise with them — so decoding those
/// straight into `Date` throws and takes the whole response down with it.
/// Timestamps therefore stay `String` on the wire and are parsed here, where a
/// bad value costs one nil instead of an entire screen.
enum ISO8601 {

    private static let withFractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    /// Plain `yyyy-MM-dd`, which is what `LocalDate` fields look like.
    private static let dayOnly: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    static func date(from string: String?) -> Date? {
        guard let string, !string.isEmpty else { return nil }
        return withFractional.date(from: string)
            ?? plain.date(from: string)
            ?? dayOnly.date(from: String(string.prefix(10)))
    }
}
