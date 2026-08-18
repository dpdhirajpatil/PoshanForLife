import XCTest

/// IOS-09 — the Practitioner's Patients tab: searchable list, profile header,
/// and the Overview/Reports/Programmes/Notes segmented detail.
///
/// Needs the backend on localhost:8080 and `testdoc1@example.com` /
/// `Admin@123`, which is assigned exactly one patient — `testpatient1`, who
/// carries health records, InBody reports, and programme assignments seeded
/// by the other UI test suites. So this is an integration smoke test, not a
/// CI candidate.
final class PatientManagementUITests: XCTestCase {

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

    func testPatientListSearchAndDetailTabs() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["Patients"].tap()

        // The row's Avatar carries its own `.accessibilityLabel(Text(name))`,
        // which surfaces as a second independent StaticText alongside the
        // name Text itself — `app.staticTexts["Test Patient"]` matches both.
        // The row as a whole is exposed as one merged Button, so target that.
        let patientRow = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Test Patient")
        ).firstMatch
        XCTAssertTrue(patientRow.waitForExistence(timeout: 20), "the assigned patient row never appeared")
        snapshot("01-patient-list")

        // --- Search narrows to a match, and to nothing for a non-match ---
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 10), "no search field on the patient list")
        search.tap()
        search.typeText("Nonexistent Zzz")

        XCTAssertTrue(
            app.staticTexts["No matching patients"].waitForExistence(timeout: 15),
            "search for a non-matching name should show the empty state, not a stale list"
        )
        XCTAssertFalse(patientRow.exists, "a non-matching search must not still show the seeded patient")

        // Clear back to the full list via the search field's own clear button.
        app.buttons["Clear text"].tap()
        XCTAssertTrue(patientRow.waitForExistence(timeout: 15), "clearing search should restore the patient row")
        snapshot("02-search-cleared")

        // --- Detail: header + segmented Overview/Reports/Programmes/Notes ---
        patientRow.tap()
        XCTAssertTrue(
            app.navigationBars["Test Patient"].waitForExistence(timeout: 15),
            "patient detail never appeared"
        )
        XCTAssertTrue(app.staticTexts["Latest vitals"].waitForExistence(timeout: 10), "Overview tab isn't showing by default")
        snapshot("03-overview-tab")

        // Reports: reuses IOS-05's own detail screen.
        app.buttons["Reports"].tap()
        let reportRow = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "InBody")
        ).firstMatch
        XCTAssertTrue(reportRow.waitForExistence(timeout: 15), "no InBody report row for the seeded patient")
        snapshot("04-reports-tab")
        reportRow.tap()
        XCTAssertTrue(
            app.navigationBars["Report"].waitForExistence(timeout: 15),
            "tapping a report row should push IOS-05's ReportDetailView"
        )
        snapshot("05-report-detail-pushed")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // Programmes: reuses IOS-06's own detail screen, with check-in hidden.
        XCTAssertTrue(app.buttons["Programmes"].waitForExistence(timeout: 10))
        app.buttons["Programmes"].tap()
        let challengeRow = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Hydration")
        ).firstMatch
        XCTAssertTrue(challengeRow.waitForExistence(timeout: 15), "no challenge row for the seeded patient")
        snapshot("06-programmes-tab")
        challengeRow.tap()
        XCTAssertTrue(
            app.staticTexts["Challenge progress"].waitForExistence(timeout: 15),
            "tapping a programme row should push IOS-06's ProgrammeDetailView"
        )
        // The practitioner must never see the patient-only check-in action.
        XCTAssertFalse(app.buttons["check-in-today"].exists,
                       "a practitioner viewing a patient's challenge must not see the check-in button")
        snapshot("07-programme-detail-no-checkin")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // Notes: edit, save via the toolbar Done button, confirm it round-tripped.
        XCTAssertTrue(app.buttons["Notes"].waitForExistence(timeout: 10))
        app.buttons["Notes"].tap()
        XCTAssertTrue(app.staticTexts["Practitioner notes"].waitForExistence(timeout: 10), "Notes tab never appeared")

        let editor = app.textViews.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 10), "no notes TextEditor")
        editor.tap()
        // Clear any prior run's text and write a fresh, uniquely-timestamped note.
        if let existing = editor.value as? String, !existing.isEmpty {
            editor.press(forDuration: 1.0)
            if app.menuItems["Select All"].waitForExistence(timeout: 3) {
                app.menuItems["Select All"].tap()
            }
        }
        let note = "UI test note \(Date().timeIntervalSince1970)"
        editor.typeText(note)
        snapshot("08-notes-edited")

        app.buttons["Done"].tap()
        XCTAssertTrue(app.staticTexts["Saved"].waitForExistence(timeout: 15), "notes save never confirmed")
        snapshot("09-notes-saved")

        // Reload the detail screen and confirm the note actually persisted
        // server-side, not just in local state.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(patientRow.waitForExistence(timeout: 15))
        patientRow.tap()
        XCTAssertTrue(app.buttons["Notes"].waitForExistence(timeout: 15))
        app.buttons["Notes"].tap()
        let reloadedEditor = app.textViews.firstMatch
        XCTAssertTrue(reloadedEditor.waitForExistence(timeout: 10))
        XCTAssertTrue(
            (reloadedEditor.value as? String)?.contains(note) ?? false,
            "the saved note should still be there after reloading the patient"
        )
    }
}
