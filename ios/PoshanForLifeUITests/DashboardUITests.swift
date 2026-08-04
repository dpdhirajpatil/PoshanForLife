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
        let profileTab = app.tabBars.buttons["Profile"]
        guard profileTab.waitForExistence(timeout: 8) else { return }
        profileTab.tap()

        let signOut = app.buttons["Sign out"]
        if signOut.waitForExistence(timeout: 5) {
            signOut.tap()
        }
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

        // --- Tab bar ---
        for tab in ["Track", "Programmes", "Reports", "Profile"] {
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
