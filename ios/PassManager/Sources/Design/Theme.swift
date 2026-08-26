import SwiftUI
import UIKit
import PassVaultCore

/// The colour palette.
///
/// Every value here is copied from
/// `protocol/src/main/kotlin/com/passmanager/protocol/design/Palette.kt` and
/// `LogoPalette.kt`, which `docs/IOS_PARITY.md` names as the single source of
/// truth. Do not invent values; if a colour is missing, add it there first.
///
/// Light and dark are both defined as one dynamic `UIColor`, so the app follows
/// the system setting with no theme plumbing and no `@Environment(\.colorScheme)`
/// branching at every call site.
enum AppColor {

    // Canvas and surfaces
    static let background = dynamic(light: 0xF6F8FC, dark: 0x0C0F14)
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x141820)
    static let surfaceVariant = dynamic(light: 0xE4E8F0, dark: 0x1E2430)
    static let surfaceContainer = dynamic(light: 0xEAEDF4, dark: 0x161B26)
    static let surfaceContainerHigh = dynamic(light: 0xE4E7EF, dark: 0x1C2230)

    // Content
    static let onBackground = dynamic(light: 0x111318, dark: 0xE8ECF2)
    static let onSurface = dynamic(light: 0x111318, dark: 0xE8ECF2)
    static let onSurfaceVariant = dynamic(light: 0x3E4450, dark: 0xA0A8B8)
    static let outline = dynamic(light: 0x6E7688, dark: 0x3B4254)
    static let outlineVariant = dynamic(light: 0xC8CDD8, dark: 0x2A3040)

    // The single accent family: teal.
    static let primary = dynamic(light: 0x0D7A70, dark: 0x5EEAD4)
    static let onPrimary = dynamic(light: 0xFFFFFF, dark: 0x003731)
    static let primaryContainer = dynamic(light: 0xCCFBF1, dark: 0x0D9488)
    static let onPrimaryContainer = dynamic(light: 0x00312C, dark: 0xCCFBF1)

    static let error = dynamic(light: 0xDC2626, dark: 0xFCA5A5)
    static let errorContainer = dynamic(light: 0xFEE2E2, dark: 0x991B1B)
    static let onErrorContainer = dynamic(light: 0x7F1D1D, dark: 0xFEE2E2)

    // Strength bar. Weak reuses `error`, strong reuses `primary`; only "fair"
    // needs its own token, and Palette.kt has exactly one.
    static let strengthFair = fixed(0xFF8F00)
    static let strengthGood = dynamic(light: 0x0284C7, dark: 0x38BDF8)

    /// Category tints are DATA IDENTITY, not decoration: the same colour means
    /// the same category on both platforms, in both themes. They are therefore
    /// fixed rather than dynamic, exactly as Palette.kt declares them.
    static func tint(for category: ItemCategory) -> Color {
        switch category {
        case .login: return fixed(0x0D9488)
        case .card: return fixed(0xB45309)
        case .note: return fixed(0x7C3AED)
        case .identity: return fixed(0x0284C7)
        case .bank: return fixed(0x059669)
        }
    }

    // Logo. The plate is pinned to LIGHT_PRIMARY_CONTAINER in BOTH themes —
    // LogoPalette.kt explains why: the shield art is fixed light-scheme colour,
    // and on the dark scheme's container it measured 1.22:1 and vanished.
    static let logoPlate = fixed(0xCCFBF1)
    static let logoTealDark = fixed(0x1A6D68)
    static let logoTealLight = fixed(0x21837D)

    // MARK: - Construction

    static func dynamic(light: UInt32, dark: UInt32) -> Color {
        return Color(UIColor { traits in
            return traits.userInterfaceStyle == .dark
                ? UIColor(rgb: dark)
                : UIColor(rgb: light)
        })
    }

    static func fixed(_ rgb: UInt32) -> Color {
        return Color(UIColor(rgb: rgb))
    }
}

extension UIColor {
    /// 24-bit `0xRRGGBB`, always fully opaque — the form every constant in
    /// Palette.kt takes once its `0xFF` alpha is dropped.
    convenience init(rgb: UInt32) {
        let red = CGFloat((rgb >> 16) & 0xFF) / 255.0
        let green = CGFloat((rgb >> 8) & 0xFF) / 255.0
        let blue = CGFloat(rgb & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue, alpha: 1.0)
    }
}

extension ItemCategory {
    /// SF Symbol per category. Chosen from symbols that have existed since well
    /// before iOS 16 so the list cannot render blank on an older device.
    var symbolName: String {
        switch self {
        case .login: return "key.fill"
        case .card: return "creditcard.fill"
        case .note: return "note.text"
        case .identity: return "person.crop.circle.fill"
        case .bank: return "building.columns.fill"
        }
    }
}

/// The 40pt category tile used in vault list rows, per `docs/IOS_PARITY.md`.
struct CategoryTile: View {
    let category: ItemCategory
    var size: CGFloat = 40

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(AppColor.tint(for: category).opacity(0.16))
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: category.symbolName)
                    .font(.system(size: size * 0.44, weight: .semibold))
                    .foregroundStyle(AppColor.tint(for: category))
            )
            .accessibilityHidden(true)
    }
}

/// The app mark: a mint plate with the shield centred on it.
///
/// APPROXIMATION, stated plainly: the canonical geometry is the 280x335 vector at
/// `app/src/main/res/drawable/ic_vault_shield.xml`, with its inner shield and
/// circuit traces. This draws the plate to spec — rounded square, 25% corner
/// radius, shield at 58.3% of the plate width — but uses an SF Symbol for the
/// shield itself rather than re-tracing the path. It reads as the same lockup at
/// icon sizes; it is not a pixel match, and should be replaced with the real path
/// before the app ships an App Store icon.
struct ShieldMark: View {
    var size: CGFloat = 72

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(AppColor.logoPlate)
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: size * 0.583, weight: .semibold))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [AppColor.logoTealDark, AppColor.logoTealLight],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            )
            .accessibilityLabel("PassManager")
    }
}

/// A four-segment strength bar shared by onboarding, the generator and the
/// add/edit form.
struct StrengthBar: View {
    let strength: PasswordStrength
    var showsLabel: Bool = true

    private var color: Color {
        switch strength {
        case .weak: return AppColor.error
        case .fair: return AppColor.strengthFair
        case .good: return AppColor.strengthGood
        case .strong: return AppColor.primary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(0..<4, id: \.self) { index in
                    Capsule()
                        .fill(index <= strength.rawValue ? color : AppColor.outlineVariant)
                        .frame(height: 4)
                }
            }
            if showsLabel {
                Text(strength.label)
                    .font(.caption)
                    .foregroundStyle(color)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Password strength: \(strength.label)")
    }
}
