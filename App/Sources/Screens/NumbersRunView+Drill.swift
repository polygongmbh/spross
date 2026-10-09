import SwiftUI
import SprossKern

/// Drill screen content of NumbersRunView — everything it shows is read
/// straight off `run`, and every control it offers dispatches an intent. State
/// lives on NumbersRunView; split out purely for file size.
extension NumbersRunView {

    var drillContent: some View {
        questionPage { question in
            QuestionCardView(question: question, voice: model?.cardVoice ?? .silent)
        } area: { controls in
            answerSection(controls)
        }
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
        if clockStart != nil {
            // why: a timeline redraws only this line each second.
            TimelineView(.periodic(from: .now, by: 1)) { context in
                scoreLine(timed: timedParts(left: clockLeft(at: context.date) ?? 0))
            }
        } else {
            scoreLine(timed: [])
        }
    }

    private func scoreLine(timed: [Text]) -> some View {
        // why: a timed run is scored, not streaked — its line holds the clock and the score instead.
        DrillStreakLine(sprosse: sprosseText, timed: timed, answerStreak: run.timed ? nil : Int(run.answerStreak))
    }

    /// The Sprosse part of the score line, worded as kern's `sprosseLine` says.
    private var sprosseText: Text? {
        guard let line = run.sprosseLine else { return nil }
        let sprosse = Int(line.sprosse)
        let text = line.digits ? Text("numbers.sprosse \(sprosse)") : Text("trainer.sprosse \(sprosse.formatted())")
        guard let emoji = line.emoji else { return text }
        return Text(verbatim: "\(emoji) ") + text
    }

    // why: no "Wusste ich" under a reveal here — drills are generated, so
    // self-reporting after seeing the answer proves nothing; revealed simply
    // counts as a miss and moves on.
    private func answerSection(_ controls: AnswerControls) -> some View {
        VStack(spacing: Theme.spacing.md) {
            AnswerArea(driver: self, controls: controls,
                       placeholder: answerPlaceholder(language, digits: run.currentReversed),
                       focus: $answerFocused,
                       correctionVoice: .init(
                           pronounce: { model?.pronounceAction(for: $0, lang: language) },
                           isPlaying: { model?.isPronouncing($0, lang: language) ?? false }),
                       nextLocale: model?.targetChromeLocale)
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
