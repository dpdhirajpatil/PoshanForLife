import XCTest

/// IOS-16 — Invoices & Estimates: admin create/manage (Admin → Invoices),
/// practitioner read/manage (More → Invoices), and the patient dashboard's
/// outstanding-balance card opening a real (read-only) invoice detail screen
/// instead of IOS-16's placeholder.
final class DocumentsUITests: XCTestCase {

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

    /// Admin has no tab bar — see `CatalogueUITests.signInAsAdmin`.
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

    // MARK: - Admin: create an estimate, then manage its status

    func testAdminCreateEstimateAndManageStatus() throws {
        signOutIfSignedIn(app)
        signInAsAdmin()

        app.staticTexts["Invoices"].tap()
        XCTAssertTrue(app.navigationBars["Invoices & estimates"].waitForExistence(timeout: 10), "Invoices screen never appeared")

        app.buttons["add-document"].tap()
        XCTAssertTrue(app.navigationBars["New estimate"].waitForExistence(timeout: 10))

        // Patient/lead picker.
        app.buttons["choose-document-party"].tap()
        XCTAssertTrue(app.navigationBars["Select patient or lead"].waitForExistence(timeout: 10))
        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText("Test Patient")
        // SwiftUI's List exposes both the row's own accessibility element and
        // its nested `Text` as separate matches for the same string — plain
        // indexed lookup finds two and `.tap()` refuses to guess.
        let patientRow = app.staticTexts["Test Patient"].firstMatch
        XCTAssertTrue(patientRow.waitForExistence(timeout: 10), "seeded patient never appeared in the picker")
        // Resign the search field's keyboard before selecting — tapping the
        // row while it still holds focus can eat the first tap as a
        // dismiss-keyboard gesture instead of activating the row.
        let keyboardSearchKey = app.keyboards.buttons["Search"]
        if keyboardSearchKey.waitForExistence(timeout: 2) {
            keyboardSearchKey.tap()
        }
        patientRow.tap()
        XCTAssertTrue(app.navigationBars["New estimate"].waitForExistence(timeout: 10), "picker never dismissed back to the estimate form")
        XCTAssertFalse(app.staticTexts["Choose patient or lead"].exists, "party was never actually selected")

        // Line item — quantity 2 × ₹300 = ₹630 subtotal, distinct from any
        // other seeded document's total so the later list-filter assertion
        // can't accidentally match the wrong row.
        fill(app.textFields["Item name"], with: "UI Test Item")
        // Qty defaults to "1" (a fresh `DraftLineItem`'s initial value, not
        // empty like the other fields) — a plain tap-then-type would append
        // and produce "12", not "2". Double-tap selects the existing digit
        // so typing replaces it, same as `CatalogueUITests`' price-edit field.
        let qtyField = app.textFields["Qty"]
        qtyField.doubleTap()
        usleep(300_000)
        qtyField.typeText("2")
        usleep(300_000)
        fill(app.textFields["Rate (₹)"], with: "300", dismissAfter: false)
        snapshot("01-create-estimate-form")

        app.buttons["save-estimate-draft"].tap()
        XCTAssertTrue(app.navigationBars["Invoices & estimates"].waitForExistence(timeout: 10), "form never dismissed back to the list")

        // Narrow to Draft so the new row is unambiguous even with other
        // statuses' documents in the list.
        app.buttons["document-status-filter"].tap()
        app.buttons["Draft"].tap()

        let newTotal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "630.00")).firstMatch
        XCTAssertTrue(newTotal.waitForExistence(timeout: 10), "newly created estimate never appeared in the Draft-filtered list")
        snapshot("02-invoices-list-draft-filter")
        newTotal.tap()

        XCTAssertTrue(app.staticTexts["UI Test Item"].waitForExistence(timeout: 10), "detail screen never showed the line item")
        XCTAssertTrue(app.staticTexts["Line items"].exists)
        XCTAssertTrue(app.buttons["mark-document-sent"].waitForExistence(timeout: 5), "admin should see the status-management action")
        snapshot("03-document-detail-draft")

        app.buttons["mark-document-sent"].tap()
        XCTAssertTrue(app.buttons["mark-document-paid"].waitForExistence(timeout: 10), "status didn't advance from sent to offering 'mark as paid'")
        XCTAssertFalse(app.buttons["mark-document-sent"].exists, "the sent action should be gone once already sent")

        app.buttons["mark-document-paid"].tap()
        XCTAssertTrue(
            waitUntilGone(app.buttons["mark-document-paid"], timeout: 10),
            "a paid document shouldn't offer any further status action"
        )
        snapshot("04-document-detail-paid")

        // Share PDF — fetches a signed URL and hands it to the system share
        // sheet; only asserting the tap doesn't crash and dismissing
        // whatever system UI appears, since the signed-URL contract itself
        // is already verified directly against the API.
        app.buttons["share-document-pdf"].tap()
        let cancelButton = app.buttons["Cancel"]
        if cancelButton.waitForExistence(timeout: 10) {
            cancelButton.tap()
        }
    }

    // MARK: - Practitioner: sees documents tied to their own patients

    func testPractitionerSeesLinkedPatientDocuments() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["More"].tap()
        app.staticTexts["Invoices"].tap()
        XCTAssertTrue(app.navigationBars["Invoices & estimates"].waitForExistence(timeout: 10))

        XCTAssertTrue(
            app.staticTexts["Test Patient"].waitForExistence(timeout: 10),
            "practitioner should see documents for their own linked patient"
        )
        XCTAssertTrue(app.buttons["add-document"].exists, "practitioners can create documents too, unlike Catalogue")
    }

    // MARK: - Patient: dashboard balance card opens a real, read-only invoice

    func testPatientDashboardOpensInvoiceDetail() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "Home")

        let outstanding = app.staticTexts["Outstanding balance"]
        XCTAssertTrue(outstanding.waitForExistence(timeout: 15), "outstanding balance card never appeared")
        snapshot("05-patient-dashboard-balance-card")

        app.buttons["Pay"].tap()

        XCTAssertTrue(app.staticTexts["Line items"].waitForExistence(timeout: 10), "invoice detail never opened from the dashboard")
        XCTAssertTrue(app.staticTexts["Consultation fee"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["mark-document-sent"].exists, "a patient must never see status-management actions")
        XCTAssertFalse(app.buttons["mark-document-paid"].exists, "a patient must never see status-management actions")
        snapshot("06-patient-invoice-detail-readonly")
    }

    /// Same keyboard-focus workaround `CatalogueUITests` needed: a trailing
    /// Return dismisses a plain-keyboard field's keyboard so the next tap
    /// always starts clean. Numeric keypads have no Return key.
    private func fill(_ field: XCUIElement, with text: String, dismissAfter: Bool = true) {
        field.tap()
        // Right after a sheet dismisses (the patient/lead picker, here), the
        // outgoing view's first-responder handoff can still be in flight —
        // the first tap lands but doesn't actually take focus, and
        // `typeText` then fails with "neither element nor descendant has
        // keyboard focus". A plain keyboard's appearance is a reliable
        // signal focus actually landed; retry once if it doesn't show up.
        if !app.keyboards.element.waitForExistence(timeout: 2) {
            field.tap()
            _ = app.keyboards.element.waitForExistence(timeout: 2)
        }
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
