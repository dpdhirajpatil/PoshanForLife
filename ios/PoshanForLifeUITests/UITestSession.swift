import XCTest

/// Shared sign-in/sign-out driving, so the three UI test classes don't each
/// carry their own copy — they did, and every change to where "Sign out" lives
/// meant fixing it in three places.
///
/// Three roles now reach sign-out this way: **More → Profile** for a patient,
/// **More → Settings** for a practitioner, and (IOS-18) a **direct Profile
/// tab** for a Lead — `LeadTabView`'s four-tab design puts Profile in the bar
/// itself rather than behind More, since a Lead has nowhere near five tabs'
/// worth of destinations to begin with.
extension XCTestCase {

    /// Drops any restored session. Safe to call when already signed out.
    ///
    /// Waits generously: a cold launch restores the Keychain session over the
    /// network before any tab bar exists, and a tight timeout here surfaces
    /// later as a baffling "login screen never appeared".
    func signOutIfSignedIn(_ app: XCUIApplication) {
        // A cold launch with a surviving Keychain session (uninstall wipes
        // the app container but not the Keychain — see the iOS memory notes)
        // restores straight into a signed-in state, which fires IOS-08's
        // rationale sheet before this function ever gets to tap "More".
        dismissNotificationRationaleIfPresent(app)

        let more = app.tabBars.buttons["More"]
        // Admin has no tab bar at all — `AdminRootView` is a plain
        // `NavigationStack` over a settings-style list, so "Settings" (with
        // sign-out) sits directly on the root screen instead of behind More.
        let adminRoot = app.navigationBars["Admin"]
        // Lead only: a tab bar button, not a row behind More — see this
        // extension's doc comment.
        let profileTab = app.tabBars.buttons["Profile"]
        let deadline = Date().addingTimeInterval(25)

        while Date() < deadline {
            if app.textFields.firstMatch.exists && !more.exists && !adminRoot.exists && !profileTab.exists { return }  // already at login
            if more.exists || adminRoot.exists || profileTab.exists { break }
            usleep(300_000)
        }
        // Captured once: `profileTab` (a live query) would still report
        // "exists" after tapping into the More tab too on some role's bar,
        // so which branch to take has to be decided before anything is
        // tapped, not re-derived from these same elements afterward.
        let hasMore = more.exists
        let hasAdminRoot = adminRoot.exists
        let hasDirectProfileTab = !hasMore && profileTab.exists
        guard hasMore || hasAdminRoot || hasDirectProfileTab else { return }

        if hasMore {
            more.tap()
        } else if hasDirectProfileTab {
            profileTab.tap()
        }

        // Lead's Profile tab lands directly on the screen with Sign out
        // already on it — nothing further to tap into. Patient/practitioner
        // (behind More) and admin (direct root list) both still need to
        // find the Profile/Settings row first.
        if !hasDirectProfileTab {
            for label in ["Profile", "Settings"] {
                let row = app.staticTexts[label]
                if row.waitForExistence(timeout: 3) {
                    row.tap()
                    break
                }
            }
        }

        let signOut = app.buttons["Sign out"]
        guard signOut.waitForExistence(timeout: 10) else { return }
        signOut.tap()

        XCTAssertTrue(
            app.textFields.firstMatch.waitForExistence(timeout: 25),
            "tapped Sign out but never returned to the login screen"
        )
    }

    /// Signs in and waits for the given tab to prove the role routed correctly.
    func signIn(
        _ app: XCUIApplication,
        email: String,
        password: String,
        expecting tab: String
    ) {
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 20), "login screen never appeared")
        field.tap()
        field.typeText(email)

        let secure = app.secureTextFields.firstMatch
        secure.tap()
        secure.typeText(password)
        app.buttons["Sign in"].tap()

        dismissNotificationRationaleIfPresent(app)

        XCTAssertTrue(
            app.tabBars.buttons[tab].waitForExistence(timeout: 30),
            "signed in as \(email) but the \(tab) tab never appeared"
        )
    }

    /// IOS-08's "why we want to notify you" sheet appears on the first
    /// sign-in after a fresh install (a per-device UserDefaults flag, not a
    /// per-account one) and is a modal `.sheet` — left up, it blocks every
    /// tab-bar tap every other test performs right after signing in. "Not
    /// now" rather than granting the real permission: accepting would pop
    /// the system alert too, which needs `addUIInterruptionMonitor`
    /// registered in advance to dismiss reliably, and no other test cares
    /// about the granted/denied state, only that nothing is left blocking
    /// the screen.
    private func dismissNotificationRationaleIfPresent(_ app: XCUIApplication) {
        let skip = app.buttons["skip-notifications"]
        if skip.waitForExistence(timeout: 5) {
            skip.tap()
        }
    }
}
