import SwiftUI
import SprossKern

/// The drilling half of the atlas overview: the nine rungs a run climbs, which
/// way round it asks, how fast it climbs, and the button that starts it. State
/// lives on CountriesOverview; split out purely for file size.
///
/// The RUNGS are not earned. The drill is ungated — the atlas is reading matter,
/// and material a learner may look up on the same page is material they may be
/// asked — so those rows never carry a padlock: they say what a rung ASKS, and
/// the run walks them by itself from rung 1 every time. Each names ONE new
/// thing, because that is all a rung brings (`CountryDrill`).
///
/// Fast is the one thing here with a price, and it is a way of PLAYING rather
/// than something to be asked: it is the reward for having topped the ladder the
/// hard way, so it wears the numbers page's locked-modifier row until then.
extension CountriesOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("trainer.overview.practice")
            VStack(alignment: .leading, spacing: DL.Space.l) {
                ForEach(Self.rungs, id: \.self) { rungRow($0) }
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            pace
            modifierTile.id(CountriesOverview.modifierAnchor)
            startButton
        }
    }

    /// Every rung the ladder has, in the order it is climbed — kern's ceiling,
    /// never a count written down beside it.
    private static var rungs: [Int] {
        Array(1...max(1, CountryDrill.shared.ceiling))
    }

    // MARK: - What a run asks

    /// One rung: the pool it opens and the question it adds. The mark is the
    /// rung's NUMBER, the letters page's rule — these rows are a ladder the run
    /// walks by itself, and a circle beside each one reads as a choice that
    /// never answers the tap.
    private func rungRow(_ rung: Int) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            Image(systemName: "\(rung).circle")
                .font(.title3)
                .foregroundStyle(Color.dlTextSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(Self.title(rung))
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Text(Self.hint(rung))
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

    // why: spelled out rather than interpolated — a key built with `\(rung)`
    // becomes the format string "countries.rung.%lld" and localizes nothing,
    // and these keys would stop being greppable from the catalog.
    private static func title(_ rung: Int) -> LocalizedStringKey {
        switch rung {
        case 1: return "countries.rung.1"
        case 2: return "countries.rung.2"
        case 3: return "countries.rung.3"
        case 4: return "countries.rung.4"
        case 5: return "countries.rung.5"
        case 6: return "countries.rung.6"
        case 7: return "countries.rung.7"
        case 8: return "countries.rung.8"
        default: return "countries.rung.9"
        }
    }

    private static func hint(_ rung: Int) -> LocalizedStringKey {
        switch rung {
        case 1: return "countries.rung.1.hint"
        case 2: return "countries.rung.2.hint"
        case 3: return "countries.rung.3.hint"
        case 4: return "countries.rung.4.hint"
        case 5: return "countries.rung.5.hint"
        case 6: return "countries.rung.6.hint"
        case 7: return "countries.rung.7.hint"
        case 8: return "countries.rung.8.hint"
        default: return "countries.rung.9.hint"
        }
    }

    /// How the ladder is walked, said once instead of marked on every row — and,
    /// where a run has climbed before, how far it came.
    private var pace: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("countries.pace")
            if bestRung > 0 {
                // why: clamped to the ladder's own ceiling. A best booked under
                // an older, shorter ladder means something else now, and one
                // booked under a LONGER one would print a rung that is not
                // there — neither is worth a migration before release, but
                // neither may show the learner a number off the page either.
                Text("countries.best \(min(bestRung, CountryDrill.shared.ceiling).formatted())")
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
            // why: the atlas rung costs THREE clean wins, so the shared
            // "statt zwei" hint would misprice it — this ladder says its own.
            (open ? Text("countries.fast.hint")
                  : Text("numbers.unlock") + Text(verbatim: " ")
                      + Text("trainer.rung \(CountryDrill.shared.ceiling.formatted())"))
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
        return String(format: DLChrome.string("countries.reverse.hint %@ %@", locale: locale),
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
