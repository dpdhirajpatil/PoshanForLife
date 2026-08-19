import XCTest

/// IOS-11 — Apple Health connect/sync entry points: the Track tab's
/// "Connect Apple Health" card and the Profile screen's status row. Doesn't
/// assert real synced values — the Simulator has no seeded Health data —
/// only that the connect flow completes and both screens reflect it.
final class HealthKitSyncUITests: XCTestCase {

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

    func testConnectFromTrackTabAndProfileReflectsStatus() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "Track")

        app.tabBars.buttons["Track"].tap()
        XCTAssertTrue(app.navigationBars["Track"].waitForExistence(timeout: 15), "Track tab never appeared")

        let connectButton = app.buttons["Connect Apple Health"]
        XCTAssertTrue(connectButton.waitForExistence(timeout: 15), "no Connect Apple Health button on the Track tab")
        snapshot("01-track-not-connected")
        connectButton.tap()

        // The HealthKit permission sheet is presented by a separate system
        // process, but its elements are reachable straight off `app` rather
        // than through an interruption monitor (unlike the camera alert in
        // IOS-10's CaptureView test, which is a plain system alert). Its
        // "Allow" button starts out *disabled* until at least one category
        // switch is on, so "Turn On All" — a `Cell`, not a `Button` — has to
        // be tapped first, or tapping "Allow" is a silent no-op that leaves
        // the sheet on screen forever.
        let turnOnAll = app.cells["UIA.Health.AuthSheet.AllCategoryButton"]
        XCTAssertTrue(turnOnAll.waitForExistence(timeout: 15), "HealthKit permission sheet never appeared")
        turnOnAll.tap()
        let allow = app.buttons["UIA.Health.AuthSheet.DoneButton"]
        XCTAssertTrue(allow.waitForExistence(timeout: 5), "Allow button missing from the HealthKit permission sheet")
        XCTAssertTrue(allow.isEnabled, "Allow should enable once Turn On All is tapped")
        allow.tap()

        XCTAssertTrue(
            app.staticTexts["Connected"].waitForExistence(timeout: 20)
                || app.buttons["Sync now"].waitForExistence(timeout: 5),
            "Track tab never reflected a connected HealthKit status"
        )
        snapshot("02-track-connected")

        // Profile screen should show the same connected status.
        app.tabBars.buttons["More"].tap()
        app.staticTexts["Profile"].tap()
        XCTAssertTrue(app.navigationBars["Profile"].waitForExistence(timeout: 15), "Profile screen never appeared")
        XCTAssertTrue(
            app.staticTexts["Connected"].waitForExistence(timeout: 15),
            "Profile's Apple Health row should read Connected after Track's connect flow"
        )
        snapshot("03-profile-connected")
    }
}
