import SwiftUI
import PassManagerKit

/// The design's tokens, in one place.
///
/// Carried over from version 1's palette rather than reinvented — the restyle changed
/// structure, not colour, and a vault that looks like a different application after an
/// update is a vault people trust less.
enum Palette {
    static let background = dynamic(light: 0xF6F8FC, dark: 0x0C0F14)
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x141820)
    static let surfaceVariant = dynamic(light: 0xEAEDF4, dark: 0x1B2029)

    static let onSurface = dynamic(light: 0x111318, dark: 0xE8ECF2)
    static let onSurfaceVariant = dynamic(light: 0x3E4450, dark: 0xA0A8B8)
    static let outline = dynamic(light: 0xC8CDD8, dark: 0x2A3040)

    static let primary = dynamic(light: 0x0D7A70, dark: 0x5EEAD4)
    static let onPrimary = dynamic(light: 0xFFFFFF, dark: 0x003731)

    static let error = dynamic(light: 0xDC2626, dark: 0xFCA5A5)

    /// One colour per category, so a row's kind reads before its text does.
    static func category(_ category: ItemCategory) -> Color {
        switch category {
        case .card: return dynamic(light: 0x7C3AED, dark: 0xC4B5FD)
        case .note: return dynamic(light: 0xB45309, dark: 0xFCD34D)
        case .identity: return dynamic(light: 0x0284C7, dark: 0x7DD3FC)
        case .bank: return dynamic(light: 0x15803D, dark: 0x86EFAC)
        default: return primary
        }
    }

    private static func dynamic(light: Int, dark: Int) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: dark) : UIColor(rgb: light) })
    }
}

private extension UIColor {
    convenience init(rgb: Int) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

enum Metrics {
    static let card: CGFloat = 20
    static let field: CGFloat = 12
    static let pill: CGFloat = 100
}

extension ItemCategory {
    var label: String {
        switch self {
        case .card: return "Card"
        case .note: return "Note"
        case .identity: return "Identity"
        case .bank: return "Bank"
        default: return "Login"
        }
    }

    var symbol: String {
        switch self {
        case .card: return "creditcard.fill"
        case .note: return "note.text"
        case .identity: return "person.text.rectangle.fill"
        case .bank: return "building.columns.fill"
        default: return "key.fill"
        }
    }
}

/// A grouped panel, which is the one structural device the whole design is built from.
struct PanelCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.card, style: .continuous))
    }
}

/// The label sits inside an unbroken border rather than above it — the field shape the
/// restyle introduced, and the reason this is a component rather than a modifier.
struct PanelField: View {
    let label: String
    @Binding var text: String
    var secure: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.footnote)
                .foregroundStyle(Palette.onSurfaceVariant)
            Group {
                if secure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
            }
            .foregroundStyle(Palette.onSurface)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .overlay(
            RoundedRectangle(cornerRadius: Metrics.field, style: .continuous)
                .stroke(Palette.outline, lineWidth: 1)
        )
    }
}

struct PillButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
        }
        .background(enabled ? Palette.primary : Palette.outline)
        .foregroundStyle(Palette.onPrimary)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.pill, style: .continuous))
        .disabled(!enabled)
    }
}
