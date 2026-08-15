import SwiftUI
import SprossKern

/// Tiny first-launch sheet: pick the language you already speak (source)
/// and the one you want to learn (target).
/// Coverage-driven: sources are languages with at least one learnable
/// target; targets come from `Catalog.availableTargets` (≥ 50 concepts).
/// Chrome speaks the language being picked: the device language greets first
/// (it seeds the source pick), and every later tap re-renders in that pick —
/// English for a source we have no chrome for yet.
/// Neither side hides the other's pick: choosing it swaps the selections.
///
/// One side is open at a time and the other stands folded on its pick: both lists
/// at once is more of the screen than the two questions are worth, and a pick answers
/// its own question — so choosing a source hands the screen to the target.
/// The known side opens folded, the device language being a good guess already.
///
/// Two pages: the pair, then what a round asks of you
/// (`OnboardingView+HowItWorks.swift`). Only the second one commits — the first
/// merely turns the page — so the box is joined once, behind something worth reading.
/// This view is the FIRST-RUN path alone (`RootView` binds it to `phase == .onboarding`);
/// a later language change is the box's own settings, and takes neither page.
struct OnboardingView: View {
    let model: AppModel

    enum Page { case languages, howItWorks }

    @State private var source: String
    @State private var target: String?
    @State private var pickingSource = false
    @State private var starting = false
    // why: internal, not private — the second page lives in an extension of its own.
    @State var page: Page = .languages

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
            Group {
                switch page {
                case .languages:
                    VStack(alignment: .leading, spacing: DL.Space.l) {
                        header
                        sourceSection
                        targetSection
                        nextButton
                    }
                case .howItWorks:
                    howItWorksPage
                }
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
        picker(question: "onboarding.source.question",
               open: pickingSource,
               options: sources,
               selected: source,
               onOpen: { pickingSource = true }) { candidate in
            guard let catalog = model.catalog else { return }
            apply(LanguageChoices.shared.pickSource(catalog: catalog,
                                                    selection: selection,
                                                    code: candidate))
            withAnimation(.easeInOut(duration: 0.2)) { pickingSource = false }
        }
    }

    // MARK: - Which language you want to learn

    /// Learnable targets, plus the current source where the swapped pair teaches
    /// something — the rows are `LanguageChoices.targetChoices`.
    private var targetChoices: [String] {
        guard let catalog = model.catalog else { return [] }
        return LanguageChoices.shared.targetChoices(catalog: catalog, selection: selection)
    }

    private var targetSection: some View {
        picker(question: "onboarding.target.question",
               open: !pickingSource,
               options: targetChoices,
               selected: target,
               onOpen: { pickingSource = false }) { candidate in
            apply(LanguageChoices.shared.pickTarget(selection: selection, code: candidate))
        }
    }

    // MARK: - One side of the pair

    /// The question, and either the languages to choose from or the chosen one as
    /// the row that opens them again.
    private func picker(question: LocalizedStringKey,
                        open: Bool,
                        options: [String],
                        selected: String?,
                        onOpen: @escaping () -> Void,
                        onPick: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text(question)
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            if open {
                ForEach(options, id: \.self) { candidate in
                    DLSelectionRow(title: Text(verbatim: languageName(candidate)),
                                   mark: .one,
                                   selected: selected == candidate) { onPick(candidate) }
                }
            } else if let selected {
                DLSelectionRow(title: Text(verbatim: languageName(selected)),
                               mark: .fold,
                               selected: false) {
                    withAnimation(.easeInOut(duration: 0.2)) { onOpen() }
                }
            }
        }
    }

    /// The pair's own button: it commits nothing, it turns the page.
    private var nextButton: some View {
        Button {
            guard target != nil else { return }
            withAnimation(.easeInOut(duration: 0.2)) { page = .howItWorks }
        } label: {
            Text("common.next")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(target == nil)
        .padding(.top, DL.Space.l)
    }

    /// The one button that commits: it joins the box AND opens the round it was
    /// picked for, so the spinner covers the join rather than a blank screen.
    // why: internal, not private — the page it sits on is an extension of its own.
    @ViewBuilder
    var startButton: some View {
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
        .padding(.top, DL.Space.l)
    }
}

#Preview {
    OnboardingView(model: AppModel())
}
