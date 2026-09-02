import SwiftUI
import SprossKern

/// Drill screen content of TrainerSessionView — everything it shows is read
/// straight off `run`, and every control it offers dispatches an intent. State
/// lives on TrainerSessionView; split out purely for file size.
extension TrainerSessionView {

    /// The question on screen. Kern hands back an ordinary task whichever way
    /// round it was drawn, so the card never learns the direction.
    private var current: TrainerTask { run.currentTask }

    /// A prompt made of WORDS is laid out like one — smaller and wrapped — where a
    /// numeral gets the one big line. Asked of the prompt rather than of the run, so
    /// a composed sentence and a reversed reading are both read as what they are.
    private var wordyPrompt: Bool { current.promptDisplay.contains(where: \.isLetter) }

    /// Place word shown the first time a new number length appears — on the
    /// card itself, so the prompts that carry no hint sit exactly as high.
    private var placeValueHint: TrainerPromptCard.Hint? {
        run.placeValueHint.map { .init(icon: "textformat.123", text: "numbers.newPlace \($0)") }
    }

    var drillContent: some View {
        ScrollView {
            VStack(spacing: DL.Space.m) {
                streakLine
                // ZStack so outgoing and incoming prompt overlap during the
                // flip; .id gives each run position its own view identity.
                ZStack {
                    TrainerPromptCard(task: current, sentence: wordyPrompt,
                                      hint: placeValueHint,
                                      otherWord: run.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                                      revealed: run.showsAnswer,
                                      pronounce: model?.pronounceAction(for: current.display, lang: language),
                                      isPlaying: model?.isPronouncing(current.display, lang: language) ?? false)
                        .id(run.index)
                        .transition(reduceMotion ? .opacity : .dlCardFlip)
                }
                controls
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
        .sheet(isPresented: $showingReference) {
            NumberReferenceSheet(language: language, catalog: catalog, voice: referenceVoice)
        }
    }

    /// How the look-up sheet says a row. nil with no model — a preview run has
    /// nothing to look a voice up with, and the sheet then reads silently.
    private var referenceVoice: DLVoice? {
        guard let model else { return nil }
        return DLVoice(pronounce: { model.pronounceAction(for: $0, lang: language) },
                       isPlaying: { model.isPronouncing($0, lang: language) })
    }

    private var streakLine: some View {
        DrillStreakLine(level: levelText, streak: Int(run.streak), bestStreak: Int(run.bestStreak),
                        announcesRecord: true)
    }

    /// The Sprosse part of the score line, for the variant that just asked: numbers
    /// count DIGITS, everything else counts plain levels — and a variant with one
    /// Sprosse shows none. The emoji leads only where the run offers more than one
    /// variant, since a run that asks one thing has already said what it asks.
    private var levelText: Text? {
        guard run.showsSprosse else { return nil }
        let variant = run.currentVariant
        let sprosse = Int(run.currentLevel)
        guard variant != .numbers else {
            // why: `trainer.digits` is the numbers drill's own wording and already
            // wears 🔢 — putting the variant's face in front would double it.
            return Text("numbers.sprosse \(sprosse)")
        }
        let text = Text("trainer.sprosse \(sprosse.formatted())")
        guard run.severalVariants else { return text }
        return Text(verbatim: "\(drillVariantEmoji(variant: variant)) ") + text
    }

    /// What the field asks for. Naming the language is right only while the
    /// answer is words — a reversed task wants the value written out, and
    /// "Auf Swahili …" over a number pad asks for the wrong thing.
    private var fieldPlaceholder: String {
        run.currentReversed
            ? DLChrome.string("numbers.answer.placeholder", locale: locale)
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
                            keyboard: run.currentReversed ? .numbersAndPunctuation : .default) {
                submit()
            }
            .onChange(of: input) { _, _ in typed() }
            switch feedback {
            case .neutral:
                // ONE primary action: empty input reveals, typed input checks.
                Button {
                    checkOrReveal()
                } label: {
                    Text(input.isBlankAnswer ? "session.reveal" : "common.check")
                        .frame(maxWidth: .infinity)
                        .contentTransition(.opacity)
                }
                .buttonStyle(DLPrimaryButtonStyle())
                .keyboardShortcut(.defaultAction)
                .animation(.easeOut(duration: 0.15), value: input.isBlankAnswer)
            case .almost:
                // A typo pauses — the box above spells the word out; this only
                // waits for the tap that books it amber.
                nextButton
                    .transition(.opacity)
            case .correct:
                // A clean answer auto-advances on kern's beat. Under a screen
                // reader the timer never arms, so the branch offers the tap.
                if screenReaderOn {
                    nextButton
                        .transition(.opacity)
                }
            case .revealed:
                // why: no "Wusste ich" here — drills are generated, so
                // self-reporting after seeing the answer proves nothing;
                // revealed simply counts as a miss and moves on.
                VStack(spacing: DL.Space.s) {
                    nextButton
                    if run.offersFinish { DrillStopOffer { closeRun() } }
                }
            }
            if run.offersLookUp {
                lookupButton
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// The one button that books whatever the feedback already said — kern
    /// decides what that is, so every branch reaching for it says the same word.
    private var nextButton: some View {
        Button {
            dispatch(TrainerIntent.ConfirmPending.shared)
        } label: {
            Text("common.next")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        // why: Enter advances here too (hardware keyboards).
        .keyboardShortcut(.defaultAction)
    }

    /// The whole numbers page, one tap away mid-run — the overview's table, not
    /// a second, smaller truth beside it. Outside the feedback switch because a
    /// miss is exactly when a learner wants to look the word up.
    private var lookupButton: some View {
        Button {
            lookUp()
        } label: {
            Label("numbers.lookup", systemImage: "questionmark.circle")
                .font(DL.Fonts.caption)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.dlTextSecondary)
    }
}
