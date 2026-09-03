import SwiftUI
import SprossKern

/// The reading half of the dates overview: the seven weekdays and the twelve
/// months, both sides beside each other. The page around it is `DrillOverview`,
/// which asks the face for this section and knows nothing of what is in it.
///
/// The table is `DateDrill.reference` — the same joined rows the run grades
/// against, so it cannot claim one name and ask for another. Under a learned
/// name stand the forms the drill also accepts and teaches: its short form,
/// its other lexemes (de `Sonnabend`), and what it becomes inside a date where
/// that differs (uk `березня`).
struct DatesReference: View {
    let model: AppModel
    let content: DateDrillContent
    /// The language the learner KNOWS — the left of every row.
    let source: String
    /// The language being learned — the right of it, and the side a tap says.
    let target: String

    var body: some View {
        let groups = DateDrill.shared.reference(content: content)
        return VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            DrillHeading("dates.reference")
            if groups.contains(where: canBeHeard) {
                ReferenceTapHint()
            }
            ForEach(groups, id: \.kind) { group in
                kindGroup(group)
            }
        }
    }

    /// Whether the device can actually say a group's names — the page must not
    /// offer a sound it has no voice for.
    private func canBeHeard(_ group: DateReferenceGroup) -> Bool {
        group.rows.contains { speak($0.target) != nil }
    }

    private func kindGroup(_ group: DateReferenceGroup) -> some View {
        VStack(alignment: .leading, spacing: Theme.spacing.md) {
            // The Sprosse rows above already name the two pools, so the group
            // headings reuse their words rather than authoring a second pair.
            Text(Self.groupTitle(group.kind))
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                ForEach(Array(group.rows.enumerated()), id: \.offset) { _, row in
                    nameRow(row)
                }
            }
            .padding(Theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                    .fill(Theme.colors.surface)
            )
        }
    }

    /// The reference groups only ever carry the two bare-name pools, and each
    /// wears the Sprosse's own name — the face's table, never a second copy of
    /// the numbering, which is what drifted the day the ladder grew a rung.
    private static func groupTitle(_ kind: DateTaskKind) -> LocalizedStringKey {
        DateDrillFace.sprosseTitle([kind])
    }

    /// One name, twice: the known language on the left, the learned one on the
    /// right, with the learned side's other forms under it.
    ///
    /// A tap says the LEARNED side only: the other column is the reader's own
    /// language, and a reference sheet is read to hear what one cannot yet say.
    /// The whole row is that target, the numbers table's rule — the hint under
    /// the heading is where the page says so.
    private func nameRow(_ row: DateReferenceRow) -> some View {
        HStack(alignment: .top, spacing: Theme.spacing.md) {
            Text(verbatim: row.source)
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
                .spoken(row.source, language: source)
            Spacer(minLength: Theme.spacing.sm)
            VStack(alignment: .trailing, spacing: 2) {
                Text(verbatim: row.target)
                    .font(Theme.typography.headline)
                    .foregroundStyle(Theme.colors.accent)
                    .spoken(row.target, language: target)
                if let under = Self.otherForms(row) {
                    Text(verbatim: under)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                        .spoken(under, language: target)
                }
            }
            .multilineTextAlignment(.trailing)
        }
        .fixedSize(horizontal: false, vertical: true)
        // why: one name is one VoiceOver stop — both sides and the forms under
        // them are the same row of the table.
        .accessibilityElement(children: .combine)
        .pronounceOnTap(speak(row.target))
    }

    /// What else the learned name answers to, on one caption line: the short
    /// form the prompt wears, the other lexemes, and the in-a-date form.
    private static func otherForms(_ row: DateReferenceRow) -> String? {
        var forms: [String] = []
        if let abbr = row.abbr { forms.append(abbr) }
        forms += row.synonyms
        if let dateForm = row.dateForm { forms.append(dateForm) }
        return forms.isEmpty ? nil : forms.joined(separator: " · ")
    }

    /// Hearing a name in the language being learned — nil where the device can
    /// neither play nor say it, so the page offers no sound it cannot make.
    private func speak(_ name: String) -> (() -> Void)? {
        model.pronounceAction(for: name, lang: target)
    }
}
