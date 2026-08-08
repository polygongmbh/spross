import SwiftUI
import SprossKern

/// Drill screen content + close summary of TrainerSessionView. State lives
/// on TrainerSessionView; split out purely for file size.
extension TrainerSessionView {
    private var isNumbers: Bool { currentVariant == .numbers }

    /// Which tasks the numbers page can answer a question about: counting and the
    /// forms, whose marks the page's last band writes out. The clock and a sentence
    /// are asking something the table does not hold.
    private var opensReference: Bool { currentVariant == .numbers || currentVariant == .forms }

    /// A prompt made of WORDS is laid out like one — smaller and wrapped — where a
    /// numeral gets the one big line. Asked of the prompt rather than of the run, so
    /// a composed sentence and a reversed reading are both read as what they are and
    /// the card never learns which direction the run is running in.
    private var wordyPrompt: Bool { current.promptDisplay.contains(where: \.isLetter) }

    /// The card carries the answer whenever the learner did not produce it —
    /// a miss or "Aufdecken". A typo leaves it closed: the correction box
    /// already spells the word out, and the answer is never on screen twice.
    private var cardRevealed: Bool {
        if case .revealed = feedback { return true }
        return false
    }

    /// Digit count of the current numeric prompt (nil outside the numbers
    /// drill). Internal: advance() marks each length as seen.
    ///
    /// Nil when the task is reversed: the prompt is then the reading, which already
    /// names the place the hint would introduce.
    var currentDigits: Int? { isNumbers && !currentReversed ? current.prompt.count : nil }

    /// The form this task asks, the first time the run asks it (nil after that, and
    /// nil when the task was reversed — the prompt is then the reading, which names
    /// the form in words already). Internal: advance() marks each form as seen.
    var currentForm: String? {
        guard !currentReversed, let key = current.formKey, !seenForms.contains(key) else { return nil }
        return key
    }

    /// What the card says the first time a prompt carries something new — always a word
    /// in the language being learned: the place word for a length never seen, the word
    /// the form ADDS for a mark never seen ("Neu: menos"). One slot, so the prompts that
    /// carry no hint sit exactly as high.
    ///
    /// Naming the category instead ("negative Zahl") was the first try and taught
    /// nothing: a learner cannot say it, and the card is where saying it is owed.
    /// The form wins where both could fire — a decimal's whole part is not the lesson
    /// on the card that introduces the comma.
    private var promptHint: TrainerPromptCard.Hint? {
        if let form = currentForm,
           let marker = Trainer.shared.formHint(formKey: form, language: language) {
            return .init(icon: "number.square", text: "trainer.newForm \(marker)")
        }
        guard let digits = currentDigits, !seenDigitCounts.contains(digits),
              let place = Trainer.shared.placeValueHint(digits: Int32(digits), language: language)
        else { return nil }
        return .init(icon: "textformat.123", text: "trainer.newPlace \(place)")
    }

    var drillContent: some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
                streakLine
                // ZStack so outgoing and incoming prompt overlap during the
                // flip; .id gives each run position its own view identity.
                ZStack {
                    TrainerPromptCard(task: current, sentence: wordyPrompt,
                                      hint: promptHint, revealed: cardRevealed,
                                      pronounce: model?.pronounceAction(for: current.display, lang: language),
                                      isPlaying: model?.isPronouncing(current.display, lang: language) ?? false)
                        .id(index)
                        .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                controls
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
        .sheet(isPresented: $showingReference) {
            NumberReferenceSheet(language: language, catalog: catalog)
        }
    }

    private var streakLine: some View {
        DrillStreakLine(level: levelText, streak: streak, bestStreak: bestStreak,
                        announcesRecord: true)
    }

    /// The rung part of the score line, for the variant that just asked: numbers
    /// count DIGITS, everything else counts plain levels — and a variant with one
    /// rung shows none. The emoji leads only where the run offers more than one
    /// variant, since a run that asks one thing has already said what it asks.
    private var levelText: Text? {
        let variant = currentVariant
        guard maxLevel(variant) > 1 else { return nil }
        let rung = level(variant)
        guard !isNumbers else {
            // why: `trainer.digits` is the numbers drill's own wording and already
            // wears 🔢 — putting the variant's face in front would double it.
            return Text("trainer.digits \(rung)")
        }
        let text = Text("trainer.level \(rung.formatted())")
        guard mode.variants.count > 1 else { return text }
        return Text(verbatim: "\(variant.trainerEmoji) ") + text
    }

    /// What the field asks for. Naming the language is right only while the
    /// answer is words — a reversed task wants the value written out, and
    /// "Auf Swahili …" over a number pad asks for the wrong thing.
    private var fieldPlaceholder: String {
        currentReversed
            ? DLChrome.string("trainer.answer.digits", locale: locale)
            : answerPlaceholder(language)
    }

    private var controls: some View {
        VStack(spacing: DL.Space.m) {
            AnswerInputView(text: $input,
                            feedback: feedback,
                            placeholder: fieldPlaceholder,
                            focus: $answerFocused,
                            correctionVoice: .init(
                                pronounce: { model?.pronounceAction(for: $0, lang: language) },
                                isPlaying: { model?.isPronouncing($0, lang: language) ?? false }),
                            keyboard: currentReversed ? .numbersAndPunctuation : .default) {
                submit()
            }
            .onChange(of: input) { _, _ in approveWhenTyped() }
            switch feedback {
            case .neutral:
                VStack(spacing: DL.Space.s) {
                    // ONE primary action: empty input reveals, typed input checks.
                    Button {
                        if input.isBlankAnswer {
                            DLSound.reveal()
                            // why: the field stays empty — the card is where the
                            // answer stands, and typing it in for the learner
                            // would put the same word on screen twice.
                            withAnimation { feedback = .revealed }
                        } else {
                            submit()
                        }
                    } label: {
                        Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                            .frame(maxWidth: .infinity)
                            .contentTransition(.opacity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    .keyboardShortcut(.defaultAction)
                    .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
                }
            case .almost:
                // A typo pauses — the box above spells the word out; this only
                // waits for the tap that books it amber.
                Button {
                    advance(correct: true, segment: .tough)
                } label: {
                    Text("common.next")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .transition(.opacity)
            case .correct:
                // A clean answer auto-advances after ~1.2 s.
                if screenReaderOn {
                    // why: the timer never arms under VoiceOver/Switch Control —
                    // without this the clean-correct branch offers nothing to tap.
                    Button {
                        advance(correct: true, segment: hintUsed ? .tough : .right)
                    } label: {
                        Text("common.next")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    .keyboardShortcut(.defaultAction)
                    .transition(.opacity)
                } else {
                    EmptyView()
                }
            case .revealed:
                // why: no "Wusste ich" here — drills are generated, so
                // self-reporting after seeing the answer proves nothing;
                // revealed simply counts as a miss and moves on.
                VStack(spacing: DL.Space.s) {
                    Button {
                        advance(correct: false)
                    } label: {
                        Text("common.next")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(DLPrimaryButtonStyle())
                    // why: Enter advances when revealed (hardware keyboards).
                    .keyboardShortcut(.defaultAction)
                    if missRun >= 1 { DrillStopOffer { closeRun() } }
                }
            }
            if opensReference {
                lookupButton
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// The whole numbers page, one tap away mid-run — the overview's table, not
    /// a second, smaller truth beside it. Outside the feedback switch because a
    /// miss is exactly when a learner wants to look the word up.
    private var lookupButton: some View {
        Button {
            // why: a look-up while the answer is still owed costs the rung, the
            // way the tens list always did — the task books amber. Once the
            // answer is in, nothing is owed and reading is free.
            if case .neutral = feedback { hintUsed = true }
            showingReference = true
        } label: {
            Label("trainer.lookup", systemImage: "questionmark.circle")
                .font(DL.Fonts.caption)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.dlTextSecondary)
    }

}
