import SwiftUI
import DuoKern

/// Full-screen session: recognition (reveal + self-grade) for de→target,
/// typed production for target→de. Presented as a full-screen cover.
struct SessionView: View {
    @Bindable var model: AppModel

    @State private var input = ""
    @State private var feedback: AnswerInputView.Feedback = .neutral
    @State private var revealed = false
    @State private var autoAdvance: Task<Void, Never>?

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
                                onClose: { model.closeSession() }) {
                    scaffoldContent
                }
            }
        }
        .onChange(of: currentCardID) { _, _ in
            resetCardState()
        }
        .onDisappear {
            autoAdvance?.cancel()
        }
        #if DEBUG
        // UI-test hook: `-uitest-reveal 1` shows the first card revealed.
        .onAppear {
            if UserDefaults.standard.bool(forKey: "uitest-reveal") {
                revealed = true
            }
        }
        #endif
    }

    private var currentCardID: String? {
        if case .card(let id)? = model.sessionStep { return id }
        return nil
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
            VStack(spacing: DL.Space.xl) {
                VocabCardView(
                    emoji: card.displayEmoji,
                    article: card.article,
                    headword: card.german,
                    plural: card.plural,
                    translation: card.translation,
                    note: card.note,
                    mode: mode,
                    revealed: cardRevealed
                )
                controls(card)
            }
            .padding(.top, DL.Space.s)
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private var mode: VocabCardView.Mode {
        model.box?.config.direction == .targetToDe ? .production : .recognition
    }

    private var cardRevealed: Bool {
        mode == .recognition ? revealed : feedback != .neutral
    }

    @ViewBuilder
    private func controls(_ card: Card) -> some View {
        if mode == .recognition {
            if revealed {
                RatingButtonsView { model.answerCurrent(kernRating($0)) }
            } else {
                Button {
                    withAnimation { revealed = true }
                } label: {
                    Text("Aufdecken")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
            }
        } else {
            productionControls(card)
        }
    }

    private func productionControls(_ card: Card) -> some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: "Auf Deutsch …") {
                submit(card)
            }
            switch feedback {
            case .neutral:
                Button {
                    submit(card)
                } label: {
                    Text("Prüfen")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
            case .correct:
                // Auto-advances after ~800 ms (design §Review UX).
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

    private func submit(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        if AnswerNormalizer.matches(input: trimmed, expected: card.german) {
            feedback = .correct
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(800))
                guard !Task.isCancelled else { return }
                rate(.good)
            }
        } else {
            feedback = .revealed(correctAnswer: card.germanWithArticle)
        }
    }

    private func rate(_ rating: Rating) {
        autoAdvance?.cancel()
        model.answerCurrent(rating)
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
