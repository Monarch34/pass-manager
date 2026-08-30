import PassManagerKit
import SwiftUI

struct VaultListView: View {
    @EnvironmentObject private var session: AppSession
    @State private var search = ""
    @State private var filter: ItemCategory?
    @State private var adding = false
    @State private var showingSettings = false

    private var visible: [VaultItem] {
        session.search(search).filter { filter == nil || $0.category == filter }
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Palette.background.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 10) {
                        categoryFilter

                        if visible.isEmpty {
                            emptyState
                        } else {
                            PanelCard {
                                ForEach(Array(visible.enumerated()), id: \.element.id.value) { index, item in
                                    NavigationLink(destination: ItemDetailView(item: item)) {
                                        ItemRow(item: item)
                                    }
                                    .buttonStyle(.plain)
                                    if index < visible.count - 1 {
                                        Divider().padding(.leading, 56)
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 80)
                }

                Button { adding = true } label: {
                    Image(systemName: "plus")
                        .font(.title2.weight(.semibold))
                        .frame(width: 56, height: 56)
                }
                .background(Palette.primary)
                .foregroundStyle(Palette.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(20)
            }
            .navigationTitle("Vault")
            .searchable(text: $search, prompt: "Search")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showingSettings = true } label: { Image(systemName: "gearshape") }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { session.lock() } label: { Image(systemName: "lock.fill") }
                }
            }
            .sheet(isPresented: $adding) {
                AddEditItemView(existing: nil)
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
            }
        }
    }

    private var categoryFilter: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip("All", active: filter == nil) { filter = nil }
                ForEach(AllCategories.list, id: \.name) { category in
                    chip(category.label, active: filter == category) {
                        filter = (filter == category) ? nil : category
                    }
                }
            }
        }
    }

    private func chip(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
        }
        .background(active ? Palette.primary : Palette.surface)
        .foregroundStyle(active ? Palette.onPrimary : Palette.onSurface)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray")
                .font(.system(size: 34))
                .foregroundStyle(Palette.onSurfaceVariant)
            Text(session.items.isEmpty ? "Nothing saved yet" : "Nothing matches")
                .font(.headline)
            Text(session.items.isEmpty ? "Add your first entry with the button below." : "Try a different search or category.")
                .font(.subheadline)
                .foregroundStyle(Palette.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 60)
    }
}

/// Kotlin enum entries do not arrive as a Swift `CaseIterable`, so the order the UI shows
/// them in is stated here rather than inferred.
enum AllCategories {
    static let list: [ItemCategory] = [.login, .card, .note, .identity, .bank]
}

struct ItemRow: View {
    let item: VaultItem

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Palette.category(item.category))
                .frame(width: 3, height: 28)
            Image(systemName: item.category.symbol)
                .font(.system(size: 15))
                .foregroundStyle(Palette.category(item.category))
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.payload.title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Palette.onSurface)
                if let subtitle = subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(Palette.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote)
                .foregroundStyle(Palette.onSurfaceVariant)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    /// Never a secret. The list is the one screen that is on display in a coffee shop.
    private var subtitle: String? {
        if let login = item.payload as? ItemPayloadLogin {
            return login.username.isEmpty ? login.address : login.username
        }
        if let card = item.payload as? ItemPayloadCard { return card.cardholderName }
        if let bank = item.payload as? ItemPayloadBank { return bank.bankName }
        if let identity = item.payload as? ItemPayloadIdentity { return identity.email }
        return nil
    }
}
