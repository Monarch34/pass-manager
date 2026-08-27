import XCTest

/// Walks the app on a simulator and attaches one screenshot per screen, ONCE PER
/// APPEARANCE.
///
/// The attachment NAMES are the contract with CI: the workflow exports
/// attachments out of the `.xcresult` and the exported filenames derive from
/// these, so they are fixed strings and not descriptions. Every name carries an
/// appearance suffix, so the light and dark sets never collide on export and can
/// be compared side by side.
///
/// Capturing both appearances is not thoroughness for its own sake. Every colour
/// in the palette has a dark value, but a hardcoded colour or a leaked `.tint`
/// only reveals itself as a wrong-hued or low-contrast element on the other
/// scheme — and a light-only tour can never show it.
///
/// The tour is written to be TOLERANT rather than strict. A UI test that fails
/// the build on one unreachable screen produces no artefact at all, which is the
/// opposite of useful when the artefact is the deliverable. So each step captures
/// what it can, records what it could not reach, and attaches a plain-text report
/// listing both. A floor assertion still fails the run if the app is broken
/// outright rather than merely awkward to navigate.
final class ScreenshotTests: XCTestCase {

    private var app: XCUIApplication!
    private var suffix: String = ""
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

    override func tearDownWithError() throws {
        // Leave the simulator light, or the next test class inherits the dark
        // appearance this one finished in.
        //
        // `.unspecified` is the tempting value here and the simulator REFUSES
        // it — "Requested appearance value (0) is not valid". It exists to mean
        // "no override" when reading, not as something you may assign.
        XCUIDevice.shared.appearance = .light
    }

    func testCaptureScreens() {
        runTour(appearance: .light, suffix: "-light")
        runTour(appearance: .dark, suffix: "-dark")

        attachReport()
        XCTAssertGreaterThanOrEqual(
            captured.count,
            10,
            "Only \(captured.count) screens captured across both appearances.\n"
                + "captured: \(captured)\n"
                + "skipped: \(skipped)\n"
                + "notes: \(notes)"
        )
    }

    /// One complete pass in one appearance.
    ///
    /// The app is relaunched from scratch each time rather than having its
    /// appearance switched underneath it, because the tour ENDS on the lock
    /// screen — flipping the scheme in place would start the second pass halfway
    /// through the story, with no onboarding to photograph.
    private func runTour(appearance: XCUIDevice.Appearance, suffix: String) {
        self.suffix = suffix

        // Set BEFORE launch so the system chrome comes up already in this scheme
        // and no screenshot catches it mid-transition.
        //
        // This alone is NOT enough, and the first run proved it: the assignment
        // reported success while the simulator stayed light, and every "dark"
        // screenshot came back light apart from the clock. So the app is ALSO
        // told which scheme to render, via a launch argument it honours with
        // `.preferredColorScheme`. Belt and braces, because a silently-light
        // dark set is worse than no dark set — it looks like evidence.
        XCUIDevice.shared.appearance = appearance

        app = XCUIApplication()
        app.launchArguments += [
            "-uiTestReset",
            "-uiTestRelaxedKeychain",
            "-uiTestSeed",
            "-uiTestImportFixture",
            appearance == .dark ? "-uiTestAppearanceDark" : "-uiTestAppearanceLight"
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

        app.terminate()
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
        // "Decrypting…". The subtitle comes from the same pass, so this also
        // guarantees the row is showing its finished two-line form.
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
        // Tapping the title text rather than `cells.element(boundBy: 0)`: the
        // first cell is the filter-chip strip now that it scrolls with the list,
        // so an index would open nothing and silently drop this screen.
        let title = app.staticTexts["GitHub"]
        guard title.waitForExistence(timeout: 10), title.isHittable else {
            skip("04-view-item", "no vault row was reachable")
            return
        }
        title.tap()
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
        guard tap(tab("Generator")) else {
            skip("05-generator", "the generator tab was not reachable")
            return
        }
        guard app.navigationBars["Generator"].waitForExistence(timeout: 10) else {
            skip("05-generator", "the generator never appeared")
            return
        }
        // It generates on appear; give the first password a moment to render.
        _ = app.staticTexts["CHARACTER SETS"].waitForExistence(timeout: 10)
        capture("05-generator")
    }

    private func captureSettings() {
        guard openSettings() else {
            skip("06-settings", "settings was not reachable")
            return
        }
        capture("06-settings")
    }

    private func captureExport() {
        guard openSettings() else {
            skip("08-export", "settings was not reachable")
            return
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

    /// Settings is a TAB now, not a sheet, so it is always one tap away and can
    /// be re-entered without dismissing anything.
    private func openSettings() -> Bool {
        if app.navigationBars["Settings"].exists {
            return true
        }
        guard tap(tab("Settings")) else {
            return false
        }
        return app.navigationBars["Settings"].waitForExistence(timeout: 10)
    }

    private func tab(_ label: String) -> XCUIElement {
        return app.tabBars.buttons[label]
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
        attachment.name = name + suffix
        attachment.lifetime = .keepAlways
        add(attachment)
        captured.append(name + suffix)
    }

    private func skip(_ name: String, _ reason: String) {
        skipped.append("\(name)\(suffix) — \(reason)")
    }

    private func note(_ text: String) {
        notes.append("\(suffix.isEmpty ? "" : String(suffix.dropFirst()) + ": ")\(text)")
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
