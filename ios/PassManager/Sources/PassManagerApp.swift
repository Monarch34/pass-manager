import SwiftUI

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

/// Routes between the three top-level states. Nothing below this point renders
/// vault content unless the session is unlocked.
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
    }
}
