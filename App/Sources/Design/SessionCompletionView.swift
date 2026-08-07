import SwiftUI

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
    var canPracticeMore: Bool = false
    /// Today's recall has fallen far below what the box schedules for
    /// (`TodayReport.recallStrained`). Practising on stays available either
    /// way — this only adds the line saying why stopping is the better call.
    var restSuggested: Bool = false
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

    /// "3 neu · 2 gefestigt · 8 wiederholt" — only the non-zero parts. Built
    /// as `Text` so each part localizes via the environment locale.
    private var summaryText: Text {
        var parts: [Text] = []
        if newCount > 0 { parts.append(Text("session.summary.new \(newCount.formatted())")) }
        if graduatedCount > 0 { parts.append(Text("session.summary.consolidated \(graduatedCount.formatted())")) }
        if reviewCount > 0 { parts.append(Text("session.summary.reviewed \(reviewCount.formatted())")) }
        return parts.joined() ?? Text("session.summary.allDone")
    }

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            Spacer()
            // why: the tree takes the hero slot when the round grew an area —
            // a party popper is the same picture whatever the learner did, and
            // two celebratory graphics on one screen is one too many.
            if grownArea == nil { burstHero } else { grownAreaHero }
            Text("session.finished.title")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
            summaryText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            VStack(spacing: DL.Space.s) {
                StreakFlameView(days: streakDays)
                if streakIsRecord {
                    Text("session.finished.streakRecord")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
            if restSuggested {
                Text("session.finished.restHint")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            SessionExitButtons(onDone: onDone,
                               onPractice: canPracticeMore ? onPractice : nil)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground.ignoresSafeArea())
        .overlay(ConfettiView(run: celebration).ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture(perform: replay)
        // why: after the overlay and the replay gesture, so the corner stays
        // tappable — a tap there leaves instead of setting off the confetti.
        .sessionCloseCorner(label: "common.done", action: onDone)
        .onAppear {
            burst = true
            DLSound.cheer()
        }
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
        DLSound.cheer()
    }

    /// The area the round moved most, as it stood before this round and as it
    /// stands now. The area is LABELLED rather than named in a sentence: the
    /// area did not grow — what the learner can say did — and a sentence that
    /// swallowed "Die Küche" would claim the opposite while reading badly.
    @ViewBuilder
    private var grownAreaHero: some View {
        if let grownArea, !grownArea.after.isBare {
            VStack(spacing: DL.Space.s) {
                GrowingTreeView(transition: grownArea,
                                progress: burst || reduceMotion ? 1 : 0)
                    .frame(height: 190)
                    .animation(reduceMotion ? nil
                                : .spring(response: 1.5, dampingFraction: 0.85).delay(0.25),
                               value: burst)
                VStack(spacing: 2) {
                    Text(growthHeadline)
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Text(verbatim: "\(grownArea.after.emoji) \(grownArea.after.title)")
                        .font(DL.Fonts.caption)
                        .foregroundStyle(Color.dlTextSecondary)
                }
            }
            .accessibilityElement(children: .combine)
        }
    }

    /// What the round did, said about the tree standing above it.
    ///
    /// Read off THE PICTURE — what this area gained — and never off the round's
    /// own tallies. Those are session-wide, so a round that graduated a word in
    /// the bathroom while working mostly in the kitchen printed a blossom line
    /// over a kitchen tree that had gained no blossom. The copy says "hier"; it
    /// must be true of the tree the learner is looking at.
    ///
    /// The subject is always what the learner can say, never the area. The area
    /// did not grow, and it is labelled separately below for that reason — which
    /// is also what gives "hier" something to point at.
    private var growthHeadline: LocalizedStringKey {
        // why: a day the box itself is telling the learner to stop makes no
        // growth claim — a screen that celebrates and is contradicted two lines
        // down teaches the learner not to believe it. The vagueness of that one
        // line is the point: on a bad day the box genuinely cannot say which
        // words survived, and pretending otherwise is what the rest hint exists
        // to prevent.
        guard !restSuggested else { return "session.finished.grew" }
        guard let move = grownArea else { return "session.finished.growth.grown.0" }

        // Ground where there had never been any: the most narratable thing the
        // box does, and it used to read like any other round of new words.
        if move.before.isBare { return "session.finished.growth.opened" }

        // why: the key is built as a STRING and only then wrapped. Interpolating
        // inside `LocalizedStringKey("…\(n)")` takes the string-INTERPOLATION
        // initializer, which makes the key "…%lld" with an argument — it
        // compiles, and renders the raw key at runtime.
        let kind: String
        var variants = 3
        var offset = 0
        let landed = move.after.blossoms + move.after.fruit
        let hadLanded = move.before.blossoms + move.before.fruit
        if landed > hadLanded {
            kind = "blooming"
        } else if move.after.growing > move.before.growing,
                  move.after.canopyCount <= move.before.canopyCount {
            kind = "sown"
        } else {
            kind = "grown"
            // A round that added no mark to the canopy can only honestly claim
            // depth — nothing visibly grew, what was there took a firmer hold.
            // Those are variants 1 and 2; variant 0 says "wächst".
            if move.before.canopyCount == move.after.canopyCount {
                variants = 2
                offset = 1
            }
        }
        let key = "session.finished.growth.\(kind).\(variant(of: variants) + offset)"
        return LocalizedStringKey(key)
    }

    /// A stable choice among `count`.
    ///
    /// Stability is only needed WITHIN one summary, so the streak joins the
    /// round's tallies in the seed: a learner with a steady habit answers the
    /// same shape of round every morning, and on the tallies alone would have
    /// read the very same sentence every day forever.
    private func variant(of count: Int) -> Int {
        let seed = SplitMix64("\(newCount):\(graduatedCount):\(reviewCount):\(streakDays)").seed
        return Int(SplitMix64.mix(seed) % UInt64(count))
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
                    .dlSway(angle: Self.swayAngle(index), period: Self.swayPeriod(index))
            }
            Text(verbatim: "🎉")
                .font(.system(size: 88))
                .scaleEffect(burst ? 1 : 0.4)
                .rotationEffect(.degrees(burst ? 0 : -25))
                .animation(.spring(response: 0.5, dampingFraction: 0.5), value: burst)
                // why: the popper carries the least of it — a big shape rocking
                // as far as a small one reads as the screen itself tilting.
                .dlSway(angle: 3, period: 3.7)
        }
        .frame(height: 180)
        .accessibilityHidden(true) // why: purely celebratory; "session.finished.title" below carries the message
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
