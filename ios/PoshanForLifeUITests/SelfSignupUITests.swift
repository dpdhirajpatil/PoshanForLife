import XCTest

/// End users joining from the app: email sign-up, phone (OTP) sign-up and
/// phone login. Each creates a LEAD account named "IOS-SELF-<timestamp>", so
/// runs never collide and the test data is easy to find.
///
/// Phone flows rely on the backend's local/dev OTP bypass (fixed code
/// 000000), so run them against local or the AWS dev environment (the
/// Staging configuration), never prod.
final class SelfSignupUITests: XCTestCase {

    private var app: XCUIApplication!
    private let stamp = String(Int(Date().timeIntervalSince1970))

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        signOutIfSignedIn(app)
    }

    func testEmailSignupLandsOnLeadHomeAndRequestsConsultation() throws {
        app.buttons["Don't have an account? Sign up"].tap()

        let name = app.textFields["signup.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 10), "sign-up screen never appeared")
        name.tap(); name.typeText("IOS-SELF-\(stamp) Email")
        let email = app.textFields["signup.email"]
        email.tap(); email.typeText("ios-self-\(stamp)@example.com")
        let password = app.secureTextFields["signupPassword"]
        password.tap()
        // iOS offers "Use Strong Password?" on a new-password field; the sheet
        // swallows typing until it's closed.
        let closeAutofill = app.buttons["Close"]
        if closeAutofill.waitForExistence(timeout: 3) {
            closeAutofill.tap()
            password.tap()
        }
        password.typeText("Signup\(stamp)1")

        app.buttons["Create account"].tap()
        assertOnLeadHome(offersToSavePassword: true)

        // Daily check-in.
        let checkIn = app.buttons["Check in today"]
        XCTAssertTrue(checkIn.waitForExistence(timeout: 15), "check-in button never appeared")
        checkIn.tap()
        XCTAssertTrue(app.buttons["Checked in today"].waitForExistence(timeout: 15), "check-in never registered")

        // Request a consultation.
        app.buttons["requestConsultation"].tap()
        let submit = app.buttons["Request consultation"]
        XCTAssertTrue(submit.waitForExistence(timeout: 10), "consultation form never appeared")
        submit.tap()
        XCTAssertTrue(
            app.staticTexts["consultationSubmitted"].waitForExistence(timeout: 15),
            "consultation request was never confirmed"
        )
        app.buttons["Done"].tap()

        signOutIfSignedIn(app)
    }

    func testPhoneSignupThenPhoneLogin() throws {
        // 10 digits starting with 9, unique per run.
        let phone = "9" + String(stamp.suffix(9))

        // Unknown number on login → offered sign-up in place.
        enterPhoneAndSendCode(phone)
        let offer = app.buttons["Create an account with this number"]
        XCTAssertTrue(offer.waitForExistence(timeout: 15), "unknown number was not offered sign-up")
        offer.tap()

        let name = app.textFields["nameField"]
        XCTAssertTrue(name.waitForExistence(timeout: 15), "code step (sign-up) never appeared")
        name.tap(); name.typeText("IOS-SELF-\(stamp) Phone")
        typeDevCode()
        app.buttons["Create account"].tap()
        assertOnLeadHome()

        signOutIfSignedIn(app)

        // Same number, now an existing account: plain OTP login.
        enterPhoneAndSendCode(phone)
        XCTAssertTrue(app.textFields["otpField"].waitForExistence(timeout: 15), "code step (login) never appeared")
        typeDevCode()
        app.buttons["Verify and sign in"].tap()
        assertOnLeadHome()

        signOutIfSignedIn(app)
    }

    // MARK: - Helpers

    private func enterPhoneAndSendCode(_ phone: String) {
        let phoneTab = app.segmentedControls["loginMethod"].buttons["Phone"]
        XCTAssertTrue(phoneTab.waitForExistence(timeout: 20), "login screen never appeared")
        phoneTab.tap()
        let field = app.textFields["phoneField"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap(); field.typeText(phone)
        app.buttons["Send code"].tap()
    }

    private func typeDevCode() {
        let otp = app.textFields["otpField"]
        otp.tap(); otp.typeText("000000")
    }

    private func assertOnLeadHome(offersToSavePassword: Bool = false) {
        dismissNotificationRationaleIfPresent(app)
        XCTAssertTrue(
            app.staticTexts["leadGreeting"].waitForExistence(timeout: 30),
            "never reached the lead home screen"
        )
        // After a new password account, iOS offers to save it to Keychain a
        // few seconds after the screen changes, from a system process.
        if offersToSavePassword {
            let notNow = app.buttons["Not Now"]
            if notNow.waitForExistence(timeout: 10) { notNow.tap() }
        }
        for tab in ["Home", "Track", "Goals", "Profile"] {
            XCTAssertTrue(app.tabBars.buttons[tab].exists, "lead tab \(tab) missing")
        }
        XCTAssertFalse(app.tabBars.buttons["More"].exists, "lead should not get the patient tab bar")
    }
}
