import XCTest

/// IOS-06 — the Programmes tab.
///
/// Needs the backend on localhost:8080 and the `ios6.patient@example.com`
/// fixture (one programme, two sessions past and future, one challenge), so
/// like `DashboardUITests` this is an integration smoke test rather than a CI
/// candidate.
final class ProgrammesUITests: XCTestCase {

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

    /// Drops any restored session so a specific account can be signed in.
    private func signOutIfSignedIn() {
        let profileTab = app.tabBars.buttons["Profile"]
        guard profileTab.waitForExistence(timeout: 20) else { return }
        profileTab.tap()
        let signOut = app.buttons["Sign out"]
        XCTAssertTrue(signOut.waitForExistence(timeout: 10), "Profile tab has no Sign out button")
        signOut.tap()
        XCTAssertTrue(
            app.textFields.firstMatch.waitForExistence(timeout: 20),
            "tapped Sign out but never returned to the login screen"
        )
    }

    private func signIn(email: String) {
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "login screen never appeared")
        field.tap()
        field.typeText(email)
        let password = app.secureTextFields.firstMatch
        password.tap()
        password.typeText("Ios6Test@123")
        app.buttons["Sign in"].tap()
        XCTAssertTrue(
            app.tabBars.buttons["Programmes"].waitForExistence(timeout: 25),
            "signed in as \(email) but the patient tab bar never appeared"
        )
    }

    /// A patient with zero assignments must get the empty state, not a blank
    /// screen — the one case that can't be reached from the seeded fixture.
    func testEmptyState() throws {
        signOutIfSignedIn()
        signIn(email: "ios6.empty@example.com")

        app.tabBars.buttons["Programmes"].tap()
        XCTAssertTrue(
            app.staticTexts["No active programmes yet"].waitForExistence(timeout: 20),
            "empty state headline never appeared"
        )
        XCTAssertTrue(
            app.staticTexts.containing(
                NSPredicate(format: "label CONTAINS[c] %@", "Ask your practitioner")
            ).firstMatch.exists,
            "empty state is missing the 'Ask your practitioner' guidance"
        )
        // The section label only renders when there are rows, so its absence
        // confirms the empty branch rather than an empty list under a header.
        XCTAssertFalse(app.staticTexts["YOUR PLAN"].exists,
                       "empty state should not show the section label")
        snapshot("06-empty-state")

        // Leave the simulator on the fixture account for the other test.
        signOutIfSignedIn()
        signIn(email: "ios6.patient@example.com")
    }

    private func signInIfNeeded() {
        // The Keychain survives app reinstalls on a Simulator, so a previous run
        // may already be signed in — and cold launch restores that session over
        // the network before the tab bar exists. Wait generously for either.
        if app.tabBars.buttons["Programmes"].waitForExistence(timeout: 20) { return }

        let email = app.textFields.firstMatch
        XCTAssertTrue(email.waitForExistence(timeout: 10), "neither a tab bar nor a login screen appeared")
        email.tap()
        email.typeText("ios6.patient@example.com")

        let password = app.secureTextFields.firstMatch
        password.tap()
        password.typeText("Ios6Test@123")
        app.buttons["Sign in"].tap()

        XCTAssertTrue(
            app.tabBars.buttons["Programmes"].waitForExistence(timeout: 25),
            "signed in but the patient tab bar never appeared"
        )
    }

    func testProgrammesListAndDetail() throws {
        signInIfNeeded()

        app.tabBars.buttons["Programmes"].tap()

        // The section label proves the list rendered rows rather than the empty
        // state — they're mutually exclusive in the view.
        XCTAssertTrue(
            app.staticTexts["YOUR PLAN"].waitForExistence(timeout: 20),
            "Programmes list never appeared (or fell through to the empty state)"
        )
        snapshot("01-programmes-list")

        // All three service types should be present for this fixture, each with
        // its own progress treatment.
        let programme = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "360 Degree")
        ).firstMatch
        XCTAssertTrue(programme.waitForExistence(timeout: 10), "the programme row is missing")

        let challenge = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Water Daily")
        ).firstMatch
        XCTAssertTrue(challenge.exists, "the challenge row is missing")

        let session = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Consultation")
        ).firstMatch
        XCTAssertTrue(session.exists, "the session rows are missing")

        // A session shows a point-in-time state, never a progress bar. The past
        // session must read Completed even though its assignment status is
        // still "active" — that distinction is the whole reason SessionState
        // exists.
        XCTAssertTrue(app.staticTexts["Completed"].exists,
                      "the past session should read Completed")

        app.swipeUp()
        snapshot("02-programmes-scrolled")

        // --- Challenge detail: the check-in flow ---
        challenge.tap()

        XCTAssertTrue(
            app.staticTexts["Challenge progress"].waitForExistence(timeout: 15),
            "challenge detail never appeared"
        )
        snapshot("03-challenge-detail")

        // Fresh fixture: never checked in, so the actionable button is showing.
        let checkIn = app.buttons["check-in-today"]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 10),
                      "expected an actionable Check in today button on a fresh challenge")
        checkIn.tap()

        // After a successful check-in the button is replaced by the disabled
        // confirmation — that swap only happens on the success path, so it
        // doubles as the assertion that the PATCH round-tripped.
        XCTAssertTrue(
            app.staticTexts["Checked in today"].waitForExistence(timeout: 15),
            "check-in didn't complete — button never flipped to Checked in today"
        )
        // And the streak must now be non-zero.
        let streak = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "day streak")
        ).firstMatch
        XCTAssertTrue(streak.exists, "no streak shown after checking in")
        snapshot("04-challenge-checked-in")

        app.navigationBars.buttons.element(boundBy: 0).tap()

        // --- Programme detail: notes + calendar progress, no check-in ---
        XCTAssertTrue(programme.waitForExistence(timeout: 10))
        programme.tap()
        XCTAssertTrue(
            app.staticTexts["Notes from your practitioner"].waitForExistence(timeout: 15),
            "programme detail should show the practitioner's notes"
        )
        // A programme is not a challenge: no check-in control belongs here.
        XCTAssertFalse(app.buttons["check-in-today"].exists,
                       "a programme must not offer a challenge check-in")
        snapshot("05-programme-detail")
    }
}
