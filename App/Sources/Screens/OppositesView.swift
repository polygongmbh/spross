import SwiftUI
import SprossKern

/// The opposites drill: a word the box holds, and its opposite typed back in
/// the same language (`docs/drills-words.md`). It wears the word scramble's
/// typed card; the reveal names EVERY opposite, since a word the language
/// writes alike for two meanings has one for each.
///
/// The RUN is kern's (`OppositesRun`); the driver is the shared one
/// (`DrillRunning`), wired up in OppositesView+Run.swift along with the
/// screen's content. State stays here.
struct OppositesView: View, LanguageNaming {
    let model: AppModel
    let language: String
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.locale) var locale
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    // why: internal, not private — the +Run extension reads and drives it.
    @State var run: OppositesRunState
    @State var input = ""
    // why: internal, not private — the +Run extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    @State var answerVoice = AnswerVoice()
    @FocusState var answerFocused: Bool

    init(model: AppModel, language: String, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.language = language
        self.onFinish = onFinish
        let config = OppositesRunConfig(
            report: OppositesAvailability(model: model).report,
            normalizer: model.languageInfo(language)
                .map { AnswerNormalizer.companion.drill(answerLanguage: $0) },
            cleared: TrainerProgress.held(for: Self.storageKey(language))
        )
        _run = State(initialValue: OppositesRun.shared.open(config: config, rng: drillRandom))
    }

    /// Where the ladder is filed (`OppositesRunState.storageKey`).
    static func storageKey(_ language: String) -> String {
        OppositesRunState.companion.storageKey(language: language)
    }

    var storageKey: String { Self.storageKey(language) }

    var current: OppositesTask? { run.task }

    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        runScreen(asking: current != nil,
                  scoreLine: DrillStreakLine(sprosse: Text("trainer.sprosse \(Int(run.sprosse).formatted())"),
                                             answerStreak: Int(run.answerStreak))) {
            drillContent
        }
        // why: BOTH hooks. .onChange never fires for the FIRST question, and a
        // single hook therefore ships a first card the keyboard is not up for.
        .onAppear { answerFocused = !screenReaderOn }
        .onChange(of: shownQuestion) { _, shown in
            guard shown != nil else { return }
            // why: not under a screen reader — moving the keyboard focus would
            // drag VoiceOver off the card it was just handed.
            answerFocused = !screenReaderOn
        }
        .onDisappear {
            autoAdvance?.cancel()
            answerVoice.hush()
        }
        #if DEBUG
        .onAppear { uitestDriveRun() }
        #endif
    }

    /// Every opposite on one line — two where the prompt merges two meanings.
    func answerLine(_ task: OppositesTask) -> String {
        task.answers.map(\.text).joined(separator: " · ")
    }

    /// What the prompt means, then what its opposites mean, in the learner's own language.
    func glossLine(_ task: OppositesTask) -> String {
        "\(task.gloss) ↔ \(task.answers.map(\.gloss).joined(separator: " · "))"
    }
}
