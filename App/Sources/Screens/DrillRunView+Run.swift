import SwiftUI
import SprossKern

/// What the shared run driver (`DrillRunning`) needs to drive a typed drill:
/// the face's machine, and the two stores the atlas and the calendar pages read
/// a closed run back out of. State lives on DrillRunView; split out purely for
/// file size, the way the letter drill splits its off.
///
/// Grading itself is kern's, against every form the run accepts (each spelling
/// of each valid answer; on the dates ladder every pattern filling of an
/// assembled date). All this side still owes is the grader — one STRICT drill
/// normalizer for the language the answer is owed in.
extension DrillRunView: DrillRunning {

    var appModel: AppModel? { model }

    // MARK: - The machine under this drill

    func reduce(_ run: Face.Run, _ move: DrillMove) -> DrillStep<Face.Run> {
        Face.reduce(run, move)
    }

    func questionIndex(_ run: Face.Run) -> Int { Face.snapshot(run).index }

    func isFinished(_ run: Face.Run) -> Bool { Face.snapshot(run).finished }

    func typedMove(_ text: String) -> DrillMove? { .typed(text) }

    func submitMove(_ text: String) -> DrillMove? { .submitted(text) }

    var confirmMove: DrillMove { .confirmed }

    var advanceMove: DrillMove { .advanced }

    var keepPracticingMove: DrillMove { .keptPracticing }

    var turnFeedback: TurnFeedback { current.feedback }

    var resultTitle: LocalizedStringKey { Face.resultTitle }

    var voiceModel: AppModel? { model }

    // MARK: - What the learner does beyond the field

    /// A tapped tile: submitted as the text it carries, which is the answer's own
    /// canonical reading, so kern grades it against the same accepted set a
    /// written answer meets.
    func choose(_ name: String) {
        chosen = name
        dispatch(.submitted(name))
    }

    // MARK: - Close → back to the page that opened it


    func closing() -> DrillClose<Face.Run> {
        let closed = Face.close(run, standingRecord: TrainerRecords.best(for: storageKey))
        return DrillClose(run: closed.run, summary: closed.summary, effects: closed.effects, bookings: closed.bookings)
    }

    /// The STRICT drill normalizer, built exactly as the letter drill builds
    /// its own: no article leniency (the material authors its own article, and
    /// its `accepts` are what admit the accusative). Resolved
    /// once, when the run opens, for the language the answer is owed IN.
    @MainActor static func normalizer(model: AppModel, content: Face.Content,
                                      reverse: Bool) -> AnswerNormalizer? {
        let language = Face.answerLanguage(content: content, reverse: reverse)
        return model.languageInfo(language).map { AnswerNormalizer.companion.drill(answerLanguage: $0) }
    }
}

#if DEBUG
extension DrillRunView {

    func seedAnswerStreak(_ answerStreak: Int) {
        run = Face.seedAnswerStreak(run, answerStreak)
    }

    /// Nothing of its own beyond the two hooks every drill takes.
    func uitestStart() {
        uitestDriveRun()
    }
}
#endif
