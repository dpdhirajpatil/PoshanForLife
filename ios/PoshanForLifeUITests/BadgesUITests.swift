import XCTest

/// IOS-12 — the Badges grid, the earned/locked visual states, the
/// celebration overlay for a newly-earned badge, and the description
/// popover. Requires the local backend seeded (via this run's setup) with
/// a mix of earned and locked badges for testpatient1.
final class BadgesUITests: XCTestCase {

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

    func testBadgesGridShowsEarnedAndLockedWithCelebrationAndPopover() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "Track")

        app.tabBars.buttons["More"].tap()
        app.staticTexts["Badges"].tap()

        XCTAssertTrue(app.navigationBars["Badges"].waitForExistence(timeout: 15), "Badges screen never appeared")

        // A newly-earned badge celebrates once, for ~1.8s, the first time
        // this device sees it — `waitForExistence`'s ~1s poll cadence is too
        // coarse to reliably catch a window that short, so poll as fast as
        // `.exists` (an instant snapshot, no built-in wait) allows instead.
        var sawCelebration = false
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline {
            if app.staticTexts["Badge earned!"].exists {
                sawCelebration = true
                snapshot("01-badges-celebration")
                break
            }
        }
        XCTAssertTrue(sawCelebration, "celebration overlay never appeared")

        // Let the celebration finish dismissing before interacting with tiles underneath.
        let celebrationGone = NSPredicate(format: "exists == false")
        expectation(for: celebrationGone, evaluatedWith: app.staticTexts["Badge earned!"])
        waitForExpectations(timeout: 5)

        XCTAssertTrue(app.staticTexts["First Steps"].waitForExistence(timeout: 10), "earned badge tile missing")
        XCTAssertTrue(app.staticTexts["On a Roll"].exists, "locked badge tile missing")
        XCTAssertTrue(app.staticTexts["Rising Star"].exists, "locked custom badge tile missing")
        snapshot("02-badges-grid")

        // Tap the earned badge — popover shows its description and "Earned".
        app.staticTexts["First Steps"].tap()
        XCTAssertTrue(app.staticTexts["Complete your first check-in on a challenge."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Earned"].exists)
        snapshot("03-earned-popover")

        // Dismiss via the popover's own Close button — `.popover` adapts to
        // a full-screen sheet on iPhone, where tap-outside has nothing to
        // land on.
        app.buttons["Close"].tap()
        XCTAssertTrue(app.staticTexts["On a Roll"].waitForExistence(timeout: 5))
        app.staticTexts["On a Roll"].tap()
        XCTAssertTrue(app.staticTexts["Complete your first programme."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Not yet earned"].exists)
        snapshot("04-locked-popover")
    }
}
