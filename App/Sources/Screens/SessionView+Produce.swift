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
                // A clean answer auto-advances after ~1.2 s (design §Review
                // UX). A typo pauses here — show the proper spelling and wait
                // for a tap so the slip is reviewed.
                if let typoCorrection {
                    VStack(spacing: DL.Space.m) {
                        Text("Fast! Richtig geschrieben: \(typoCorrection)")
                            .font(DL.Fonts.caption)
                            .italic()
                            .foregroundStyle(Color.dlTextSecondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        Button {
                            rate(.good)
                        } label: {
                            DLActionLabel(key: "Weiter", targetLocale: model.targetChromeLocale)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                        .keyboardShortcut(.defaultAction)
                    }
                } else {
                    EmptyView()
                }
            case .revealed:
                HStack(spacing: DL.Space.m) {
                    // Correct after reveal → .hard (design §Session 4).
                    Button("Wusste ich doch") { rate(.hard) }
                        .buttonStyle(DLSoftButtonStyle(color: .dlTeal))
                    Button {
                        rate(.again)
                    } label: {
                        DLActionLabel(key: "Weiter", targetLocale: model.targetChromeLocale)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    // why: Enter advances when revealed (hardware keyboards).
                    .keyboardShortcut(.defaultAction)
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
        return String(format: DLChrome.string("Auf %@ …", locale: locale), name)
    }

    func submit(_ card: Card) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty,
              let normalizer = model.answerNormalizer else { return }
        // Kern normalizer: accepted forms = target text ∪ synonyms ∪ variants,
        // article-optional, verb-prefix-optional, article-mismatch → typo.
        switch onEnum(of: normalizer.evaluate(input: trimmed, card: card)) {
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
        case .wrong:
            feedback = .revealed(correctAnswer: CardDisplay.citation(of: card.target))
            DLSound.wrong()
        }
    }
}
