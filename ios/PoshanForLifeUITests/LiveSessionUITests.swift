import XCTest

/// IOS-13 — the "Join call" join-window gating on the appointments list, and
/// the pre-call scaffold (permissions + self-preview + placeholder call
/// screen) it opens into. Not a working video call — see `LiveSessionView`'s
/// doc comment. The system camera/mic permission alerts aren't reliably
/// scriptable across Simulator versions (same caveat `ReportUploadUITests`
/// notes for IOS-10's capture screen), so this attempts to grant them but
/// treats reaching each screen, not a granted permission, as the hard
/// requirement.
final class LiveSessionUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()

        addUIInterruptionMonitor(withDescription: "Camera/microphone permission") { alert in
            let allow = alert.buttons["OK"].exists ? alert.buttons["OK"] : alert.buttons["Allow"]
            if allow.exists {
                allow.tap()
                return true
            }
            return false
        }

        app.launch()
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testJoinCallWindowGatingAndPreCallScaffold() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "Track")

        app.tabBars.buttons["More"].tap()
        app.staticTexts["Appointments"].tap()
        XCTAssertTrue(app.navigationBars["Appointments"].waitForExistence(timeout: 15), "Appointments screen never appeared")

        let joinButtons = app.buttons.matching(identifier: "join-call")
        XCTAssertTrue(joinButtons.firstMatch.waitForExistence(timeout: 15), "no 'Join call' row found — expected at least one video appointment")
        snapshot("01-appointments-with-join-buttons")

        // Tap through to a live one — the test setup rescheduled a video
        // appointment to a couple of minutes from now, inside the -5min/+30min
        // join window, so at least one "Join call" button should be enabled.
        var tappedLiveButton = false
        for i in 0..<joinButtons.count {
            let button = joinButtons.element(boundBy: i)
            if button.isEnabled {
                button.tap()
                tappedLiveButton = true
                break
            }
        }
        XCTAssertTrue(tappedLiveButton, "no enabled 'Join call' button found — is the seeded appointment still inside its join window?")

        // Pre-call screen: title, self-preview or permission prompt, Join now/Cancel.
        XCTAssertTrue(app.staticTexts["Call with Test Practitioner"].waitForExistence(timeout: 10), "pre-call screen never appeared")
        XCTAssertTrue(app.buttons["join-now"].exists)
        snapshot("02-precall-screen")

        // Give the permission `.task` a moment to fire the system alerts.
        // XCUITest only checks for an interrupting element around an actual
        // synthesized action (a `.tap()`), not a bare `.exists`/`.isEnabled`
        // query — so tap something inert (the title) each pass to keep
        // giving it that chance, rather than just polling state.
        let title = app.staticTexts["Call with Test Practitioner"]
        let joinNow = app.buttons["join-now"]
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline, !joinNow.isEnabled {
            if title.exists { title.tap() }
        }
        snapshot("03-precall-after-permission")

        if joinNow.isEnabled {
            joinNow.tap()
            XCTAssertTrue(app.staticTexts["Video calling coming soon"].waitForExistence(timeout: 10), "placeholder call screen never appeared")
            snapshot("04-live-session-placeholder")

            app.buttons["Back to appointments"].tap()
            XCTAssertTrue(app.navigationBars["Appointments"].waitForExistence(timeout: 10), "didn't return to Appointments after leaving the call")
        } else {
            // Permission wasn't granted in this run (Simulator flakiness) —
            // still real: Cancel returns cleanly to Appointments.
            app.buttons["Cancel"].tap()
            XCTAssertTrue(app.navigationBars["Appointments"].waitForExistence(timeout: 10), "didn't return to Appointments after cancelling")
        }
    }
}
