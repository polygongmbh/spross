import SwiftUI
import SprossKern

/// The reading half of the atlas overview: every country the pair joins, both
/// sides beside each other. The page around it is `DrillOverview`, which asks
/// the face for this section and knows nothing of what is in it.
///
/// The table is `CountryDrill.reference` — the same joined rows the run grades
/// against, grouped by the tier a row enters the ladder at, innermost first. It
/// cannot claim one name and ask for another, because there is nothing for it to
/// drift from.
struct CountriesReference: View {
    let model: AppModel
    let content: CountryDrillContent
    /// The language the learner KNOWS — the left of every row.
    let source: String
    /// The language being learned — the right of it, and the side a tap says.
    let target: String

    var body: some View {
        ReferenceSheet(heading: "countries.reference",
                       groups: CountryDrill.shared.reference(content: content).map {
                           ReferenceGroup(title: Self.tierTitle(Int($0.tier)), rows: $0.rows)
                       },
                       speak: { speak($0.target) },
                       row: countryRow)
    }

    /// One country, twice: the known language on the left, the learned one on
    /// the right, each with the people and the language(s) under the name — the
    /// triple the drill asks about, written down in one place.
    ///
    /// A tap says the LEARNED side only: the other column is the reader's own
    /// language, and a reference sheet is read to hear what one cannot yet say.
    private func countryRow(_ row: CountryReferenceRow) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: row.flag)
                .font(.system(size: 28)) // card-parity: a picture, not a type role
                .accessibilityHidden(true)
            side(name: row.source, nationality: row.sourceNationality,
                 languages: row.sourceLanguages, tint: Theme.colors.textPrimary,
                 alignment: .leading, language: source)
            Spacer(minLength: Theme.spacing.sm)
            side(name: row.target, nationality: row.targetNationality,
                 languages: row.targetLanguages, tint: Theme.colors.accent,
                 alignment: .trailing, language: target)
        }
    }

    /// Hearing a country's name in the language being learned — nil where the
    /// device can neither play nor say it, so the page offers no sound it
    /// cannot make.
    private func speak(_ name: String) -> (() -> Void)? {
        model.pronounceAction(for: name, lang: target)
    }

    /// [language] tags the column for VoiceOver, so the learned side is read in
    /// its own voice instead of spelled out in the reader's.
    private func side(name: String, nationality: String, languages: [String],
                      tint: Color, alignment: HorizontalAlignment,
                      language: String) -> some View {
        let people = ([nationality] + languages).joined(separator: " · ")
        return VStack(alignment: alignment, spacing: 2) {
            Text(verbatim: name)
                .font(Theme.typography.headline)
                .foregroundStyle(tint)
                .spoken(name, language: language)
            Text(verbatim: people)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .spoken(people, language: language)
        }
        .multilineTextAlignment(alignment == .trailing ? .trailing : .leading)
        .fixedSize(horizontal: false, vertical: true)
    }

    /// How far from home a group sits. Tier 1 is the profile's own and is
    /// derived per learner, never authored — kern hands the number over already
    /// effective, so this only names it.
    private static func tierTitle(_ tier: Int) -> LocalizedStringKey {
        switch tier {
        case 1: return "countries.tier.1"
        case 2: return "countries.tier.2"
        case 3: return "countries.tier.3"
        default: return "countries.tier.4"
        }
    }
}
