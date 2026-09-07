import XCTest

/// IOS-18 — the LEAD self-signup flow: the signup form landing a new account
/// in `LeadTabView` under `LeadTheme`, the Request Consultation flow, the
/// reused Track/Goals/Profile tabs, and the role-refresh-on-resume logic that
/// notices a practitioner converted this Lead to a Patient server-side.
final class LeadSignupUITests: XCTestCase {

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

    /// A fresh, never-used email per test run — signup is a one-shot action
    /// against the real backend, so re-running with a fixed address would
    /// hit `EMAIL_CONFLICT` on every run after the first.
    private func freshEmail() -> String {
        "ios18-\(Int(Date().timeIntervalSince1970))-\(Int.random(in: 1000...9999))@example.com"
    }

    private func fill(_ field: XCUIElement, with text: String, dismissAfter: Bool = true) {
        field.tap()
        if !app.keyboards.element.waitForExistence(timeout: 2) {
            field.tap()
            _ = app.keyboards.element.waitForExistence(timeout: 2)
        }
        usleep(300_000)
        field.typeText(dismissAfter ? text + "\n" : text)
        usleep(300_000)
    }

    /// Drives the signup form to completion, landing in `LeadTabView`.
    /// Returns the email used, so callers can look the account up server-side.
    @discardableResult
    private func signUpFreshLead(name: String = "IOS18 UI Test") -> String {
        let email = freshEmail()

        let loginEmailField = app.textFields.firstMatch
        XCTAssertTrue(loginEmailField.waitForExistence(timeout: 20), "login screen never appeared")

        app.buttons["Create an account"].tap()
        if !app.staticTexts["Create account"].waitForExistence(timeout: 10) {
            print("=== DEBUG: AFTER CREATE-ACCOUNT TAP ===")
            print(app.debugDescription)
            print("=== END DEBUG ===")
        }
        XCTAssertTrue(app.staticTexts["Create account"].waitForExistence(timeout: 10), "signup screen never appeared")

        fill(app.textFields["Full name"], with: name)
        fill(app.textFields["Email"], with: email)
        fill(app.secureTextFields["Password"], with: "testpass123")
        // Phone/city/health goal are optional — left blank to also prove the
        // happy path works with only the three required fields filled.

        app.buttons["Create account"].tap()
        XCTAssertTrue(
            app.tabBars.buttons["Home"].waitForExistence(timeout: 30),
            "signup never landed in a tab bar"
        )
        return email
    }

    // MARK: - Signup

    func testSignupCreatesLeadAccountAndLandsInLeadHome() throws {
        signOutIfSignedIn(app)
        signUpFreshLead()

        // Lead's exact four tabs — Programmes/Reports (Patient-only) must
        // NOT be present.
        XCTAssertTrue(app.tabBars.buttons["Home"].exists)
        XCTAssertTrue(app.tabBars.buttons["Track"].exists)
        XCTAssertTrue(app.tabBars.buttons["Goals"].exists)
        XCTAssertTrue(app.tabBars.buttons["Profile"].exists)
        XCTAssertFalse(app.tabBars.buttons["Programmes"].exists, "a Lead shouldn't get Patient-only tabs")

        XCTAssertTrue(app.buttons["request-consultation-cta"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 5), "today's health summary never appeared on Lead Home")
        XCTAssertTrue(app.buttons["inbody-upload-nudge"].exists)
        XCTAssertTrue(app.staticTexts["Health tip"].exists)
        snapshot("01-lead-home")
    }

    // MARK: - Request consultation

    func testRequestConsultationFlow() throws {
        signOutIfSignedIn(app)
        signUpFreshLead()

        app.buttons["request-consultation-cta"].tap()
        XCTAssertTrue(app.navigationBars["Request consultation"].waitForExistence(timeout: 10))

        fill(app.textFields["Preferred contact time (optional)"], with: "Weekday evenings", dismissAfter: false)
        app.buttons["send-consultation-request"].tap()

        XCTAssertTrue(app.staticTexts["We'll be in touch soon!"].waitForExistence(timeout: 10), "confirmation view never appeared")
        snapshot("02-consultation-confirmation")

        app.buttons["consultation-confirmation-done"].tap()
        XCTAssertTrue(app.buttons["request-consultation-cta"].waitForExistence(timeout: 10), "sheet never dismissed back to Home")
    }

    // MARK: - Reused Track/Goals/Profile tabs are real, not placeholders

    func testTrackGoalsProfileTabsAreReal() throws {
        signOutIfSignedIn(app)
        signUpFreshLead()

        app.tabBars.buttons["Track"].tap()
        XCTAssertTrue(app.navigationBars["Track"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Water"].waitForExistence(timeout: 5), "Track tab looks like a placeholder, not the real TrackView")

        app.tabBars.buttons["Goals"].tap()
        XCTAssertTrue(app.staticTexts["Daily steps"].waitForExistence(timeout: 10), "Goals tab looks like a placeholder, not the real GoalsView")

        app.tabBars.buttons["Profile"].tap()
        XCTAssertTrue(app.navigationBars["Profile"].waitForExistence(timeout: 10))
        // `.insetGrouped` section headers render (and expose their
        // accessibility label) in all-caps, regardless of the `Text`'s
        // literal case — match case-insensitively rather than the exact
        // "Appearance" string.
        let appearanceHeader = app.staticTexts.matching(NSPredicate(format: "label ==[c] %@", "Appearance")).firstMatch
        XCTAssertTrue(appearanceHeader.waitForExistence(timeout: 5), "Profile tab looks like a placeholder, not the real ProfileView")
        XCTAssertTrue(app.buttons["Sign out"].exists)
    }

    // MARK: - Role refresh on resume: Lead -> Patient after server-side conversion

    func testRoleRefreshOnResumeAfterConversion() throws {
        signOutIfSignedIn(app)
        let email = signUpFreshLead(name: "IOS18 Convert Test")

        // Confirm still Lead before converting, so a false-pass isn't
        // possible if conversion silently no-ops.
        XCTAssertTrue(app.tabBars.buttons["Goals"].waitForExistence(timeout: 10))

        // Background the app, convert the Lead to a Patient server-side
        // (exactly what a practitioner does from the staff CRM), then
        // foreground again — this is the literal scenario
        // `AuthViewModel.refreshUserOnResume()` exists for.
        XCUIDevice.shared.press(.home)
        sleep(1)
        convertLeadToPatient(email: email)
        app.activate()

        XCTAssertTrue(
            app.tabBars.buttons["Programmes"].waitForExistence(timeout: 20),
            "app never re-navigated to the Patient tab set after the role flip"
        )
        XCTAssertFalse(app.tabBars.buttons["Goals"].exists, "Lead-only tab should be gone after promotion to Patient")
        snapshot("03-promoted-to-patient")
    }

    /// Talks to the backend directly (admin login → find the lead → convert)
    /// rather than through the app under test, mirroring what a practitioner
    /// does from the staff CRM — this is server-side state the UI test needs
    /// to mutate out of band, not something to drive through more UI.
    private func convertLeadToPatient(email: String) {
        let expectation = expectation(description: "lead converted")
        Task {
            defer { expectation.fulfill() }
            guard let adminToken = await login(email: "admin@poshanforlife.com", password: "Admin@123") else {
                XCTFail("admin login failed")
                return
            }
            guard let leadId = await findLeadId(email: email, token: adminToken) else {
                XCTFail("could not find the newly signed-up lead by email")
                return
            }
            await convert(leadId: leadId, token: adminToken)
        }
        wait(for: [expectation], timeout: 20)
    }

    private func login(email: String, password: String) async -> String? {
        guard let url = URL(string: "http://localhost:8080/api/v1/auth/login") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["email": email, "password": password])
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let dataDict = json["data"] as? [String: Any]
        else { return nil }
        return dataDict["accessToken"] as? String
    }

    private func findLeadId(email: String, token: String) async -> String? {
        guard var components = URLComponents(string: "http://localhost:8080/api/v1/leads") else { return nil }
        components.queryItems = [URLQueryItem(name: "search", value: email), URLQueryItem(name: "limit", value: "5")]
        guard let url = components.url else { return nil }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let dataDict = json["data"] as? [String: Any],
              let leads = dataDict["leads"] as? [[String: Any]],
              let first = leads.first
        else { return nil }
        return first["id"] as? String
    }

    private func convert(leadId: String, token: String) async {
        guard let url = URL(string: "http://localhost:8080/api/v1/leads/\(leadId)/convert") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: [String: String]())
        _ = try? await URLSession.shared.data(for: request)
    }
}
