import SwiftUI
import SprossKern

/// PRODUCE half of SessionView: typing-first controls over kern's turn. Every
/// button and keystroke here is a `TurnIntent` — what each one is worth, and
/// which beat it earns, is `TurnMachine`'s. Split out purely for file size.
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
            submitFromField()
        }
        // why: writing the word out is the answer — the same rule the write-out
        // step runs on, so a word you know never asks for a confirming tap.
        .onChange(of: input) { _, _ in dispatch(TurnIntent.InputChanged(text: input)) }
        .padding(.bottom, DL.Space.m)
        .onAppear { focusAnswerField() }
    }

    /// Enter in the answer field. Which intent it IS depends on what the field
    /// currently stands for — kern grades each of the three differently, and
    /// only the platform knows which one is on screen.
    private func submitFromField() {
        if retryApproved {
            // why: the retype already stands — Enter only skips the beat the
            // timer is waiting out, it does not re-grade it.
            dispatch(TurnIntent.ConfirmPending.shared)
        } else if case .revealed = feedback {
            // why: Enter used to hit the "Next" button's default action once
            // revealed — a hardware keyboard still needs a way to give up
            // without finishing the retype.
            dispatch(TurnIntent.GiveUp.shared)
        } else {
            dispatch(TurnIntent.Submit(text: input))
        }
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
                RatingButtonsView(onGrade: { dispatch(TurnIntent.SelfGrade(verdict: $0.verdict)) },
                                  caption: gradeCaption)
            case .neutral:
                // ONE primary action: empty input reveals, typed input checks.
                // kern's Submit is inert on blank text, so which of the two a
                // press is stays the platform's to decide.
                Button {
                    if input.isBlankAnswer {
                        dispatch(TurnIntent.Reveal.shared)
                    } else {
                        dispatch(TurnIntent.Submit(text: input))
                    }
                } label: {
                    Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
            case .almost:
                // A typo or a heard-instead pauses here — the box above spells
                // the owed form out and says it; this waits for the tap that
                // books the card, on the rating kern parked at grading time.
                Button {
                    dispatch(TurnIntent.ConfirmPending.shared)
                } label: {
                    DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
            case .correct:
                // A clean answer auto-advances on the beat kern armed
                // (design §Review UX).
                if screenReaderOn {
                    // why: the timer never armed here, so this is the only way
                    // on — a clean answer would otherwise leave nothing to tap.
                    Button {
                        dispatch(TurnIntent.ConfirmPending.shared)
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
                // to the whole-word prefix that was already right — finishing
                // the retype IS the self-grade: completing it books the
                // recalled-with-help rating, giving up an honest Again.
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
                        // which grades Again, not what it just earned.
                        Button {
                            dispatch(TurnIntent.ConfirmPending.shared)
                        } label: {
                            DLActionLabel(key: "common.next", targetLocale: model.targetChromeLocale)
                        }
                        .buttonStyle(DLPrimaryButtonStyle())
                    }
                    // why: always reachable — a step you cannot leave is a
                    // trap, same as the write-out step's own skip. Giving up
                    // here ends the card: this field already is the one
                    // write-out the word gets, so nothing hands it a second.
                    Button("session.skipCopy") { dispatch(TurnIntent.GiveUp.shared) }
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
            }
        }
    }

    private var inputPlaceholder: String {
        model.targetLanguage.map { answerPlaceholder($0) } ?? ""
    }
}
