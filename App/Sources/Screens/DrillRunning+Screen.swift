import SwiftUI
import SprossKern

/// Where the run state is one of kern's own, everything the driver reads off it comes
/// from `DrillRunProgress` — the question and its controls too — and the drill spells none of it out.
extension DrillRunning {

    func questionIndex(_ run: Run) -> Int { Int(run.index) }

    var question: Question? { run.question }

    var controls: AnswerControls? { run.controls }

    var reading: Reading? { run.reading }

    /// The field's text, where the drill has one — the driver clears it itself.
    var answerText: Binding<String> { Binding(get: { input }, set: { input = $0 }) }

    func isFinished(_ run: Run) -> Bool { run.finished }

    var turnFeedback: TurnFeedback { run.feedback }

    /// The question the learner can see — nil while a pause stands in its place,
    /// so what a view does on a question's arrival (autoplay, focus) waits until
    /// the run goes on rather than playing under the pause.
    var shownQuestion: Int? { run.pause == nil ? Int(run.index) : nil }

    /// The run's screen, or kern's pause in its place (`DrillPacing`): Done
    /// closes the run as the ✕ does, keep practicing carries it on. Every drill
    /// screen stands in this, so it is also where each question is read aloud.
    func pausable(_ screen: some View) -> some View {
        Group {
            if run.pause != nil {
                DrillPauseView(run: run, onDone: { closeRun() }, onKeepPracticing: { keepPracticing() })
                    .transition(.opacity)
            } else {
                screen
            }
        }
        // why: the first question, and the one a pause hands back, arrive
        // without a dispatch — this says their prompt as they show.
        .onChange(of: run.reading, initial: true) { _, _ in readAloud() }
    }

    /// The run on its endless chrome — kern's tally as the counter, its outcomes as
    /// the segments — with the pause standing in when kern calls one. `asking` false
    /// is a run that opened with nothing to ask: the page that offered it gates on the
    /// same predicate, so this closes at once rather than showing a screen.
    @ViewBuilder
    func runScreen(asking: Bool = true, showsMuteButton: Bool = false,
                   speaksPastMute: Bool = false,
                   scoreLine: some View,
                   @ViewBuilder content: () -> some View) -> some View {
        if asking {
            pausable(SessionScaffold.endless(tally: run.tally,
                                             outcomes: run.outcomes.map { SessionOutcome($0) },
                                             showsMuteButton: showsMuteButton,
                                             speaksPastMute: speaksPastMute,
                                             scoreLine: scoreLine,
                                             onClose: { closeRun() },
                                             content: content))
        } else {
            Theme.colors.background.ignoresSafeArea().onAppear { dismiss() }
        }
    }
}

#if DEBUG
extension DrillRunning {

    /// The two run-through hooks (UserDefaults launch arguments) every drill
    /// takes, so a screenshot run needs no thumb: `-uitest-streak N` stands a run
    /// mid-streak, and `-uitest-close 1` leaves the way the ✕ leaves, so the tile
    /// the run drops on the page behind it can be photographed.
    ///
    /// Each drill's own hooks sit beside this call, never inside it.
    func uitestDriveRun() {
        let defaults = UserDefaults.standard
        let preset = defaults.integer(forKey: "uitest-streak")
        if preset > 0 { seedAnswerStreak(preset) }
        if defaults.bool(forKey: "uitest-close") {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(400))
                closeRun()
            }
        }
    }
}
#endif

/// The Sprosse a `-uitest-<drill>-level N` launch argument opens a run on, which is
/// how a run-through reaches an outer Sprosse deterministically (kern clamps it);
/// 0 where none is given, and always in a release build.
func uitestOpeningSprosse(_ key: String) -> Int32 {
    #if DEBUG
    Int32(UserDefaults.standard.integer(forKey: key))
    #else
    0
    #endif
}
