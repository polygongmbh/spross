import SwiftUI
import SprossKern

/// Tiny first-launch sheet: pick the language you already speak (source)
/// and the one you want to learn (target, with its concept count).
/// Coverage-driven: sources are languages with at least one learnable
/// target; targets come from `Catalog.availableTargets` (≥ 50 concepts).
/// Chrome speaks the language being picked: the device language greets first
/// (it seeds the source pick), and every later tap re-renders in that pick —
/// English for a source we have no chrome for yet.
/// Neither side hides the other's pick: choosing it swaps the selections.
struct OnboardingView: View {
    let model: AppModel

    @State private var source: String
    @State private var target: String?
    @State private var starting = false

    init(model: AppModel) {
        self.model = model
        let source = model.defaultSource
        _source = State(initialValue: source)
        _target = State(initialValue: model.catalog?
            .availableTargets(source: source).first?.code)
    }

    private var sources: [String] {
        model.catalog?.coveredSources() ?? []
    }

    /// The pair as the two lists see it, before anything is persisted.
    private var selection: LanguageChoices.Selection {
        LanguageChoices.Selection(source: source, target: target)
    }

    private func apply(_ next: LanguageChoices.Selection) {
        source = next.source
        target = next.target
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                header
                sourceSection
                targetSection
                startButton
            }
            .padding(DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
        .environment(\.locale, AppModel.chromeLocale(source: source))
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(verbatim: "👋")
                .font(.system(size: 44))
            Text("onboarding.welcome")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("onboarding.subtitle")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private func languageName(_ code: String) -> String {
        LanguageNames.pickerRow(code, catalog: model.catalog)
    }

    // MARK: - Which language you already speak

    private var sourceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("onboarding.source.question")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            ForEach(sources, id: \.self) { candidate in
                DLSelectionRow(title: Text(verbatim: languageName(candidate)),
                               mark: .one,
                               selected: source == candidate) {
                    guard let catalog = model.catalog else { return }
                    apply(LanguageChoices.shared.pickSource(catalog: catalog,
                                                            selection: selection,
                                                            code: candidate))
                }
            }
        }
    }

    // MARK: - Which language you want to learn

    /// Learnable targets, plus the current source where the swapped pair teaches
    /// something — the rows and their counts are `LanguageChoices.targetChoices`.
    private var targetChoices: [LanguageChoices.TargetChoice] {
        guard let catalog = model.catalog else { return [] }
        return LanguageChoices.shared.targetChoices(catalog: catalog, selection: selection)
    }

    private var targetSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("onboarding.target.question")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            ForEach(targetChoices, id: \.code) { candidate in
                DLSelectionRow(title: Text(verbatim: languageName(candidate.code)),
                               caption: Text("onboarding.termsCount \(Int(candidate.conceptCount).formatted())"),
                               mark: .one,
                               selected: target == candidate.code) {
                    apply(LanguageChoices.shared.pickTarget(selection: selection,
                                                            code: candidate.code))
                }
            }
        }
    }

    private var startButton: some View {
        Button {
            guard !starting, let target else { return }
            starting = true
            Task {
                await model.completeOnboarding(source: source, target: target)
            }
        } label: {
            Group {
                if starting {
                    ProgressView().tint(.white)
                } else {
                    Text("onboarding.start")
                }
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(target == nil)
        .padding(.top, DL.Space.l)
    }
}

#Preview {
    OnboardingView(model: AppModel())
}
