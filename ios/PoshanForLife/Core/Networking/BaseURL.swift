import Foundation

/// Where the app talks to the Spring Boot backend, resolved from the build
/// configuration rather than a runtime toggle.
///
/// The Simulator shares the host's network stack, so `localhost` reaches a
/// backend running on this Mac directly — unlike Android, whose emulator needs
/// the `10.0.2.2` host alias (see `android/app/build.gradle.kts`).
///
/// A physical device is on the LAN and needs the Mac's actual IP. That varies
/// per machine and per network, so it comes from the Debug Info.plist key
/// `DevLANHost`, fed by `DEV_LAN_HOST` in the xcconfig. Override it in a
/// gitignored `Local.xcconfig` instead of editing the checked-in default.
enum BaseURL {

    static var current: URL {
        guard let url = URL(string: currentString) else {
            // Only reachable if an xcconfig override is malformed; failing loudly
            // in development beats every request silently going nowhere.
            fatalError("Invalid base URL: \(currentString)")
        }
        return url
    }

    private static var currentString: String {
        #if DEBUG
            #if targetEnvironment(simulator)
                return "http://localhost:8080"
            #else
                return "http://\(devLANHost):8080"
            #endif
        #else
            return "https://api.poshanforlife.com"
        #endif
    }

    /// Placeholder default — real value belongs in `Config/Local.xcconfig`.
    private static let fallbackLANHost = "192.168.1.42"

    private static var devLANHost: String {
        let configured = Bundle.main.object(forInfoDictionaryKey: "DevLANHost") as? String
        // An unsubstituted "$(DEV_LAN_HOST)" means the xcconfig never defined it.
        guard let configured,
              !configured.isEmpty,
              !configured.hasPrefix("$(")
        else {
            return fallbackLANHost
        }
        return configured
    }
}
