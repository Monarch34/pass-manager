import SwiftUI
import UIKit
import PassVaultCore

/// The site's icon where the category tile would be, falling back to the category
/// tile whenever there is not one.
///
/// INTERCHANGEABLE WITH ``CategoryTile`` BY CONSTRUCTION: same footprint, same
/// 25% corner radius, so a row does not move when an icon arrives, when one fails
/// to, or when the setting is switched off. Every failure lands in the same
/// place — a disabled setting, an address with no domain in it, a locked vault, a
/// 404, a refused redirect, a body that is not an image.
///
/// The icon is drawn on a plate rather than straight onto the row. A favicon is
/// artwork drawn for a white browser tab and a great many of them are a dark
/// glyph on transparency: GitHub's, to name the one in this app's own screenshot
/// tour. On the dark scheme's near-black canvas those vanish completely. The
/// plate is the same decision ``ShieldMark`` records for the shield — art that is
/// fixed light-scheme colour needs a fixed light-scheme ground — and it is what
/// makes the row read the same way in both appearances.
struct SiteIconTile: View {

    let category: ItemCategory
    /// The item's address envelope. Only a domain is ever taken out of it.
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

    /// The icon AND the domain it belongs to, together.
    ///
    /// A `List` reuses a row's storage when the item at that position changes, so
    /// an image held on its own would be drawn for one frame beside the next
    /// item's title. Keeping the domain with it means a stale icon is simply not
    /// the one being asked for, and is never shown.
    @State private var loaded: Loaded?

    private struct Loaded {
        let domain: String
        let image: UIImage
    }

    /// `nil` whenever no request should be made — which is the same condition as
    /// "show the category tile", so there is only one thing to get right.
    private var domain: String? {
        guard useSiteIcons else {
            return nil
        }
        return SiteIcon.domain(from: address)
    }

    private var shape: RoundedRectangle {
        return RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
    }

    var body: some View {
        Group {
            if let domain = domain, let loaded = loaded, loaded.domain == domain {
                Image(uiImage: loaded.image)
                    .resizable()
                    .scaledToFit()
                    .padding(size * 0.14)
                    .frame(width: size, height: size)
                    .background(AppColor.siteIconPlate)
                    .clipShape(shape)
                    // The plate is white in both schemes, so in the light one it
                    // needs an edge or it dissolves into the card behind it.
                    .overlay(shape.stroke(AppColor.outlineVariant, lineWidth: 1))
                    // Named the way Android names it. VoiceOver would otherwise
                    // meet an unlabelled image where the category used to be.
                    .accessibilityLabel("Icon for \(domain)")
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
                return
            }
            if let image = await SiteIconLoader.shared.image(for: domain) {
                loaded = Loaded(domain: domain, image: image)
            } else {
                loaded = nil
            }
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
