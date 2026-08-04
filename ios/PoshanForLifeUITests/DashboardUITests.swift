import XCTest

/// Drives the real app on a Simulator: signs in as a patient and walks the
/// dashboard. Needs the backend running on localhost:8080 and the credentials
/// below to be valid, so it's an integration smoke test rather than something
/// to wire into CI unchanged.
final class DashboardUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    /// Captures whatever is on screen into the .xcresult bundle, so the run can
    /// be inspected afterwards rather than only asserted on.
    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func signOutIfSignedIn() {
        // Generous waits: a cold launch restores the session over the network
        // before the tab bar exists, and an under-tight timeout here surfaces
        // later as a baffling "login screen never appeared".
        let profileTab = app.tabBars.buttons["Profile"]
        guard profileTab.waitForExistence(timeout: 20) else { return }  // already signed out
        profileTab.tap()

        let signOut = app.buttons["Sign out"]
        XCTAssertTrue(signOut.waitForExistence(timeout: 10), "Profile tab has no Sign out button")
        signOut.tap()

        // Assert the outcome rather than assuming it: if signing out stops
        // working, this should say so directly.
        XCTAssertTrue(
            app.textFields.firstMatch.waitForExistence(timeout: 20),
            "tapped Sign out but never returned to the login screen"
        )
    }

    func testSignInThenPatientDashboard() throws {
        // Tokens live in the Keychain and survive app reinstalls on a Simulator,
        // so a previous run can leave this already signed in. Sign out first
        // rather than assuming a cold start.
        signOutIfSignedIn()

        // --- Login screen ---
        let email = app.textFields.firstMatch
        XCTAssertTrue(email.waitForExistence(timeout: 10), "login screen never appeared")
        snapshot("01-login")

        email.tap()
        email.typeText("neha.kapoor@example.com")

        let password = app.secureTextFields.firstMatch
        password.tap()
        password.typeText("Admin@123")
        snapshot("02-login-filled")

        app.buttons["Sign in"].tap()

        // --- Dashboard ---
        // The greeting is uppercased by the Patient theme, so match on the
        // uppercased name: seeing it proves login, role routing AND the
        // Patient theme's heading rule all worked.
        let greeting = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "NEHA")
        ).firstMatch
        XCTAssertTrue(greeting.waitForExistence(timeout: 25), "dashboard greeting never appeared")

        XCTAssertTrue(app.staticTexts["InBody score"].waitForExistence(timeout: 15))
        snapshot("03-dashboard")

        // Scroll to reach the lower cards.
        app.swipeUp()
        snapshot("04-dashboard-scrolled")

        // --- Track tab (IOS-04) ---
        app.tabBars.buttons["Track"].tap()
        XCTAssertTrue(app.staticTexts["Water"].waitForExistence(timeout: 10), "Track tab never appeared")
        snapshot("05-track")

        // Quick-add is local-first, so the total must update without a network
        // round trip. Asserted as a delta, not an absolute: the on-disk cache
        // legitimately survives between runs, so "today" may already have water
        // logged against it.
        // .firstMatch: SwiftUI propagates the identifier to the wrapping
        // element as well, so the plain subscript is ambiguous.
        let waterTotal = app.staticTexts.matching(identifier: "water-total").firstMatch
        XCTAssertTrue(waterTotal.waitForExistence(timeout: 5), "water total not found")
        let before = Int((waterTotal.value as? String ?? "").filter(\.isNumber)) ?? 0
        app.buttons["+250 ml"].tap()
        app.buttons["+250 ml"].tap()

        // Compare digits only. iOS localises numeric accessibility values, so
        // this element reads "2,000 ml" — an exact string match against
        // "2000 ml" fails even when the logging worked perfectly.
        let expected = before + 500
        let deadline = Date().addingTimeInterval(5)
        var current = before
        while Date() < deadline {
            current = Int((waterTotal.value as? String ?? "").filter(\.isNumber)) ?? before
            if current == expected { break }
            usleep(200_000)
        }
        XCTAssertEqual(current, expected, "water total didn't rise by 500 after two +250 ml taps")
        snapshot("06-track-water-logged")

        // Weight is the one metric that syncs; log it and confirm the field
        // clears, which only happens on the success path.
        let weightField = app.textFields["kg"]
        if weightField.waitForExistence(timeout: 5) {
            weightField.tap()
            weightField.typeText("70.5")
            app.buttons["Log"].tap()
            XCTAssertTrue(
                app.staticTexts["Last logged: 70.5 kg"].waitForExistence(timeout: 10),
                "weight wasn't recorded locally"
            )
            snapshot("07-track-weight-logged")
        }

        // Goals settings, reached from the Track toolbar.
        app.buttons["Goals"].tap()
        XCTAssertTrue(app.staticTexts["Daily steps"].waitForExistence(timeout: 5))
        snapshot("08-goals")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // --- Remaining tabs ---
        for tab in ["Programmes", "Reports", "Profile"] {
            let button = app.tabBars.buttons[tab]
            if button.exists {
                button.tap()
                snapshot("05-tab-\(tab)")
            }
        }

        app.tabBars.buttons["Home"].tap()
        snapshot("06-back-home")
    }
}
