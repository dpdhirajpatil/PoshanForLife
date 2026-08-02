import Foundation
import Security

/// Token persistence seam. A protocol so tests (and previews) can substitute an
/// in-memory store without touching the real Keychain.
protocol TokenStore: AnyObject {
    func saveTokens(access: String, refresh: String) throws
    func accessToken() throws -> String?
    func refreshToken() throws -> String?
    func clear() throws
}

enum KeychainError: Error, Equatable {
    case unexpectedData
    case status(OSStatus)
}

/// JWTs in the Keychain, never `UserDefaults` — that plist is unencrypted and
/// is backed up in the clear to iCloud/iTunes by default.
///
/// Items use `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`:
/// *AfterFirstUnlock* so a background refresh on a locked device can still read
/// the token, *ThisDeviceOnly* so tokens are excluded from iCloud Keychain sync
/// and from encrypted backups restored onto a different device.
final class KeychainTokenStore: TokenStore {

    private let service: String

    private enum Key {
        static let access = "access_token"
        static let refresh = "refresh_token"
    }

    init(service: String = Bundle.main.bundleIdentifier ?? "com.poshanforlife.ios") {
        self.service = service
    }

    func saveTokens(access: String, refresh: String) throws {
        try set(access, for: Key.access)
        try set(refresh, for: Key.refresh)
    }

    func accessToken() throws -> String? {
        try get(Key.access)
    }

    func refreshToken() throws -> String? {
        try get(Key.refresh)
    }

    func clear() throws {
        try delete(Key.access)
        try delete(Key.refresh)
    }

    // MARK: - Keychain Services

    private func set(_ value: String, for account: String) throws {
        guard let data = value.data(using: .utf8) else {
            throw KeychainError.unexpectedData
        }

        // Update-then-add rather than delete-then-add: a delete/add pair leaves a
        // window where the token is absent, and an interrupted write would sign
        // the user out silently.
        let query = baseQuery(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainError.status(updateStatus)
        }

        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw KeychainError.status(addStatus)
        }
    }

    private func get(_ account: String) throws -> String? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else {
            throw KeychainError.status(status)
        }
        guard let data = item as? Data, let value = String(data: data, encoding: .utf8) else {
            throw KeychainError.unexpectedData
        }
        return value
    }

    private func delete(_ account: String) throws {
        let status = SecItemDelete(baseQuery(account: account) as CFDictionary)
        // Already gone is the state the caller wanted.
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.status(status)
        }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

/// Non-persistent store for tests and SwiftUI previews.
final class InMemoryTokenStore: TokenStore {
    private var access: String?
    private var refresh: String?

    init(access: String? = nil, refresh: String? = nil) {
        self.access = access
        self.refresh = refresh
    }

    func saveTokens(access: String, refresh: String) throws {
        self.access = access
        self.refresh = refresh
    }

    func accessToken() throws -> String? { access }
    func refreshToken() throws -> String? { refresh }

    func clear() throws {
        access = nil
        refresh = nil
    }
}
