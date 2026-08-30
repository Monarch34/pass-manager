import PassManagerKit
import SwiftUI

/// A password field, with what it is worth written under it.
///
/// The meter is here rather than on a screen of its own because the only moment the number
/// can change a decision is while the password is being typed or chosen. Shown afterwards it
/// is trivia.
struct PasswordField: View {
    let label: String
    @Binding var text: String
    @State private var generating = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PanelField(label: label, text: $text, secure: true)

            HStack(alignment: .bottom) {
                if text.isEmpty {
                    Spacer()
                } else {
                    StrengthMeter(strength: PasswordStrength.companion.of(password: text))
                }
                Button("Generate") { generating = true }
                    .font(.subheadline)
            }
        }
        .sheet(isPresented: $generating) {
            GeneratorSheet { drawn in
                text = drawn
                generating = false
            }
        }
    }
}

private struct StrengthMeter: View {
    let strength: PasswordStrength

    private var colour: Color {
        switch strength.band {
        case .trivial, .weak: return Palette.error
        case .reasonable: return Palette.onSurfaceVariant
        default: return Palette.primary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.outline).frame(height: 3)
                    Capsule()
                        .fill(colour)
                        .frame(width: geometry.size.width * CGFloat(strength.fraction), height: 3)
                }
            }
            .frame(height: 3)

            Text(strength.summary)
                .font(.footnote)
                .foregroundStyle(colour)
        }
    }
}

/// The generator, with the password visible before it is accepted.
///
/// Shown rather than applied silently, because a password nobody has looked at is one nobody
/// noticed was rejected by the site's own rules — and because the length and the alphabet are
/// the two things people actually want to change.
private struct GeneratorSheet: View {
    let onUse: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var length = Double(PasswordRecipe.companion.DefaultLength)
    @State private var uppercase = true
    @State private var digits = true
    @State private var symbols = true
    @State private var unambiguous = false
    @State private var drawn = ""

    private var recipe: PasswordRecipe {
        PasswordRecipe(
            length: Int32(length.rounded()),
            lowercase: true,
            uppercase: uppercase,
            digits: digits,
            symbols: symbols,
            avoidAmbiguous: unambiguous,
            requireEachClass: true
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    PanelCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(drawn)
                                .font(.system(.body, design: .monospaced))
                                .textSelection(.enabled)
                            Text("\(Int(length.rounded())) characters, \(Int(PasswordGenerator.shared.bits(recipe: recipe).rounded())) bits")
                                .font(.footnote)
                                .foregroundStyle(Palette.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                    }

                    Slider(
                        value: $length,
                        in: Double(PasswordRecipe.companion.MinLength)...64,
                        step: 1
                    )

                    // Lower case is not offered as a switch, which is what keeps every
                    // reachable combination legal: a recipe with no classes at all is refused
                    // outright, and this way nobody can ask for one.
                    Toggle("Capitals", isOn: $uppercase)
                    Toggle("Digits", isOn: $digits)
                    Toggle("Symbols", isOn: $symbols)
                    Toggle("Avoid lookalike characters", isOn: $unambiguous)

                    Button("Draw another") { redraw() }
                        .font(.subheadline)
                }
                .padding(16)
            }
            .background(Palette.background.ignoresSafeArea())
            .navigationTitle("Generate a password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Use this") { onUse(drawn) }
                }
            }
            .onAppear(perform: redraw)
            // Every knob changes the recipe, and a preview of a recipe nobody asked for is
            // worse than no preview: it is a password the user believes they are about to get.
            .onChange(of: length) { _ in redraw() }
            .onChange(of: uppercase) { _ in redraw() }
            .onChange(of: digits) { _ in redraw() }
            .onChange(of: symbols) { _ in redraw() }
            .onChange(of: unambiguous) { _ in redraw() }
        }
    }

    private func redraw() {
        drawn = PasswordGenerator.shared.generate(recipe: recipe).revealed()
    }
}
