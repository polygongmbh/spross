import SwiftUI
import SprossKern

/// PRODUCE half of SessionView: typing-first controls and Kern-normalized
/// grading. State lives on SessionView; split out purely for file size.
extension SessionView {
    /// The one typed-answer field for a card's whole life — mounted for
    /// BOTH roles (`controls(_:role:)` places it above the role switch) and
    /// every produce sub-state, just visually collapsed rather than removed
    /// when there is nothing to type into (`fieldHidden`). The same view
    /// staying mounted throughout is what keeps focus reliable: a view that
    /// gets torn down and rebuilt can lose a focus request racing the
    /// rebuild, which is what made a reappearing field sometimes not
    /// autofocus. Recognize never reads `input` (self-graded, never typed),
    /// so it's harmless for the field to sit there quietly collapsed.
    func answerField(_ card: Card, role: PresentationRole) -> some View {
        let hidden = fieldHidden(role: role)
        return AnswerInputView(text: $input,
                        feedback: feedback,
                        placeholder: inputPlaceholder,
                        // why: the card's reveal already carries the
                        // answer with its article color, plural and
                        // alternates — the panel repeated it.
                        showsRevealPanel: false,
                        focus: $answerFocused,
                        // why: a disabled field can't hold keyboard focus,
                        // which would defeat the point of keeping it
                        // mounted through the hidden states.
                        locked: false) {
            guard role == .produce else { return }
            // why: Enter used to hit the "Next" button's default
            // action once revealed — a hardware keyboard still needs
            // a way to give up without finishing the retype.
            if case .revealed = feedback {
                rate(.again)
            } else {
                submit(card)
            }
        }
        // why: writing the word out is the answer — the same rule the copy
        // step runs on, so a word you know never asks for a confirming tap.
        .onChange(of: input) { _, _ in
            guard role == .produce else { return }
            approveWhenTyped(card)
        }
        .frame(height: hidden ? 0 : nil)
        .padding(.bottom, hidden ? 0 : DL.Space.m)
        .opacity(hidden ? 0 : 1)
        .clipped()
        .allowsHitTesting(!hidden)
        .accessibilityHidden(hidden)
    }

    /// True while there is nothing to type into: recognize never types, and
    /// produce's blank "Aufdecken" self-grade hides its own field too.
    private func fieldHidden(role: PresentationRole) -> Bool {
        role == .recognize || (revealed && feedback == .neutral)
    }

    /// Typing first (recall beats recognition); "Aufdecken" stays available
    /// for self-grading without typing. The field itself is `answerField`,
    /// mounted by the shared caller — this is buttons only.
    func produceButtons(_ card: Card) -> some View {
        Group {
            switch feedback {
            case .neutral where revealed:
                // Revealed without typing → honest self-grade.
                RatingButtonsView { rate(gradedRating($0)) }
            case .neutral:
                // ONE primary action: empty input reveals, typed input checks.
                Button {
                    if inputEmpty {
                        DLSound.reveal()
                        markRecallEnded()
                        // why: answerField stays mounted and focused — this
                        // only collapses it visually, so the keyboard never
                        // has to drop and come back.
                        withAnimation { revealed = true }
                    } else {
                        submit(card)
                    }
                } label: {
                    Text(inputEmpty ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: inputEmpty)
            case .correct:
                // A clean answer auto-advances after ~1.2 s (design §Review
                // UX). A typo pauses here — show the proper spelling and wait
                // for a tap so the slip is reviewed.
                if let typoCorrection {
                    VStack(spacing: DL.Space.m) {
                        // why: the proper spelling is the point of this pause —
                        // it has to be as readable as the reveal's own lines.
                        Text("session.typoCorrection \(typoCorrection)")
                            .font(DL.Fonts.subheadline)
                            .italic()
                            .foregroundStyle(Color.dlTextSecondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        Button {
                            rate(.good)
                        } label: {
                            DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                        .keyboardShortcut(.defaultAction)
                    }
                } else {
                    EmptyView()
                }
            case .revealed:
                // A miss reveals the answer on the card and primes this field
                // to the whole-word prefix that was already right
                // (`primeRetry`) — finishing the retype IS the self-grade:
                // completing it types .hard through `approveRetry`, and
                // giving up here grades an honest .again.
                VStack(spacing: DL.Space.m) {
                    if let otherWord {
                        // why: same line as the typo correction — both explain
                        // what became of the answer, so they read alike.
                        Text("session.otherWord \(otherWord.word) \(otherWord.meanings.joined(separator: ", "))")
                            .font(DL.Fonts.subheadline)
                            .italic()
                            .foregroundStyle(Color.dlTextSecondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                    // why: always reachable — a step you cannot leave is a
                    // trap, same as the copy step's own skip.
                    Button("session.skipCopy") { rate(.again) }
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
            }
        }
    }

    // MARK: - Grading (produce only — recognize is button self-grade)

    private var inputEmpty: Bool {
        input.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var inputPlaceholder: String {
        guard let target = model.targetLanguage else { return "" }
        let name = LanguageNames.display(target, locale: locale, catalog: model.catalog)
        return String(format: DLChrome.string("session.answer.placeholder %@", locale: locale), name)
    }

    /// Take a word typed out exactly right as the answer, without a "Prüfen" tap:
    /// the field turns green with its checkmark and the card flips a beat later.
    ///
    /// EXACT only, where Return still forgives a typo — the typo budget would fire
    /// a letter early and grade the word before it was finished, and a real typo
    /// has to pause on its correction anyway. Backing out of a finished word takes
    /// the green with it, so typing past the answer never commits it.
    private func approveWhenTyped(_ card: Card) {
        guard copyPending == nil, !revealed, typoCorrection == nil else { return }
        if case .revealed = feedback {
            approveRetry(card)
            return
        }
        autoAdvance?.cancel()
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, isExactAnswer(trimmed, card: card) else {
            if feedback == .correct { withAnimation { feedback = .neutral } }
            return
        }
        if feedback != .correct { DLSound.correct() }
        withAnimation { feedback = .correct }
        armFinishedTyping { rate(.good) }
    }

    /// Finishing the retype after a miss is the self-grade: reaching the
    /// exact answer counts as recalled-with-help, so it grades .hard rather
    /// than the blind .again a bare "give up" would.
    private func approveRetry(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, isExactAnswer(trimmed, card: card) else { return }
        autoAdvance?.cancel()
        DLSound.correct()
        armFinishedTyping { rate(.hard) }
    }

    /// After a miss reveals the answer, keep the whole words [input] already
    /// had right and drop only the wrong tail — full words, per kern's
    /// [AnswerNormalizer.matchingPrefixWordCount] — so the retry above picks
    /// up where the slip started instead of from scratch. `answerField` is
    /// already mounted and focused (a miss never hides it), so there is
    /// nothing to re-focus here.
    private func primeRetry(_ card: Card) {
        guard let normalizer = model.answerNormalizer else { return }
        let words = input.split(separator: " ", omittingEmptySubsequences: true).map(String.init)
        let count = Int(normalizer.matchingPrefixWordCount(input: input, answer: card.target.text))
        let kept = words.prefix(count).joined(separator: " ")
        input = kept.isEmpty ? "" : kept + " "
    }

    private func isExactAnswer(_ text: String, card: Card) -> Bool {
        guard let grader = model.produceGrader else { return false }
        if case .exact = onEnum(of: grader.grade(input: text, card: card)) { return true }
        return false
    }

    func submit(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty,
              let grader = model.produceGrader else { return }
        // Kern normalizer: accepted forms = target text ∪ synonyms ∪ variants,
        // article-optional, verb-prefix-optional, article-mismatch → typo;
        // a form another concept owns is that word, not a slip of this one.
        switch onEnum(of: grader.grade(input: trimmed, card: card)) {
        case .exact:
            feedback = .correct
            DLSound.correct()
            autoAdvance = Task {
                try? await Task.sleep(for: .milliseconds(1200))
                guard !Task.isCancelled else { return }
                rate(.good)
            }
        case .typo(let typo):
            // why: don't auto-advance on a typo — pause (keeping the typed
            // text visible) so the learner reviews the slip. Still counts as
            // correct once they tap on.
            feedback = .correct
            DLSound.correct()
            typoCorrection = typo.corrected
        case .otherWord(let other):
            // why: the typed word is taken — no typo credit (kufunga is not a
            // slip of kufungua), and the reveal says what they did write.
            feedback = .revealed(correctAnswer: CardDisplay.citation(of: card.target))
            otherWord = other
            DLSound.wrong()
            primeRetry(card)
        case .wrong:
            feedback = .revealed(correctAnswer: CardDisplay.citation(of: card.target))
            DLSound.wrong()
            primeRetry(card)
        }
    }
}
