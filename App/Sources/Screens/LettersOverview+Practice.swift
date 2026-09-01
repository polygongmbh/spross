import SwiftUI
import SprossKern

/// The drilling half of the letters overview: the four stages a run walks
/// through, where this learner's run opens, and the button that starts it.
/// State lives on LettersOverview; split out purely for file size.
///
/// There is no ladder to earn here — the letter drill books no review and keeps
/// no record (D12). What the rows say instead is what the run will be: the
/// entry stage comes from the words the learner already holds, and dictation
/// exists only once enough of them can be played back.
extension LettersOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("overview.practice")
            VStack(alignment: .leading, spacing: DL.Space.l) {
                ForEach(Self.stages, id: \.self) { stageRow($0) }
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            startButton
            if !drillAvailable {
                Text("letters.unavailable")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// The run's shape, in the order it is climbed.
    private static var stages: [LetterStage] {
        [.choiceEasy, .choiceConfusable, .typed, .dictation]
    }

    // MARK: - What a run asks

    /// One stage: what it asks, and whether this run will get there. The stage
    /// the run OPENS on is marked and says so — every learner starts somewhere
    /// different, and the page should not make them guess where.
    ///
    /// The mark is the stage's NUMBER, not a circle: these rows are a ladder the
    /// run walks by itself, and an empty circle beside each one reads as a choice
    /// that never answers the tap.
    private func stageRow(_ stage: LetterStage) -> some View {
        let open = reachable(stage)
        let entry = open && stage == entryStage
        let step = (Self.stages.firstIndex(of: stage) ?? 0) + 1
        return HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            Image(systemName: open ? "\(step).circle\(entry ? ".fill" : "")" : "lock.fill")
                .font(.title3)
                .foregroundStyle(entry ? Color.dlAccent : Color.dlTextSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.title(stage))
                    .font(DL.Fonts.headline)
                    .foregroundStyle(open ? Color.dlTextPrimary : Color.dlTextSecondary)
                caption(stage, entry: entry, open: open)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        // why: one stage is one VoiceOver stop — the mark, the name and the line
        // under it describe a single thing.
        .accessibilityElement(children: .combine)
    }

    /// What a stage asks for, and what the entry row says instead. Only
    /// dictation states a price: where the drill cannot run at all, every stage
    /// is out of reach for the one reason the line under the button gives.
    private func caption(_ stage: LetterStage, entry: Bool, open: Bool) -> Text {
        if !open, drillAvailable { return Text("letters.stage.dictation.locked") }
        guard entry else { return Text(Self.hint(stage)) }
        return Text(Self.hint(stage)) + Text(verbatim: " · ") + Text("letters.stage.entry")
    }

    private static func title(_ stage: LetterStage) -> LocalizedStringKey {
        switch stage {
        case .choiceEasy: return "letters.stage.choiceEasy"
        case .choiceConfusable: return "letters.stage.choiceConfusable"
        case .typed: return "letters.stage.typed"
        case .dictation: return "letters.stage.dictation"
        }
    }

    private static func hint(_ stage: LetterStage) -> LocalizedStringKey {
        switch stage {
        case .choiceEasy: return "letters.stage.choiceEasy.hint"
        case .choiceConfusable: return "letters.stage.choiceConfusable.hint"
        case .typed: return "letters.stage.typed.hint"
        case .dictation: return "letters.stage.dictation.hint"
        }
    }

    // MARK: - How far this device reaches

    /// The drill exists where the alphabet does AND this device can actually ask
    /// something: a bundled letter recording, or a voice for the language.
    /// Swahili on the iPhone has neither, so its page is the alphabet alone.
    ///
    /// It does NOT turn on reading aloud being switched on. Hiding a whole
    /// feature behind a one-tap-fixable state is how a feature stops being
    /// found; the drill says so on its own prompt card instead (§6.1).
    var drillAvailable: Bool { availability?.drillAvailable ?? false }

    /// The stage a run would open on, from the rung Kern derives out of the
    /// learner's consolidated words — never recomputed here.
    private var entryStage: LetterStage? { availability?.entryStage }

    /// Dictation needs a pool of playable words the learner already holds; below
    /// that floor the ramp stops one rung short of it, so the row is a padlock
    /// with its price rather than a stage that never arrives.
    private func reachable(_ stage: LetterStage) -> Bool {
        guard let availability, availability.drillAvailable else { return false }
        return stage != .dictation || availability.dictationAvailable
    }

    // MARK: - Los

    private var startButton: some View {
        Button {
            start()
        } label: {
            Text("overview.start")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(!drillAvailable)
    }
}
