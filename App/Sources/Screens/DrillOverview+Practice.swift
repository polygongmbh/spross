import SwiftUI
import SprossKern

/// The drilling half of a typed drill's overview: the Sprossen a run climbs,
/// which way round it asks, how fast it climbs, and the button that starts it.
/// State lives on DrillOverview; split out purely for file size.
///
/// The RUNGS are not earned. The drills are ungated — the table is reading
/// matter, and material a learner may look up on the same page is material they
/// may be asked — so those rows never carry a padlock: they say what a Sprosse
/// ASKS, and the run walks them by itself from Sprosse 1 every time.
///
/// How tall the ladder is, and what each Sprosse is named, is the face's
/// (`DrillFace.Sprossen`) — the atlas has nine fixed ones, while the dates ladder
/// depends on what the pair's content carries and which way round the run asks.
/// The panel redraws when the reverse switch below it flips, so the page shows
/// exactly the ladder the start button opens.
///
/// Fast is the one thing here with a price, and it is a way of PLAYING rather
/// than something to be asked: it is the reward for having topped the ladder
/// the hard way, so it wears the numbers page's locked-modifier row until then.
extension DrillOverview {

    var practiceSection: some View {
        let sprossen = Face.sprossen(content, reverse: reverse)
        return VStack(alignment: .leading, spacing: DL.Space.l) {
            DrillHeading("trainer.overview.practice")
            if !sprossen.isEmpty {
                VStack(alignment: .leading, spacing: DL.Space.l) {
                    ForEach(Array(sprossen.enumerated()), id: \.offset) { row in
                        sprosseRow(row.offset + 1, row.element)
                    }
                }
                .padding(DL.Space.l)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(Color.dlSurface)
                )
            }
            pace
            modifierTile.id(DrillAnchor.modifiers)
            startButton
        }
    }

    /// This ladder's own ceiling, as the switches stand — kern's, never a count
    /// written down beside it.
    private var ladderCeiling: Int { Face.ceiling(content, reverse: reverse) }

    // MARK: - What a run asks

    /// One Sprosse: the pool it opens and the question it adds. The mark is the
    /// Sprosse's NUMBER, the letters page's rule — these rows are a ladder the run
    /// walks by itself, and a circle beside each one reads as a choice that
    /// never answers the tap.
    private func sprosseRow(_ number: Int, _ sprosse: DrillSprosse) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            Image(systemName: "\(number).circle")
                .font(.title3)
                .foregroundStyle(Color.dlTextSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(sprosse.title)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Text(sprosse.hint)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        // why: one Sprosse is one VoiceOver stop — the mark, the name and the line
        // under it describe a single thing.
        .accessibilityElement(children: .combine)
    }

    /// How the ladder is walked, said once instead of marked on every row — and,
    /// where a run has climbed before, how far it came.
    private var pace: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(Face.paceKey)
            if bestSprosse > 0 {
                // why: printed as it stands, ceiling and all — the Sprosse keeps
                // counting past the named ladder, so the record is a number to
                // beat rather than a row on the page.
                Text(Face.bestLine(bestSprosse))
            }
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - How it is played

    /// How a run is PLAYED, in the numbers page's modifier tile: a switch apiece
    /// with a line under it saying what it does. Reverse is free (nothing here
    /// is bought by asking a question the other way round); fast is earned, and
    /// keeps its switch — dimmed, beside a padlock — with its price where the
    /// line would be, because a ladder you can see is a reason to climb it.
    private var modifierTile: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            VStack(alignment: .leading, spacing: DL.Space.s) {
                Toggle(isOn: $reverse) {
                    Text("trainer.modifier.reverse")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                }
                .tint(.dlAccent)
                Text(verbatim: reverseHint)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            fastRow
        }
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
    }

    /// Fast, priced out of kern's own rule: the top Sprosse, stood on once. The
    /// caption follows the numbers page's unlock line ("Freischalten: Sprosse
    /// 9") rather than authoring a second price beside the ladder that sets it.
    private var fastRow: some View {
        let open = fastUnlocked
        return VStack(alignment: .leading, spacing: DL.Space.s) {
            Toggle(isOn: $fast) {
                HStack(spacing: DL.Space.s) {
                    if !open {
                        Image(systemName: "lock.fill")
                            .font(DL.Fonts.caption)
                            .foregroundStyle(Color.dlTextSecondary)
                    }
                    Text("trainer.modifier.fast")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(open ? Color.dlTextPrimary : Color.dlTextSecondary)
                }
            }
            .tint(.dlAccent)
            .disabled(!open)
            // why: a Sprosse here costs THREE clean wins, so the shared
            // "statt zwei" hint would misprice it — each ladder says its own.
            (open ? Text(Face.fastHintKey)
                  : Text("numbers.unlock") + Text(verbatim: " ")
                      + Text("trainer.sprosse \(ladderCeiling.formatted())"))
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Which side asks and which side answers, as the switch stands right now —
    /// a runtime `%@` pair, so it resolves through `DLChrome`.
    private var reverseHint: String {
        let asked = reverse ? target : source
        let owed = reverse ? source : target
        return String(format: DLChrome.string(Face.reverseHintKey, locale: locale),
                      LanguageNames.display(asked, locale: locale, catalog: model.catalog),
                      LanguageNames.display(owed, locale: locale, catalog: model.catalog))
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
        .disabled(content == nil)
    }
}
