import XCTest

/// IOS-10 — the Practitioner's report-capture entry point from a patient's
/// Reports tab. Doesn't drive an actual capture: the Simulator has no InBody
/// printout to photograph, and the system camera-permission alert isn't
/// reliably scriptable across Simulator versions. This only proves the
/// screen reaches, presents, and tears down cleanly — same
/// backend/credentials caveat as `PatientManagementUITests`.
final class ReportUploadUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()

        // The camera-permission `.task` fires `AVCaptureDevice.requestAccess`
        // as soon as CaptureView appears, which can raise the system alert —
        // dismiss it if it shows so the rest of the test isn't blocked.
        addUIInterruptionMonitor(withDescription: "Camera permission") { alert in
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

    func testCaptureScreenReachableFromPatientReports() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["Patients"].tap()

        let patientRow = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Test Patient")
        ).firstMatch
        XCTAssertTrue(patientRow.waitForExistence(timeout: 20), "the assigned patient row never appeared")
        patientRow.tap()

        XCTAssertTrue(
            app.navigationBars["Test Patient"].waitForExistence(timeout: 15),
            "patient detail never appeared"
        )
        app.buttons["Reports"].tap()
        XCTAssertTrue(app.staticTexts["No reports yet"].waitForExistence(timeout: 15)
            || app.staticTexts.containing(NSPredicate(format: "label CONTAINS[c] %@", "InBody")).firstMatch.waitForExistence(timeout: 5),
            "Reports tab never loaded")

        // Camera toolbar button — the fullScreenCover entry point to CaptureView.
        let cameraButton = app.navigationBars.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@", "camera")
        ).firstMatch
        XCTAssertTrue(cameraButton.waitForExistence(timeout: 10), "no camera button on the Reports tab")
        cameraButton.tap()

        XCTAssertTrue(
            app.navigationBars["Capture InBody report"].waitForExistence(timeout: 15),
            "tapping the camera button should present CaptureView"
        )
        // Let the interruption monitor get a chance to fire and dismiss any
        // system permission alert before we screenshot and tear down.
        app.tap()
        snapshot("01-capture-presented")

        // Dismiss back to the patient detail.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(
            app.navigationBars["Test Patient"].waitForExistence(timeout: 15),
            "closing the capture cover should return to the patient detail"
        )
    }
}
