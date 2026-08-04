import Foundation

/// Codable-to-disk persistence for small collections.
///
/// **iOS 16 vs 17 trade-off, deliberate:** SwiftData would be the natural
/// choice here — a model macro, a `@Query`, done — but it is iOS 17+ and this
/// app targets iOS 16, so it isn't available. The alternatives were a
/// third-party SQLite wrapper (GRDB), which buys real querying at the cost of
/// the project's zero-dependency rule, or this. Given the data is a few hundred
/// small rows read wholesale on a single screen and filtered in memory, a JSON
/// file is enough and costs nothing. **Raise the deployment target to 17 and
/// this whole file should be deleted in favour of SwiftData** — the store
/// protocol below is what the rest of the app talks to, so that swap is
/// contained.
///
/// An `actor` so concurrent writes can't interleave and corrupt the file.
actor JSONFileStore<Element: Codable> {

    private let url: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(filename: String) {
        let directory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        // Application Support isn't guaranteed to exist on a fresh install.
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        self.url = directory.appendingPathComponent(filename)
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    func load() -> [Element] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        // A corrupt or half-written file returns empty rather than throwing:
        // losing local cache is recoverable, refusing to open the screen is not.
        return (try? decoder.decode([Element].self, from: data)) ?? []
    }

    func save(_ elements: [Element]) {
        guard let data = try? encoder.encode(elements) else { return }
        // Atomic so a crash mid-write can't leave a truncated file behind.
        try? data.write(to: url, options: .atomic)
    }
}
