import SwiftUI
import DuoKern

/// Full-screen session. Both directions offer typing first; "Aufdecken"
/// without typing falls back to four-button self-grading. The direction a
/// card is SHOWN in is asked per card (`presentationDirection`) so mixed
/// mode can alternate; rating semantics are direction-independent.
/// Presented as a full-screen cover.
struct SessionView: View {
    @Bindable var model: AppModel

    @State private var input = ""
    @State private var feedback: AnswerInputView.Feedback = .neutral
    @State private var revealed = false
    @State private var autoAdvance: Task<Void, Never>?
    /// Owned here (not in AnswerInputView) so the keyboard is up the moment
    /// a card appears and stays up across cards.
    @FocusState private var answerFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if model.sessionStep == .completed {
                SessionCompletionView(reviewCount: model.sessionAnswered,
                                      streakDays: model.stats?.streak ?? 0) {
                    model.closeSession()
                }
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
        } else if case .pause(let until)? = model.sessionStep {
            PauseView(until: until, model: model)
        } else {
            ProgressView()
                .tint(.dlAccent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Card + controls

    private func cardContent(_ card: Card) -> some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
                // ZStack so outgoing and incoming card overlap during the flip
                // instead of stacking; .id gives each card its own identity.
                ZStack {
                    VocabCardView(
                        emoji: card.displayEmoji,
                        article: card.article,
                        headword: card.german,
                        plural: card.plural,
                        translation: card.translation,
                        note: card.note,
                        mode: mode(for: card),
                        revealed: cardRevealed,
                        compact: true,
                        hideEmojiUntilRevealed: hideEmoji(for: card)
                    )
                    .id(card.id)
                    .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                controls(card)
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
    }

    /// Review/relearning cards "stick" — the emoji would give the answer away,
    /// so it stays hidden until reveal. New/learning cards keep the hint.
    private func hideEmoji(for card: Card) -> Bool {
        switch model.scheduling(for: card.id)?.phase {
        case .review, .relearning: return true
        default: return false
        }
    }

    /// Per-card presentation direction (alternates with mixed directions):
    /// `.targetToDe` → production (target prompt, German typed),
    /// `.deToTarget` → recognition (German prompt, target typed).
    private func mode(for card: Card) -> VocabCardView.Mode {
        model.presentationDirection(for: card.id) == .targetToDe ? .production : .recognition
    }

    private var cardRevealed: Bool {
        revealed || feedback != .neutral
    }

    /// Both directions offer typing first (recall beats recognition);
    /// "Aufdecken" stays available for self-grading without typing.
    private func controls(_ card: Card) -> some View {
        VStack(spacing: DL.Space.m) {
            if !(revealed && feedback == .neutral) {
                AnswerInputView(text: $input,
                                feedback: feedback,
                                placeholder: inputPlaceholder(card),
                                focus: $answerFocused) {
                    submit(card)
                }
            }
            switch feedback {
            case .neutral where revealed:
                // Revealed without typing → honest self-grade, four ratings.
                RatingButtonsView { rate(kernRating($0)) }
            case .neutral:
                // ONE primary action: empty input reveals, typed input checks.
                Button {
                    if inputEmpty {
                        DLSound.reveal()
                        withAnimation { revealed = true }
                    } else {
                        submit(card)
                    }
                } label: {
                    Text(inputEmpty ? "Aufdecken" : "Prüfen")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: inputEmpty)
            case .correct:
                // Auto-advances after ~1.2 s (design §Review UX).
                EmptyView()
            case .revealed:
                HStack(spacing: DL.Space.m) {
                    // Correct after reveal → .hard (design §Session 4).
                    Button("Wusste ich doch") { rate(.hard) }
                        .buttonStyle(DLSoftButtonStyle(color: .dlTeal))
                    Button {
                        rate(.again)
                    } label: {
                        Text("Weiter")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    // why: Enter advances when revealed (hardware keyboards).
                    .keyboardShortcut(.defaultAction)
                }
            }
        }
    }

    // MARK: - Grading

    private var inputEmpty: Bool {
        input.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func inputPlaceholder(_ card: Card) -> String {
        mode(for: card) == .production ? "Auf Deutsch …"
            : (card.pair == .deSw ? "Auf Swahili …" : "Auf Ukrainisch …")
    }

    private func submit(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        let mode = mode(for: card)
        let expected = mode == .production ? card.german : card.translation
        switch AnswerNormalizer.evaluate(input: trimmed, expected: expected) {
        case .exact, .typo:
            // Typos count as correct; the revealed card shows the proper
            // spelling during the auto-advance window (never punishing).
            feedback = .correct
            DLSound.correct()
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(1200))
                guard !Task.isCancelled else { return }
                rate(.good)
            }
        case .wrong:
            let answer = mode == .production ? card.germanWithArticle : card.translation
            feedback = .revealed(correctAnswer: answer)
            DLSound.wrong()
        }
    }

    private func rate(_ rating: Rating) {
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
    }

    /// Design's RatingButtonsView has its own local Rating (no DuoKern dep);
    /// map it to the domain rating at the boundary.
    private func kernRating(_ rating: RatingButtonsView.Rating) -> Rating {
        switch rating {
        case .again: return .again
        case .hard: return .hard
        case .good: return .good
        case .easy: return .easy
        }
    }
}

// MARK: - PauseView

/// "Kurze Pause": learning steps come due within minutes; count down to the
/// next one (or continue early).
private struct PauseView: View {
    let until: Date
    let model: AppModel

    @State private var now = Date()
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            Text("☕️")
                .font(.system(size: 72))
                .accessibilityHidden(true)
            Text("Kurze Pause")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            Text("Frisch Gelerntes braucht einen Moment zum Setzen.\nIn \(remainingText) geht's weiter.")
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            Button("Jetzt weitermachen") { model.skipPause() }
                .buttonStyle(DLSoftButtonStyle())
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .onReceive(timer) { date in
            now = date
            model.resumePauseIfDue(now: date)
        }
    }

    private var remainingText: String {
        let seconds = max(0, Int(until.timeIntervalSince(now).rounded(.up)))
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
