import XCTest
import SwiftUI
@testable import PassManager

/// How big a site icon is drawn, and why it is never a fractional enlargement.
///
/// `docs/IOS_PARITY.md` states the rule: ask the CDN for `size=256`, inset the
/// icon inside its plate, and where the source is still smaller than that box
/// draw it at the largest WHOLE multiple of itself that fits. The first half is a
/// URL and is tested in `SiteIconTests`; this is the half no request parameter can
/// fix, because a site that publishes 32×32 publishes 32×32 whatever you ask for.
///
/// Arithmetic, so it is tested as arithmetic. A `@ViewBuilder` cannot be.
final class SiteIconTileTests: XCTestCase {

    /// The vault row's tile and the item hero's, in points.
    private let rowTile: CGFloat = 40
    private let heroTile: CGFloat = 56

    private func box(_ tile: CGFloat) -> CGFloat {
        return tile * SiteIconTile.contentFraction
    }

    // MARK: - The case the complaint was about

    /// GitHub publishes 32×32 and returns it at every requested size, so this is
    /// the domain the whole rule exists for. Filling a 40pt plate on a 3x screen
    /// meant 120px from a 32px source — a 3.75x smooth upscale, which is what read
    /// as cheap in `10-site-icons-light.png`.
    ///
    /// The number asserted is DEVICE PIXELS, because that is where the mistake
    /// lived: 64 is 32 doubled, exactly, with nothing left to interpolate.
    func testSmallSourceLandsOnAWholeMultipleInTheRow() {
        let plan = SiteIconTile.drawing(sourcePixels: 32, box: box(rowTile), scale: 3)
        XCTAssertEqual(plan.edge * 3, 64, accuracy: 0.001, "32px must be drawn at exactly 2x.")
        XCTAssertTrue(plan.isWholeMultipleUpscale, "A whole-multiple draw must turn interpolation off.")
    }

    /// The hero is 56pt, so the same source gets a whole 3x there. Different tile,
    /// same rule, and no second code path.
    func testSmallSourceLandsOnAWholeMultipleInTheHero() {
        let plan = SiteIconTile.drawing(sourcePixels: 32, box: box(heroTile), scale: 3)
        XCTAssertEqual(plan.edge * 3, 96, accuracy: 0.001, "32px must be drawn at exactly 3x in the hero.")
        XCTAssertTrue(plan.isWholeMultipleUpscale)
    }

    /// Netflix caps at 64×64. At 1x it is simply drawn at its own size rather than
    /// stretched to the 80px box — a native-size icon is never wrong.
    func testSourceTooLargeToDoubleIsDrawnAtItsOwnSize() {
        let plan = SiteIconTile.drawing(sourcePixels: 64, box: box(rowTile), scale: 3)
        XCTAssertEqual(plan.edge * 3, 64, accuracy: 0.001)
        XCTAssertTrue(plan.isWholeMultipleUpscale)
    }

    // MARK: - Downscaling

    /// `stackoverflow.com` and `garantibbva.com.tr` return 180×180 at `size=256`.
    /// Bigger than the box, so it comes down to fill it — with interpolation ON,
    /// because downscaling is the direction that wants it.
    func testLargeSourceFillsTheBoxAndInterpolates() {
        for source in [CGFloat(128), 144, 180, 256] {
            let plan = SiteIconTile.drawing(sourcePixels: source, box: box(rowTile), scale: 3)
            XCTAssertEqual(plan.edge, box(rowTile), accuracy: 0.001, "\(source)px should fill the box.")
            XCTAssertFalse(plan.isWholeMultipleUpscale, "\(source)px is a downscale, not an enlargement.")
        }
    }

    // MARK: - Properties that must hold everywhere

    /// The icon may never spill out of its inset box, whatever the source or the
    /// screen. This is the assertion that keeps the plate a container.
    func testTheIconNeverExceedsItsBox() {
        for scale in [CGFloat(1), 2, 3] {
            for tile in [rowTile, heroTile] {
                for source in stride(from: CGFloat(1), through: 400, by: 1) {
                    let plan = SiteIconTile.drawing(sourcePixels: source, box: box(tile), scale: scale)
                    XCTAssertLessThanOrEqual(
                        plan.edge,
                        box(tile) + 0.001,
                        "source \(source) at \(scale)x in a \(tile)pt tile overflowed its box."
                    )
                }
            }
        }
    }

    /// Every enlargement is a whole number of source pixels per drawn pixel. A
    /// fractional one is the defect, so it is asserted as a property rather than
    /// spot-checked.
    func testEveryEnlargementIsAWholeMultiple() {
        for scale in [CGFloat(1), 2, 3] {
            for source in stride(from: CGFloat(1), through: 400, by: 1) {
                let plan = SiteIconTile.drawing(sourcePixels: source, box: box(rowTile), scale: scale)
                guard plan.isWholeMultipleUpscale else {
                    continue
                }
                let multiple = plan.edge * scale / source
                XCTAssertEqual(
                    multiple,
                    multiple.rounded(),
                    accuracy: 0.001,
                    "source \(source) at \(scale)x was enlarged by \(multiple), which is not whole."
                )
                XCTAssertGreaterThanOrEqual(multiple, 1, "An icon is never drawn smaller than its source.")
            }
        }
    }

    /// A decode that somehow yields no pixels falls back to the box rather than to
    /// a zero-sized image or a division by zero. The row would rather show a
    /// stretched nothing than crash.
    func testDegenerateInputFallsBackToTheBox() {
        for source in [CGFloat(0), -1] {
            let plan = SiteIconTile.drawing(sourcePixels: source, box: box(rowTile), scale: 3)
            XCTAssertEqual(plan.edge, box(rowTile), accuracy: 0.001)
            XCTAssertFalse(plan.isWholeMultipleUpscale)
        }
        let noScale = SiteIconTile.drawing(sourcePixels: 32, box: box(rowTile), scale: 0)
        XCTAssertEqual(noScale.edge, box(rowTile), accuracy: 0.001)
    }

    // MARK: - The inset itself

    /// The plate is a container, so the icon is content inside it and not a frame
    /// around a stretched image. Pinned because "make the icon bigger" is the
    /// obvious wrong fix for an icon that looks soft.
    func testTheIconIsInsetInsideThePlate() {
        XCTAssertLessThan(SiteIconTile.contentFraction, 0.8)
        XCTAssertGreaterThan(SiteIconTile.contentFraction, 0.5)
    }
}
