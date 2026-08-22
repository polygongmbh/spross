import SwiftUI
import SprossKern

/// The TURN half of SessionView: how an event reaches kern, what comes back,
/// and what the screen reads off the result. Split out purely for file size —
/// the rules themselves are `TurnMachine`'s, and nothing here decides any of
/// them: every event becomes a `TurnIntent`, kern's next state replaces the
/// turn whole, and its effects are the only things allowed to reach outside it.
extension SessionView {

    // MARK: - Driving the turn

    /// One event against the turn: kern's next state replaces it whole, and its
    /// effects are the only things that reach outside it. `nowMillis` is for
    /// the hooks' sake — live callers pass none.
    func dispatch(_ intent: TurnIntent, at nowMillis: Int64? = nil) {
        guard let current = ensureTurn(), let machine else { return }
        let reduction = machine.reduce(state: current, intent: intent,
                                       nowEpochMillis: nowMillis ?? Date().epochMillis)
        // why: the write-out mounts a field of its own — kern names the step,
        // the text in it is ours, so it opens empty.
        if current.copyStep == nil, reduction.state.copyStep != nil { copyInput = "" }
        withAnimation { turn = reduction.state }
        for effect in reduction.effects { apply(effect) }
    }

    private func apply(_ effect: TurnEffect) {
        switch onEnum(of: effect) {
        case .answer(let done):
            commit(done.rating)
        case .armAdvance(let beat):
            // why: AutoAdvance skips the timer under a screen reader (it
            // truncates the announcement and moves the screen), and the branch
            // renders "Weiter" there — same rating, through ConfirmPending.
            AutoAdvance.schedule(beat.tier, &autoAdvance) {
                dispatch(TurnIntent.AdvanceElapsed.shared)
            }
        case .cancelAdvance:
            autoAdvance?.cancel()
        case .primeField(let primed):
            // why: the answer field is the one the turn owns here — a miss
            // never hides it, so there is nothing to re-focus.
            input = primed.text
        case .tone(let cue):
            switch cue.kind {
            case .correct: DLSound.correct()
            case .wrong: DLSound.wrong()
            case .reveal: DLSound.reveal()
            }
        case .releaseFocus:
            // why: a pause that waits for a tap must not hold the keyboard —
            // it covers the button the pause is waiting for. The pending
            // retry is canceled first, or it re-focuses 120 ms later.
            focusRetry?.cancel()
            answerFocused = false
        }
    }

    /// Hand the answer to the engine and flip to the next card.
    private func commit(_ rating: Rating) {
        autoAdvance?.cancel()
        // why: the next turn begins BEFORE the card switch, in the same
        // transaction — the incoming card must never render one frame
        // carrying the outgoing one's reveal.
        resetCardState()
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
            model.answerCurrent(rating)
        }
    }

    // MARK: - Beginning and ending a turn

    @discardableResult
    func ensureTurn() -> TurnState? {
        if let turn { return turn }
        beginTurn()
        return turn
    }

    /// A card goes on screen: a fresh turn, and the machine that grades it.
    /// The recall clock starts with it.
    private func beginTurn() {
        guard let card = model.currentCard,
              let normalizer = model.answerNormalizer,
              // A card asked by ear is typed in the SOURCE language, so kern grades
              // it under that language's rules (`kern/docs/presentation.md`).
              let meanings = model.meaningNormalizer,
              let grader = model.produceGrader else {
            machine = nil
            turn = nil
            return
        }
        let machine = TurnMachine(grader: grader, normalizer: normalizer,
                                  meaningNormalizer: meanings)
        let role = model.presentationRole(for: card.id)
        let prompt = model.producePrompt(for: card)
        self.machine = machine
        turn = machine.begin(card: card,
                             role: role,
                             prompt: prompt,
                             // The form the prompt stands on: the rotated one on
                             // recognition, else the source word — or, where the
                             // question is the sound, the very form that plays.
                             promptForm: role == .recognize ? model.promptForm(for: card)
                                 : (prompt == .sound ? card.target.text : card.source.text),
                             firstExposure: model.isFirstExposure(card.id),
                             consolidated: model.isConsolidated(card.id),
                             nowEpochMillis: Date().epochMillis)
    }

    func resetCardState() {
        autoAdvance?.cancel()
        // why: the word in the air belongs to the card that is leaving — the
        // one place playback is stopped, together with .onDisappear.
        Pronouncer.shared.stop()
        pronouncedCardID = nil
        input = ""
        copyInput = ""
        beginTurn()
    }

    // MARK: - What the screen reads off the turn

    var feedback: AnswerInputView.Feedback {
        turn.map { AnswerInputView.Feedback($0.feedback) } ?? .neutral
    }

    var revealed: Bool { turn?.revealed ?? false }

    var retryApproved: Bool { turn?.retryApproved ?? false }

    var otherWord: MatchOtherWord? { turn?.otherWord }

    /// The card expands only when the word was NOT produced — "Aufdecken" or a
    /// wrong answer. A correct answer already stands in the input field, and a
    /// typo's proper spelling is carried by the correction box, so revealing
    /// there would put the same word on screen twice.
    var cardRevealed: Bool { turn?.answerRevealed ?? false }

    /// The form an amber hold owes back, and why it does: a slip's proper
    /// spelling, or the word that played where a form this card also accepts
    /// was written.
    var almostHold: (form: String, reason: SprossKern.AlmostReason)? {
        guard let feedback = turn?.feedback,
              case .almost(let hold) = onEnum(of: feedback) else { return nil }
        return (hold.correctForm, hold.reason)
    }

    /// That form where a SLIP owed it. The correction box is the only place a
    /// typo's proper spelling stands, which is why the reveal speaks it.
    ///
    /// Never on a card asked by ear: the form it owes back is then a SOURCE word,
    /// and the target voice would read a German word in Swahili.
    var typoCorrection: String? {
        guard !answerIsMeaning else { return nil }
        return almostHold.flatMap { $0.reason == .typo ? $0.form : nil }
    }

    /// Whether this turn is typed in the language the learner already HAS — kern's
    /// answer side, read rather than re-derived. The field's placeholder, the
    /// correction's speaker and the reveal's word all turn on it.
    var answerIsMeaning: Bool {
        guard let turn else { return false }
        return turn.answerLang != turn.card.target.lang
    }

    /// The card was asked by ear and the learner could not listen: the word
    /// stands on it as text for the rest of the turn.
    var promptInText: Bool { turn?.promptInText ?? false }

    /// Whether the card on screen is asked by ear — the TURN's own fact, never a
    /// re-reading of the device: audibility can change under a card (a volume
    /// key, headphones out), and a face that flipped mid-turn would show the
    /// source word while kern still grades the meaning. The model answers only
    /// before the turn exists, which is the one moment it cannot have changed.
    func askedByEar(_ card: Card) -> Bool {
        (turn?.prompt ?? model.producePrompt(for: card)) == .sound
    }
}
