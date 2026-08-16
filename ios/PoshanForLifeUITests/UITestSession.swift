import XCTest

/// Shared sign-in/sign-out driving, so the three UI test classes don't each
/// carry their own copy — they did, and every change to where "Sign out" lives
/// meant fixing it in three places.
///
/// Both roles now reach sign-out the same way: **More → Profile** for a
/// patient, **More → Settings** for a practitioner. Neither has a Profile tab;
/// SwiftUI's five-tab ceiling pushed both behind an explicit More screen (see
/// `PatientTabView`'s header).
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
        let deadline = Date().addingTimeInterval(25)

        while Date() < deadline {
            if app.textFields.firstMatch.exists && !more.exists { return }  // already at login
            if more.exists { break }
            usleep(300_000)
        }
        guard more.exists else { return }
        more.tap()

        // Patient keeps sign-out on Profile; practitioner on Settings.
        for label in ["Profile", "Settings"] {
            let row = app.staticTexts[label]
            if row.waitForExistence(timeout: 3) {
                row.tap()
                break
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
