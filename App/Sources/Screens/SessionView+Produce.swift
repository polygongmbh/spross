import SwiftUI
import SprossKern

/// PRODUCE half of SessionView: typing-first controls and Kern-normalized
/// grading. State lives on SessionView; split out purely for file size.
extension SessionView {
    /// Typing first (recall beats recognition); "Aufdecken" stays available
    /// for self-grading without typing.
    func produceControls(_ card: Card) -> some View {
        VStack(spacing: DL.Space.m) {
            if !(revealed && feedback == .neutral) {
                AnswerInputView(text: $input,
                                feedback: feedback,
                                placeholder: inputPlaceholder,
                                // why: the card's reveal already carries the
                                // answer with its article color, plural and
                                // alternates — the panel repeated it.
                                showsRevealPanel: false,
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
                    HStack(spacing: DL.Space.m) {
                        // Correct after reveal → .hard (design §Session 4).
                        Button("session.knewIt") { rate(.hard) }
                            .buttonStyle(DLSoftButtonStyle(color: .dlTeal))
                        Button {
                            rate(.again)
                        } label: {
                            DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                        // why: Enter advances when revealed (hardware keyboards).
                        .keyboardShortcut(.defaultAction)
                    }
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
        case .wrong:
            feedback = .revealed(correctAnswer: CardDisplay.citation(of: card.target))
            DLSound.wrong()
        }
    }
}
