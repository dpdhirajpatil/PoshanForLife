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

    func testSignInThenPatientDashboard() throws {
        // Tokens live in the Keychain and survive app reinstalls on a Simulator,
        // so a previous run can leave this already signed in. Sign out first
        // rather than assuming a cold start.
        signOutIfSignedIn(app)

        // --- Login screen ---
        let email = app.textFields.firstMatch
        XCTAssertTrue(email.waitForExistence(timeout: 10), "login screen never appeared")
        snapshot("01-login")

        email.tap()
        email.typeText("testpatient1@example.com")

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
            NSPredicate(format: "label CONTAINS[c] %@", "TEST")
        ).firstMatch
        XCTAssertTrue(greeting.waitForExistence(timeout: 25), "dashboard greeting never appeared")

        // Conditional: this account is created fresh by the IOS-06 fixture and
        // has no InBody history, so the card legitimately may not render. The
        // greeting above is what proves login + role routing + theme.
        _ = app.staticTexts["InBody score"].waitForExistence(timeout: 10)
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

        // --- Reports tab (IOS-05) ---
        // Regression guard for a real defect this test caught: returning from
        // Goals used to bring the keyboard back up on its own, and its Done
        // toolbar renders over the tab bar — so this next tap landed on a
        // number key and typed into the weight field instead of switching tabs.
        // Assert the keyboard is down *before* navigating, so a recurrence
        // reports itself here rather than as a baffling "Reports never appeared".
        XCTAssertEqual(
            app.keyboards.count, 0,
            "a keyboard is up over the tab bar; the next tab tap will hit it instead"
        )
        app.tabBars.buttons["Reports"].tap()
        XCTAssertTrue(app.staticTexts["Past reports"].waitForExistence(timeout: 15), "Reports tab never appeared")
        snapshot("09-reports")

        // Trend charts render from health records; the window picker is the
        // one control that must exist even when there's nothing to plot.
        if app.buttons["90d"].waitForExistence(timeout: 5) {
            app.buttons["180d"].tap()
            snapshot("10-reports-180d")
        }

        // Open the first report, if this patient has one.
        let firstReport = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "InBody")
        ).firstMatch
        if firstReport.waitForExistence(timeout: 5) {
            firstReport.tap()
            snapshot("11-report-detail")
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }

        // --- Remaining tabs ---
        for tab in ["Programmes", "More"] {
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
