import SwiftUI

/// Small shared building blocks that more than one screen needs.
///
/// These live here rather than being copied per view so that a change to, say,
/// how a copy confirmation is worded happens once.

// MARK: - Copy confirmation

/// The transient line shown after copying a secret.
///
/// It names the clipboard expiry explicitly. "Copied" alone leaves the user to
/// wonder whether a password is now sitting in the pasteboard indefinitely; the
/// whole point of ``Clipboard/copySecret(_:)`` is that it is not, and the UI is
/// the only place that fact can be stated.
struct CopyConfirmation: View {
    var body: some View {
        Label {
            Text("Copied — clears in \(Int(Clipboard.clearAfter))s")
                .font(AppFont.footnote)
                .foregroundStyle(AppColor.primary)
        } icon: {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(AppColor.primary)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Warning row

/// An advisory row: amber triangle, footnote text.
///
/// Deliberately not `AppColor.error` — a vault that has never been exported is a
/// risk to point out, not a failure that has already happened.
struct WarningRow: View {
    let message: String

    var body: some View {
        Label {
            Text(message)
                .font(AppFont.footnote)
                .foregroundStyle(AppColor.onSurface)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(AppColor.strengthFair)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Primary action button

/// A full-width prominent button with a hit target that clears 44pt.
///
/// Used for the one action that a first-run screen exists to perform. Being a
/// real `.borderedProminent` rather than a hand-rolled rounded rectangle means it
/// picks up the system's pressed and disabled treatments for free.
struct PrimaryActionButton: View {
    let title: String
    let busyTitle: String
    let isBusy: Bool
    let action: () -> Void

    init(
        title: String,
        busyTitle: String,
        isBusy: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.busyTitle = busyTitle
        self.isBusy = isBusy
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isBusy {
                    ProgressView()
                        .tint(AppColor.onPrimary)
                }
                Text(isBusy ? busyTitle : title)
                    .font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 30)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .tint(AppColor.primary)
    }
}

// MARK: - Colourised secret

/// Renders a password with its character classes distinguished by colour.
///
/// This is a readability aid rather than decoration: when a user reads a
/// generated password aloud or retypes it, the expensive mistakes are at the
/// class boundaries — a digit taken for a letter, a symbol missed entirely.
/// Tinting the two non-letter classes makes those boundaries visible at a glance.
///
/// Built by concatenating `Text` so it stays one text run that Dynamic Type
/// scales and VoiceOver reads normally. The accessibility label is overridden by
/// the caller, because reading a password character-by-character is the only
/// useful way to hear one.
enum SecretText {

    static func colorized(_ value: String) -> Text {
        var result = Text("")
        for character in value {
            result = result + Text(String(character)).foregroundColor(tint(for: character))
        }
        return result
    }

    private static func tint(for character: Character) -> Color {
        if character.isNumber {
            return AppColor.digitTint
        }
        if character.isLetter {
            return AppColor.onSurface
        }
        return AppColor.symbolTint
    }
}

// MARK: - Detail field row

/// A label-above-value row.
///
/// The label/value-on-one-line arrangement iOS grouped lists default to works
/// for short values and fails badly for the ones this app actually stores: a URL
/// or a 16-digit card number gets squeezed into whatever the label leaves behind
/// and truncates. Stacking gives the value the full row width.
struct DetailFieldRow<Value: View>: View {
    let label: String
    let value: Value

    init(_ label: String, @ViewBuilder value: () -> Value) {
        self.label = label
        self.value = value()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(AppFont.fieldLabel)
                .foregroundStyle(AppColor.onSurfaceVariant)
            value
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 2)
    }
}
