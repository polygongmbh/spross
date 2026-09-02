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
        let groups = CountryDrill.shared.reference(content: content)
        return VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            DrillHeading("countries.reference")
            if groups.contains(where: canBeHeard) {
                ReferenceTapHint()
            }
            ForEach(groups, id: \.tier) { group in
                tierGroup(group)
            }
        }
    }

    /// Whether the device can actually say a group's countries — the page must
    /// not offer a sound it has no voice for.
    private func canBeHeard(_ group: CountryReferenceGroup) -> Bool {
        group.rows.contains { speak($0.target) != nil }
    }

    private func tierGroup(_ group: CountryReferenceGroup) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            Text(Self.tierTitle(Int(group.tier)))
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                ForEach(group.rows, id: \.slug) { countryRow($0) }
            }
            .padding(Theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
            )
        }
    }

    /// One country, twice: the known language on the left, the learned one on
    /// the right, each with the people and the language(s) under the name — the
    /// triple the drill asks about, written down in one place.
    ///
    /// A tap says the LEARNED side only: the other column is the reader's own
    /// language, and a reference sheet is read to hear what one cannot yet say.
    /// The whole row is that target, the numbers table's rule — the hint under
    /// the heading is where the page says so.
    private func countryRow(_ row: CountryReferenceRow) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: row.flag)
                .font(.system(size: 28))
                .accessibilityHidden(true)
            side(name: row.source, nationality: row.sourceNationality,
                 languages: row.sourceLanguages, tint: Theme.colors.textPrimary,
                 alignment: .leading, language: source)
            Spacer(minLength: Theme.spacing.sm)
            side(name: row.target, nationality: row.targetNationality,
                 languages: row.targetLanguages, tint: Theme.colors.accent,
                 alignment: .trailing, language: target)
        }
        // why: one country is one VoiceOver stop — both names, the people and
        // the languages are the same row of the table.
        .accessibilityElement(children: .combine)
        .pronounceOnTap(speak(row.target))
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
                .dlSpoken(name, language: language)
            Text(verbatim: people)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
                .dlSpoken(people, language: language)
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
