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

// MARK: - Field chrome

/// The fill and border for a standalone text field on onboarding and lock.
///
/// Replaces `.textFieldStyle(.roundedBorder)`, which the dark screenshots
/// caught rendering a near-black box on a near-black background — the field was
/// effectively invisible until focused. `.roundedBorder` draws a system fill
/// that assumes it sits on a grouped-list backdrop; these two screens are bare
/// canvas, so the fill has to come from the palette instead.
///
/// `surfaceContainerHigh` separates from `background` in BOTH schemes, and the
/// outline keeps the edge defined where the fill alone is subtle.
private struct FieldChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(AppColor.surfaceContainerHigh)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(AppColor.outlineVariant, lineWidth: 1)
            )
    }
}

extension View {
    func passphraseFieldChrome() -> some View {
        return modifier(FieldChrome())
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

// MARK: - Edge-faded horizontal strip

/// A horizontally scrolling row that FADES OUT at any edge it has content beyond,
/// instead of ending in a hard vertical cut.
///
/// A hard cut is not read as "there is more to the right". The vault list's filter
/// strip proved it: the last chip was sliced mid-word against a straight edge, and
/// the honest reaction to that is "the layout is broken", not "scroll me". Android
/// has never had the problem because `View` has `requiresFadingEdge`; iOS has no
/// equivalent, so the gradient is applied by hand.
///
/// EDGE-AWARE rather than a permanent trailing fade. A fade over an edge with
/// nothing past it is its own small lie — it implies content that is not there,
/// and it dims a chip the user can already fully see. So the strip measures itself
/// and fades only where it is actually cut off: nothing when everything fits, a
/// trailing fade when there is more to the right, and a leading one once something
/// has been scrolled off to the left.
///
/// The measurement is `GeometryReader` + `PreferenceKey`, which is the iOS 16 way.
/// `.onScrollGeometryChange` would say this in three lines and is iOS 18; the
/// deployment target is 16.0.
///
/// `.mask` and not an overlay of background-coloured gradient: a mask makes no
/// assumption about what the strip is sitting on, so it keeps working if the row
/// ever moves onto a card or a tinted section. It also leaves hit testing alone,
/// so a chip caught under the fade is still tappable.
struct EdgeFadedScrollView<Content: View>: View {

    private let content: Content
    private let fadeWidth: CGFloat

    @State private var contentFrame: CGRect = .zero
    @State private var viewportWidth: CGFloat = 0

    /// - Parameter fadeWidth: how much of a cut-off edge the ramp covers. The
    ///   default is wide enough that a clipped chip is unmistakably continuing,
    ///   and narrow enough that it never swallows a whole one.
    init(fadeWidth: CGFloat = 24, @ViewBuilder content: () -> Content) {
        self.fadeWidth = fadeWidth
        self.content = content()
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            content
                .background(
                    GeometryReader { proxy in
                        // Measured in the scroll view's OWN space, so `minX` is
                        // the scroll offset negated: 0 at rest, and increasingly
                        // negative as the content travels left.
                        Color.clear.preference(
                            key: EdgeFadeContentFrameKey.self,
                            value: proxy.frame(in: .named(edgeFadeCoordinateSpace))
                        )
                    }
                )
        }
        .coordinateSpace(name: edgeFadeCoordinateSpace)
        .background(
            GeometryReader { proxy in
                Color.clear.preference(
                    key: EdgeFadeViewportWidthKey.self,
                    value: proxy.size.width
                )
            }
        )
        .onPreferenceChange(EdgeFadeContentFrameKey.self) { frame in
            contentFrame = frame
        }
        .onPreferenceChange(EdgeFadeViewportWidthKey.self) { width in
            viewportWidth = width
        }
        // Closure form on purpose: the bare `mask(_:)` is deprecated from iOS 15.
        .mask { fade }
    }

    // MARK: - Geometry

    /// How far the content has travelled off the leading edge.
    private var scrollOffset: CGFloat {
        return -contentFrame.minX
    }

    private var showsLeadingFade: Bool {
        // A rubber-band overscroll drives this negative, which correctly reads as
        // "nothing is hidden on the left".
        return scrollOffset > 0.5
    }

    private var showsTrailingFade: Bool {
        // Before the first measurement lands, ASSUME there is more to the right.
        // A fade that shows for one frame too many is invisible; a hard cut for
        // one frame is the exact artefact this type exists to remove.
        guard contentFrame.width > 0, viewportWidth > 0 else {
            return true
        }
        return scrollOffset < contentFrame.width - viewportWidth - 0.5
    }

    /// Alpha ramp at each cut edge, opaque everywhere else.
    ///
    /// `.mask` keys off the mask's ALPHA, so `.clear` hides and `.black` shows;
    /// the colours themselves never appear on screen.
    private var fade: some View {
        HStack(spacing: 0) {
            LinearGradient(
                colors: [.clear, .black],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: showsLeadingFade ? fadeWidth : 0)

            Rectangle().fill(Color.black)

            LinearGradient(
                colors: [.black, .clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: showsTrailingFade ? fadeWidth : 0)
        }
        // Animated so a fade arrives and leaves with the scroll rather than
        // popping in at the moment the finger lifts.
        .animation(.easeOut(duration: 0.15), value: showsLeadingFade)
        .animation(.easeOut(duration: 0.15), value: showsTrailingFade)
    }
}

/// Resolved against the NEAREST enclosing space of this name, so two strips on one
/// screen each measure against their own scroll view despite sharing the constant.
private let edgeFadeCoordinateSpace = "EdgeFadedScrollView"

private struct EdgeFadeContentFrameKey: PreferenceKey {
    static let defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        // Siblings that never set the key contribute the default; taking it would
        // wipe a real measurement.
        let next = nextValue()
        if next != .zero {
            value = next
        }
    }
}

private struct EdgeFadeViewportWidthKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
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
