import SwiftUI
import SprossKern

/// The drilling half of the dates overview: the rungs a run climbs, which way
/// round it asks, how fast it climbs, and the button that starts it. State
/// lives on DatesOverview; split out purely for file size.
///
/// The RUNGS are not earned — the atlas page's rule, for the atlas page's
/// reason — so the rows never carry a padlock: they say what a rung ASKS, and
/// the run walks them by itself from rung 1 every time.
///
/// Unlike its siblings the ladder here is not one fixed list: how tall it is
/// depends on what the pair's content carries (no `dateWithYear` pattern, no
/// year rung) and which way round the run asks (reversed, only the name rungs
/// stand). The rows are therefore drawn from kern's own `kinds` per rung, and
/// the panel redraws when the reverse switch below it flips — the page shows
/// exactly the ladder the start button opens.
extension DatesOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("trainer.overview.practice")
            if let content {
                VStack(alignment: .leading, spacing: DL.Space.l) {
                    ForEach(1...ladderCeiling, id: \.self) { rungRow($0, content: content) }
                }
                .padding(DL.Space.l)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(Color.dlSurface)
                )
            }
            pace
            modifierTile.id(DatesOverview.modifierAnchor)
            startButton
        }
    }

    /// This pair's own ceiling, as the switches stand — kern's, never a count
    /// written down beside it.
    private var ladderCeiling: Int {
        content.map { DateDrill.shared.ceiling(content: $0, reverse: reverse) } ?? 1
    }

    // MARK: - What a run asks

    /// One rung: what standing on it is asked. The mark is the rung's NUMBER,
    /// the letters page's rule — these rows are a ladder the run walks by
    /// itself, and a circle beside each one reads as a choice that never
    /// answers the tap.
    private func rungRow(_ rung: Int, content: DateDrillContent) -> some View {
        let kinds = DateDrill.shared.kinds(content: content, level: rung, reverse: reverse)
        return HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            Image(systemName: "\(rung).circle")
                .font(.title3)
                .foregroundStyle(Color.dlTextSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.title(kinds))
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Text(Self.hint(kinds))
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        // why: one rung is one VoiceOver stop — the mark, the name and the line
        // under it describe a single thing.
        .accessibilityElement(children: .combine)
    }

    // The catalog keys are indexed by KIND in full-ladder order — weekday, month,
    // day, day+month, date, date+year — because the ladder itself has no fixed
    // length: a pair without a year pattern skips index 6, and the number on
    // screen is the row's own position. A rung carries every kind below it, so
    // the LAST one is what it introduced and what it is named for.
    // why: spelled out rather than interpolated — a key built with an index
    // becomes a format string and localizes nothing, and these keys would stop
    // being greppable from the catalog.
    private static func title(_ kinds: [DateTaskKind]) -> LocalizedStringKey {
        switch kinds.last {
        case .weekday: return "dates.rung.1"
        case .month: return "dates.rung.2"
        case .dayOfMonth: return "dates.rung.3"
        case .dayAndMonth: return "dates.rung.4"
        case .fullDate: return "dates.rung.5"
        default: return "dates.rung.6"
        }
    }

    private static func hint(_ kinds: [DateTaskKind]) -> LocalizedStringKey {
        switch kinds.last {
        case .weekday: return "dates.rung.1.hint"
        case .month: return "dates.rung.2.hint"
        case .dayOfMonth: return "dates.rung.3.hint"
        case .dayAndMonth: return "dates.rung.4.hint"
        case .fullDate: return "dates.rung.5.hint"
        default: return "dates.rung.6.hint"
        }
    }

    /// How the ladder is walked, said once instead of marked on every row — and,
    /// where a run has climbed before, how far it came.
    private var pace: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("dates.pace")
            if bestRung > 0 {
                // why: clamped to the ladder's own ceiling — a best booked under
                // a longer ladder (the forward one, while reverse is switched
                // on) must never print a rung that is not on the page.
                Text("dates.best \(min(bestRung, ladderCeiling).formatted())")
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

    /// Fast, priced out of kern's own rule: the top rung, stood on once. The
    /// caption follows the numbers page's unlock line rather than authoring a
    /// second price beside the ladder that sets it.
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
            // why: the dates rung costs THREE clean wins, so the shared
            // "statt zwei" hint would misprice it — this ladder says its own.
            (open ? Text("dates.fast.hint")
                  : Text("numbers.unlock") + Text(verbatim: " ")
                      + Text("trainer.rung \(ladderCeiling.formatted())"))
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
        return String(format: DLChrome.string("dates.reverse.hint %@ %@", locale: locale),
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
