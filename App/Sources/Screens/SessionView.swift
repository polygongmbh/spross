import SwiftUI
import SprossKern

/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to self-grading);
/// RECOGNIZE prompts one rotated target form and is reveal + self-grade
/// only — never typed, bar the first exposure's write-it-out
/// (SessionView+Copy.swift). Presented as a full-screen cover.
///
/// What an answer is WORTH, which beat it earns and what a miss opens is kern's
/// `TurnMachine`: every event becomes a `TurnIntent`, and what comes back is the
/// whole next state plus the only side effects this screen takes.
struct SessionView: View, LanguageNaming {
    @Bindable var model: AppModel

    /// The turn under way, whole: where the answer stands, what the card
    /// shows, and which write-out it still owes. Nil only with no card up.
    // why: internal, not private — SessionView+Produce/Copy/Audio (file-size
    // splits) render and drive this same turn from their extensions.
    @State var turn: TurnState?
    /// Rebuilt with each turn: the grader snapshots the join, so a card that
    /// arrives after the box moved is graded against the box standing now.
    @State private var machine: TurnMachine?
    /// The learner's TEXT stays platform-owned — kern is handed it in intents
    /// and may prime it, but never holds it. Only ever one field is mounted,
    /// so the answer and the write-out keep their own.
    @State var input = ""
    @State var copyInput = ""
    @State var autoAdvance: Task<Void, Never>?
    /// The card whose word has already been said. The one-shot autoplay guard
    /// (SessionView+Audio.swift) — stored here because a SwiftUI extension
    /// cannot carry state of its own.
    @State var pronouncedCardID: String?
    /// Owned here (not in AnswerInputView) so whichever field is on screen —
    /// the answer field or the write-out step's — takes focus the moment it
    /// mounts. Only ever one of them is mounted at a time.
    @FocusState var answerFocused: Bool
    @State var focusRetry: Task<Void, Never>?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.locale) var locale
    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        Group {
            if model.sessionCompleted {
                SessionCompletionView(newCount: model.sessionNew,
                                      graduatedCount: model.sessionGraduated,
                                      reviewCount: model.sessionReviews,
                                      streakDays: model.stats?.streakDays ?? 0,
                                      streakIsRecord: model.streakIsRecord,
                                      grownArea: model.sessionGrowth,
                                      canPracticeMore: model.canPracticeMore,
                                      restSuggested: model.today?.recallStrained ?? false,
                                      onPractice: { model.continueEndless() },
                                      onDone: { model.closeSession() })
            } else {
                SessionScaffold(position: model.sessionPosition,
                                total: max(model.sessionTotal, 1),
                                outcomes: model.sessionSegments,
                                showsMuteButton: true,
                                onClose: { model.closeSession() }) {
                    scaffoldContent
                }
            }
        }
        // why: the order is normative — reset stops whatever is sounding and
        // clears the one-shot guard, focus lands before anything is played,
        // and only then does the new card speak. Autoplay placed ahead of the
        // reset would be killed by it on the same frame.
        .onChange(of: currentCardID) { _, _ in
            // why: safety net only — an answer already begins the next turn
            // BEFORE the switch, so no card can render one frame revealed.
            resetCardState()
            // why: a field carried over from the previous card is not
            // re-mounted, so nothing else would re-assert focus for it.
            focusAnswerField()
            if let card = model.currentCard { autoplayPrompt(card) }
        }
        .onChange(of: produceAudioTrigger) { was, now in
            if !was, now { autoplayProduceReveal() }
        }
        .onAppear {
            // why: the card-change hook does not see the FIRST card, so the
            // first turn (and with it the recall clock) begins here.
            ensureTurn()
            // why: pays the process's first audio-session activation with an
            // inaudible clip, here where nothing is typed — never on a produce
            // reveal that carries the keyboard.
            Pronouncer.shared.warmUp()
            DLSound.warmUp()
            if let card = model.currentCard { autoplayPrompt(card) }
        }
        .onDisappear {
            autoAdvance?.cancel()
            focusRetry?.cancel()
            Pronouncer.shared.stop()
        }
        #if DEBUG
        // UI-test hooks: `-uitest-reveal 1` shows the first card revealed,
        // the shared answer hooks (`UITestAnswer`),
        // `-uitest-sound 1` plays each feedback sound with a console probe,
        // `-uitest-pronounce <form>` says one form and prints which branch said it.
        .onAppear {
            let defaults = UserDefaults.standard
            if defaults.bool(forKey: "uitest-reveal") {
                // why: revealed at the prompt's own timestamp — a card up
                // revealed measured no recall, and an unmeasured span must
                // not be the one that earns Easy.
                dispatch(TurnIntent.Reveal.shared, at: ensureTurn()?.promptShownAtMillis)
            }
            if let prefill = UITestAnswer.prefill { input = prefill }
            UITestAnswer.submitAfterBeat { dispatch(TurnIntent.Submit(text: input)) }
            if defaults.bool(forKey: "uitest-sound") {
                DLSound.uitestProbe()
            }
            if let form = defaults.string(forKey: "uitest-pronounce") {
                uitestPronounce(form)
            }
        }
        #endif
    }

    // why: internal, not private — the audio extension reads it to drop a
    // delayed word whose card has already gone.
    var currentCardID: String? { model.currentCardId }

    /// VoiceOver and Switch Control both make a timed screen change hostile:
    /// it truncates the correctness announcement and moves the page under the
    /// user. Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    @ViewBuilder
    private var scaffoldContent: some View {
        if let card = model.currentCard {
            cardContent(card)
        } else {
            ProgressView()
                .tint(.dlAccent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Card + controls

    private func cardContent(_ card: Card) -> some View {
        let role = model.presentationRole(for: card.id)
        return ScrollView {
            VStack(spacing: DL.Space.m) {
                // ZStack so outgoing and incoming card overlap during the flip
                // instead of stacking; .id gives each card its own identity.
                ZStack {
                    VocabCardView(
                        emoji: card.emoji,
                        emojiCue: model.emojiCue(for: card) == .upfront ? .upfront : .onReveal,
                        prompt: promptSide(card, role: role),
                        answer: answerSide(card, role: role),
                        note: card.target.note ?? card.source.note,
                        revealed: cardRevealed,
                        compact: true
                    )
                    .id(card.id)
                    .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                controls(card, role: role)
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Grammar (article coloring, plural) renders TARGET-side only; on
    /// recognition it decorates the prompt only when the canonical form is
    /// the one prompted (synonym rotations carry no citation grammar).
    private func promptSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            // why: a consolidated word is sometimes asked by ear alone — the
            // meaning is withheld ON PURPOSE, so no cue rides along with it
            // either; what stands is the replay glyph and nothing else.
            if model.producePrompt(for: card) == .sound {
                return .init(text: card.target.text,
                             language: model.targetLanguage,
                             pronounce: pronounceAction(for: card.target.text),
                             isPlaying: isPronouncing(card.target.text),
                             listening: true)
            }
            return .init(text: card.source.text,
                         // why: the area title IS the disambiguating cue, in the source
                         // language — it is a plain name, so nothing is trimmed off it.
                         context: card.promptAmbiguous ? model.areaTitle(card.area) : nil,
                         femMarker: card.promptFeminineMarker)
        case .recognize:
            // why: deliberately NO context cue here — the prompt is the target form, so
            // any cue precise enough to disambiguate would reveal the answer (same
            // reasoning as the emoji matrix). Self-grading absorbs the ambiguity.
            let form = model.promptForm(for: card)
            let canonical = form == card.target.text
            return .init(text: form,
                         article: CardDisplay.articleLabel(of: card.target, shown: form),
                         plural: canonical ? CardDisplay.plural(of: card.target, locale: locale) : nil,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: form),
                         isPlaying: isPronouncing(form))
        }
    }

    /// The reveal always shows the full family: produce reveals the target
    /// citation + synonyms; recognize reveals the source meaning (synonyms
    /// joined informatively) + the remaining target forms as "auch: …".
    private func answerSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            // why: a sound-prompted card never said what the word MEANS, so the
            // reveal owes it — otherwise a miss teaches nothing but spelling.
            let meaning = ([card.source.text] + card.source.synonyms).joined(separator: " / ")
            let alternates = CardDisplay.alternates(of: card.target,
                                                    shown: card.target.text,
                                                    locale: locale)
            let heard = model.producePrompt(for: card) == .sound
            let below: String? = heard
                ? [meaning, alternates].compactMap { $0 }.joined(separator: " · ")
                : alternates
            return .init(text: card.target.text,
                         article: CardDisplay.articleLabel(of: card.target,
                                                           shown: card.target.text),
                         plural: CardDisplay.plural(of: card.target, locale: locale),
                         alternates: below,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: card.target.text),
                         isPlaying: isPronouncing(card.target.text))
        case .recognize:
            let meaning = ([card.source.text] + card.source.synonyms).joined(separator: " / ")
            return .init(text: meaning,
                         alternates: CardDisplay.alternates(of: card.target,
                                                            shown: model.promptForm(for: card),
                                                            locale: locale),
                         femMarker: card.promptFeminineMarker)
        }
    }

    /// A field is on screen only where there is something to type: produce
    /// before its blank self-grade, and the write-out step. Recognize brings up
    /// none of its own — iOS drops the keyboard for a hidden field anyway, so
    /// pretending otherwise only cost reliable focus.
    @ViewBuilder
    private func controls(_ card: Card, role: PresentationRole) -> some View {
        if let step = turn?.copyStep {
            copyControls(step)
        } else {
            VStack(spacing: 0) {
                if role == .produce, !produceFieldHidden {
                    answerField(card)
                }
                switch role {
                case .recognize: recognizeControls
                case .produce: produceButtons(card)
                }
            }
        }
    }

    /// It matters here for the step "Unbekannt" opens: the write-it-out field
    /// mounts in the same frame as the request (`AnswerFocus`).
    func focusAnswerField() {
        AnswerFocus.claim($answerFocused, retry: &focusRetry)
    }

    /// Comprehension check: reveal, then honest self-grade —
    /// never typed, so no schedule is ever graded against a language it
    /// wasn't learned with. The very first exposure takes this path too: the
    /// word is prompted before it is taught, so a learner who already knows it
    /// gets the moment to recall it (contract §3).
    @ViewBuilder
    private var recognizeControls: some View {
        if revealed {
            RatingButtonsView { dispatch(TurnIntent.SelfGrade(verdict: $0.verdict)) }
        } else {
            Button {
                dispatch(TurnIntent.Reveal.shared)
            } label: {
                Text("session.reveal")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
        }
    }

    // Produce controls live in SessionView+Produce.swift, the write-out step in
    // SessionView+Copy.swift — both drive this same turn.

    // MARK: - The turn

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
            // retry is cancelled first, or it re-focuses 120 ms later.
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

    @discardableResult
    private func ensureTurn() -> TurnState? {
        if let turn { return turn }
        beginTurn()
        return turn
    }

    /// A card goes on screen: a fresh turn, and the machine that grades it.
    /// The recall clock starts with it.
    private func beginTurn() {
        guard let card = model.currentCard,
              let normalizer = model.answerNormalizer,
              let grader = model.produceGrader else {
            machine = nil
            turn = nil
            return
        }
        let machine = TurnMachine(grader: grader, normalizer: normalizer)
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
                             settled: model.isSettled(card.id),
                             nowEpochMillis: Date().epochMillis)
    }

    private func resetCardState() {
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
    var typoCorrection: String? {
        almostHold.flatMap { $0.reason == .typo ? $0.form : nil }
    }
}
