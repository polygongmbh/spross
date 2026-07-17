import SwiftUI
import DuoKern

/// Tiny first-launch sheet: pick which two languages, and which one you
/// already speak (that decides the drill/review direction).
struct OnboardingView: View {
    let model: AppModel

    @State private var pair: LanguagePair = .deSw
    /// Whether the learner already speaks the pair's base language (then they
    /// learn the target); otherwise the base language is the one being learned.
    @State private var knowsBase = true
    @State private var starting = false

    /// Which side you already speak sets the review/drill direction.
    private var direction: Direction { knowsBase ? .deToTarget : .targetToDe }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                header
                pairSection
                knownSection
                startButton
            }
            .padding(DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("👋")
                .font(.system(size: 44))
            Text("Willkommen bei DuoLernen")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("Deine Box wächst mit dir — jeden Tag ein paar neue Karten.")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    // MARK: - Which two languages

    private var pairSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Welches Sprachpaar?")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            HStack(spacing: DL.Space.m) {
                ForEach(LanguagePair.allCases, id: \.self) { candidate in
                    pairTile(candidate)
                }
            }
        }
    }

    private func pairTile(_ candidate: LanguagePair) -> some View {
        let selected = pair == candidate
        return Button {
            pair = candidate
        } label: {
            HStack(spacing: DL.Space.s) {
                Text(candidate.flag)
                    .font(.system(size: 28))
                Text("\(candidate.baseName) · \(candidate.targetName)")
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, DL.Space.m)
            .padding(.horizontal, DL.Space.s)
            .background(selectionBackground(selected))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: - Which language you already speak

    private var knownSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Welche Sprache kannst du schon?")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            knownRow(base: true,
                     title: pair.baseName,
                     subtitle: "Du lernst \(pair.targetName).")
            knownRow(base: false,
                     title: pair.targetName,
                     subtitle: "Du lernst \(pair.baseName).")
        }
    }

    private func knownRow(base: Bool, title: String, subtitle: String) -> some View {
        let selected = knowsBase == base
        return Button {
            knowsBase = base
        } label: {
            HStack(spacing: DL.Space.m) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Color.dlAccent : Color.dlTextSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Text(subtitle)
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                Spacer(minLength: 0)
            }
            .padding(DL.Space.m)
            .background(selectionBackground(selected))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func selectionBackground(_ selected: Bool) -> some View {
        RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
            .fill(selected ? Color.dlSurfaceTint : Color.dlSurface)
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .strokeBorder(selected ? Color.dlAccent : Color.dlSeparator,
                                  lineWidth: selected ? 2 : 1)
            )
    }

    private var startButton: some View {
        Button {
            guard !starting else { return }
            starting = true
            Task {
                await model.completeOnboarding(pair: pair, direction: direction)
            }
        } label: {
            Group {
                if starting {
                    ProgressView().tint(.white)
                } else {
                    Text("Los geht's!")
                }
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .padding(.top, DL.Space.l)
    }
}

#Preview {
    OnboardingView(model: AppModel())
}
