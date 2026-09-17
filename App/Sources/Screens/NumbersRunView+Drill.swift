import SwiftUI
import SprossKern

/// Drill screen content of NumbersRunView — everything it shows is read
/// straight off `run`, and every control it offers dispatches an intent. State
/// lives on NumbersRunView; split out purely for file size.
extension NumbersRunView {

    /// The question on screen. Kern hands back an ordinary task whichever way
    /// round it was drawn, so the card never learns the direction.
    private var current: NumbersTask { run.currentTask }

    /// A prompt made of WORDS is laid out like one — smaller and wrapped — where a
    /// numeral gets the one big line. Asked of the prompt rather than of the run, so
    /// a composed sentence and a reversed reading are both read as what they are.
    private var wordyPrompt: Bool { current.promptDisplay.contains(where: \.isLetter) }

    /// Place word shown the first time a new number length appears — on the
    /// card itself, so the prompts that carry no hint sit exactly as high.
    private var placeValueHint: DrillHint? {
        run.placeValueHint.map { .init(icon: "textformat.123", text: "numbers.newPlace \($0)") }
    }

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                streakLine
                // ZStack so outgoing and incoming prompt overlap during the
                // flip; .id gives each run position its own view identity.
                ZStack {
                    DrillPromptCard(prompt: Text(current.promptDisplay),
                                      size: wordyPrompt ? .sentence : .digits,
                                      answer: current.display,
                                      language: current.language,
                                      gloss: current.gloss,
                                      hint: placeValueHint,
                                      otherWord: run.otherWord.map { ($0.word, $0.meanings.joined(separator: ", ")) },
                                      revealed: run.showsAnswer,
                                      pronounce: model?.pronounceAction(for: current.display, lang: language),
                                      isPlaying: model?.isPronouncing(current.display, lang: language) ?? false)
                        .id(run.index)
                        .transition(reduceMotion ? .opacity : .cardFlip)
                }
                controls
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
        .sheet(isPresented: $showingReference) {
            NumberReferenceSheet(language: language, catalog: catalog, voice: referenceVoice)
        }
    }

    /// How the look-up sheet says a row. nil with no model — a preview run has
    /// nothing to look a voice up with, and the sheet then reads silently.
    private var referenceVoice: Voice? {
        guard let model else { return nil }
        return Voice(pronounce: { model.pronounceAction(for: $0, lang: language) },
                       isPlaying: { model.isPronouncing($0, lang: language) })
    }

    private var streakLine: some View {
        DrillStreakLine(level: levelText, streak: Int(run.streak), bestStreak: Int(run.bestStreak),
                        announcesRecord: true)
    }

    /// The Sprosse part of the score line, for the exercise that just asked: numbers
    /// count DIGITS, everything else counts plain levels — and an exercise with one
    /// Sprosse shows none. The emoji leads only where the run offers more than one
    /// exercise, since a run that asks one thing has already said what it asks.
    private var levelText: Text? {
        guard run.showsSprosse else { return nil }
        let exercise = run.currentExercise
        let sprosse = Int(run.currentLevel)
        guard exercise != .counting else {
            // why: `trainer.digits` is the numbers drill's own wording and already
            // wears 🔢 — putting the exercise's face in front would double it.
            return Text("numbers.sprosse \(sprosse)")
        }
        let text = Text("trainer.sprosse \(sprosse.formatted())")
        guard run.severalExercises else { return text }
        return Text(verbatim: "\(numbersExerciseEmoji(exercise: exercise)) ") + text
    }

    // why: no "Wusste ich" under a reveal here — drills are generated, so
    // self-reporting after seeing the answer proves nothing; revealed simply
    // counts as a miss and moves on.
    private var controls: some View {
        VStack(spacing: Theme.spacing.md) {
            DrillAnswerControls(text: $input,
                                feedback: feedback,
                                placeholder: answerPlaceholder(language, digits: run.currentReversed),
                                focus: $answerFocused,
                                correctionVoice: .init(
                                    pronounce: { model?.pronounceAction(for: $0, lang: language) },
                                    isPlaying: { model?.isPronouncing($0, lang: language) ?? false }),
                                keyboard: run.currentReversed ? .numbersAndPunctuation : .default,
                                onType: { typed() },
                                onSubmit: { submit() },
                                onConfirm: { confirm() },
                                onStop: run.offersFinish ? { closeRun() } : nil)
            if run.offersLookUp {
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
            lookUp()
        } label: {
            Label("numbers.lookup", systemImage: "questionmark.circle")
                .font(Theme.typography.caption)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Theme.colors.textSecondary)
    }
}
