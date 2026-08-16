import XCTest

/// IOS-08 — the permission rationale sheet, the real system prompt, and the
/// in-app notification bell/list.
///
/// `testRationaleSheetThenSystemPrompt` needs a genuinely fresh app
/// container — the "have we asked" flag lives in UserDefaults, which
/// `simctl uninstall` wipes (same as IOS-04's local JSON cache) — so unlike
/// every other UI test class here, this one is meant to run right after a
/// clean reinstall, not against whatever state the simulator is already in.
/// The bell/list test has no such requirement.
final class PushNotificationsUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Deliberately does NOT call `signOutIfSignedIn` — that helper dismisses
    /// the rationale sheet defensively (so every *other* test isn't blocked
    /// by it), and doing that here would consume the one-shot "have we
    /// asked" flag before this test ever gets to see the sheet appear on its
    /// own. This test's precondition is instead external: run it against a
    /// simulator the app was just `simctl uninstall`'d from (wipes the
    /// UserDefaults flag) — not something a UI test can do to itself.
    ///
    /// The Keychain survives that uninstall (documented gotcha — the app
    /// container is wiped, the Keychain is not), so the fresh install's cold
    /// launch restores straight into a signed-in session and the rationale
    /// sheet fires immediately, with no explicit sign-in needed. If for some
    /// reason no session survived, this falls back to signing in manually.
    func testRationaleSheetThenSystemPrompt() throws {
        let rationale = app.staticTexts["Stay in the loop"]
        let loginField = app.textFields.firstMatch

        guard XCTWaiter.wait(for: [
            XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in rationale.exists || loginField.exists }, object: nil)
        ], timeout: 25) == .completed else {
            XCTFail("neither the rationale sheet nor a login screen appeared on a fresh launch")
            return
        }

        if !rationale.exists {
            loginField.tap()
            loginField.typeText("testpatient1@example.com")
            let password = app.secureTextFields.firstMatch
            password.tap()
            password.typeText("Admin@123")
            app.buttons["Sign in"].tap()
        }

        XCTAssertTrue(
            rationale.waitForExistence(timeout: 20),
            "the notification rationale sheet never appeared on a fresh install"
        )
        snapshot("01-rationale-sheet")

        // The real system permission dialog is UIKit chrome outside the app's
        // own view hierarchy — it has to be handled via an interruption
        // monitor, registered before it can appear. Simulator genuinely
        // shows this dialog (unlike some CI sandboxes), so this is exercising
        // the real OS prompt, not a stand-in for it.
        let systemAlertHandled = expectation(description: "system notification permission alert")
        let monitor = addUIInterruptionMonitor(withDescription: "Notification permission") { alert in
            let allow = alert.buttons["Allow"]
            guard allow.waitForExistence(timeout: 5) else { return false }
            allow.tap()
            systemAlertHandled.fulfill()
            return true
        }
        defer { removeUIInterruptionMonitor(monitor) }

        app.buttons["enable-notifications"].tap()
        // Interruption monitors only fire on the next interaction with `app`,
        // so this otherwise-harmless tap is what actually invokes the handler
        // above rather than the alert just sitting there.
        app.tap()

        wait(for: [systemAlertHandled], timeout: 15)
        snapshot("02-after-system-prompt")

        // Sheet must be gone and normal navigation must work again — proves
        // this didn't leave the app stuck behind a dismissed-but-not-really
        // modal.
        XCTAssertTrue(
            app.tabBars.buttons["More"].waitForExistence(timeout: 20),
            "app never returned to the patient tab bar after the permission flow"
        )
    }

    func testBellIconAndNotificationList() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "More")

        // Bell lives on the Home tab's toolbar for a patient — see
        // DashboardView.
        app.tabBars.buttons["Home"].tap()
        let bell = app.buttons["notification-bell"]
        XCTAssertTrue(bell.waitForExistence(timeout: 20), "no notification bell on the patient Home toolbar")
        snapshot("03-bell-on-home")

        bell.tap()
        XCTAssertTrue(
            app.navigationBars["Notifications"].waitForExistence(timeout: 15),
            "notification list sheet never appeared"
        )
        snapshot("04-notification-list")

        // Either a real row or the empty state — both are valid depending on
        // what this account has accumulated from other tests' API calls
        // (booking an appointment notifies the practitioner, not this
        // patient, so this account's own inbox is not guaranteed non-empty).
        let hasEmptyState = app.staticTexts["No notifications yet"].waitForExistence(timeout: 10)
        let hasRows = app.cells.firstMatch.exists
        XCTAssertTrue(hasEmptyState || hasRows, "neither the empty state nor any row rendered")

        // "Mark all read" must be disabled with nothing unread — this is the
        // fully-caught-up state XCTAssertTrue above already established is
        // possible.
        if hasEmptyState {
            XCTAssertFalse(app.buttons["Mark all read"].isEnabled,
                           "Mark all read should be disabled with zero notifications")
        }

        app.buttons["Close"].tap()
        XCTAssertFalse(app.navigationBars["Notifications"].exists, "sheet didn't dismiss")
    }

    func testPractitionerScheduleHasBell() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Schedule")

        app.tabBars.buttons["Schedule"].tap()
        XCTAssertTrue(
            app.buttons["notification-bell"].waitForExistence(timeout: 20),
            "no notification bell on the practitioner Schedule toolbar"
        )
        snapshot("05-practitioner-bell")

        // Composes with AppointmentsListView's own toolbar rather than
        // replacing it — the book-appointment button is patient-only and
        // must still be absent here.
        XCTAssertFalse(app.buttons["book-appointment"].exists,
                       "practitioner must not see the patient's book-appointment action")
    }
}
