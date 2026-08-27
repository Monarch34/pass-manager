import SwiftUI
import PassVaultCore
import PassVaultStorage

struct VaultListView: View {

    @EnvironmentObject private var session: AppSession
    @State private var query: String = ""
    @State private var categoryFilter: ItemCategory?
    @State private var showingAdd = false

    private var visibleItems: [VaultItemHeaderRow] {
        return session.filteredHeaders(query: query, category: categoryFilter)
    }

    var body: some View {
        NavigationStack {
            Group {
                if session.itemCount == 0 {
                    emptyVault
                } else {
                    itemList
                }
            }
            .background(AppColor.background.ignoresSafeArea())
            .navigationTitle("Vault")
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $query, prompt: "Search vault")
            .toolbar {
                // Settings and the generator are tabs now, so `+` is the only
                // action left here — which is what a trailing nav-bar slot is
                // for. `.navigationBarTrailing`, not `.topBarTrailing`: that
                // spelling is iOS 17 and the deployment target is 16.
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showingAdd = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add item")
                }
            }
            .navigationDestination(for: String.self) { itemID in
                ItemDetailView(itemID: itemID)
            }
        }
        .sheet(isPresented: $showingAdd) {
            AddEditItemView(itemID: nil)
        }
    }

    // MARK: - Pieces

    /// The filter chips, shaped to sit as the FIRST ROW OF THE LIST rather than
    /// above it.
    ///
    /// Pinned above the list they permanently occupied the top of the screen and
    /// the last chip was clipped by the enclosing frame with no way to reach it.
    /// As a list row the strip scrolls away with the content, and the horizontal
    /// padding moves INSIDE the scroll view so the trailing chip can scroll fully
    /// into view instead of being cut off at the edge.
    ///
    /// TWO THINGS FIX THE TRAILING EDGE, and they are not alternatives.
    ///
    /// ``EdgeFadedScrollView`` removes the hard cut: whatever the width, an edge
    /// with more content past it now ramps to transparent instead of guillotining
    /// a chip mid-word. That is the part that has to hold at any Dynamic Type size
    /// and on any screen, because on a narrow phone or at accessibility sizes the
    /// row simply cannot fit and something must be off-screen.
    ///
    /// The padding numbers then buy back a chip. There are SIX — "All" plus the
    /// five categories — and an inset-grouped section already indents this row
    /// 20pt per side, leaving a 353pt viewport on a 393pt phone against about
    /// 405pt of chips. Something is off the end here no matter what; the only
    /// question is how much. Trimming each chip's horizontal padding 14 → 10 and
    /// the strip's own inset 20 → 16 recovers 52pt, which is precisely enough to
    /// pull "Identity" fully inside — it ends at 346pt now, against a 373pt
    /// edge — and leave "Bank" as the one under the fade. Those figures are
    /// measured off the CI screenshot tour rather than estimated.
    ///
    /// The smaller inset also starts the strip 4pt nearer the leading edge of the
    /// cards below it, which it should have been aligned with all along.
    private var categoryChips: some View {
        EdgeFadedScrollView {
            HStack(spacing: 8) {
                chip(title: "All", tint: AppColor.primary, isSelected: categoryFilter == nil) {
                    categoryFilter = nil
                }
                ForEach(ItemCategory.allCases, id: \.self) { category in
                    chip(
                        title: category.label,
                        tint: AppColor.tint(for: category),
                        isSelected: categoryFilter == category
                    ) {
                        categoryFilter = (categoryFilter == category) ? nil : category
                    }
                }
            }
            .padding(.horizontal, 16)
            // 2, not 8: each chip now carries its own 44pt hit box, so the strip
            // keeps the height it always had rather than growing by 12pt.
            .padding(.vertical, 2)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func chip(
        title: String,
        tint: Color,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(isSelected ? tint.opacity(0.18) : AppColor.surfaceVariant)
                .foregroundStyle(isSelected ? tint : AppColor.onSurfaceVariant)
                .clipShape(Capsule())
                .overlay(
                    Capsule().stroke(isSelected ? tint.opacity(0.5) : Color.clear, lineWidth: 1)
                )
                // The capsule measures about 30pt tall and the TARGET must not.
                // Narrowing the padding above shrank the chip in one axis, so the
                // other one is stated rather than inherited: 44pt of tappable
                // height around an unchanged capsule. That is the HIG floor, and
                // it was already being missed before this change.
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var itemList: some View {
        List {
            Section {
                categoryChips
            }

            if visibleItems.isEmpty {
                Section {
                    noMatches
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
            } else {
                Section {
                    ForEach(visibleItems, id: \.id) { header in
                        NavigationLink(value: header.id) {
                            row(for: header)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                session.deleteItem(id: header.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: false) {
                            copyPasswordButton(for: header)
                        }
                        .contextMenu {
                            copyPasswordButton(for: header)
                            Button(role: .destructive) {
                                session.deleteItem(id: header.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                } footer: {
                    Text(countLine)
                        .font(AppFont.footnote)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(AppColor.background)
    }

    /// Copy-password, offered only where the category actually has one.
    ///
    /// This is the one place in the list that reads a payload, and it does so
    /// ONLY in response to a deliberate tap on a single row — never to render
    /// one. Rendering stays on the header cache.
    @ViewBuilder
    private func copyPasswordButton(for header: VaultItemHeaderRow) -> some View {
        if header.category.hasCopyablePassword {
            Button {
                copyPassword(for: header.id)
            } label: {
                Label("Copy password", systemImage: "doc.on.doc")
            }
            .tint(AppColor.primary)
        }
    }

    private func row(for header: VaultItemHeaderRow) -> some View {
        // The title comes from the decrypted header cache. Before that pass
        // completes it is empty, and the row says so rather than showing a
        // placeholder that looks like real data.
        let title = displayTitle(for: header)
        return VaultRow(
            category: header.category,
            title: title,
            // The subtitle is the item's IDENTIFYING secondary value — the
            // login's address, the card's cardholder, the bank's name. It used to
            // repeat the category, which the tile to its left already says in
            // both colour and glyph, so the row spent a whole line telling you
            // nothing.
            //
            // This is the third envelope, decrypted by the header cache alongside
            // the title. Rendering a row never touches the payload.
            subtitle: subtitle(for: header, title: title),
            address: session.subtitle(for: header.id),
            useSiteIcons: session.useSiteIcons
        )
    }

    private func displayTitle(for header: VaultItemHeaderRow) -> String {
        let title = session.title(for: header.id)
        return title.isEmpty ? "Decrypting…" : title
    }

    /// `nil` when there is nothing worth putting on a second line.
    ///
    /// An item whose envelope is empty — or which merely repeats the title, as a
    /// bank record named after its own bank does — gets a single-line row. That
    /// is correct, not a missing subtitle: the point of the line is to add
    /// identifying information, and a second copy of the title adds none.
    private func subtitle(for header: VaultItemHeaderRow, title: String) -> String? {
        let subtitle = session.subtitle(for: header.id)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !subtitle.isEmpty else {
            return nil
        }
        guard subtitle.caseInsensitiveCompare(title) != .orderedSame else {
            return nil
        }
        return subtitle
    }

    private func copyPassword(for id: String) {
        guard let payload = session.payload(for: id) else {
            return
        }
        switch payload {
        case .login(let value):
            Clipboard.copySecret(value.password)
        case .bank(let value):
            Clipboard.copySecret(value.password)
        case .card, .note, .identity:
            break
        }
    }

    private var countLine: String {
        let count = visibleItems.count
        return count == 1 ? "1 item" : "\(count) items"
    }

    private var emptyVault: some View {
        VStack(spacing: 12) {
            Spacer()
            placeholder(
                symbol: "lock.open.rotation",
                title: "Your vault is empty",
                message: "Tap + to add your first login, card, note, identity or bank record."
            )
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    /// Kept INSIDE the list rather than replacing it, so the chip that filtered
    /// everything away is still on screen to be switched off again.
    private var noMatches: some View {
        placeholder(
            symbol: "magnifyingglass",
            title: "No matches",
            message: "Nothing here matches that filter."
        )
        .padding(.vertical, 28)
    }

    private func placeholder(symbol: String, title: String, message: String) -> some View {
        VStack(spacing: 12) {
            // `.system(size:)` is glyph sizing, not text — this is artwork.
            Image(systemName: symbol)
                .font(.system(size: 40))
                .foregroundStyle(AppColor.outline)
                .accessibilityHidden(true)
            Text(title)
                .font(.headline)
                .foregroundStyle(AppColor.onSurface)
            Text(message)
                .font(AppFont.rowSubtitle)
                .multilineTextAlignment(.center)
                .foregroundStyle(AppColor.onSurfaceVariant)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity)
    }
}

/// One vault entry: tile, title, identifying value.
///
/// A view rather than a function on the list, because it needs STATE — whether
/// its tile is currently showing a site icon. That answer arrives from the
/// network long after the row was laid out, and it decides where the category
/// lives.
///
/// THE RULE IS "THE CATEGORY IS WHEREVER THE TILE ISN'T." While the tile is the
/// category tile, the tile says it and the subtitle line stays exactly what it
/// has always been: the identifying value, or a single-line row when there is
/// none worth showing. The moment the tile becomes a site's icon, the category
/// moves onto the subtitle line as a small tinted glyph.
///
/// Drawn this way rather than "a glyph on every row while the setting is on",
/// which is what the first version did and what the screenshot then argued
/// against: on a row still showing its category tile, the glyph is the same
/// symbol in the same tint twice, twelve points apart — the exact repetition
/// this row was cured of. The cost of the narrower rule is that the glyph
/// arrives with the image rather than with the row, which is one small shift of
/// one line, once.
private struct VaultRow: View {

    let category: ItemCategory
    let title: String
    /// `nil` when there is nothing worth putting on a second line.
    let subtitle: String?
    /// The address envelope. Only a domain is ever taken out of it.
    let address: String
    let useSiteIcons: Bool

    @State private var tileShowsSiteIcon = false

    var body: some View {
        HStack(spacing: 12) {
            SiteIconTile(
                category: category,
                address: address,
                useSiteIcons: useSiteIcons,
                // Exactly one element speaks the category to VoiceOver: the tile
                // while it is one, the glyph once it is not.
                announcesCategory: !tileShowsSiteIcon,
                onIconVisible: { tileShowsSiteIcon = $0 }
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(AppFont.rowTitle)
                    .foregroundStyle(AppColor.onSurface)
                    .lineLimit(1)
                subtitleLine
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var subtitleLine: some View {
        if tileShowsSiteIcon {
            HStack(spacing: 5) {
                CategoryGlyph(category: category)
                // A row with no identifying value shows the category's own name
                // here, so the category never disappears entirely just because
                // the tile stopped carrying it.
                subtitleText(subtitle ?? category.label)
            }
        } else if let subtitle = subtitle {
            subtitleText(subtitle)
        }
    }

    private func subtitleText(_ value: String) -> some View {
        return Text(value)
            .font(AppFont.rowSubtitle)
            .foregroundStyle(AppColor.onSurfaceVariant)
            .lineLimit(1)
            .truncationMode(.middle)
    }
}
