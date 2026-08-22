import XCTest

/// IOS-14 — the Practitioner Leads tab: list (filters/search/summary),
/// detail (stage change, log activity, schedule follow-up), and the
/// convert-to-patient flow's deep link to the new patient's detail screen.
/// Requires the local backend seeded (via this run's setup) with two leads
/// assigned to `testdoc1@example.com`: "Priya Sharma" and "Arjun Mehta".
final class LeadManagementUITests: XCTestCase {

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

    func testLeadListDetailStageActivityFollowupAndConvert() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["Leads"].tap()
        XCTAssertTrue(app.navigationBars["Leads"].waitForExistence(timeout: 15), "Leads screen never appeared")

        // Summary row + stage chips.
        XCTAssertTrue(app.staticTexts["Follow-ups today"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Conversion rate"].exists)
        XCTAssertTrue(app.staticTexts["New this week"].exists)
        XCTAssertTrue(app.buttons["stage-chip-New"].exists)
        XCTAssertTrue(app.buttons["stage-chip-Converted"].exists)
        snapshot("01-lead-list")

        // Search narrows to one lead.
        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText("Priya")
        XCTAssertTrue(app.staticTexts["Priya Sharma"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Arjun Mehta"].exists)
        snapshot("02-lead-list-searched")

        app.staticTexts["Priya Sharma"].tap()
        XCTAssertTrue(app.navigationBars["Priya Sharma"].waitForExistence(timeout: 10), "Lead detail never appeared")
        XCTAssertTrue(app.staticTexts["9876543210"].waitForExistence(timeout: 5))
        snapshot("03-lead-detail")

        // Stage picker: change new -> contacted.
        let stagePicker = app.buttons["stage-picker"]
        XCTAssertTrue(stagePicker.waitForExistence(timeout: 5))
        stagePicker.tap()
        app.buttons["Contacted"].tap()
        XCTAssertTrue(app.staticTexts["Contacted"].waitForExistence(timeout: 10), "stage change didn't reflect")
        snapshot("04-stage-changed")

        // Log activity.
        app.buttons["log-activity"].tap()
        XCTAssertTrue(app.navigationBars["Log activity"].waitForExistence(timeout: 5))
        app.buttons["Call"].tap()
        let descriptionField = app.textFields["activity-description"]
        XCTAssertTrue(descriptionField.waitForExistence(timeout: 5))
        descriptionField.tap()
        descriptionField.typeText("Spoke about the 12-week programme.")
        app.buttons["Save"].tap()
        XCTAssertTrue(
            app.staticTexts["Spoke about the 12-week programme."].waitForExistence(timeout: 10),
            "logged activity never appeared in the timeline"
        )
        snapshot("05-activity-logged")

        // Schedule follow-up.
        app.buttons["Schedule follow-up"].tap()
        XCTAssertTrue(app.navigationBars["Schedule follow-up"].waitForExistence(timeout: 5))
        app.buttons["Save"].tap()
        XCTAssertTrue(app.navigationBars["Priya Sharma"].waitForExistence(timeout: 10), "didn't return to lead detail after scheduling")
        snapshot("06-followup-scheduled")

        // Back to the list, convert the other lead.
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Leads"].waitForExistence(timeout: 10))
        searchField.buttons["Clear text"].tap()
        XCTAssertTrue(app.staticTexts["Arjun Mehta"].waitForExistence(timeout: 10))
        app.staticTexts["Arjun Mehta"].tap()
        XCTAssertTrue(app.navigationBars["Arjun Mehta"].waitForExistence(timeout: 10))

        app.buttons["convert-to-patient"].tap()
        XCTAssertTrue(app.navigationBars["Convert to patient"].waitForExistence(timeout: 10), "convert screen never appeared")
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        XCTAssertEqual(nameField.value as? String, "Arjun Mehta")
        snapshot("07-convert-form")

        app.buttons["confirm-convert"].tap()
        XCTAssertTrue(app.navigationBars["Arjun Mehta"].waitForExistence(timeout: 15), "didn't land on the new patient's detail screen")
        XCTAssertTrue(app.segmentedControls.firstMatch.waitForExistence(timeout: 5), "patient detail screen missing its section picker")
        snapshot("08-converted-patient-detail")
    }
}
