import XCTest

/// Walks the app on a simulator and attaches one screenshot per screen.
///
/// The attachment NAMES are the contract with CI: the workflow exports
/// attachments out of the `.xcresult` and the exported filenames derive from
/// these, so they are fixed strings and not descriptions.
///
/// The tour is written to be TOLERANT rather than strict. A UI test that fails
/// the build on one unreachable screen produces no artefact at all, which is the
/// opposite of useful when the artefact is the deliverable. So each step captures
/// what it can, records what it could not reach, and attaches a plain-text report
/// listing both. A floor assertion still fails the run if the app is broken
/// outright rather than merely awkward to navigate.
final class ScreenshotTests: XCTestCase {

    private var app: XCUIApplication!
    private var captured: [String] = []
    private var skipped: [String] = []
    private var notes: [String] = []

    private let masterPassphrase = "Screenshot-Master-2026"
    /// Must match `UITestMode.importFixturePassphrase` in the app target.
    private let importPassphrase = "UITest-Import-2026"

    override func setUpWithError() throws {
        // One unreachable screen must not abandon the remaining ones.
        continueAfterFailure = true
    }

    func testCaptureScreens() {
        app = XCUIApplication()
        app.launchArguments += [
            "-uiTestReset",
            "-uiTestRelaxedKeychain",
            "-uiTestSeed",
            "-uiTestImportFixture"
        ]
        app.launch()

        captureOnboarding()
        createVault()
        captureVaultList()
        captureAddItem()
        captureViewItem()
        captureGenerator()
        captureSettings()
        captureExport()
        captureImportReview()
        captureLock()

        attachReport()
        XCTAssertGreaterThanOrEqual(
            captured.count,
            5,
            "Only \(captured.count) screens captured.\n"
                + "captured: \(captured)\n"
                + "skipped: \(skipped)\n"
                + "notes: \(notes)"
        )
    }

    // MARK: - Steps

    private func captureOnboarding() {
        let field = app.secureTextFields["Master passphrase"]
        guard field.waitForExistence(timeout: 30) else {
            skip("01-onboarding", "the passphrase field never appeared")
            return
        }
        // Captured before typing: the keyboard would otherwise cover half the
        // screen and the shot would show a layout no user sees at rest.
        capture("01-onboarding")
    }

    private func createVault() {
        let master = app.secureTextFields["Master passphrase"]
        guard master.waitForExistence(timeout: 15) else {
            note("master passphrase field never appeared")
            return
        }
        master.tap()
        master.typeText(masterPassphrase)

        let confirm = app.secureTextFields["Confirm passphrase"]
        guard confirm.waitForExistence(timeout: 5) else {
            note("confirm field never appeared")
            return
        }
        if !confirm.isHittable {
            app.swipeUp()
        }
        guard confirm.isHittable else {
            note("confirm field not hittable — probably under the keyboard")
            return
        }
        confirm.tap()
        // Return submits the form, which avoids depending on a button the
        // keyboard may be covering. The button tap below is the fallback.
        confirm.typeText(masterPassphrase + "\n")

        if app.navigationBars["Vault"].waitForExistence(timeout: 25) {
            return
        }

        let create = button(startingWith: "Create vault")
        note("after return: create exists=\(create.exists) "
             + "enabled=\(create.exists ? String(create.isEnabled) : "n/a") "
             + "hittable=\(create.exists ? String(create.isHittable) : "n/a")")
        if create.exists {
            if !create.isHittable {
                app.swipeUp()
            }
            if create.isHittable && create.isEnabled {
                create.tap()
            }
        }
    }

    private func captureVaultList() {
        guard app.navigationBars["Vault"].waitForExistence(timeout: 40) else {
            // Whatever went wrong, the screen itself says so — an unsatisfiable
            // Keychain protection class, a mismatched passphrase, a disabled
            // button. Capture it and scrape the labels, so the next round is
            // driven by evidence rather than another guess.
            capture("00-diagnostic-stuck-on-create")
            note("visible text: \(visibleText())")
            skip("02-vault-list", "the vault list never appeared after vault creation")
            return
        }
        // The seeded rows decrypt their titles before the list renders them;
        // waiting on one real title avoids capturing a screen full of
        // "Decrypting…".
        _ = app.staticTexts["GitHub"].waitForExistence(timeout: 20)
        capture("02-vault-list")
    }

    private func captureAddItem() {
        guard tap(button(exactly: "Add item")) else {
            skip("03-add-item", "the add button was not reachable")
            return
        }
        guard app.navigationBars["New item"].waitForExistence(timeout: 10) else {
            skip("03-add-item", "the add/edit sheet never appeared")
            return
        }
        capture("03-add-item")
        _ = tap(button(exactly: "Cancel"))
        _ = app.navigationBars["Vault"].waitForExistence(timeout: 10)
    }

    private func captureViewItem() {
        let firstRow = app.cells.element(boundBy: 0)
        guard firstRow.waitForExistence(timeout: 10), firstRow.isHittable else {
            skip("04-view-item", "no vault row was reachable")
            return
        }
        firstRow.tap()
        // The detail screen titles itself with the item, so wait on a field only
        // it shows. The password is masked by default, which is the state worth
        // photographing.
        guard app.staticTexts["Password"].waitForExistence(timeout: 10) else {
            skip("04-view-item", "the item detail never appeared")
            goBack()
            return
        }
        capture("04-view-item")
        goBack()
    }

    private func captureGenerator() {
        guard tap(button(exactly: "Password generator")) else {
            skip("05-generator", "the generator button was not reachable")
            return
        }
        guard app.navigationBars["Generator"].waitForExistence(timeout: 10) else {
            skip("05-generator", "the generator never appeared")
            return
        }
        // It generates on appear; give the first password a moment to render.
        _ = app.staticTexts["Character sets"].waitForExistence(timeout: 10)
        capture("05-generator")
        _ = tap(button(exactly: "Done"))
    }

    private func captureSettings() {
        guard openSettings() else {
            skip("06-settings", "settings was not reachable")
            return
        }
        capture("06-settings")
    }

    private func captureExport() {
        if !app.navigationBars["Settings"].exists {
            guard openSettings() else {
                skip("08-export", "settings was not reachable")
                return
            }
        }
        guard tap(button(startingWith: "Export vault")) else {
            skip("08-export", "the export row was not reachable")
            return
        }
        guard app.navigationBars["Export vault"].waitForExistence(timeout: 15) else {
            skip("08-export", "the export passphrase sheet never appeared")
            return
        }
        capture("08-export")
        _ = tap(button(exactly: "Cancel"))
    }

    private func captureImportReview() {
        guard openSettings() else {
            skip("09-import-review", "settings was not reachable")
            return
        }
        guard tap(button(startingWith: "Import vault")) else {
            skip("09-import-review", "the import row was not reachable")
            return
        }

        let field = app.secureTextFields["File passphrase"]
        guard field.waitForExistence(timeout: 15) else {
            skip("09-import-review", "the import sheet never appeared")
            return
        }
        field.tap()
        field.typeText(importPassphrase)

        guard tap(button(startingWith: "Read file")) else {
            skip("09-import-review", "the read button was not reachable")
            return
        }
        // Decryption runs Argon2 at the pinned cost, so allow real time for the
        // summary to arrive.
        guard app.staticTexts["New items"].waitForExistence(timeout: 40) else {
            skip("09-import-review", "the import summary never appeared")
            return
        }
        capture("09-import-review")
        _ = tap(button(exactly: "Cancel"))
    }

    private func captureLock() {
        guard openSettings() else {
            skip("07-lock", "settings was not reachable")
            return
        }
        guard tap(button(startingWith: "Lock now")) else {
            skip("07-lock", "the lock row was not reachable")
            return
        }
        guard app.staticTexts["Vault locked"].waitForExistence(timeout: 15) else {
            skip("07-lock", "the lock screen never appeared")
            return
        }
        capture("07-lock")
    }

    // MARK: - Navigation helpers

    private func openSettings() -> Bool {
        if app.navigationBars["Settings"].exists {
            return true
        }
        guard app.navigationBars["Vault"].waitForExistence(timeout: 15) else {
            return false
        }
        guard tap(button(exactly: "Settings")) else {
            return false
        }
        return app.navigationBars["Settings"].waitForExistence(timeout: 10)
    }

    private func goBack() {
        let back = app.navigationBars.buttons.element(boundBy: 0)
        if back.exists && back.isHittable {
            back.tap()
        }
        _ = app.navigationBars["Vault"].waitForExistence(timeout: 10)
    }

    /// Buttons whose titles end in an ellipsis are matched by prefix, so the test
    /// does not hinge on the exact `…` character surviving every layer.
    private func button(startingWith label: String) -> XCUIElement {
        return app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", label))
            .firstMatch
    }

    private func button(exactly label: String) -> XCUIElement {
        return app.buttons[label]
    }

    @discardableResult
    private func tap(_ element: XCUIElement, timeout: TimeInterval = 10) -> Bool {
        guard element.waitForExistence(timeout: timeout) else {
            return false
        }
        guard element.isHittable else {
            return false
        }
        element.tap()
        return true
    }

    // MARK: - Reporting

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        captured.append(name)
    }

    private func skip(_ name: String, _ reason: String) {
        skipped.append("\(name) — \(reason)")
    }

    private func note(_ text: String) {
        notes.append(text)
    }

    /// Every visible label, joined. The failure message is the only channel out
    /// of CI until the attachment-export step exists, so the diagnosis has to
    /// travel in it.
    private func visibleText() -> String {
        let labels = app.staticTexts.allElementsBoundByIndex
            .prefix(30)
            .map { $0.label }
            .filter { !$0.isEmpty }
        return labels.joined(separator: " | ")
    }

    /// A machine-readable record of what the tour managed, attached alongside the
    /// images so a missing screenshot has a stated reason rather than being
    /// silently absent from the artefact.
    private func attachReport() {
        var lines = ["captured (\(captured.count)):"]
        for name in captured {
            lines.append("  \(name)")
        }
        lines.append("skipped (\(skipped.count)):")
        for entry in skipped {
            lines.append("  \(entry)")
        }
        lines.append("notes (\(notes.count)):")
        for entry in notes {
            lines.append("  \(entry)")
        }
        let report = lines.joined(separator: "\n")

        let attachment = XCTAttachment(string: report)
        attachment.name = "00-capture-report"
        attachment.lifetime = .keepAlways
        add(attachment)

        // Also printed, with a greppable prefix. The attachment only exists
        // inside the .xcresult; on a PASSING run nothing from it reaches the
        // build log, so without this there is no way to tell which screens the
        // tour actually got — only that it got at least the floor.
        for line in lines {
            print("SCREENSHOT-REPORT: \(line)")
        }
    }
}
