import SwiftUI
import SprossKern

/// The reading half of the atlas overview: every country the pair joins, both
/// sides beside each other. State lives on CountriesOverview; split out purely
/// for file size.
///
/// The table is `CountryDrill.reference` — the same joined rows the run grades
/// against, grouped by the tier a row enters the ladder at, innermost first. It
/// cannot claim one name and ask for another, because there is nothing for it to
/// drift from.
extension CountriesOverview {

    @ViewBuilder
    var referenceSection: some View {
        if let content {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                heading("countries.reference")
                ForEach(CountryDrill.shared.reference(content: content), id: \.tier) { group in
                    tierGroup(group)
                }
            }
        }
    }

    private func tierGroup(_ group: CountryReferenceGroup) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text(Self.tierTitle(Int(group.tier)))
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: DL.Space.l) {
                ForEach(group.rows, id: \.slug) { countryRow($0) }
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
        }
    }

    /// One country, twice: the known language on the left, the learned one on
    /// the right, each with the people and the language(s) under the name — the
    /// triple the drill asks about, written down in one place.
    private func countryRow(_ row: CountryReferenceRow) -> some View {
        HStack(alignment: .top, spacing: DL.Space.m) {
            Text(verbatim: row.flag)
                .font(.system(size: 28))
                .accessibilityHidden(true)
            side(name: row.source, nationality: row.sourceNationality,
                 languages: row.sourceLanguages, tint: Color.dlTextPrimary,
                 alignment: .leading)
            Spacer(minLength: DL.Space.s)
            side(name: row.target, nationality: row.targetNationality,
                 languages: row.targetLanguages, tint: Color.dlAccent,
                 alignment: .trailing)
        }
        // why: one country is one VoiceOver stop — both names, the people and
        // the languages are the same row of the table.
        .accessibilityElement(children: .combine)
    }

    private func side(name: String, nationality: String, languages: [String],
                      tint: Color, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(verbatim: name)
                .font(DL.Fonts.headline)
                .foregroundStyle(tint)
            Text(verbatim: ([nationality] + languages).joined(separator: " · "))
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
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
