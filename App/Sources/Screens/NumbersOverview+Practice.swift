import SwiftUI
import SprossKern

/// The choosing half of the numbers overview: which exercises a run asks, how it
/// is played, and the button that starts it. State lives on NumbersOverview;
/// split out purely for file size.
extension NumbersOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            heading("trainer.overview.practice")
            VStack(alignment: .leading, spacing: Theme.spacing.sm) {
                ForEach(offered, id: \.self) { exerciseRow($0) }
                if !combining {
                    Text("numbers.combine.locked")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, Theme.spacing.md)
                }
            }
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                ForEach(DrillModifier.allCases, id: \.self) { modifierRow($0) }
            }
            .padding(Theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
            )
            startButton
        }
    }

    /// What this pair can be asked at all — kern's registry rule, not the
    /// ladder. A language with no forms reading and a pair the catalog realizes
    /// no frame for have nothing to unlock, so their rows are absent rather than
    /// locked: a padlock is a promise, and one that can never open is a lie.
    private var offered: [NumbersExercise] {
        DrillSelection.shared.offered(language: language, phrasesRealized: phraseDrill != nil)
    }

    // MARK: - What a run asks

    /// Mixing several exercises into one run is itself earned: while any offered
    /// exercise is still locked the list is a radio — one exercise at a time —
    /// and it turns into checkboxes only once the ladder is fully open. A learner
    /// who has just met the clock is asked to climb it, not to dilute it.
    var combining: Bool { DrillSelection.shared.combining(offered: offered, progress: ladder) }

    private func exerciseRow(_ exercise: NumbersExercise) -> some View {
        let open = unlocked(exercise)
        return SelectionRow(
            title: Text(verbatim: "\(numbersExerciseEmoji(exercise: exercise)) ") + Text(exercise.trainerTitleKey),
            caption: open ? bestCaption(exercise)
                          : unlockCaption(DrillUnlocks.shared.requirements(exercise: exercise)),
            mark: open ? (combining ? .many : .one) : .locked,
            selected: open && picked.contains(exercise)
        ) {
            // why: while the ladder is closed the picks are a radio that never
            // empties — `Los` would otherwise have nothing to open. Kern's rule.
            picked = DrillSelection.shared.toggled(picked: picked, tapped: exercise,
                                                   combining: combining)
        }
    }

    /// Keeps the picks answerable by the list as it now stands: one of them
    /// while the ladder is a radio, and never one whose row is a padlock.
    /// Called whenever the ladder is (re)read — a run can open a Sprosse, and a
    /// screenshot seed can hand the page a ladder the picks predate.
    func normalizePicks() {
        picked = DrillSelection.shared.normalized(picked: picked, offered: offered,
                                                  progress: ladder)
    }

    // MARK: - How it is played

    /// A switch with a line under it saying what it does — the settings pattern,
    /// because a modifier changes the whole run rather than adding to what it asks.
    /// A locked one keeps its switch, dimmed, and swaps the line for its price.
    private func modifierRow(_ modifier: DrillModifier) -> some View {
        let open = unlocked(modifier)
        return VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Toggle(isOn: binding(modifier)) {
                HStack(spacing: Theme.spacing.sm) {
                    if !open {
                        Image(systemName: "lock.fill")
                            .font(Theme.typography.caption)
                            .foregroundStyle(Theme.colors.textSecondary)
                    }
                    Text(modifier.trainerTitleKey)
                        .font(Theme.typography.headline)
                        .foregroundStyle(open ? Theme.colors.textPrimary : Theme.colors.textSecondary)
                }
            }
            .tint(Theme.colors.accent)
            .disabled(!open)
            (open ? Text(modifier.trainerHintKey)
                  : unlockCaption(DrillUnlocks.shared.requirements(modifier: modifier)))
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func binding(_ modifier: DrillModifier) -> Binding<Bool> {
        Binding(get: { modifiers.contains(modifier) },
                set: { on in
                    if on { modifiers.insert(modifier) } else { modifiers.remove(modifier) }
                })
    }

    // MARK: - The ladder, as a sentence

    /// Every Sprosse a locked row costs, straight out of kern's table — never a
    /// price authored beside it, which would go stale the day the table moves.
    /// Numbers counts DIGITS and its wording already wears the drill's face, so
    /// it prints as the length it is and the other exercises name themselves.
    private func unlockCaption(_ required: [NumbersExercise: KotlinInt]) -> Text {
        let parts: [Text] = NumbersExercise.allCases.compactMap { exercise in
            guard let level = required[exercise].map({ Int(truncating: $0) }) else { return nil }
            guard exercise != .counting else { return Text("numbers.sprosse \(level)") }
            return Text(verbatim: "\(numbersExerciseEmoji(exercise: exercise)) ") + Text(exercise.trainerTitleKey)
                + Text(verbatim: " ") + Text("trainer.sprosse \(level.formatted())")
        }
        guard let priced = parts.joined() else { return Text("numbers.unlock") }
        return Text("numbers.unlock") + Text(verbatim: " ") + priced
    }

    /// How far this exercise has ever climbed, under its name — the record the
    /// atlas and the calendar print under their ladder, said per exercise here
    /// because each one climbs its own. Numbers counts DIGITS, exactly as its
    /// price does; nothing shows until a run has booked a Sprosse.
    private func bestCaption(_ exercise: NumbersExercise) -> Text? {
        guard let sprosse = progress[exercise], sprosse > 0 else { return nil }
        return Text("numbers.best") + Text(verbatim: " ")
            + (exercise == .counting ? Text("numbers.sprosse \(sprosse)")
                                   : Text("trainer.sprosse \(sprosse.formatted())"))
    }

    /// The ladder as kern wants to read it — one conversion, not one per row.
    private var ladder: [NumbersExercise: KotlinInt] {
        progress.mapValues { KotlinInt(int: Int32($0)) }
    }

    func unlocked(_ exercise: NumbersExercise) -> Bool {
        DrillUnlocks.shared.unlocked(exercise: exercise, progress: ladder)
    }

    func unlocked(_ modifier: DrillModifier) -> Bool {
        DrillUnlocks.shared.unlocked(modifier: modifier, progress: ladder)
    }

    // MARK: - Los

    private var startButton: some View {
        Button {
            start()
        } label: {
            Text("trainer.overview.start")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(picked.isEmpty)
    }
}
