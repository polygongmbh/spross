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

    init(model: AppModel, language: String,
         onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.language = language
        self.onFinish = onFinish
        let config = SentenceScrambleRunConfig(
            report: SentenceScrambleAvailability(model: model).report,
            cleared: TrainerProgress.held(for: Self.storageKey(language))
        )
        #if DEBUG
        // UI-test hook: `-uitest-sentencescramble-level N` opens the run at that
        // Sprosse — the deterministic way to reach a phrase length. Kern clamps it.
        let preset = UserDefaults.standard.integer(forKey: "uitest-sentencescramble-level")
        if preset > 0 {
            _run = State(initialValue: SentenceScrambleRun.shared.openAt(config: config,
                                                                         level: Int32(preset),
                                                                         rng: drillRandom))
        } else {
            _run = State(initialValue: SentenceScrambleRun.shared.open(config: config,
                                                                       rng: drillRandom))
        }
        #else
        _run = State(initialValue: SentenceScrambleRun.shared.open(config: config, rng: drillRandom))
        #endif
    }

    /// Where the ladder is filed (`SentenceScrambleRunState.storageKey`).
    static func storageKey(_ language: String) -> String {
        SentenceScrambleRunState.companion.storageKey(language: language)
    }

    var storageKey: String { Self.storageKey(language) }

    /// The question on screen; nil only once this box can ask nothing more.
    var current: SentenceScrambleTask? { run.task }

    /// How the arrangement stands, as the bank wears it. Kern's feedback, read —
    /// this drill grades by position, so there is no near miss to render.
    private var verdict: ScrambleVerdict {
        if run.owesAnswer { return .owed }
        return run.answerAccepted ? .correct : .wrong
    }

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
        .onDisappear {
            autoAdvance?.cancel()
            Pronouncer.shared.stop()
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
        ScrollView {
            VStack(spacing: Theme.spacing.lg) {
                DrillStreakLine(level: Text("trainer.sprosse \(Int(run.level).formatted())"),
                                streak: Int(run.streak), bestStreak: Int(run.bestStreak))
                if let task = current {
                    ScrambleTileBank(bank: task.shuffled,
                                     placed: run.placedAtoms,
                                     isTaken: { run.isPlaced(index: Int32($0)) },
                                     arranged: run.arranged,
                                     verdict: verdict,
                                     place: { place($0) },
                                     take: { take($0) },
                                     reveal: { revealLines(task) })
                        .id(run.index)
                        .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.97)))
                    controls
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .animation(.easeOut(duration: 0.25), value: run.showsAnswer)
    }

    /// What the graded arrangement grows, on the answer card itself — the shared
    /// reveal, so a drill card and a vocabulary card grow the same thing.
    ///
    /// The meaning always; the authored order above it only where the
    /// arrangement missed, since the chips of a clean one already ARE that order
    /// and setting it a second time would read as a correction.
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
    /// bank is gone by the time it is drawn and no prompt stands above it. The
    /// floor lands about where the fixed sentence size did (`DrillPromptCard`).
    @ViewBuilder
    private func revealLines(_ task: SentenceScrambleTask) -> some View {
        CardReveal(note: nil) {
            if !run.answerAccepted || run.alternativeMatch {
                SpokenWord(pronounce: model.pronounceAction(for: task.display, lang: task.language),
                           isPlaying: model.isPronouncing(task.display, lang: task.language)) {
                    sentence(Text(task.display), tint: Theme.colors.accent)
                        .spoken(task.display, language: task.language)
                }
            }
            if !run.alternativeMatch {
                sentence(Text(task.gloss), tint: Theme.colors.textPrimary)
            }
        }
        .transition(.opacity)
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
    /// Once the arrangement is graded it is the shared pair every drill wears
    /// after a card opens: the way on, and — on the second miss in a row — the
    /// way out.
    @ViewBuilder
    private var controls: some View {
        if run.showsAnswer {
            DrillRevealedControls(onConfirm: { confirm() },
                                  onStop: run.offersFinish ? { closeRun() } : nil)
        }
    }

    // The conformance, the driver and the close are SentenceScrambleView+Run.swift's.
}
