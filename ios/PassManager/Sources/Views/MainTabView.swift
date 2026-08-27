import SwiftUI

/// The unlocked app's shell: three peer destinations in a tab bar.
///
/// This replaces a navigation bar that carried a gear on its LEADING side and a
/// dice glyph on its trailing side. Both were wrong on iOS: the leading slot
/// belongs to back/close, and neither icon told you where it went. Settings and
/// the generator are not modes of the vault list — they are places — so they get
/// tabs, which leaves `+` as the only trailing action on Vault.
///
/// This also removes a whole class of bug structurally rather than by timing.
/// While Settings was itself a sheet, opening a transfer sheet from it meant
/// dismissing and presenting in the same runloop turn, which UIKit drops on the
/// floor; the old code worked around it with a 0.4s delay. A tab is never
/// dismissed, so the hand-off simply does not exist any more.
struct MainTabView: View {

    var body: some View {
        TabView {
            VaultListView()
                .tabItem {
                    Label("Vault", systemImage: "lock.fill")
                }

            GeneratorTabView()
                .tabItem {
                    Label("Generator", systemImage: "wand.and.rays")
                }

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
        }
    }
}
