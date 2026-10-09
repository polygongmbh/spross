import SwiftUI
import SprossKern

/// The sentence scramble: a phrase handed over as its own words,
/// shuffled, and put back into order by tapping. It is the one drill whose
/// answer is an ARRANGEMENT rather than something spelled — the words are given
/// and only their order is withheld (`docs/drills-words.md`).
///
/// There is no check button: committing the LAST word IS the answer, the way a
/// finished spelling is on the typed drills. Until then a word can be taken
/// back, so a slip of the finger costs a tap rather than the question.
///
/// Stateless like the letter drill: no review is ever booked, and the box is
/// READ for the phrases its join carries and never written.
///
/// The RUN is kern's (`SentenceScrambleRun`): the deal, the ladder of lengths
/// and the grading by position all live in `run`, and every event becomes a
/// `SentenceScrambleIntent`. The driver is the shared one (`DrillRunning`),
/// wired up in SentenceScrambleView+Run.swift; the bank and the answer row are
/// `ScrambleTileBank`.
struct SentenceScrambleView: View {
    let model: AppModel
    /// The language being learned — carried for the ladder's storage key alone;
    /// every phrase the run deals names its own.
    let language: String
    /// Handed the run's figures just before it closes; the page that started it
    /// shows them (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) var dismiss
    @Environment(\.accessibilityReduceMotion) var reduceMotion

    /// The whole run, kern's.
    // why: internal, not private — the +Run extension reads and drives it.
    @State var run: SentenceScrambleRunState
    // why: internal, not private — the +Run extension arms and cancels it.
    @State var autoAdvance: Task<Void, Never>?
    /// Says each graded answer kern hands over (`DrillEffect.SayAnswer`).
    @State var reader = Reader()

    init(model: AppModel, language: String,
         onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.language = language
        self.onFinish = onFinish
        let config = SentenceScrambleRunConfig(
            report: SentenceScrambleAvailability(model: model).report,
            cleared: TrainerProgress.held(for: Self.storageKey(language))
        )
        let preset = uitestOpeningSprosse("uitest-sentencescramble-level")
        _run = State(initialValue: preset > 0
            ? SentenceScrambleRun.shared.openAt(config: config, sprosse: preset, rng: drillRandom)
            : SentenceScrambleRun.shared.open(config: config, rng: drillRandom))
    }

    /// Where the ladder is filed (`SentenceScrambleRunState.storageKey`).
    static func storageKey(_ language: String) -> String {
        SentenceScrambleRunState.companion.storageKey(language: language)
    }

    var storageKey: String { Self.storageKey(language) }

    /// The question on screen; nil only once this box can ask nothing more.
    var current: SentenceScrambleTask? { run.task }

    var body: some View {
        runScreen(asking: current != nil,
                  scoreLine: DrillStreakLine(sprosse: Text("trainer.sprosse \(Int(run.sprosse).formatted())"),
                                             answerStreak: Int(run.answerStreak))) {
            drillContent
        }
        .onDisappear {
            autoAdvance?.cancel()
            reader.hush()
        }
        #if DEBUG
        .onAppear {
            uitestDriveRun()
            uitestArrange()
        }
        #endif
    }

    #if DEBUG
    /// Run-through hook: `-uitest-sentencescramble-place right|wrong` arranges
    /// the whole phrase a chip at a time, which is the only way a screenshot run
    /// reaches either verdict — the tile bank has no thumb behind it.
    private func uitestArrange() {
        guard let pick = UserDefaults.standard.string(forKey: "uitest-sentencescramble-place"),
              let task = current else { return }
        // Where in the DEAL each authored word ended up — the order that solves it.
        var order = task.canonical.compactMap { atom in
            task.shuffled.firstIndex { $0.id == atom.id }
        }
        if pick == "wrong", order.count >= 2 { order.swapAt(order.count - 1, order.count - 2) }
        Task { @MainActor in
            for slot in order {
                try? await Task.sleep(for: .milliseconds(400))
                place(slot)
            }
        }
    }
    #endif

    // MARK: - What is on screen

    private var drillContent: some View {
        questionPage(spacing: Theme.spacing.lg) { _ in
            if let task = current {
                ScrambleTileBank(bank: task.shuffled,
                                 placed: run.placedAtoms,
                                 isTaken: { run.isPlaced(index: Int32($0)) },
                                 arranged: run.arranged,
                                 verdict: run.verdict,
                                 place: { place($0) },
                                 take: { take($0) },
                                 reveal: { revealLines(task) })
            }
        } area: { controls in
            answerArea(controls)
        }
        .animation(.cardReveal, value: run.showsAnswer)
    }

    /// What the graded arrangement grows, on the answer card itself — the shared
    /// reveal, so a drill card and a vocabulary card grow the same thing.
    ///
    /// kern says which: a missed arrangement opens onto the authored order and
    /// its meaning, an accepted one grows the meaning alone, since its chips
    /// already stand in an order — beside the authored order after an
    /// alternative one (`showsAuthoredOrder`).
    ///
    /// Two answers, never an answer and a footnote. The ORDER was the question,
    /// so where it was missed the authored one wears the accent every card's
    /// answer wears; the MEANING was never on screen at all while the words were
    /// being arranged, so it reads at the same size in the page's own ink rather
    /// than as the note's fine print.
    ///
    /// Both open at the WORD reveal's size and shrink only where the phrase is
    /// long enough to need it, rather than being set small in advance against
    /// the longest one the catalog might hold: this card has the room, since the
    /// bank is gone by the time it is drawn and no prompt stands above it.
    @ViewBuilder
    private func revealLines(_ task: SentenceScrambleTask) -> some View {
        if let question = run.question {
            let order = run.showsAuthoredOrder ? question.answer.text : nil
            let meaning = question.opens || question.growsNote ? ownNote(question) : nil
            if order != nil || meaning != nil {
                CardReveal(note: nil) {
                    if let order {
                        SpokenWord(pronounce: model.pronounceAction(for: order, lang: task.language),
                                   isPlaying: model.isPronouncing(order, lang: task.language)) {
                            sentence(Text(order), tint: Theme.colors.accent)
                                .spoken(order, language: task.language)
                        }
                    }
                    if let meaning {
                        sentence(Text(meaning), tint: Theme.colors.textPrimary)
                    }
                }
                .transition(.opacity)
            }
        }
    }

    private func ownNote(_ question: Question) -> String? {
        guard let note = question.closing.note, case .own(let own) = onEnum(of: note) else { return nil }
        return own.text
    }

    private func sentence(_ text: Text, tint: Color) -> some View {
        text
            .font(Theme.typography.title)
            .foregroundStyle(tint)
            .multilineTextAlignment(.center)
            .lineLimit(4)
            .minimumScaleFactor(0.6)
    }

    /// NOTHING while the order is owed — this is the one drill that needs no
    /// Reveal. Every word it withholds is already on screen, so placing them all
    /// reaches the authored order by itself and books exactly what asking to be
    /// shown it would; a button beside the bank offered a second way to do what
    /// the bank does.
    ///
    /// Once the arrangement is graded it is what every drill wears under a
    /// graded answer: a clean one moves on by itself, a miss waits for the way
    /// on and — on the second in a row — offers the way out.
    private func answerArea(_ controls: AnswerControls) -> some View {
        AnswerArea(driver: self, controls: controls, nextLocale: model.targetChromeLocale)
    }

    // The conformance, the driver and the close are SentenceScrambleView+Run.swift's.
}
