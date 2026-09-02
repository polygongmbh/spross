import SwiftUI
import SprossKern

/// The choosing half of the numbers overview: which variants a run asks, how it
/// is played, and the button that starts it. State lives on NumbersOverview;
/// split out purely for file size.
extension NumbersOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            heading("trainer.overview.practice")
            VStack(alignment: .leading, spacing: Theme.spacing.sm) {
                ForEach(offered, id: \.self) { variantRow($0) }
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
    private var offered: [DrillVariant] {
        DrillSelection.shared.offered(language: language, phrasesRealized: phraseDrill != nil)
    }

    // MARK: - What a run asks

    /// Mixing several exercises into one run is itself earned: while any offered
    /// variant is still locked the list is a radio — one exercise at a time —
    /// and it turns into checkboxes only once the ladder is fully open. A learner
    /// who has just met the clock is asked to climb it, not to dilute it.
    var combining: Bool { DrillSelection.shared.combining(offered: offered, progress: ladder) }

    /// The picks in kern's own order. It hands them back ordered too, so the
    /// same state always collapses the same way on both platforms.
    private var orderedPicks: [DrillVariant] { DrillVariant.allCases.filter(picked.contains) }

    private func variantRow(_ variant: DrillVariant) -> some View {
        let open = unlocked(variant)
        return DLSelectionRow(
            title: Text(verbatim: "\(drillVariantEmoji(variant: variant)) ") + Text(variant.trainerTitleKey),
            caption: open ? bestCaption(variant)
                          : unlockCaption(DrillUnlocks.shared.requirements(variant: variant)),
            mark: open ? (combining ? .many : .one) : .locked,
            selected: open && picked.contains(variant)
        ) {
            // why: while the ladder is closed the picks are a radio that never
            // empties — `Los` would otherwise have nothing to open. Kern's rule.
            picked = Set(DrillSelection.shared.toggled(picked: orderedPicks, tapped: variant,
                                                       combining: combining))
        }
    }

    /// Keeps the picks answerable by the list as it now stands: one of them
    /// while the ladder is a radio, and never one whose row is a padlock.
    /// Called whenever the ladder is (re)read — a run can open a Sprosse, and a
    /// screenshot seed can hand the page a ladder the picks predate.
    func normalizePicks() {
        picked = Set(DrillSelection.shared.normalized(picked: orderedPicks, offered: offered,
                                                      progress: ladder))
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
    /// it prints as the length it is and the other variants name themselves.
    private func unlockCaption(_ required: [DrillVariant: KotlinInt]) -> Text {
        let parts: [Text] = DrillVariant.allCases.compactMap { variant in
            guard let level = required[variant].map({ Int(truncating: $0) }) else { return nil }
            guard variant != .numbers else { return Text("numbers.sprosse \(level)") }
            return Text(verbatim: "\(drillVariantEmoji(variant: variant)) ") + Text(variant.trainerTitleKey)
                + Text(verbatim: " ") + Text("trainer.sprosse \(level.formatted())")
        }
        guard let priced = parts.joined() else { return Text("numbers.unlock") }
        return Text("numbers.unlock") + Text(verbatim: " ") + priced
    }

    /// How far this exercise has ever climbed, under its name — the record the
    /// atlas and the calendar print under their ladder, said per exercise here
    /// because each one climbs its own. Numbers counts DIGITS, exactly as its
    /// price does; nothing shows until a run has booked a Sprosse.
    private func bestCaption(_ variant: DrillVariant) -> Text? {
        guard let sprosse = progress[variant], sprosse > 0 else { return nil }
        return Text("numbers.best") + Text(verbatim: " ")
            + (variant == .numbers ? Text("numbers.sprosse \(sprosse)")
                                   : Text("trainer.sprosse \(sprosse.formatted())"))
    }

    /// The ladder as kern wants to read it — one conversion, not one per row.
    private var ladder: [DrillVariant: KotlinInt] {
        progress.mapValues { KotlinInt(int: Int32($0)) }
    }

    func unlocked(_ variant: DrillVariant) -> Bool {
        DrillUnlocks.shared.unlocked(variant: variant, progress: ladder)
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
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(picked.isEmpty)
    }
}
