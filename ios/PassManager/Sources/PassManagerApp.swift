import SwiftUI
import UniformTypeIdentifiers

@main
struct PassManagerApp: App {

    @StateObject private var session = AppSession()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .task {
                    session.bootstrap()
                }
        }
        .onChange(of: scenePhase) { phase in
            session.handleScenePhase(phase)
        }
    }
}

/// Routes between the three top-level states, and hosts every transfer sheet.
///
/// The transfer UI lives HERE rather than in Settings on purpose. The Files
/// picker can send the app to the background, the auto-lock timer can fire while
/// it is up, and the picker then returns into a locked vault — which tears down
/// Settings and everything presented from it. Anchored at the root, these
/// survive the lock, and `TransferLockPolicy` rewinds the stage so the user is
/// asked to unlock and then dropped back exactly where they were.
struct RootView: View {

    @EnvironmentObject private var session: AppSession

    var body: some View {
        Group {
            switch session.lockState {
            case .needsSetup:
                OnboardingView()
            case .coldLocked, .warmLocked:
                LockView()
            case .unlocked:
                VaultListView()
            }
        }
        .tint(AppColor.primary)
        .background(AppColor.background)
        .sheet(isPresented: exportPassphrasePresented) {
            ExportPassphraseView()
        }
        .sheet(isPresented: importReviewPresented) {
            ImportReviewView()
        }
        .fileExporter(
            isPresented: exportPickerPresented,
            document: session.pendingExportDocument.map { PmVaultDocument(data: $0) },
            contentType: .pmvault,
            defaultFilename: Self.exportFilename()
        ) { result in
            switch result {
            case .success:
                session.completeExport(success: true)
            case .failure:
                // A user cancel arrives here too. Either way nothing was
                // written, and the built document must not linger in memory.
                session.completeExport(success: false)
            }
        }
        .fileImporter(
            isPresented: importPickerPresented,
            allowedContentTypes: [.pmvault],
            allowsMultipleSelection: false
        ) { result in
            handleImportSelection(result)
        }
    }

    // MARK: - Presentation bound to the session, not to local flags

    private var exportPassphrasePresented: Binding<Bool> {
        return Binding(
            get: { session.transferStage == .awaitingExportPassphrase },
            set: { shown in
                if !shown && session.transferStage == .awaitingExportPassphrase {
                    session.cancelTransfer()
                }
            }
        )
    }

    private var exportPickerPresented: Binding<Bool> {
        return Binding(
            get: { session.transferStage == .exporting && session.pendingExportDocument != nil },
            set: { shown in
                if !shown && session.transferStage == .exporting {
                    session.completeExport(success: false)
                }
            }
        )
    }

    private var importReviewPresented: Binding<Bool> {
        return Binding(
            get: {
                session.transferStage == .awaitingImportPassphrase
                    || session.transferStage == .reviewingImport
            },
            set: { shown in
                if !shown && session.transferStage.isImport {
                    session.cancelTransfer()
                }
            }
        )
    }

    private var importPickerPresented: Binding<Bool> {
        return Binding(
            get: { session.isPickingImportFile },
            set: { session.isPickingImportFile = $0 }
        )
    }

    // MARK: - Helpers

    private static func exportFilename() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return "passmanager-\(formatter.string(from: Date()))"
    }

    /// Reads the picked file into memory immediately.
    ///
    /// The URL is security-scoped and that access is tied to this callback, so
    /// holding the URL to read after an auto-lock would simply fail. The BYTES
    /// are ciphertext, so keeping them across a lock is safe — which is what lets
    /// the import resume instead of sending the user back to Files.
    private func handleImportSelection(_ result: Result<[URL], Error>) {
        session.isPickingImportFile = false
        switch result {
        case .success(let urls):
            guard let url = urls.first else {
                return
            }
            let scoped = url.startAccessingSecurityScopedResource()
            defer {
                if scoped {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            guard let data = try? Data(contentsOf: url) else {
                session.errorMessage = "That file could not be read."
                return
            }
            session.beginImport(fileData: data)
        case .failure:
            // Cancelled, or the picker failed. Nothing to report.
            break
        }
    }
}
