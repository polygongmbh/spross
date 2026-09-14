import SwiftUI
import SprossKern

/// The sentence scramble: an unlocked phrase handed over as its own words,
/// shuffled, and put back into order by tapping. It is the one drill whose
/// answer is an ARRANGEMENT rather than something spelled — the words are given
/// and only their order is withheld (`docs/drills-words.md`).
///
/// There is no check button: committing the LAST word IS the answer, the way a
/// finished spelling is on the typed drills. Until then a word can be taken
/// back, so a slip of the finger costs a tap rather than the question.
///
/// Stateless like the letter drill: no review is ever booked, and the box is
/// READ for the phrases it has unlocked and never written.
///
/// The RUN is kern's (`SentenceScrambleRun`): the deal, the ladder of lengths
/// and the grading by position all live in `run`, and every event becomes a
/// `SentenceScrambleIntent`. The bank and the answer row are `ScrambleTileBank`.
struct SentenceScrambleView: View {
    let model: AppModel
    /// Handed the run's figures just before it closes; the page that started it
    /// shows them (see `DrillResultTile`).
    var onFinish: (DrillRunResult) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The whole run, kern's.
    @State private var run: SentenceScrambleRunState
    @State private var autoAdvance: Task<Void, Never>?

    init(model: AppModel, onFinish: @escaping (DrillRunResult) -> Void = { _ in }) {
        self.model = model
        self.onFinish = onFinish
        let config = SentenceScrambleRunConfig(
            report: SentenceScrambleAvailability(model: model).report,
            cleared: []
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

    /// The question on screen; nil only once this box can ask nothing more.
    private var current: SentenceScrambleTask? { run.task }

    /// VoiceOver and Switch Control both make a timed screen change hostile, so
    /// an explicit "Weiter" replaces the beat where either runs.
    private var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    /// How the arrangement stands, as the bank wears it. Kern's feedback, read —
    /// this drill grades by position, so there is no near miss to render.
    private var verdict: ScrambleTileBank.Verdict {
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
        .onAppear { uitestArrange() }
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
                dispatch(SentenceScrambleIntent.PlaceAtom(index: Int32(slot)))
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
                                     place: { dispatch(SentenceScrambleIntent.PlaceAtom(index: Int32($0))) },
                                     take: { dispatch(SentenceScrambleIntent.ReturnAtom(index: Int32($0))) })
                        .id(run.index)
                        .transition(reduceMotion ? .opacity : .opacity.combined(with: .scale(scale: 0.97)))
                    if run.showsAnswer {
                        answerCard(task)
                    }
                    controls
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .animation(.easeOut(duration: 0.25), value: run.showsAnswer)
    }

    /// The order the catalog authors, once the arrangement has failed to find
    /// it — the shared reveal, so a drill card and a vocabulary card grow the
    /// same thing. The meaning rides under it and never before it.
    private func answerCard(_ task: SentenceScrambleTask) -> some View {
        CardReveal(note: task.gloss) {
            SpokenWord(pronounce: model.pronounceAction(for: task.display, lang: task.language),
                       isPlaying: model.isPronouncing(task.display, lang: task.language)) {
                Text(task.display)
                    .font(Theme.typography.headline)
                    .foregroundStyle(Theme.colors.accent)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.6)
                    .spoken(task.display, language: task.language)
            }
        }
        .padding(Theme.spacing.lg)
        .frame(maxWidth: .infinity)
        .cardSurface()
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    @ViewBuilder
    private var controls: some View {
        if run.owesAnswer {
            // ONE primary action while the order is owed. There is no Check:
            // the last word placed grades itself, so this can only be the ask
            // to be shown the order instead.
            Button {
                dispatch(SentenceScrambleIntent.Reveal.shared)
            } label: {
                Text("common.reveal").frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
        } else if run.showsAnswer {
            VStack(spacing: Theme.spacing.sm) {
                nextButton
                if run.offersFinish { DrillStopOffer { closeRun() } }
            }
        } else if screenReaderOn {
            // why: the timer never arms under a screen reader, so a clean
            // arrangement would otherwise have nothing to move on with.
            nextButton
        }
    }

    /// The one button that books whatever the feedback already said — which of
    /// the ladder's outcomes that is stays kern's.
    private var nextButton: some View {
        Button {
            dispatch(SentenceScrambleIntent.ConfirmPending.shared)
        } label: {
            Text("common.next").frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
    }

    // MARK: - Driving the run

    private func dispatch(_ intent: SentenceScrambleIntent) {
        let reduction = SentenceScrambleRun.shared.reduce(state: run, intent: intent,
                                                          rng: drillRandom)
        let moved = reduction.state.index != run.index
        let animation: Animation = moved
            ? (reduceMotion ? .easeOut(duration: 0.2) : .cardFlip)
            : .easeOut(duration: 0.2)
        withAnimation(animation) { run = reduction.state }
        for effect in reduction.effects { apply(effect) }
        // Nothing left to ask: hand the run back, never sit on a blank bank.
        if reduction.state.finished { closeRun() }
    }

    private func apply(_ effect: DrillEffect) {
        DrillEffects.apply(effect, advance: &autoAdvance,
                           onAdvance: { dispatch(SentenceScrambleIntent.AdvanceElapsed.shared) },
                           releaseFocus: {},
                           silence: { Pronouncer.shared.stop() })
    }

    // MARK: - Close → back to the hub that opened it

    /// X during a run: kern books a pending arrangement exactly as the tap
    /// would, then hands the figures back. An untouched run leaves nothing to
    /// report, and no record line — this drill keeps no record store.
    private func closeRun() {
        let closed = SentenceScrambleRun.shared.close(state: run)
        run = closed.state
        for effect in closed.effects { apply(effect) }
        guard let summary = closed.summary else {
            dismiss()
            return
        }
        onFinish(DrillRunResult(summary, title: "trainer.skill.sentenceScramble"))
        dismiss()
    }
}
