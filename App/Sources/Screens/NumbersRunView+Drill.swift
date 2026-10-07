import SwiftUI
import SprossKern

/// Drill screen content of NumbersRunView — everything it shows is read
/// straight off `run`, and every control it offers dispatches an intent. State
/// lives on NumbersRunView; split out purely for file size.
extension NumbersRunView {

    var drillContent: some View {
        ScrollView {
            VStack(spacing: Theme.spacing.md) {
                // ZStack so outgoing and incoming prompt overlap during the
                // flip; .id gives each run position its own view identity.
                ZStack {
                    QuestionCardView(question: run.question, voice: model?.cardVoice ?? .silent)
                        .id(run.question.key)
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

    @ViewBuilder var streakLine: some View {
        if let deadline {
            // why: a timeline redraws only this line each second.
            TimelineView(.periodic(from: .now, by: 1)) { context in
                scoreLine(timed: timedParts(left: deadline.timeIntervalSince(context.date)))
            }
        } else {
            scoreLine(timed: [])
        }
    }

    private func scoreLine(timed: [Text]) -> some View {
        DrillStreakLine(sprosse: sprosseText, timed: timed, answerStreak: Int(run.answerStreak))
    }

    /// The Sprosse part of the score line, for the exercise that just asked: numbers
    /// count DIGITS, everything else counts plain Sprossen — and an exercise with one
    /// Sprosse shows none. The emoji leads only where the run offers more than one
    /// exercise, since a run that asks one thing has already said what it asks.
    private var sprosseText: Text? {
        guard run.showsSprosse else { return nil }
        let exercise = run.currentExercise
        let sprosse = Int(run.currentSprosse)
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
            AnswerArea(controls: run.controls,
                       text: $input,
                       placeholder: answerPlaceholder(language, digits: run.currentReversed),
                       focus: $answerFocused,
                       correctionVoice: .init(
                           pronounce: { model?.pronounceAction(for: $0, lang: language) },
                           isPlaying: { model?.isPronouncing($0, lang: language) ?? false }),
                       nextLocale: model?.targetChromeLocale,
                       actions: answerActions)
            if run.offersLookUp {
                lookupButton
            }
        }
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
