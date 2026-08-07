import SwiftUI
import SprossKern

/// PRODUCE half of SessionView: typing-first controls and Kern-normalized
/// grading. State lives on SessionView; split out purely for file size.
extension SessionView {
    /// The typed-answer field, mounted only while there is something to type
    /// into. It asks for focus from its own `.onAppear` (`focusAnswerField`):
    /// a request made before the field is on screen lands on nothing, so the
    /// field claiming focus for itself is the only ordering that holds.
    func answerField(_ card: Card) -> some View {
        AnswerInputView(text: $input,
                        feedback: fieldFeedback,
                        placeholder: inputPlaceholder,
                        focus: $answerFocused,
                        // why: a miss keeps typing — the retype IS the
                        // answer, so the field must not lock on reveal.
                        locked: false,
                        correctionVoice: .init(pronounce: { pronounceAction(for: $0) },
                                               isPlaying: { isPronouncing($0) })) {
            // why: Enter used to hit the "Next" button's default
            // action once revealed — a hardware keyboard still needs
            // a way to give up without finishing the retype.
            if retryApproved {
                // why: the retype already stands — Enter only skips the beat
                // the timer is waiting out, it does not re-grade it.
                rate(.hard)
            } else if case .revealed = feedback {
                // why: straight to commit — this field WAS the write-it-out
                // step, so `rate` would answer the same word with a second one.
                commit(.again)
            } else {
                submit(card)
            }
        }
        // why: writing the word out is the answer — the same rule the copy
        // step runs on, so a word you know never asks for a confirming tap.
        .onChange(of: input) { _, _ in approveWhenTyped(card) }
        .padding(.bottom, DL.Space.m)
        .onAppear { focusAnswerField() }
    }

    /// The field's own state. It parts ways with the card's on a finished
    /// retype: the card holds its reveal open while the field turns green with
    /// its checkmark, the same confirmation a first-try answer gets.
    var fieldFeedback: AnswerInputView.Feedback {
        retryApproved ? .correct : feedback
    }

    /// True while produce has nothing to type into: the blank "Aufdecken"
    /// self-grade hides its own field and hands over the rating buttons.
    var produceFieldHidden: Bool {
        revealed && feedback == .neutral
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
            case .almost:
                // A typo or a heard-instead pauses here — the box above spells
                // the owed form out and says it; this waits for the tap that
                // books the card.
                Button {
                    rate(.good)
                } label: {
                    DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
            case .correct:
                // A clean answer auto-advances after ~1.2 s (design §Review UX).
                if screenReaderOn {
                    // why: the timer never armed here, so this is the only way
                    // on — a clean answer would otherwise leave nothing to tap.
                    Button {
                        rate(.good)
                    } label: {
                        DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    .keyboardShortcut(.defaultAction)
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
                            .dlPauseLine()
                            // why: the line says what the learner DID write —
                            // the word it plays is the one they owed, the same
                            // one the reveal above it carries.
                            .pronounceOnTap(pronounceAction(for: card.target.text))
                    }
                    if retryApproved, screenReaderOn {
                        // why: the timer never arms under a screen reader, so a
                        // finished retype would have no way on but "give up" —
                        // which grades .again, not the .hard it just earned.
                        Button {
                            rate(.hard)
                        } label: {
                            DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                    }
                    // why: always reachable — a step you cannot leave is a
                    // trap, same as the copy step's own skip. Giving up here
                    // ends the card: this field already is the one write-out
                    // the word gets, so nothing may hand it a second one.
                    Button("session.skipCopy") { commit(.again) }
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
        guard copyPending == nil, !revealed, typoCorrection == nil, heardInstead == nil else { return }
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
        AutoAdvance.scheduleLive(&autoAdvance) { rate(.good) }
    }

    /// Finishing the retype after a miss is the self-grade: reaching the
    /// exact answer counts as recalled-with-help, so it grades .hard rather
    /// than the blind .again a bare "give up" would.
    private func approveRetry(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, isExactAnswer(trimmed, card: card) else {
            // why: backing out of a finished retype takes the green with it —
            // and the pending .hard, which would otherwise fire on a word that
            // no longer stands written.
            if retryApproved {
                autoAdvance?.cancel()
                withAnimation { retryApproved = false }
            }
            return
        }
        guard !retryApproved else { return }
        autoAdvance?.cancel()
        DLSound.correct()
        withAnimation { retryApproved = true }
        AutoAdvance.scheduleLive(&autoAdvance) { rate(.hard) }
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
        if case .exact = onEnum(of: grader.grade(input: text, card: gradingCard(card))) { return true }
        return false
    }

    /// What the answer is graded AGAINST. A card asked by ear accepts only the
    /// form that played — Kern's `spokenOnly`, the same rule the letter drill's
    /// dictation runs, because crediting a synonym would credit a word the
    /// learner never heard. Everywhere else the card grades as itself.
    func gradingCard(_ card: Card) -> Card {
        guard model.producePrompt(for: card) == .sound else { return card }
        return spokenOnly(card: card, spokenForm: card.target.text)
    }

    /// A form the REAL card lists as a synonym or a variant — right word, not
    /// the one that played. Amber, never wrong: the reveal itself teaches these
    /// forms ("auch: …"), so failing one would contradict the card.
    private func alsoAccepted(_ input: String, of card: Card) -> Bool {
        let typed = speechKey(form: input)
        return (card.target.synonyms + card.target.variants).contains { speechKey(form: $0) == typed }
    }

    func submit(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty,
              let grader = model.produceGrader else { return }
        // Kern normalizer: accepted forms = target text ∪ synonyms ∪ variants,
        // article-optional, verb-prefix-optional, article-mismatch → typo;
        // a form another concept owns is that word, not a slip of this one.
        let graded = grader.grade(input: trimmed, card: gradingCard(card))
        // why: BEFORE the verdict, and only where the card was asked by ear —
        // the narrowed answer set would otherwise fail a synonym the reveal
        // itself teaches. It is not wrong, it simply is not what played.
        if model.producePrompt(for: card) == .sound, alsoAccepted(trimmed, of: card) {
            feedback = .almost(correctForm: card.target.text, reason: .heard)
            DLSound.correct()
            heardInstead = card.target.text
            focusRetry?.cancel()
            answerFocused = false
            return
        }
        switch onEnum(of: graded) {
        case .exact:
            feedback = .correct
            DLSound.correct()
            // why: AutoAdvance skips the timer under a screen reader — it
            // truncates the correctness announcement and moves the screen
            // under the user (produceButtons renders "Weiter" there instead).
            AutoAdvance.scheduleExplicit(&autoAdvance) { rate(.good) }
        case .typo(let typo):
            // why: don't auto-advance on a typo — pause (keeping the typed
            // text visible) so the learner reviews the slip. Still counts as
            // correct once they tap on.
            feedback = .almost(correctForm: typo.corrected, reason: .typo)
            DLSound.correct()
            typoCorrection = typo.corrected
            // why: a pause that waits for a tap must not hold the keyboard —
            // it covers the button the pause is waiting for. The pending
            // retry is cancelled first, or it re-focuses 120 ms later.
            focusRetry?.cancel()
            answerFocused = false
        case .otherWord(let other):
            // why: the typed word is taken — no typo credit (kufunga is not a
            // slip of kufungua), and the reveal says what they did write.
            feedback = .revealed
            otherWord = other
            DLSound.wrong()
            primeRetry(card)
        case .wrong:
            feedback = .revealed
            DLSound.wrong()
            primeRetry(card)
        }
    }
}
