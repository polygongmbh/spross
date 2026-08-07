import SwiftUI
import SprossKern

/// The choosing half of the numbers overview: which variants a run asks, how it
/// is played, and the button that starts it. State lives on NumbersOverview;
/// split out purely for file size.
extension NumbersOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("numbers.practice")
            VStack(alignment: .leading, spacing: DL.Space.s) {
                ForEach(offered, id: \.self) { variantRow($0) }
            }
            VStack(alignment: .leading, spacing: DL.Space.l) {
                ForEach(DrillModifier.allCases, id: \.self) { modifierRow($0) }
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            startButton
        }
    }

    /// What this pair can be asked at all — the registry rule, not the ladder.
    /// A language with no forms reading and a pair the catalog realizes no frame
    /// for have nothing to unlock, so their rows are absent rather than locked:
    /// a padlock is a promise, and one that can never open is a lie.
    private var offered: [DrillVariant] {
        DrillVariant.allCases.filter { variant in
            switch variant {
            case .numbers, .clock: return true
            case .phrases: return phraseDrill != nil
            case .forms: return Trainer.shared.supportsForms(language: language)
            }
        }
    }

    // MARK: - What a run asks

    private func variantRow(_ variant: DrillVariant) -> some View {
        let open = unlocked(variant)
        return DLSelectionRow(
            title: Text(verbatim: "\(variant.trainerEmoji) ") + Text(variant.trainerTitleKey),
            caption: open ? nil : unlockCaption(DrillUnlocks.shared.requirements(variant: variant)),
            mark: open ? .many : .locked,
            selected: open && picked.contains(variant)
        ) {
            if picked.contains(variant) {
                picked.remove(variant)
            } else {
                picked.insert(variant)
            }
        }
    }

    // MARK: - How it is played

    /// A switch with a line under it saying what it does — the settings pattern,
    /// because a modifier changes the whole run rather than adding to what it asks.
    /// A locked one keeps its switch, dimmed, and swaps the line for its price.
    private func modifierRow(_ modifier: DrillModifier) -> some View {
        let open = unlocked(modifier)
        return VStack(alignment: .leading, spacing: DL.Space.s) {
            Toggle(isOn: binding(modifier)) {
                HStack(spacing: DL.Space.s) {
                    if !open {
                        Image(systemName: "lock.fill")
                            .font(DL.Fonts.caption)
                            .foregroundStyle(Color.dlTextSecondary)
                    }
                    Text(modifier.trainerTitleKey)
                        .font(DL.Fonts.headline)
                        .foregroundStyle(open ? Color.dlTextPrimary : Color.dlTextSecondary)
                }
            }
            .tint(.dlAccent)
            .disabled(!open)
            (open ? Text(modifier.trainerHintKey)
                  : unlockCaption(DrillUnlocks.shared.requirements(modifier: modifier)))
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
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

    /// Every rung a locked row costs, straight out of kern's table — never a
    /// price authored beside it, which would go stale the day the table moves.
    /// Numbers counts DIGITS and its wording already wears the drill's face, so
    /// it prints as the length it is and the other variants name themselves.
    private func unlockCaption(_ required: [DrillVariant: KotlinInt]) -> Text {
        let parts: [Text] = DrillVariant.allCases.compactMap { variant in
            guard let level = required[variant].map({ Int(truncating: $0) }) else { return nil }
            guard variant != .numbers else { return Text("trainer.digits \(level)") }
            return Text(verbatim: "\(variant.trainerEmoji) ") + Text(variant.trainerTitleKey)
                + Text(verbatim: " ") + Text("trainer.level \(level.formatted())")
        }
        guard let priced = parts.joined() else { return Text("numbers.unlock") }
        return Text("numbers.unlock") + Text(verbatim: " ") + priced
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
            Text("numbers.start")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(picked.isEmpty)
    }
}
