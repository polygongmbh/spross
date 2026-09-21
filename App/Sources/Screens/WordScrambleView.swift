import SwiftUI
import SprossKern

/// The word scramble: a word the box already holds, with its letters thrown out
/// of order, written back out. The answer is TYPED — handing the same letters
/// back as tiles would leave nothing to retrieve but their order, where writing
/// the word out IS the spelling — so it wears the typed card every other trainer
/// drill wears (`docs/drills-words.md`).
///
/// Stateless like the letter drill: no review is ever booked, and the box is
/// READ for the words it has consolidated and never written.
///
/// The RUN is kern's (`WordScrambleRun`): the draw, the masking ladder and the
/// verdict ladder all live in `run`, and every event becomes a
/// `WordScrambleIntent`. The driver is the shared one (`DrillRunning`), wired
/// up in WordScrambleView+Run.swift along with the screen's content; state
/// stays here — members are internal where that extension reaches them.
struct WordScrambleView: View, LanguageNaming {
    let model: AppModel
    let language: String
    /// Handed the run's figures just before it closes; the page that started it
    /// shows them (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    /// The whole run, kern's.
    // why: internal, not private — the +Run extension reads and drives it.
    @State var run: WordScrambleRunState
    /// The learner's text; the run holds every rule that decides what it means.
    @State var input = ""
    // why: internal, not private — the +Run extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    @FocusState var answerFocused: Bool

    init(model: AppModel, language: String, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.language = language
        self.onFinish = onFinish
        let config = WordScrambleRunConfig(
            report: WordScrambleAvailability(model: model).report,
            normalizer: model.languageInfo(language)
                .map { AnswerNormalizer.companion.drill(answerLanguage: $0) },
            cleared: TrainerProgress.held(for: Self.storageKey(language))
        )
        #if DEBUG
        // UI-test hook: `-uitest-wordscramble-level N` opens the run at that
        // Sprosse, which is how a masking stage is reached deterministically.
        // Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: "uitest-wordscramble-level")
        if preset > 0 {
            _run = State(initialValue: WordScrambleRun.shared.openAt(config: config,
                                                                     level: Int32(preset),
                                                                     rng: drillRandom))
        } else {
            _run = State(initialValue: WordScrambleRun.shared.open(config: config, rng: drillRandom))
        }
        #else
        _run = State(initialValue: WordScrambleRun.shared.open(config: config, rng: drillRandom))
        #endif
    }

    /// Where the ladder is filed (`WordScrambleRunState.storageKey`).
    static func storageKey(_ language: String) -> String {
        WordScrambleRunState.companion.storageKey(language: language)
    }

    var storageKey: String { Self.storageKey(language) }

    /// The question on screen; nil only once this box can ask nothing more.
    var current: WordScrambleTask? { run.task }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        Group {
            if current != nil {
                SessionScaffold.endless(tally: run.tally,
                                        outcomes: run.outcomes.map { SessionOutcome($0) },
                                        onClose: { closeRun() }) {
                    drillContent
                }
            } else {
                // Nothing this box can ask — the hub gates on the same
                // predicate, so this is a closed door, not a screen.
                Theme.colors.background.ignoresSafeArea().onAppear { dismiss() }
            }
        }
        // why: BOTH hooks. .onChange never fires for the FIRST question, and a
        // single hook therefore ships a first card the keyboard is not up for.
        .onAppear { answerFocused = !screenReaderOn }
        .onChange(of: run.index) { _, _ in
            // why: not under a screen reader — moving the keyboard focus would
            // drag VoiceOver off the card it was just handed.
            answerFocused = !screenReaderOn
        }
        .onDisappear { autoAdvance?.cancel() }
        #if DEBUG
        .onAppear { uitestDriveRun() }
        #endif
    }

    // MARK: - The mixed word

    /// The prompt: the letters as kern mixed them, with the ones the Sprosse
    /// left standing set bold. Kern says how many hold at the front
    /// (`ScrambledWord.fixedLeading`) and this side says what that looks like —
    /// weight alone, because the anchor is a recognition aid the ladder takes
    /// away, and an aid on its way out is not worth a legend.
    func promptText(_ word: ScrambledWord) -> Text {
        let letters = Array(word.display)
        let lead = min(Int(word.fixedLeading), letters.count)
        return Text(verbatim: String(letters.prefix(lead))).bold()
            + Text(verbatim: String(letters.dropFirst(lead)))
    }

    /// A mixed word is not a word, and a voice reading it as one says nothing a
    /// learner can spell from — so it is spelled OUT, letter by letter.
    func promptLabel(_ word: ScrambledWord) -> Text {
        Text(verbatim: word.display.map(String.init).joined(separator: ", "))
    }

    // The content, the conformance and the close are WordScrambleView+Run.swift's.
}
