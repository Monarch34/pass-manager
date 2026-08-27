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
    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
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
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
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
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? tint.opacity(0.18) : AppColor.surfaceVariant)
                .foregroundStyle(isSelected ? tint : AppColor.onSurfaceVariant)
                .clipShape(Capsule())
                .overlay(
                    Capsule().stroke(isSelected ? tint.opacity(0.5) : Color.clear, lineWidth: 1)
                )
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
        HStack(spacing: 12) {
            CategoryTile(category: header.category, announcesCategory: true)
            VStack(alignment: .leading, spacing: 2) {
                // The title comes from the decrypted header cache. Before that
                // pass completes it is empty, and the row says so rather than
                // showing a placeholder that looks like real data.
                Text(displayTitle(for: header))
                    .font(AppFont.rowTitle)
                    .foregroundStyle(AppColor.onSurface)
                    .lineLimit(1)

                // The subtitle is the item's IDENTIFYING secondary value — the
                // login's address, the card's cardholder, the bank's name. It
                // used to repeat the category, which the tile to its left
                // already says in both colour and glyph, so the row spent a
                // whole line telling you nothing.
                //
                // This is the third envelope, decrypted by the header cache
                // alongside the title. Rendering a row never touches the
                // payload.
                //
                // An item whose envelope is genuinely empty gets a single-line
                // row. That is correct, not a missing subtitle.
                let subtitle = session.subtitle(for: header.id)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(AppFont.rowSubtitle)
                        .foregroundStyle(AppColor.onSurfaceVariant)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    private func displayTitle(for header: VaultItemHeaderRow) -> String {
        let title = session.title(for: header.id)
        return title.isEmpty ? "Decrypting…" : title
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
