import SwiftUI
import DuoKern

/// Tiny first-launch sheet: pick the language pair and direction.
struct OnboardingView: View {
    let model: AppModel

    @State private var pair: LanguagePair = .deSw
    @State private var direction: Direction = .deToTarget
    @State private var starting = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                pairSection
                directionSection
                startButton
            }
            .padding(DL.Space.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("👋")
                .font(.system(size: 56))
            Text("Willkommen bei DuoLernen")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("Deine Box wächst mit dir: jeden Tag ein paar neue Karten, und Sätze schalten sich frei, sobald ihre Wörter sitzen.")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var pairSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("Welche Sprache lernst du?")
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
            VStack(spacing: DL.Space.s) {
                Text(candidate.flag)
                    .font(.system(size: 40))
                Text(candidate.targetName)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            .frame(maxWidth: .infinity)
            .padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(selected ? Color.dlSurfaceTint : Color.dlSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .strokeBorder(selected ? Color.dlAccent : Color.dlSeparator,
                                  lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var directionSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("Wie herum?")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            directionRow(.deToTarget,
                         title: "Deutsch → \(pair.targetName)",
                         subtitle: "Du siehst das deutsche Wort und bewertest dich selbst.")
            directionRow(.targetToDe,
                         title: "\(pair.targetName) → Deutsch",
                         subtitle: "Du tippst das deutsche Wort — die Königsdisziplin.")
        }
    }

    private func directionRow(_ candidate: Direction, title: String, subtitle: String) -> some View {
        let selected = direction == candidate
        return Button {
            direction = candidate
        } label: {
            HStack(spacing: DL.Space.m) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Color.dlAccent : Color.dlTextSecondary)
                VStack(alignment: .leading, spacing: DL.Space.xs) {
                    Text(title)
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Text(subtitle)
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                Spacer(minLength: 0)
            }
            .padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(selected ? Color.dlSurfaceTint : Color.dlSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .strokeBorder(selected ? Color.dlAccent : Color.dlSeparator,
                                  lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
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
