import XCTest

/// IOS-07 — Appointments, both role variants.
///
/// Integration test: needs the backend on localhost:8080 and the seeded
/// `testpatient1` / `testdoc1` pair (they must be linked in `doctor_patients`,
/// which the dev seed already does — booking is only ever with an assigned
/// practitioner).
///
/// Time-sensitive by nature: the bookable window is 09:00–17:00 **UTC**, which
/// in IST is 14:30–22:00. Run this before ~21:30 local or the day's slots have
/// all passed and there is legitimately nothing to book.
final class AppointmentsUITests: XCTestCase {

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

    // MARK: - Patient

    func testPatientBooksThenCancels() throws {
        signOutIfSignedIn(app)
        signIn(app, email: "testpatient1@example.com", password: "Admin@123", expecting: "More")

        app.tabBars.buttons["More"].tap()
        let appointmentsRow = app.staticTexts["Appointments"]
        XCTAssertTrue(appointmentsRow.waitForExistence(timeout: 15), "More tab has no Appointments row")
        appointmentsRow.tap()

        XCTAssertTrue(
            app.navigationBars["Appointments"].waitForExistence(timeout: 20),
            "Appointments screen never appeared"
        )
        snapshot("01-appointments-initial")

        // --- Book ---
        app.buttons["book-appointment"].tap()
        XCTAssertTrue(
            app.navigationBars["Book appointment"].waitForExistence(timeout: 15),
            "booking sheet never appeared"
        )

        // The slot buttons carry a "slot-HH:mm:ss" identifier. Their *labels*
        // are local times, which is the behaviour under test — a slot labelled
        // 09:00 here would mean the UTC time leaked into the UI.
        let firstSlot = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "slot-")
        ).firstMatch
        XCTAssertTrue(firstSlot.waitForExistence(timeout: 20), "no bookable slots offered")

        let slotLabel = firstSlot.label
        XCTAssertFalse(
            slotLabel.contains("09:00") || slotLabel.contains("9:00 AM"),
            "slot is showing raw UTC (\(slotLabel)) — it must render in the device's time zone"
        )
        firstSlot.tap()
        snapshot("02-slot-selected")

        app.buttons["confirm-booking"].tap()

        // Back on the list, the new appointment shows under Today.
        XCTAssertTrue(
            app.staticTexts["Today"].waitForExistence(timeout: 25),
            "booked appointment never appeared in the list"
        )
        // And it reads the same local time the slot button did.
        XCTAssertTrue(
            app.staticTexts[slotLabel].waitForExistence(timeout: 10),
            "the list shows a different time (\(slotLabel) expected) than the slot that was booked"
        )
        snapshot("03-booked")

        // --- Cancel, via swipe + confirmation ---
        // Must be a *scheduled* row: only those carry swipe actions, and an
        // earlier run's cancelled appointment sits in this same list.
        guard let row = scheduledRow(containing: "Test Practitioner") else {
            XCTFail("no scheduled appointment row to cancel")
            return
        }
        row.swipeLeft()

        let cancelAction = app.buttons["Cancel"]
        XCTAssertTrue(cancelAction.waitForExistence(timeout: 10), "swipe didn't reveal a Cancel action")
        cancelAction.tap()

        // Confirmation is required before anything is released. Scoped to the
        // dialog — an unscoped query can resolve to the row's own swipe action
        // and leave the dialog sitting open.
        let confirm = app.sheets.buttons["Cancel appointment"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 10), "cancelling didn't ask for confirmation")
        snapshot("04-cancel-confirmation")
        confirm.tap()

        XCTAssertTrue(
            app.staticTexts["Cancelled"].waitForExistence(timeout: 20),
            "appointment wasn't marked cancelled"
        )
        snapshot("05-cancelled")

        // The cancelled row is real data in a shared dev database — clear it so
        // repeated runs don't accumulate, and so the practitioner test isn't
        // left picking through this test's leftovers.
        deleteCancelledAppointments()
    }

    // MARK: - Practitioner

    func testPractitionerCompletesAppointment() throws {
        // Seed an upcoming appointment through the API so this test doesn't
        // depend on the patient test having run first.
        guard let seeded = seedAppointment() else {
            XCTFail("couldn't seed an appointment to work with")
            return
        }

        signOutIfSignedIn(app)
        signIn(app, email: "testdoc1@example.com", password: "Admin@123", expecting: "Schedule")

        app.tabBars.buttons["Schedule"].tap()
        XCTAssertTrue(
            app.navigationBars["Schedule"].waitForExistence(timeout: 20),
            "Schedule tab never appeared"
        )
        snapshot("06-practitioner-schedule")

        // The practitioner sees the *patient's* name on each row. Pick a
        // *scheduled* one specifically: tapping a cancelled appointment hides
        // the complete action for entirely correct reasons, which would read
        // as a failure here.
        XCTAssertTrue(
            app.cells.firstMatch.waitForExistence(timeout: 20),
            "the schedule never populated"
        )
        guard let scheduled = scheduledRow(containing: "Test Patient") else {
            XCTFail("no scheduled appointment in the practitioner's list")
            return
        }
        // Tap the row's own text rather than the cell: tapping the cell itself
        // doesn't reliably activate the NavigationLink inside it.
        scheduled.staticTexts["Test Patient"].tap()

        XCTAssertTrue(
            app.navigationBars["Appointment"].waitForExistence(timeout: 15),
            "appointment detail never appeared"
        )
        snapshot("07-practitioner-detail")

        // Notes are practitioner-only — a patient gets INSUFFICIENT_ROLE here.
        let notes = app.textViews["appointment-notes"]
        XCTAssertTrue(notes.waitForExistence(timeout: 10), "notes editor missing")
        notes.tap()
        notes.typeText("Reviewed InBody trend; continue current plan.")
        app.buttons["Done"].tap()
        app.buttons["Save notes"].tap()

        // Mark completed, behind its own confirmation.
        let complete = app.buttons["mark-completed"]
        XCTAssertTrue(complete.waitForExistence(timeout: 10), "no Mark completed action")
        complete.tap()

        // Scoped to the dialog: the trigger button and the dialog's action
        // carry the same title, so an unscoped query matches the trigger and
        // just reopens the dialog instead of confirming.
        let confirm = app.sheets.buttons["Mark completed"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 10), "completing didn't ask for confirmation")
        confirm.tap()

        // LabeledContent merges its label and value into one accessibility
        // element ("Status, Completed"), so there is no standalone "Completed"
        // text to match on here.
        XCTAssertTrue(
            app.staticTexts.containing(
                NSPredicate(format: "label CONTAINS[c] %@", "Status, Completed")
            ).firstMatch.waitForExistence(timeout: 20),
            "appointment never showed as completed"
        )
        snapshot("08-practitioner-completed")

        cleanUp(appointmentId: seeded)
    }

    // MARK: - Row lookup

    /// The first row naming `name` that is neither cancelled nor completed.
    ///
    /// A `List` cell exposes **no aggregated label** — its text lives entirely
    /// in child `StaticText`s — so `cell.label` is always empty and filtering
    /// on it silently matches every row. Descendants have to be queried
    /// directly, which is what this does.
    private func scheduledRow(containing name: String) -> XCUIElement? {
        let candidates = app.cells.containing(
            NSPredicate(format: "label CONTAINS[c] %@", name)
        )
        guard candidates.firstMatch.waitForExistence(timeout: 15) else { return nil }

        return candidates.allElementsBoundByIndex.first { cell in
            !cell.staticTexts["Cancelled"].exists && !cell.staticTexts["Completed"].exists
        }
    }

    // MARK: - Fixture helpers (direct API, not through the UI)

    private func api(
        _ path: String,
        method: String,
        body: [String: Any]? = nil,
        token: String? = nil
    ) -> [String: Any]? {
        var request = URLRequest(url: URL(string: "http://localhost:8080/api/v1/\(path)")!)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try? JSONSerialization.data(withJSONObject: body) }

        var result: [String: Any]?
        let done = expectation(description: path)
        URLSession.shared.dataTask(with: request) { data, _, _ in
            if let data { result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] }
            done.fulfill()
        }.resume()
        wait(for: [done], timeout: 30)
        return result
    }

    private func token(email: String, password: String) -> String? {
        let response = api("auth/login", method: "POST", body: ["email": email, "password": password])
        let data = response?["data"] as? [String: Any]
        return data?["accessToken"] as? String
    }

    /// Books tomorrow's first free slot as the patient, so the practitioner has
    /// something upcoming to act on.
    private func seedAppointment() -> String? {
        guard let patientToken = token(email: "testpatient1@example.com", password: "Admin@123") else { return nil }

        let practitionerId = "4f2ae86f-4082-4b6d-9adc-93036faae10c"
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: Date())!
        let day = formatter.string(from: tomorrow)

        let slotsResponse = api(
            "appointments/available-slots?practitionerId=\(practitionerId)&date=\(day)",
            method: "GET", token: patientToken
        )
        guard let slots = slotsResponse?["data"] as? [[String: Any]],
              let free = slots.first(where: { $0["available"] as? Bool == true }),
              let time = free["time"] as? String
        else { return nil }

        // Slot clock times are UTC, so the instant is built as UTC.
        let scheduledAt = "\(day)T\(time)Z"
        let created = api(
            "appointments", method: "POST",
            body: ["practitionerId": practitionerId, "scheduledAt": scheduledAt, "isVideo": false],
            token: patientToken
        )
        return (created?["data"] as? [String: Any])?["id"] as? String
    }

    /// Hard-deletes the seeded row so repeated runs don't pile up appointments
    /// in the shared dev database.
    private func cleanUp(appointmentId: String) {
        guard let adminToken = token(email: "admin@poshanforlife.com", password: "Admin@123") else { return }
        _ = api("appointments/\(appointmentId)", method: "DELETE", token: adminToken)
    }

    /// Removes every cancelled appointment. `DELETE` is ADMIN-only, which is
    /// why this borrows an admin token rather than the patient's.
    private func deleteCancelledAppointments() {
        guard let adminToken = token(email: "admin@poshanforlife.com", password: "Admin@123") else { return }
        let response = api("appointments?status=cancelled&limit=100", method: "GET", token: adminToken)
        guard let rows = response?["data"] as? [[String: Any]] else { return }
        for row in rows {
            if let id = row["id"] as? String {
                _ = api("appointments/\(id)", method: "DELETE", token: adminToken)
            }
        }
    }
}
