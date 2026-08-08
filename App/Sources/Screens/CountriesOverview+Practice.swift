import SwiftUI
import SprossKern

/// The drilling half of the atlas overview: the six rungs a run climbs, which
/// way round it asks, and the button that starts it. State lives on
/// CountriesOverview; split out purely for file size.
///
/// There is no ladder to earn here. The drill is ungated — the atlas is reading
/// matter, and material a learner may look up on the same page is material they
/// may be asked — so the rows never carry a padlock: they say what a rung ASKS,
/// and the run walks them by itself from rung 1 every time.
extension CountriesOverview {

    var practiceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("overview.practice")
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
            reverseTile
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

    private static func title(_ rung: Int) -> LocalizedStringKey {
        switch rung {
        case 1: return "countries.rung.1"
        case 2: return "countries.rung.2"
        case 3: return "countries.rung.3"
        case 4: return "countries.rung.4"
        case 5: return "countries.rung.5"
        default: return "countries.rung.6"
        }
    }

    private static func hint(_ rung: Int) -> LocalizedStringKey {
        switch rung {
        case 1: return "countries.rung.1.hint"
        case 2: return "countries.rung.2.hint"
        case 3: return "countries.rung.3.hint"
        case 4: return "countries.rung.4.hint"
        case 5: return "countries.rung.5.hint"
        default: return "countries.rung.6.hint"
        }
    }

    /// How the ladder is walked, said once instead of marked on every row — and,
    /// where a run has climbed before, how far it came.
    private var pace: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("countries.pace")
            if bestRung > 0 {
                Text("countries.best \(bestRung.formatted())")
            }
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - How it is played

    /// The numbers page's modifier row: a switch with a line under it saying
    /// what it does to a run. It names the two languages outright, because
    /// "reversed" alone leaves the learner to work out which side is which.
    private var reverseTile: some View {
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
        .padding(DL.Space.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
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
            Text("overview.start")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(content == nil)
    }
}
