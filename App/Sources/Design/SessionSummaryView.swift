import SwiftUI
import SprossKern

// MARK: - SessionSummaryView
//
// "Geschafft!" — warm and playful. Confetti falls over the whole screen
// (ConfettiView) while the hero — the grown tree, or the popper — rises under it;
// the cheer sounds once as the screen arrives. Tapping anywhere but the buttons replays all three.

struct SessionSummaryView: View {
    /// The round's answers spelled out (`RoundSummary.parts`).
    var parts: [TallyPart] = []
    /// The area this round worked hardest, as it stood before the round and as
    /// it stands now, where the summary shows its tree (`RoundSummary.shownTree`).
    /// The round just moved it, so its tree is the one thing on
    /// this screen about THIS learner's box rather than about having finished.
    var grownArea: TreeTransition?
    /// The garden its tree grows in (`AppModel.garden`).
    var garden: String = ""
    /// The area's emoji and name, labeling the tree right under it.
    var grownAreaLabel: String = ""
    /// What the summary says over the tree (`RoundSummary.headline`).
    var headline: GrowthHeadline?
    var canPracticeMore: Bool = false
    /// Today's recall has fallen far below what the box schedules for
    /// (`RoundSummary.restSuggested`). Practicing on stays available either
    /// way — this only adds the line saying why stopping is the better call.
    var restSuggested: Bool = false
    /// Offered only where there is a box to brief (`AppModel.hasBriefing`); the
    /// round just filled it with the words a conversation would be about.
    var onTalk: (() -> Void)?
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @State private var risen = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// "3 neu · 8 wiederholt · 2 gefestigt" under the title — which parts a finished
    /// round names, and in which order, is the box's (`RoundSummary.parts`); the
    /// words are ours. Built as `Text` so each part localizes via the environment
    /// locale. Nil when the round named nothing: the title alone then says it is done.
    private var tallyText: Text? {
        parts.map { Self.partText($0, alone: parts.count == 1) }.joined()
    }

    private var showsTree: Bool { grownArea != nil }

    private static func partText(_ part: TallyPart, alone: Bool) -> Text {
        let count = Int(part.count).formatted()
        switch part.kind {
        case .introduced:
            return alone ? Text("session.done.tally.newOnly \(count)") : Text("session.done.tally.new \(count)")
        case .settled: return Text("tally.settled \(Int(part.count))")
        case .reviewed:
            return alone ? Text("session.done.tally.reviewedOnly \(count)") : Text("session.done.tally.reviewed \(count)")
        }
    }

    var body: some View {
        // why: the tree takes the hero slot when the round grew an area —
        // a party popper is the same picture whatever the learner did, and
        // two celebratory graphics on one screen is one too many. One title:
        // the growth claim where a tree stands over it, the plain "All done!"
        // where the popper does.
        SummaryScaffold(title: Text(showsTree ? headlineKey : "session.done.title"),
                        tally: tallyText,
                        hint: restSuggested ? Text("session.done.restHint") : nil,
                        // why: the area is LABELED under its tree rather than named in the
                        // title — the area did not grow, what the learner can say did.
                        heroLabel: showsTree ? Text(verbatim: grownAreaLabel) : nil,
                        onDone: onDone, onTalk: onTalk,
                        onPractice: canPracticeMore ? onPractice : nil) { ceiling, celebration in
            if showsTree {
                grownAreaHero(ceiling: ceiling).onChange(of: celebration) { replayRise() }
            } else {
                SummaryGlyph(glyph: "🎉", celebration: celebration)
            }
        }
        .onAppear { risen = true }
    }

    /// Snaps the tree back to its crouch with no animation, then re-triggers
    /// the rise on the next runloop turn so the spring actually replays.
    private func replayRise() {
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { risen = false }
        DispatchQueue.main.async { risen = true }
    }

    /// The area the round moved most, as it stood before this round and as it stands now.
    @ViewBuilder
    private func grownAreaHero(ceiling: CGFloat) -> some View {
        if let grownArea {
            GrowingTreeView(transition: grownArea, garden: garden,
                            progress: risen || reduceMotion ? 1 : 0)
                .frame(height: AreaTree.shared.heroHeight(tree: grownArea.after, ceiling: ceiling))
                .animation(reduceMotion ? nil
                            : .spring(response: TreeRise.companion.SPRING_RESPONSE,
                                      dampingFraction: TreeRise.companion.SPRING_DAMPING)
                                .delay(Double(TreeRise.companion.DELAY_MILLIS) / 1000),
                           value: risen)
        }
    }

    /// What the round did, said about the tree standing above it — kern's claim
    /// (`growthHeadline`), which is read off what THIS area gained.
    /// The subject is always what the learner can say, never the area,
    /// which is labeled on the line below.
    private var headlineKey: LocalizedStringKey {
        guard let headline else { return "session.done.growth.grown.0" }
        // why: the key is built as a STRING and only then wrapped. Interpolating
        // inside `LocalizedStringKey("…\(n)")` takes the string-INTERPOLATION
        // initializer, which makes the key "…%lld" with an argument — it
        // compiles, and renders the raw key at runtime.
        // Three lines per family in the string table; which one is kern's (`GrowthHeadline.line`).
        let line = Int(headline.line(count: 3))
        let key: String
        switch headline.claim {
        case .unclaimed: key = "session.done.growth.grew"
        case .opened: key = "session.done.growth.opened"
        case .settled: key = "session.done.growth.blooming.\(line)"
        case .met: key = "session.done.growth.sown.\(line)"
        case .held, .grew: key = "session.done.growth.grown.\(line)"
        }
        return LocalizedStringKey(key)
    }
}

// MARK: - Previews

#Preview("Completion") {
    SessionSummaryView(parts: [TallyPart(kind: .reviewed, count: 18)], canPracticeMore: true, onTalk: {})
}

#Preview("Completion · dark") {
    SessionSummaryView(parts: [TallyPart(kind: .reviewed, count: 5)])
        .preferredColorScheme(.dark)
}
