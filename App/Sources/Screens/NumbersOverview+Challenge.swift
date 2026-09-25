import SwiftUI
import SprossKern

/// The numbers overview's challenge section: start one and share its code, or enter a code.
/// Code rules are kern's (`NumbersChallenge`); state lives on NumbersOverview.
extension NumbersOverview {

    var challengeSection: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            DrillHeading("trainer.challenge.title")
            Text("trainer.challenge.hint \(Int(TimedRun.shared.SECONDS))")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                startChallenge()
            } label: {
                Text("trainer.challenge.start")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(SoftButtonStyle())
            .disabled(!challengeOffered)
            if !challengeOffered {
                caption(Text("trainer.challenge.phrases"))
            }
            codeField
            if let challengeRefusal {
                caption(challengeRefusal)
            }
        }
    }

    /// Whether the picks hold anything besides Phrases.
    private var challengeOffered: Bool {
        NumbersChallenge.companion.offered(mode: buildMode())
    }

    private var codeField: some View {
        HStack(spacing: Theme.spacing.md) {
            TextField("trainer.challenge.code", text: $challengeCode)
                .font(Theme.typography.body.monospaced())
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .onSubmit(acceptChallenge)
                .padding(Theme.spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .fill(Theme.colors.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.radius.control, style: .continuous)
                        .strokeBorder(Theme.colors.separator, lineWidth: 1)
                )
            Button("trainer.challenge.accept", action: acceptChallenge)
                .buttonStyle(SoftButtonStyle())
                .fixedSize()
                .disabled(challengeCode.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func caption(_ text: Text) -> some View {
        text
            .font(Theme.typography.caption)
            .foregroundStyle(Theme.colors.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Starting one

    private func startChallenge() {
        guard let challenge = NumbersChallenge.companion.create(mode: buildMode(), rng: drillRandom)
        else { return }
        launch = DrillLaunch(value: NumbersLaunch(mode: challenge.mode, challenge: challenge))
    }

    private func acceptChallenge() {
        switch onEnum(of: NumbersChallenge.companion.read(text: challengeCode, language: language)) {
        case .ready(let ready):
            challengeRefusal = nil
            launch = DrillLaunch(value: NumbersLaunch(mode: ready.challenge.mode,
                                                      challenge: ready.challenge))
        case .otherLanguage(let other):
            let name = LanguageNames.display(other.language, catalog: model.catalog)
            challengeRefusal = Text("trainer.challenge.otherLanguage \(name)")
        case .unreadable:
            challengeRefusal = Text("trainer.challenge.unreadable")
        }
    }
}
