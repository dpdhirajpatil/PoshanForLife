import XCTest

/// IOS-15 — the Service Catalogue: admin browse+CRUD (Admin → Products),
/// practitioner read-only browse (More → Products), and the
/// convert-to-patient flow's migrated picker (IOS-14's `ConvertToPatientView`
/// delegating to `CatalogueView` in selection mode instead of its old inline
/// search/list).
final class CatalogueUITests: XCTestCase {

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

    /// Admin has no tab bar (`AdminRootView` is a plain `NavigationStack`
    /// over a list), so this can't reuse `signIn(_:email:password:expecting:)`,
    /// which waits on a tab bar button.
    private func signInAsAdmin() {
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 20), "login screen never appeared")
        field.tap()
        field.typeText("admin@poshanforlife.com")

        let secure = app.secureTextFields.firstMatch
        secure.tap()
        secure.typeText("Admin@123")
        app.buttons["Sign in"].tap()

        let skip = app.buttons["skip-notifications"]
        if skip.waitForExistence(timeout: 5) { skip.tap() }

        XCTAssertTrue(app.navigationBars["Admin"].waitForExistence(timeout: 30), "admin sign-in never reached the Admin root screen")
    }

    // MARK: - Admin: browse, create, edit, delete

    func testAdminCatalogueBrowseCreateEditDelete() throws {
        signOutIfSignedIn(app)
        signInAsAdmin()

        app.staticTexts["Products"].tap()
        XCTAssertTrue(app.navigationBars["Catalogue"].waitForExistence(timeout: 10), "Catalogue screen never appeared")

        // Programmes segment is the default — the seeded item should render
        // with its price, duration, and a "Published" status badge.
        XCTAssertTrue(app.staticTexts["12-Week Weight Loss Programme"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Published"].exists)
        snapshot("01-admin-catalogue-programmes")

        // Switch to Sessions and create a new one.
        app.buttons["Sessions"].tap()
        XCTAssertTrue(app.staticTexts["Nutrition Consultation"].waitForExistence(timeout: 10))

        app.buttons["add-catalogue-item"].tap()
        XCTAssertTrue(app.navigationBars["New session"].waitForExistence(timeout: 10))

        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        fill(name, with: "IOS15 Test Session")
        fill(app.textFields["Service code"], with: "IOS15-TEST-01")
        fill(app.textFields["Category (e.g. Weight loss)"], with: "Testing")
        // Numeric keypads (decimalPad/numberPad) have no Return key.
        fill(app.textFields["Price (₹)"], with: "999", dismissAfter: false)
        fill(app.textFields["Duration (minutes)"], with: "30", dismissAfter: false)

        snapshot("02-admin-catalogue-create-form")
        app.buttons["save-catalogue-item"].tap()

        XCTAssertTrue(
            app.staticTexts["IOS15 Test Session"].waitForExistence(timeout: 10),
            "newly created session never appeared in the list"
        )
        snapshot("03-admin-catalogue-created")

        // Edit it — tapping the row opens the form pre-filled.
        app.staticTexts["IOS15 Test Session"].tap()
        XCTAssertTrue(app.navigationBars["Edit session"].waitForExistence(timeout: 10))
        let priceField = app.textFields["Price (₹)"]
        XCTAssertTrue(priceField.waitForExistence(timeout: 5))
        XCTAssertEqual(priceField.value as? String, "999")
        // Double-tap selects the field's whole (numeric, single-token) value,
        // so typing replaces it instead of appending — a plain `Form`
        // `TextField` has no "Clear text" button to tap instead.
        priceField.doubleTap()
        usleep(300_000)
        priceField.typeText("1499")
        app.buttons["save-catalogue-item"].tap()

        XCTAssertTrue(app.staticTexts["IOS15 Test Session"].waitForExistence(timeout: 10))
        // Substring match rather than an exact literal: the iOS Simulator's
        // `en_IN` currency formatting inserts a narrow no-break space (not
        // a plain U+0020) between "₹" and the amount, which doesn't survive
        // being typed into this Swift source file as a literal.
        let updatedPrice = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "1,499.00")).firstMatch
        XCTAssertTrue(updatedPrice.waitForExistence(timeout: 10), "updated price never reflected in the list")
        snapshot("04-admin-catalogue-edited")

        // Delete it via the swipe action — cleans up the test data this run added.
        app.staticTexts["IOS15 Test Session"].swipeLeft()
        app.buttons["Delete"].tap()
        app.buttons["Delete"].tap() // confirm the destructive confirmation dialog
        XCTAssertTrue(
            waitUntilGone(app.staticTexts["IOS15 Test Session"], timeout: 10),
            "deleted session was still in the list"
        )
    }

    // MARK: - Practitioner: read-only browse

    func testPractitionerCatalogueBrowseIsReadOnly() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["More"].tap()
        app.staticTexts["Products"].tap()
        XCTAssertTrue(app.navigationBars["Catalogue"].waitForExistence(timeout: 10))

        XCTAssertTrue(app.staticTexts["12-Week Weight Loss Programme"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["add-catalogue-item"].exists, "practitioners shouldn't see the admin add button")
        snapshot("05-practitioner-catalogue-readonly")

        // Tapping a row is inert — no edit form should appear.
        app.staticTexts["12-Week Weight Loss Programme"].tap()
        XCTAssertFalse(app.navigationBars["Edit programme"].waitForExistence(timeout: 3))
    }

    // MARK: - Convert-to-patient: the migrated catalogue picker

    func testConvertToPatientUsesCataloguePicker() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["Leads"].tap()
        XCTAssertTrue(app.navigationBars["Leads"].waitForExistence(timeout: 15))

        XCTAssertTrue(app.staticTexts["Priya Sharma"].waitForExistence(timeout: 10))
        app.staticTexts["Priya Sharma"].tap()
        XCTAssertTrue(app.navigationBars["Priya Sharma"].waitForExistence(timeout: 10))

        app.buttons["convert-to-patient"].tap()
        XCTAssertTrue(app.navigationBars["Convert to patient"].waitForExistence(timeout: 10))

        let toggle = app.switches.firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        // `Toggle` in a `Form` exposes both a row-spanning accessibility
        // container AND the literal switch knob as `switches` matches;
        // `.firstMatch` (the row container) hit-tests at its center — over
        // the label text, not the knob — so a plain `.tap()` there is a
        // no-op. Tapping near the row's trailing edge lands on the actual
        // control regardless of which nested element `.firstMatch` resolves to.
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()

        let chooseButton = app.buttons["choose-catalogue-item"]
        XCTAssertTrue(chooseButton.waitForExistence(timeout: 10), "the service picker button never appeared after enabling assignment")
        chooseButton.tap()
        XCTAssertTrue(app.navigationBars["Select a service"].waitForExistence(timeout: 10), "catalogue picker sheet never appeared")
        XCTAssertTrue(app.staticTexts["12-Week Weight Loss Programme"].waitForExistence(timeout: 10))
        snapshot("06-convert-catalogue-picker")

        app.staticTexts["12-Week Weight Loss Programme"].tap()
        XCTAssertTrue(
            app.navigationBars["Convert to patient"].waitForExistence(timeout: 10),
            "picker sheet never dismissed back to the convert form"
        )
        XCTAssertTrue(app.staticTexts["12-Week Weight Loss Programme"].waitForExistence(timeout: 5), "selected item name never populated")
        snapshot("07-convert-item-selected")

        // Cancel out — this run isn't meant to actually convert the lead.
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.navigationBars["Priya Sharma"].waitForExistence(timeout: 10))
    }

    /// Tapping into a `Form` `TextField` while a *previous* field's keyboard
    /// is still up intermittently never actually transfers focus in this
    /// sheet — confirmed via a hierarchy dump showing the old field still
    /// "Keyboard Focused" after the tap, reproducing regardless of which
    /// field content sits in that slot, the delay before/after the tap, or
    /// the tap mechanism (plain tap, double-tap, coordinate tap, press).
    /// What *is* reliable every time: a tap that starts with no keyboard up
    /// at all (the very first field always works). So every fill here sends
    /// a trailing Return to resign the keyboard once its text lands, making
    /// the next field's tap a fresh one. Only valid for fields with a plain
    /// keyboard — numeric keypads have no Return key to send.
    private func fill(_ field: XCUIElement, with text: String, dismissAfter: Bool = true) {
        field.tap()
        usleep(300_000)
        field.typeText(dismissAfter ? text + "\n" : text)
        usleep(300_000)
    }

    private func waitUntilGone(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !element.exists { return true }
            usleep(200_000)
        }
        return !element.exists
    }
}
