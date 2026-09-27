import SwiftUI
import SprossKern

// MARK: - SessionCompletionView
//
// "Geschafft!" — warm and playful. Confetti falls over the whole screen
// (ConfettiView) while an emoji burst opens under it; the cheer sounds once
// as the screen arrives. Tapping anywhere but the buttons replays all three.

struct SessionCompletionView: View {
    var newCount: Int = 0
    var graduatedCount: Int = 0
    let reviewCount: Int
    let streakDays: Int
    /// Today's run is the longest the box has ever held (`BoxStatistics`), so the
    /// streak is worth naming rather than just counting.
    var streakIsRecord: Bool = false
    /// The area this round worked hardest, as it stood before the round and as
    /// it stands now. The round just moved it, so its tree is the one thing on
    /// this screen about THIS learner's box rather than about having finished.
    var grownArea: TreeTransition?
    /// The area's emoji and name, under the headline.
    var grownAreaLabel: String = ""
    /// What the summary says over the tree (`AppModel.sessionHeadline`).
    var headline: GrowthHeadline?
    var canPracticeMore: Bool = false
    /// Today's recall has fallen far below what the box schedules for
    /// (`TodayReport.recallStrained`). Practicing on stays available either
    /// way — this only adds the line saying why stopping is the better call.
    var restSuggested: Bool = false
    /// Offered only where there is a box to brief (`AppModel.hasBriefing`); the
    /// round just filled it with the words a conversation would be about.
    var onTalk: (() -> Void)?
    var onPractice: () -> Void = {}
    var onDone: () -> Void = {}

    @State private var burst = false
    /// Bumped on every replay; ConfettiView adds a wave per value.
    @State private var celebration = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The ring around the popper — one sign per idea (sprout, star, hands,
    /// sparkle, arm, balloon), so no two read as the same thing at a glance.
    /// The radius keeps them against the popper's own edge: further out they
    /// stop reading as its burst and start floating on their own.
    private static let pieces: [(emoji: String, angle: Double, distance: CGFloat)] = [
        ("🌱", -184, 78), ("⭐️", -146, 84), ("🙌", -110, 88),
        ("✨", -70, 88), ("💪", -34, 84), ("🎈", 4, 78),
    ]

    private static func swayAngle(_ index: Int) -> Double { 5 + Double(index % 3) * 2 }
    private static func swayPeriod(_ index: Int) -> Double { 2.1 + Double(index) * 0.27 }

    /// "3 neu · 2 gefestigt · 8 wiederholt" — which parts a finished round names,
    /// and in which order, is the box's (`completionTallyParts`); the words are
    /// ours. Built as `Text` so each part localizes via the environment locale.
    private var summaryText: Text {
        let parts = completionTallyParts(introduced: Int32(newCount),
                                         consolidated: Int32(graduatedCount),
                                         reviews: Int32(reviewCount))
        return parts.map { Self.partText($0, alone: parts.count == 1) }.joined() ?? Text("session.done.tally.allDone")
    }

    private static func partText(_ part: TallyPart, alone: Bool) -> Text {
        let count = Int(part.count).formatted()
        switch part.kind {
        case .introduced:
            return alone ? Text("session.done.tally.newOnly \(count)") : Text("session.done.tally.new \(count)")
        case .consolidated: return Text("session.done.tally.consolidated \(count)")
        case .reviews: return Text("session.done.tally.reviewed \(count)")
        }
    }

    var body: some View {
        // why: Spacer()-centered content overflows a fixed frame under large
        // Dynamic Type — GrowingTreeView's fixed hero height leaves no give,
        // so the caption below it (restHint) got compressed and truncated
        // instead. A GeometryReader'd min-height keeps the centering when
        // everything fits and falls back to scrolling when it does not.
        GeometryReader { geo in
            ScrollView {
                sessionContent
                    .padding(Theme.spacing.xl)
                    .frame(minWidth: geo.size.width, minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background(Theme.colors.background.ignoresSafeArea())
        .overlay(ConfettiView(run: celebration).ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: replay)
        // why: after the overlay and the replay gesture, so the corner stays
        // tappable — a tap there leaves instead of setting off the confetti.
        .sessionCloseCorner(label: "common.done", action: onDone)
        .onAppear {
            burst = true
            Sound.cheer()
        }
    }

    private var sessionContent: some View {
        VStack(spacing: Theme.spacing.xl) {
            Spacer()
            // why: the tree takes the hero slot when the round grew an area —
            // a party popper is the same picture whatever the learner did, and
            // two celebratory graphics on one screen is one too many.
            if grownArea == nil { burstHero } else { grownAreaHero }
            Text("session.done.title")
                .font(Theme.typography.hero)
                .foregroundStyle(Theme.colors.textPrimary)
            summaryText
                .font(Theme.typography.body)
                .foregroundStyle(Theme.colors.textSecondary)
                .multilineTextAlignment(.center)
            VStack(spacing: Theme.spacing.sm) {
                // why: reaching this screen means a round was just answered, so
                // today has reviews by construction — the flame is lit or nothing.
                StreakFlameView(days: streakDays, flame: .lit)
                if streakIsRecord {
                    Text("session.done.streakRecord")
                        .font(Theme.typography.headline)
                        .foregroundStyle(Theme.colors.accent)
                }
            }
            if restSuggested {
                Text("session.done.restHint")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            // why: the round is over and the words are warm — the one moment a
            // conversation about them costs nothing to offer. It asks rather than
            // instructs, and it sits under the celebration rather than in it: the
            // screen's own answer to "what now" is still Fertig.
            if let onTalk {
                Button("session.done.talk", action: onTalk)
                    .buttonStyle(SoftButtonStyle())
            }
            SessionExitButtons(onDone: onDone,
                               onPractice: canPracticeMore ? onPractice : nil)
        }
        .frame(maxWidth: .infinity)
    }

    /// Snaps the burst back to rest with no animation, then re-triggers it
    /// on the next runloop turn so the spring actually replays.
    private func replay() {
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { burst = false }
        DispatchQueue.main.async {
            burst = true
            celebration += 1
        }
        Sound.cheer()
    }

    /// The area the round moved most, as it stood before this round and as it
    /// stands now. The area is LABELED rather than named in a sentence: the
    /// area did not grow — what the learner can say did — and a sentence that
    /// swallowed "Die Küche" would claim the opposite while reading badly.
    @ViewBuilder
    private var grownAreaHero: some View {
        if let grownArea, !grownArea.after.isBare {
            VStack(spacing: Theme.spacing.sm) {
                GrowingTreeView(transition: grownArea,
                                progress: burst || reduceMotion ? 1 : 0)
                    .frame(height: OrchardLayout.heroHeight(grownArea.after))
                    .animation(reduceMotion ? nil
                                : .spring(response: 1.5, dampingFraction: 0.85).delay(0.25),
                               value: burst)
                VStack(spacing: 2) {
                    Text(headlineKey)
                        .font(Theme.typography.headline)
                        .foregroundStyle(Theme.colors.textPrimary)
                    Text(verbatim: grownAreaLabel)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
            }
            .accessibilityElement(children: .combine)
        }
    }

    /// What the round did, said about the tree standing above it — kern's claim
    /// (`growthHeadline`), which is read off what THIS area gained.
    /// The subject is always what the learner can say, never the area,
    /// which is labeled separately below.
    private var headlineKey: LocalizedStringKey {
        guard let headline else { return "session.done.growth.grown.0" }
        // why: the key is built as a STRING and only then wrapped. Interpolating
        // inside `LocalizedStringKey("…\(n)")` takes the string-INTERPOLATION
        // initializer, which makes the key "…%lld" with an argument — it
        // compiles, and renders the raw key at runtime.
        let pick = Int(headline.pick)
        let key: String
        switch headline.claim {
        case .unclaimed: key = "session.done.growth.grew"
        case .opened: key = "session.done.growth.opened"
        case .matured: key = "session.done.growth.blooming.\(pick % 3)"
        case .met: key = "session.done.growth.sown.\(pick % 3)"
        // Line 0 says the words grew; a round that added none claims only depth.
        case .held: key = "session.done.growth.grown.\(1 + pick % 2)"
        case .grew: key = "session.done.growth.grown.\(pick % 3)"
        }
        return LocalizedStringKey(key)
    }

    private var burstHero: some View {
        ZStack {
            ForEach(Array(Self.pieces.enumerated()), id: \.offset) { index, piece in
                let radians = piece.angle * .pi / 180
                Text(piece.emoji)
                    .font(.title2)
                    .offset(
                        x: burst ? piece.distance * cos(radians) : 0,
                        y: burst ? piece.distance * sin(radians) : 0
                    )
                    .scaleEffect(burst ? 1 : 0.2)
                    .rotationEffect(.degrees(burst ? 0 : index.isMultiple(of: 2) ? -70 : 70))
                    .opacity(burst ? 1 : 0)
                    .animation(
                        .spring(response: 0.6, dampingFraction: 0.6)
                        .delay(0.15 + Double(index) * 0.06),
                        value: burst
                    )
                    .sway(angle: Self.swayAngle(index), period: Self.swayPeriod(index))
            }
            Text(verbatim: "🎉")
                .font(.system(size: 88)) // card-parity: the done screen's own glyph, not a card prompt
                .scaleEffect(burst ? 1 : 0.4)
                .rotationEffect(.degrees(burst ? 0 : -25))
                .animation(.spring(response: 0.5, dampingFraction: 0.5), value: burst)
                // why: the popper carries the least of it — a big shape rocking
                // as far as a small one reads as the screen itself tilting.
                .sway(angle: 3, period: 3.7)
        }
        .frame(height: 180)
        .accessibilityHidden(true) // why: purely celebratory; "session.done.title" below carries the message
    }
}

// MARK: - Previews

#Preview("Completion") {
    SessionCompletionView(reviewCount: 18, streakDays: 7, canPracticeMore: true)
}

#Preview("Completion · dark") {
    SessionCompletionView(reviewCount: 5, streakDays: 1)
        .preferredColorScheme(.dark)
}
