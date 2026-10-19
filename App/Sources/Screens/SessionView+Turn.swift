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
        case .cued(let cued):
            // why: the beat renders "Weiter" under a screen reader instead of
            // running — same rating, through ConfirmPending.
            carryOut(cued.cue) { dispatch(TurnIntent.AdvanceElapsed.shared) }
        case .primeField(let primed):
            // why: the answer field is the one the turn owns here — a miss
            // never hides it, so there is nothing to re-focus.
            input = primed.text
        }
    }

    // why: a pause that waits for a tap must not hold the keyboard —
    // it covers the button the pause is waiting for. The pending
    // retry is canceled first, or it re-focuses 120 ms later.
    func releaseFocus() {
        focusRetry?.cancel()
        answerFocused = false
    }

    /// Hand the answer to the engine and flip to the next card.
    private func commit(_ rating: Rating) {
        autoAdvance?.cancel()
        // why: the next turn begins only once the card has changed (`dealt`), so the
        // outgoing card flips away still showing its own answer.
        withAnimation(reduceMotion ? .cardFlipReduced : .cardFlip) {
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
              // it under that language's rules (`docs/review.md`).
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
                             promptForm: model.promptForm(for: card, role: role, prompt: prompt),
                             firstExposure: model.isFirstExposure(card.id),
                             arrived: model.hasArrived(card.id),
                             nowEpochMillis: Date().epochMillis,
                             answer: model.produceAnswer(for: card.id))
    }

    func resetCardState() {
        autoAdvance?.cancel()
        // why: the word in the air belongs to the card that is leaving — the
        // one place playback is stopped, together with .onDisappear.
        reader.hush()
        reader.forget()
        input = ""
        copyInput = ""
        beginTurn()
    }

    // MARK: - What the screen reads off the turn

    var question: Question? { turn?.question }

    var controls: AnswerControls? { turn?.controls }

    /// What the card says aloud — its prompt as it goes up, its answer once it has settled.
    var reading: Reading? { turn?.reading(saysMeaning: Pronouncer.shared.saysMeaning) }

    var voiceModel: AppModel? { model }

    var feedback: TurnFeedback { turn?.feedback ?? TurnFeedbackNeutral.shared }

    var revealed: Bool { turn?.revealed ?? false }

    var retryApproved: Bool { turn?.retryApproved ?? false }

    /// What a report opened right now carries as the learner's answer — kern's call
    /// (`TurnState.answerForReport`): the word the catalog refused, which a miss has
    /// already primed out of the field, else whatever stands in it.
    var answerForReport: String { turn?.answerForReport(fieldText: input) ?? input }

    /// The word the card owes back is on screen and the turn waits on the learner —
    /// kern's own fact (`TurnState.answerOut`). What the card's menu hangs on: a typo
    /// holds on its correction without ever expanding the card, and that pause is
    /// exactly when a learner has something to say about the word.
    var answerOut: Bool { turn?.answerOut ?? false }

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
}
