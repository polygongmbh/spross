import SwiftUI
import SprossKern

/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to self-grading);
/// RECOGNIZE prompts one rotated target form and is reveal + self-grade
/// only — never typed, bar the first exposure's write-it-out
/// (SessionView+Copy.swift). Presented as a full-screen cover.
struct SessionView: View, LanguageNaming {
    @Bindable var model: AppModel

    // why: internal, not private — SessionView+Produce.swift (file-size
    // split) drives the same card state from its extension.
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    @State var revealed = false
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown and the card waits for a tap so the slip is seen.
    @State var typoCorrection: String?
    /// Set when a SOUND-prompted answer is a form this very card accepts, but
    /// not the one that played. Amber like a typo: the word is right, it is
    /// simply not the one the learner heard, and the line says which was.
    @State var heardInstead: String?
    /// The rating a missed word already earned, held while it is written out
    /// (SessionView+Copy.swift). Non-nil ⇒ the copy step owns the controls.
    @State var copyPending: Rating?
    @State var copyInput = ""
    @State var copyMissed = false
    /// When the current prompt went on screen, and how long the learner spent
    /// on it before asking to see the answer. That span is the recall attempt;
    /// the time spent picking a button afterwards is thumb travel, so it is
    /// deliberately not part of it (`SelfGrading`).
    @State var promptShownAt: Date?
    @State var recallMs: Int64 = 0
    /// The copy field's own feedback — green edge + checkmark the moment the
    /// word stands written, re-judged on every keystroke.
    @State var copyFeedback: AnswerInputView.Feedback = .neutral
    /// Set when the typed answer is a word the catalog owns elsewhere: the
    /// reveal names it, so a near-miss teaches the other word instead of
    /// only failing (kufunga vs kufungua).
    @State var otherWord: MatchOtherWord?
    /// The retype after a miss has reached the word. The card stays on its
    /// reveal (`feedback` is still `.revealed`) while the FIELD goes green —
    /// the two say different things at that moment.
    @State var retryApproved = false
    @State var autoAdvance: Task<Void, Never>?
    /// The card whose word has already been said. The one-shot autoplay guard
    /// (SessionView+Audio.swift) — stored here because a SwiftUI extension
    /// cannot carry state of its own.
    @State var pronouncedCardID: String?
    /// Owned here (not in AnswerInputView) so whichever field is on screen —
    /// the answer field or the copy step's — takes focus the moment it
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
            // why: safety net only — rate() already resets BEFORE the switch
            // so the next card can never render one frame still revealed.
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
            promptShownAt = Date()
            // why: pays the process's first audio-session activation with an
            // inaudible clip, here where nothing is typed — never on a produce
            // reveal that carries the keyboard.
            Pronouncer.shared.warmUp()
            DLSound.warmUp()
            // why: the card-change hook does not see the FIRST card, exactly
            // like promptShownAt; the one-shot guard covers the overlap.
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
                revealed = true
            }
            if let prefill = UITestAnswer.prefill { input = prefill }
            UITestAnswer.submitAfterBeat { if let card = model.currentCard { submit(card) } }
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

    /// The card expands only when the word was NOT produced — "Aufdecken" or a
    /// wrong answer. A correct answer already stands in the input field, and a
    /// typo's proper spelling is carried by the correction box, so revealing
    /// there would put the same word on screen twice.
    var cardRevealed: Bool {
        if case .revealed = feedback { return true }
        return revealed
    }

    /// A field is on screen only where there is something to type: produce
    /// before its blank self-grade, and the copy step. Recognize brings up
    /// none of its own — iOS drops the keyboard for a hidden field anyway, so
    /// pretending otherwise only cost reliable focus.
    @ViewBuilder
    private func controls(_ card: Card, role: PresentationRole) -> some View {
        if copyPending != nil {
            copyControls(card)
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
            RatingButtonsView { rate(gradedRating($0)) }
        } else {
            Button {
                DLSound.reveal()
                markRecallEnded()
                withAnimation { revealed = true }
            } label: {
                Text("session.reveal")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
        }
    }

    // Produce controls + typed grading live in SessionView+Produce.swift.

    func rate(_ rating: Rating) {
        autoAdvance?.cancel()
        // why: a missed word is written out once before the session moves on; the
        // rating is held, not changed, and applied when the copy is done.
        if copyPending == nil, wantsCopyStep(rating, card: model.currentCard) {
            copyPending = rating
            copyInput = ""
            copyMissed = false
            copyFeedback = .neutral
            withAnimation { revealed = true }
            // why: copyControls mounts its own field and claims focus from
            // its .onAppear — asserting it here would land before it exists.
            return
        }
        commit(rating)
    }

    /// Hand the answer to the engine and flip to the next card. Separate from
    /// `rate` so the copy step can finish through it WITHOUT re-entering the
    /// copy check — going back through `rate` would divert the same Again
    /// forever, since the word is still unsettled at that point.
    func commit(_ rating: Rating) {
        autoAdvance?.cancel()
        // why: reset BEFORE the card switch, in the same transaction — the
        // next card must never render one frame with the old revealed state.
        resetCardState()
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
            model.answerCurrent(rating)
        }
    }

    private func resetCardState() {
        autoAdvance?.cancel()
        // why: the word in the air belongs to the card that is leaving — the
        // one place playback is stopped, together with .onDisappear.
        Pronouncer.shared.stop()
        pronouncedCardID = nil
        input = ""
        feedback = .neutral
        revealed = false
        typoCorrection = nil
        heardInstead = nil
        otherWord = nil
        retryApproved = false
        copyPending = nil
        copyInput = ""
        copyMissed = false
        copyFeedback = .neutral
        // why: called in the same transaction as the card switch, so the recall
        // clock starts with the prompt the learner is about to see.
        promptShownAt = Date()
        recallMs = 0
    }

    /// Close the recall attempt: the prompt has been on screen since
    /// `promptShownAt`, and the learner has just asked to see the answer.
    func markRecallEnded() {
        guard let promptShownAt else { return }
        recallMs = Int64(Date().timeIntervalSince(promptShownAt) * 1000)
    }

    /// The buttons report what the learner knows; the engine turns that plus
    /// the recall time into an FSRS rating (rule: kern `SelfGrading`).
    func gradedRating(_ outcome: SessionOutcome) -> Rating {
        SelfGrading.shared.rating(verdict: verdict(outcome),
                                  elapsedMs: recallMs,
                                  promptChars: Int32(promptChars))
    }

    private func verdict(_ outcome: SessionOutcome) -> SelfGrading.Verdict {
        switch outcome {
        case .right: return .knew
        case .tough: return .tough
        case .wrong: return .unknown
        }
    }

    /// What the learner had to read before recall could start — the prompted
    /// form on recognition, the source word on a produce card that was revealed.
    private var promptChars: Int {
        guard let card = model.currentCard else { return 0 }
        return model.presentationRole(for: card.id) == .recognize
            ? model.promptForm(for: card).count
            : card.source.text.count
    }
}
