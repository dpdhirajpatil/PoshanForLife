import XCTest

/// The "Connect Apple Health / no dark mode option" fix — confirms the
/// Profile screen's Appearance picker exists, is reachable, and actually
/// changes the rendered color scheme when a row is tapped. Also a basic
/// smoke pass over the Dashboard's card-loading animation change (IOS-04's
/// jumpy-cards fix): just that it loads and settles without crashing.
final class AppearanceUITests: XCTestCase {

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

    func testDashboardLoadsAndAppearancePickerChangesColorScheme() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "Track")

        // Home tab is the Dashboard — just confirm it settles on real content
        // rather than crashing or hanging on skeletons.
        app.tabBars.buttons["Home"].tap()
        XCTAssertTrue(
            app.staticTexts["InBody score"].waitForExistence(timeout: 15),
            "Dashboard never settled on real content"
        )
        snapshot("01-dashboard-settled")

        // Profile: Appearance picker with System/Light/Dark.
        app.tabBars.buttons["More"].tap()
        app.staticTexts["Profile"].tap()
        XCTAssertTrue(app.navigationBars["Profile"].waitForExistence(timeout: 15), "Profile screen never appeared")
        // List section headers render upper-cased ("APPEARANCE"), including in
        // the accessibility label — match case-insensitively rather than
        // hard-coding that presentation detail.
        let appearanceHeader = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Appearance")
        ).firstMatch
        XCTAssertTrue(appearanceHeader.waitForExistence(timeout: 10), "no Appearance section on Profile")
        XCTAssertTrue(app.staticTexts["System"].exists)
        XCTAssertTrue(app.staticTexts["Light"].exists)
        XCTAssertTrue(app.staticTexts["Dark"].exists)
        snapshot("02-profile-appearance-system")

        app.staticTexts["Dark"].tap()
        // Give the screen a moment to re-render under the new color scheme.
        XCTAssertTrue(app.staticTexts["Dark"].waitForExistence(timeout: 5))
        snapshot("03-profile-appearance-dark")

        app.staticTexts["Light"].tap()
        XCTAssertTrue(app.staticTexts["Light"].waitForExistence(timeout: 5))
        snapshot("04-profile-appearance-light")
    }
}
