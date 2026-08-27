import SwiftUI
import UIKit
import PassVaultCore

/// The site's icon where the category tile would be, falling back to the category
/// tile whenever there is not one.
///
/// INTERCHANGEABLE WITH ``CategoryTile`` BY CONSTRUCTION: same footprint, same
/// 25% corner radius, so a row does not move when an icon arrives, when one fails
/// to, or when the setting is switched off. Every failure lands in the same
/// place — a disabled setting, an item that is not a login, an address with no
/// domain in it, a locked vault, a 404, a refused redirect, a body that is not an
/// image. Four of the five categories therefore stay on their tile permanently,
/// which is the intended look and not a degraded one.
///
/// The icon is drawn on a plate rather than straight onto the row. A favicon is
/// artwork drawn for a white browser tab and a great many of them are a dark
/// glyph on transparency: GitHub's, to name the one in this app's own screenshot
/// tour. On the dark scheme's near-black canvas those vanish completely. The
/// plate is the same decision ``ShieldMark`` records for the shield — art that is
/// fixed light-scheme colour needs a fixed light-scheme ground — and it is what
/// makes the row read the same way in both appearances.
///
/// THE PLATE IS A CONTAINER AND THE ICON IS CONTENT INSIDE IT, which is how the
/// system's own list rows treat an app icon, and it is also what keeps a small
/// source honest: filling the plate edge to edge means smooth-scaling GitHub's
/// 32×32 by 3.75 on a 3x screen, and that fractional upscale is precisely what
/// reads as cheap. So the icon is inset to ``contentFraction`` of the plate and
/// then, if the source is still smaller than that box, drawn at the largest
/// INTEGER multiple of itself that fits. See ``drawing(for:)``.
struct SiteIconTile: View {

    /// Decides whether the address envelope is even looked at: only a login's
    /// holds a site. See `SiteIcon.domain(for:address:)`.
    let category: ItemCategory
    /// The item's address envelope. Only a login's domain is ever taken out of
    /// it — for every other category this string is never parsed at all.
    let address: String
    /// Passed in rather than read from the environment, so that "the setting is
    /// off, therefore nothing is requested" is visible at the call site instead
    /// of buried two types down.
    let useSiteIcons: Bool
    var size: CGFloat = 40
    /// Forwarded to the fallback tile: true where the tile is the only thing
    /// carrying the category. When an icon IS showing, the category is carried
    /// beside the subtitle instead and this tile must not say it twice.
    var announcesCategory: Bool = false
    /// Called when this tile starts or stops showing a real site icon.
    ///
    /// The tile is the only place that knows — the answer arrives from the
    /// network, long after the row was laid out — and the row needs it, because
    /// the category has to move exactly when the tile stops carrying it and not
    /// one row sooner.
    var onIconVisible: ((Bool) -> Void)? = nil

    /// The icon AND the domain it belongs to, together.
    ///
    /// A `List` reuses a row's storage when the item at that position changes, so
    /// an image held on its own would be drawn for one frame beside the next
    /// item's title. Keeping the domain with it means a stale icon is simply not
    /// the one being asked for, and is never shown.
    @State private var loaded: Loaded?

    private struct Loaded {
        let domain: SiteIcon.Domain
        let image: UIImage
    }

    /// `nil` whenever no request should be made — which is the same condition as
    /// "show the category tile", so there is only one thing to get right.
    ///
    /// The category goes in alongside the address and the gate refuses everything
    /// but a login, so a card, a bank, a note and an identity all land here as
    /// `nil` without their envelope ever being parsed.
    private var domain: SiteIcon.Domain? {
        guard useSiteIcons else {
            return nil
        }
        return SiteIcon.domain(for: category, address: address)
    }

    private var shape: RoundedRectangle {
        return RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
    }

    // MARK: - How big the icon is drawn

    /// How much of the plate the icon may occupy before the integer clamp.
    ///
    /// Two thirds, which is roughly where the system puts an app icon inside a
    /// list row's rounded square, and on a 3x screen it also happens to land
    /// GitHub's 32×32 on a clean whole multiple: a 40pt plate gives an 80px box,
    /// and the largest whole multiple of 32 that fits is 64px.
    ///
    /// That 64px is the number to protect if this fraction ever moves. Measured on
    /// the 3x tour screenshot, the seeded rows' category glyphs draw 57–62px of
    /// ink inside their 120px tiles — so the icon and the tiles it sits above read
    /// as one size family rather than one being conspicuously smaller.
    static let contentFraction: CGFloat = 2.0 / 3.0

    /// Points and pixels are different questions here, so the scale is read from
    /// the environment rather than from `UIScreen.main` — which is deprecated for
    /// exactly this and is the wrong screen in a second scene anyway.
    @Environment(\.displayScale) private var displayScale

    /// The edge to draw the icon at, and whether getting there is a whole-number
    /// enlargement.
    ///
    /// Carries a `Bool` rather than an `Image.Interpolation` so the arithmetic
    /// below is arithmetic and nothing else — the tests can hold it without
    /// reaching into SwiftUI, and the view turns it into a resampling mode at the
    /// one place that draws.
    struct Drawing: Equatable {
        let edge: CGFloat
        let isWholeMultipleUpscale: Bool
    }

    /// NEVER UPSCALE A RASTER ICON BY A FRACTIONAL FACTOR. That is the whole rule,
    /// and `docs/IOS_PARITY.md` states it for both platforms.
    ///
    /// Google's CDN serves what the site publishes and no more, so `size=256` does
    /// not save a domain that only ever drew a 32×32 — GitHub is exactly that
    /// domain, and it is the one in this app's screenshot tour. Stretched to fill
    /// a 40pt tile on a 3x screen it was being smooth-scaled by 3.75, which is the
    /// blur the icons were rightly called out for.
    ///
    /// So: a source SMALLER than its box is drawn at the largest whole multiple of
    /// itself that fits, centred, with interpolation off — at an exact 2x or 3x
    /// there is nothing to interpolate and asking for it only reintroduces the
    /// softness. A source LARGER than its box is scaled down normally, at high
    /// quality; downscaling is always the better direction and a 180×180 has
    /// detail to spare.
    ///
    /// Static and pure so the clamp can be tested at every scale the app runs at,
    /// which a `@ViewBuilder` cannot be. Everything is in PIXELS except `box` and
    /// the returned `edge`, which are points, because that is the boundary the
    /// mistake lives on.
    static func drawing(sourcePixels: CGFloat, box: CGFloat, scale: CGFloat) -> Drawing {
        let boxPixels = box * scale
        guard sourcePixels > 0, scale > 0, sourcePixels < boxPixels else {
            // Nothing to enlarge, or a source big enough to come DOWN to the box.
            return Drawing(edge: box, isWholeMultipleUpscale: false)
        }
        let multiple = max(1, (boxPixels / sourcePixels).rounded(.down))
        return Drawing(edge: sourcePixels * multiple / scale, isWholeMultipleUpscale: true)
    }

    private func drawing(for image: UIImage) -> Drawing {
        return Self.drawing(
            // `image.size` is in points; a favicon decoded from `Data` has scale 1,
            // so this is its pixel edge either way rather than only usually.
            sourcePixels: max(image.size.width, image.size.height) * image.scale,
            box: size * Self.contentFraction,
            scale: max(displayScale, 1)
        )
    }

    var body: some View {
        Group {
            if let domain = domain, let loaded = loaded, loaded.domain == domain {
                let plan = drawing(for: loaded.image)
                Image(uiImage: loaded.image)
                    .resizable()
                    .interpolation(plan.isWholeMultipleUpscale ? Image.Interpolation.none : .high)
                    .scaledToFit()
                    // Two frames, not one: the inner one is the icon's own size,
                    // the outer one is the plate, and the default centre alignment
                    // is what puts the icon in the middle of it. `.padding` cannot
                    // express this — it would give the image the whole remaining
                    // box and let it stretch to fill.
                    .frame(width: plan.edge, height: plan.edge)
                    .frame(width: size, height: size)
                    .background(AppColor.siteIconPlate)
                    .clipShape(shape)
                    // The plate is white in both schemes, so in the light one it
                    // needs an edge or it dissolves into the card behind it.
                    .overlay(shape.stroke(AppColor.outlineVariant, lineWidth: 1))
                    // Named the way Android names it. VoiceOver would otherwise
                    // meet an unlabelled image where the category used to be.
                    .accessibilityLabel("Icon for \(domain.value)")
            } else {
                CategoryTile(
                    category: category,
                    size: size,
                    announcesCategory: announcesCategory
                )
            }
        }
        // Keyed on the domain, so this re-runs when the setting is switched — off
        // clears the image, on asks again — and not on every unrelated redraw.
        .task(id: domain) {
            guard let domain = domain else {
                loaded = nil
                onIconVisible?(false)
                return
            }
            let image = await SiteIconLoader.shared.image(for: domain)
            loaded = image.map { Loaded(domain: domain, image: $0) }
            onIconVisible?(image != nil)
        }
    }
}

/// The category, as a small tinted glyph that sits ON the subtitle line.
///
/// It exists because of a collision between two good decisions. Android's vault
/// row spends its subtitle on the category label, which is what lets the tile
/// become the site's icon without the category disappearing. This app's row
/// spends that line on the item's identifying value instead — the login's
/// address, the card's cardholder — which is strictly more useful and is staying.
/// So when icons are on and the tile stops being the category, the category needs
/// somewhere else to live, and this is it.
///
/// Sized with the subtitle's own text style at `.small` image scale rather than a
/// fixed point size: it has to sit WITH the subtitle at every Dynamic Type size,
/// which a hardcoded glyph would stop doing at the second notch.
struct CategoryGlyph: View {
    let category: ItemCategory

    var body: some View {
        Image(systemName: category.symbolName)
            .font(AppFont.rowSubtitle)
            .imageScale(.small)
            .foregroundStyle(AppColor.tint(for: category))
            // Carries the category for VoiceOver now that the tile alongside it
            // may be showing a site icon instead.
            .accessibilityLabel(category.label)
    }
}
