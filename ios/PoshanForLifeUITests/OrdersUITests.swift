import XCTest

/// IOS-17 — Orders & Transactions: admin/practitioner order browse+detail+
/// mark-as-paid (Admin → Orders / Practitioner → More → Orders), and the
/// Transactions ledger's summary cards + filters (Admin → Transactions /
/// Practitioner → More → Transactions).
final class OrdersUITests: XCTestCase {

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

    // MARK: - Admin: order list, detail, mark as paid

    func testAdminOrdersListDetailAndMarkAsPaid() throws {
        signOutIfSignedIn(app)
        signInAsAdmin()

        app.staticTexts["Orders"].tap()
        XCTAssertTrue(app.navigationBars["Orders"].waitForExistence(timeout: 10), "Orders screen never appeared")

        let pendingAmount = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "1,234.00")).firstMatch
        XCTAssertTrue(pendingAmount.waitForExistence(timeout: 10), "the seeded pending order never appeared")
        snapshot("01-orders-list")

        // Already-paid order: opening it should show full patientProgramme
        // context and no "mark as paid" action.
        let paidAmount = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "500.00")).firstMatch
        XCTAssertTrue(paidAmount.waitForExistence(timeout: 10))
        paidAmount.tap()
        XCTAssertTrue(app.staticTexts["Service"].waitForExistence(timeout: 10), "order detail never showed the service section")
        XCTAssertTrue(app.staticTexts["Nutrition Consultation"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Transactions"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["mark-order-paid"].exists, "an already-paid order shouldn't offer mark-as-paid")
        snapshot("02-paid-order-detail")
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Orders"].waitForExistence(timeout: 10))

        // Pending order: swipe → confirm → mark as paid.
        pendingAmount.swipeLeft()
        let markPaidSwipeButton = app.buttons["Mark as paid"]
        XCTAssertTrue(markPaidSwipeButton.waitForExistence(timeout: 5))
        markPaidSwipeButton.tap()

        XCTAssertTrue(app.staticTexts["Mark this order as paid?"].waitForExistence(timeout: 5), "confirmation dialog never appeared")
        snapshot("03-mark-paid-confirm")
        app.buttons["Mark as paid"].tap()

        // Re-open the same order to verify the status actually flipped.
        XCTAssertTrue(pendingAmount.waitForExistence(timeout: 10))
        pendingAmount.tap()
        XCTAssertTrue(app.staticTexts["12-Week Weight Loss Programme"].waitForExistence(timeout: 10))
        XCTAssertTrue(
            waitUntilGone(app.buttons["mark-order-paid"], timeout: 10),
            "order still offers mark-as-paid after being marked paid"
        )
        snapshot("04-order-now-paid")
    }

    // MARK: - Practitioner: sees own patients' orders, and the new Transactions menu item

    func testPractitionerOrdersAndTransactionsMenu() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Patients")

        app.tabBars.buttons["More"].tap()
        app.staticTexts["Orders"].tap()
        XCTAssertTrue(app.navigationBars["Orders"].waitForExistence(timeout: 10))
        XCTAssertTrue(
            app.staticTexts["Test Patient"].firstMatch.waitForExistence(timeout: 10),
            "practitioner should see orders for their own linked patient"
        )
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.navigationBars["More"].waitForExistence(timeout: 10))

        app.staticTexts["Transactions"].tap()
        XCTAssertTrue(app.navigationBars["Transactions"].waitForExistence(timeout: 10), "Transactions menu item never opened a real screen")
        XCTAssertTrue(app.staticTexts["Transaction value"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Credit consumed"].exists)
        XCTAssertFalse(app.buttons["transaction-practitioner-filter"].exists, "a practitioner shouldn't see the admin-only practitioner filter")
        snapshot("05-practitioner-transactions")
    }

    // MARK: - Admin: transactions summary + filters

    func testAdminTransactionsSummaryAndFilters() throws {
        signOutIfSignedIn(app)
        signInAsAdmin()

        app.staticTexts["Transactions"].tap()
        XCTAssertTrue(app.navigationBars["Transactions"].waitForExistence(timeout: 10))

        let value = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "1,234.00")).firstMatch
        XCTAssertTrue(value.waitForExistence(timeout: 10), "summary/list never reflected the seeded transactions")
        XCTAssertTrue(app.buttons["transaction-practitioner-filter"].exists, "admin should see the practitioner filter")
        snapshot("06-admin-transactions")

        app.buttons["transaction-catalogue-filter"].tap()
        app.buttons["Programme"].tap()
        // `TransactionRow` combines patient name and service name into one
        // `Text("\(patient) · \(service)")` — there's no standalone element
        // with just the service name to match exactly.
        let programmeRow = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "12-Week Weight Loss Programme")).firstMatch
        XCTAssertTrue(programmeRow.waitForExistence(timeout: 10), "catalogue-type filter never narrowed the list")
        snapshot("07-transactions-filtered")
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
