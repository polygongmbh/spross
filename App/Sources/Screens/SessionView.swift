import SwiftUI
import SprossKern

/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to self-grading);
/// RECOGNIZE prompts one rotated target form and is reveal + self-grade
/// only — never typed. Presented as a full-screen cover.
struct SessionView: View {
    @Bindable var model: AppModel

    // why: internal, not private — SessionView+Produce.swift (file-size
    // split) drives the same card state from its extension.
    @State var input = ""
    @State var feedback: AnswerInputView.Feedback = .neutral
    @State var revealed = false
    /// Set when the answer was accepted with a small typo — the proper
    /// spelling is shown and the card waits for a tap so the slip is seen.
    @State var typoCorrection: String?
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
    @State var autoAdvance: Task<Void, Never>?
    /// Owned here (not in AnswerInputView) so the keyboard is up the moment
    /// a card appears and stays up across cards.
    @FocusState var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.locale) var locale

    var body: some View {
        Group {
            if model.sessionStep == .completed {
                SessionCompletionView(newCount: model.sessionNew,
                                      graduatedCount: model.sessionGraduated,
                                      reviewCount: model.sessionReviews,
                                      streakDays: model.stats?.streakDays ?? 0,
                                      canPracticeMore: model.canPracticeMore,
                                      restSuggested: model.today?.recallStrained ?? false,
                                      onPractice: { model.continueEndless() },
                                      onDone: { model.closeSession() })
            } else {
                SessionScaffold(position: model.sessionPosition,
                                total: max(model.sessionTotal, 1),
                                outcomes: model.sessionRatings.map(outcome(for:)),
                                onClose: { model.closeSession() }) {
                    scaffoldContent
                }
            }
        }
        .onChange(of: currentCardID) { _, _ in
            // why: safety net only — rate() already resets BEFORE the switch
            // so the next card can never render one frame still revealed.
            resetCardState()
            answerFocused = true
        }
        .onAppear {
            answerFocused = true
            promptShownAt = Date()
        }
        .onDisappear {
            autoAdvance?.cancel()
        }
        #if DEBUG
        // UI-test hooks: `-uitest-reveal 1` shows the first card revealed,
        // `-uitest-input xyz` prefills the answer field,
        // `-uitest-submit 1` submits the prefilled answer after 0.6 s,
        // `-uitest-sound 1` plays each feedback sound with a console probe.
        .onAppear {
            let defaults = UserDefaults.standard
            if defaults.bool(forKey: "uitest-reveal") {
                revealed = true
            }
            if let prefill = defaults.string(forKey: "uitest-input") {
                input = prefill
            }
            if defaults.bool(forKey: "uitest-submit") {
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(600))
                    if let card = model.currentCard { submit(card) }
                }
            }
            if defaults.bool(forKey: "uitest-sound") {
                DLSound.uitestProbe()
            }
        }
        #endif
    }

    private var currentCardID: String? {
        if case .card(let id)? = model.sessionStep { return id }
        return nil
    }

    /// Bar segments: good/easy green, hard amber, again brick.
    private func outcome(for rating: Rating) -> SessionOutcome {
        switch rating {
        case .again: return .wrong
        case .hard: return .tough
        case .good, .easy: return .right
        }
    }

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
            return .init(text: card.source.text,
                         context: card.promptAmbiguous ? areaCue(card.area) : nil,
                         femMarker: card.promptFeminineMarker)
        case .recognize:
            // why: deliberately NO context cue here — the prompt is the target form, so
            // any cue precise enough to disambiguate would reveal the answer (same
            // reasoning as the emoji matrix). Self-grading absorbs the ambiguity.
            let form = model.promptForm(for: card)
            let canonical = form == card.target.text
            return .init(text: form,
                         article: canonical ? CardDisplay.article(of: card.target) : nil,
                         plural: canonical ? CardDisplay.plural(of: card.target, locale: locale) : nil)
        }
    }

    /// Area label as a disambiguating cue, in the source language (`areaTitle` already
    /// resolves there). Titles carry a "·" flavour tail ("Jikoni · karibu chakula
    /// kitamu!") — only the head is a label, so the tail is dropped.
    private func areaCue(_ area: String) -> String {
        model.areaTitle(area)
            .split(separator: "·", maxSplits: 1)
            .first
            .map { $0.trimmingCharacters(in: .whitespaces) } ?? model.areaTitle(area)
    }

    /// The reveal always shows the full family: produce reveals the target
    /// citation + synonyms; recognize reveals the source meaning (synonyms
    /// joined informatively) + the remaining target forms as "auch: …".
    private func answerSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            return .init(text: card.target.text,
                         article: CardDisplay.article(of: card.target),
                         plural: CardDisplay.plural(of: card.target, locale: locale),
                         alternates: CardDisplay.alternates(of: card.target,
                                                            shown: card.target.text,
                                                            locale: locale))
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
    /// typo's proper spelling is carried by the correction line, so revealing
    /// there would put the same word on screen twice.
    private var cardRevealed: Bool {
        if case .revealed = feedback { return true }
        return revealed
    }

    @ViewBuilder
    private func controls(_ card: Card, role: PresentationRole) -> some View {
        if copyPending != nil {
            copyControls(card)
        } else {
            switch role {
            case .recognize: recognizeControls
            case .produce: produceControls(card)
            }
        }
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
            answerFocused = true
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
        input = ""
        feedback = .neutral
        revealed = false
        typoCorrection = nil
        otherWord = nil
        copyPending = nil
        copyInput = ""
        copyMissed = false
        copyFeedback = .neutral
        // why: called in the same transaction as the card switch, so the recall
        // clock starts with the prompt the learner is about to see.
        promptShownAt = Date()
        recallMs = 0
    }

    /// A beat between the last letter and the card leaving, shared by every
    /// path where finishing the word IS the action (typing an answer,
    /// writing a missed word out).
    ///
    /// why: a flip on the same frame as the final keystroke reads as a glitch
    /// rather than as having finished. Re-armed on every keystroke, so typing
    /// past the word calls the flip off instead of racing it.
    func armFinishedTyping(_ action: @escaping @MainActor () -> Void) {
        autoAdvance = Task {
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            action()
        }
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
