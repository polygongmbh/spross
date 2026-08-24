import SwiftUI
import SprossKern

/// Tiny first-launch sheet: pick the language you already speak (source)
/// and the one you want to learn (target), and — if you like — say what to be called.
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
/// Three pages: the pair, what the box is for, then what a round asks of you
/// (`OnboardingView+Story.swift`). Only the last one commits — the two before it
/// merely turn the page — so the box is joined once, behind something worth reading,
/// and the pair stays reachable through the way back the story pages carry.
/// This view opens on `phase == .onboarding` (`RootView`), either the first run
/// or a deliberate restart (Box settings' "restart tutorial" row,
/// `AppModel.restartOnboarding`); a later language change alone is the box's
/// own settings, and takes none of the pages.
struct OnboardingView: View {
    let model: AppModel

    enum Page { case languages, why, firstRound }

    @State private var source: String
    @State private var target: String?
    @State private var pickingSource = false
    /// Worth nothing until the last page commits it, like the pair beside it.
    @State private var name = ""
    // why: internal, not private — the story pages live in an extension of their own.
    @State var starting = false
    @State var page: Page = .languages

    /// The head of the shared scroll, so a page turn opens on the new page's first line.
    private let topAnchor = "onboarding.top"

    /// `skipLanguagePick` starts past the pair's own page — a restart of an
    /// already-made pair (`RootView`) has nothing left for it to ask there.
    init(model: AppModel, skipLanguagePick: Bool = false) {
        self.model = model
        if skipLanguagePick, let target = model.targetLanguage {
            _source = State(initialValue: model.sourceLanguage)
            _target = State(initialValue: target)
            _page = State(initialValue: .why)
        } else {
            let source = model.defaultSource
            _source = State(initialValue: source)
            _target = State(initialValue: model.catalog?
                .availableTargets(source: source).first?.code)
        }
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
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Color.clear.frame(height: 0).id(topAnchor)
                    Group {
                        switch page {
                        case .languages: languagesPage
                        case .why: whyPage
                        case .firstRound: firstRoundPage
                        }
                    }
                }
                .padding(DL.Space.l)
            }
            .scrollBounceBehavior(.basedOnSize)
            // why: a page turn from a scrolled picker to a shorter page — the offset
            // it kept would open that page mid-sentence at large Dynamic Type.
            .onChange(of: page) { _, _ in proxy.scrollTo(topAnchor, anchor: .top) }
        }
        .background(Color.dlBackground.ignoresSafeArea())
        // why: the OUTERMOST view, above the switch — inside a branch, the story pages
        // would fall back to the persisted profile's locale, empty on a first run.
        .environment(\.locale, AppModel.chromeLocale(source: source))
    }

    // why: internal, not private — the story pages turn to their neighbors themselves.
    func turn(to next: Page) {
        withAnimation(.easeInOut(duration: 0.2)) { page = next }
    }

    // MARK: - The pair

    /// The picker keeps its own rhythm: a leading-aligned form under a centered hero,
    /// which is why it takes the hero alone and not the story pages' scaffold.
    private var languagesPage: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            OnboardingHero(emoji: "👋",
                           title: "onboarding.welcome",
                           subtitle: "onboarding.languages.subtitle")
            sourceSection
            targetSection
            nameSection
            nextButton
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

    // MARK: - What to call you

    /// The one question this page does not need answered: it gates nothing.
    /// The greeting has a wording for a learner it cannot name.
    ///
    /// It opens empty. `UIDevice.current.name` returns the MODEL ("iPhone") on iOS 16 and
    /// later for any app without the user-assigned-device-name entitlement, which is a
    /// granted one and not among this app's — a model name is nobody, so there is nothing
    /// here to prefill from. Android fills the same field in from its device name
    /// (`DeviceName.kt`), where that name is someone's.
    private var nameSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("onboarding.name.question")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            DLNameField(placeholder: "settings.name.placeholder", text: $name)
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
            turn(to: .why)
        } label: {
            Text("common.next")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(target == nil)
        .padding(.top, DL.Space.l)
    }

    /// The one action that commits: it joins the box AND opens the round it was
    /// picked for, so the spinner covers the join rather than a blank screen.
    // why: internal, not private — the page it sits on is an extension of its own.
    func start() {
        guard !starting, let target else { return }
        starting = true
        // A blank field is no name at all, which is what it looked like.
        model.setLearnerName(name)
        Task {
            await model.completeOnboarding(source: source, target: target)
        }
    }
}

#Preview {
    OnboardingView(model: AppModel())
}
