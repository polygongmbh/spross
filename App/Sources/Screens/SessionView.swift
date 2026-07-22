import SwiftUI
import SprossKern

/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to four-button self-grading);
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
                        emoji: model.emojiVisible(for: card) ? card.emoji : nil,
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
            return .init(text: card.source.text, femMarker: card.promptFeminineMarker)
        case .recognize:
            let form = model.promptForm(for: card)
            let canonical = form == card.target.text
            return .init(text: form,
                         article: canonical ? CardDisplay.article(of: card.target) : nil,
                         plural: canonical ? CardDisplay.plural(of: card.target, locale: locale) : nil)
        }
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

    private var cardRevealed: Bool {
        revealed || feedback != .neutral
    }

    @ViewBuilder
    private func controls(_ card: Card, role: PresentationRole) -> some View {
        switch role {
        case .recognize: recognizeControls
        case .produce: produceControls(card)
        }
    }

    /// Comprehension check: reveal, then honest four-button self-grade —
    /// never typed, so no schedule is ever graded against a language it
    /// wasn't learned with.
    @ViewBuilder
    private var recognizeControls: some View {
        if revealed {
            RatingButtonsView { rate(kernRating($0)) }
        } else {
            Button {
                DLSound.reveal()
                withAnimation { revealed = true }
            } label: {
                Text("Aufdecken")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
        }
    }

    // Produce controls + typed grading live in SessionView+Produce.swift.

    func rate(_ rating: Rating) {
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
    }

    /// Design's RatingButtonsView has its own local Rating (no Kern dep);
    /// map it to the domain rating at the boundary.
    func kernRating(_ rating: RatingButtonsView.Rating) -> Rating {
        switch rating {
        case .again: return .again
        case .hard: return .hard
        case .good: return .good
        case .easy: return .easy
        }
    }
}
