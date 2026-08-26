import SwiftUI
import PassVaultCore
import PassVaultStorage

struct VaultListView: View {

    @EnvironmentObject private var session: AppSession
    @State private var query: String = ""
    @State private var categoryFilter: ItemCategory?
    @State private var showingAdd = false
    @State private var showingSettings = false
    @State private var showingGenerator = false

    private var visibleItems: [VaultItemHeaderRow] {
        return session.filteredHeaders(query: query, category: categoryFilter)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                categoryChips

                if session.itemCount == 0 {
                    emptyVault
                } else if visibleItems.isEmpty {
                    noMatches
                } else {
                    itemList
                }
            }
            .background(AppColor.background.ignoresSafeArea())
            .navigationTitle("Vault")
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $query, prompt: "Search vault")
            .toolbar {
                // `.navigationBarLeading` / `.navigationBarTrailing`, not
                // `.topBarLeading` / `.topBarTrailing` — those are iOS 17.
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        showingSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        showingGenerator = true
                    } label: {
                        Image(systemName: "dice")
                    }
                    .accessibilityLabel("Password generator")

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
        .sheet(isPresented: $showingSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showingGenerator) {
            GeneratorView(constraintCategory: nil, onUse: nil)
        }
    }

    // MARK: - Pieces

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
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
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
                ForEach(visibleItems, id: \.id) { header in
                    NavigationLink(value: header.id) {
                        row(for: header)
                    }
                }
                .onDelete(perform: delete)
            } footer: {
                Text(countLine)
                    .font(.footnote)
                    .foregroundStyle(AppColor.onSurfaceVariant)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(AppColor.background)
    }

    private func row(for header: VaultItemHeaderRow) -> some View {
        HStack(spacing: 12) {
            CategoryTile(category: header.category)
            VStack(alignment: .leading, spacing: 2) {
                // The title comes from the decrypted header cache. Before that
                // pass completes it is empty, and the row says so rather than
                // showing a placeholder that looks like real data.
                Text(displayTitle(for: header))
                    .font(.body.weight(.medium))
                    .foregroundStyle(AppColor.onSurface)
                    .lineLimit(1)
                Text(header.category.label)
                    .font(.caption)
                    .foregroundStyle(AppColor.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    private func displayTitle(for header: VaultItemHeaderRow) -> String {
        let title = session.title(for: header.id)
        return title.isEmpty ? "Decrypting…" : title
    }

    private var countLine: String {
        let count = visibleItems.count
        return count == 1 ? "1 item" : "\(count) items"
    }

    private var emptyVault: some View {
        placeholder(
            symbol: "lock.open.rotation",
            title: "Your vault is empty",
            message: "Tap + to add your first login, card, note, identity or bank record."
        )
    }

    private var noMatches: some View {
        placeholder(
            symbol: "magnifyingglass",
            title: "No matches",
            message: "Nothing here matches that search."
        )
    }

    private func placeholder(symbol: String, title: String, message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: symbol)
                .font(.system(size: 44))
                .foregroundStyle(AppColor.outline)
            Text(title)
                .font(.headline)
                .foregroundStyle(AppColor.onSurface)
            Text(message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(AppColor.onSurfaceVariant)
                .padding(.horizontal, 40)
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func delete(at offsets: IndexSet) {
        let items = visibleItems
        for index in offsets {
            if index < items.count {
                session.deleteItem(id: items[index].id)
            }
        }
    }
}
