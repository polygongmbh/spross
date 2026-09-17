import SwiftUI

/// A page of reading matter: rows a learner runs their eye down and taps to hear,
/// gathered into titled panels. The numbers table, the atlas and the calendar are
/// this one sheet with three sets of rows.
///
/// The sheet owns the ASKING side of the page — the heading, the tap hint and
/// whether it is offered at all, the panels and their names, and the fact that one
/// row is one stop that says its own learned side. What stands INSIDE a row is the
/// caller's, because that is the only place the three differ: a flag and two
/// columns, a name and the forms under it, a numeral and its reading.
///
/// [speak] decides the hint as well as the rows: where the device can say nothing
/// on the page, the hint would promise a gesture that does nothing.
struct ReferenceSheet<Row, Content: View>: View {
    /// The page's own heading, where the sheet carries one. The numbers table is
    /// shown under the overview's heading and again in a sheet of its own, so it
    /// supplies none and lets each door title it.
    var heading: LocalizedStringKey?
    let groups: [ReferenceGroup<Row>]
    /// Saying one row in the language being learned — nil where the device can
    /// neither play nor speak it.
    let speak: (Row) -> (() -> Void)?
    /// A group's rows as they stand inside the panel. `ReferenceRows` is what a
    /// plain column of them looks like; the numbers table pairs its columns.
    @ViewBuilder let panel: ([Row]) -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            if let heading {
                DrillHeading(heading)
            }
            if groups.contains(where: audible) {
                ReferenceTapHint()
            }
            ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                VStack(alignment: .leading, spacing: Theme.spacing.sm) {
                    if let title = group.title {
                        Text(title)
                            .font(Theme.typography.caption)
                            .foregroundStyle(Theme.colors.textSecondary)
                            .textCase(.uppercase)
                            .accessibilityAddTraits(.isHeader)
                    }
                    panel(group.rows)
                        .panelSurface()
                }
            }
        }
    }

    private func audible(_ group: ReferenceGroup<Row>) -> Bool {
        group.rows.contains { speak($0) != nil }
    }
}

extension ReferenceSheet {
    /// The plain sheet: one tappable row per entry, read straight down its panel.
    init<RowView: View>(heading: LocalizedStringKey? = nil,
                        groups: [ReferenceGroup<Row>],
                        speak: @escaping (Row) -> (() -> Void)?,
                        @ViewBuilder row: @escaping (Row) -> RowView)
    where Content == ReferenceRows<Row, RowView> {
        self.init(heading: heading, groups: groups, speak: speak,
                  panel: { ReferenceRows(rows: $0, speak: speak, row: row) })
    }
}

/// One panel of a reference sheet: the rows standing in it, and what the block is
/// called. A group goes unnamed where the sheet has only one kind of row, or where
/// this build has no wording for a band kern has just grown.
struct ReferenceGroup<Row> {
    var title: LocalizedStringKey?
    let rows: [Row]
}

/// A panel's rows, one under the other, each saying its own learned side.
///
/// The WHOLE row is the target, and nothing on the row says so: reading matter is
/// read by running down it, and a glyph to aim at is a detour per line. The sheet's
/// hint discloses the gesture once, for the page.
struct ReferenceRows<Row, RowView: View>: View {
    let rows: [Row]
    /// How far apart the lines stand. A band of counting words is read at a glance
    /// and sits tighter than a table of two-column entries.
    var spacing: CGFloat = Theme.spacing.lg
    let speak: (Row) -> (() -> Void)?
    @ViewBuilder let row: (Row) -> RowView

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, entry in
                row(entry)
                    // why: one row is one VoiceOver stop — both sides of the table
                    // are the same entry, not two the reader has to pair up by ear.
                    .accessibilityElement(children: .combine)
                    .pronounceOnTap(speak(entry))
            }
        }
    }
}

/// What tells a reference page's reader that the rows sound: on a page of reading
/// matter the content is the control, so the gesture is disclosed once for the
/// whole page instead of by a glyph on every row. Drawn by the sheets and the box,
/// and only where the device can say the language.
///
/// Silent to VoiceOver: every row already offers hearing it as an action, and a
/// line that exists to be seen is noise when it is read out.
struct ReferenceTapHint: View {
    /// Defaults to the reference tables' own wording; the box names its rows
    /// "words" rather than a table's, so it passes its own key.
    var textKey: LocalizedStringKey = "trainer.reference.tapToHear"

    var body: some View {
        HStack(spacing: Theme.spacing.sm) {
            Image(systemName: "speaker.wave.2.fill")
            Text(textKey)
        }
        .font(Theme.typography.caption)
        .foregroundStyle(Theme.colors.textSecondary)
        .accessibilityHidden(true)
    }
}

/// The authored prose beside a generated table: two to four lines the catalog
/// carries per language, picked for the reader — kern falls back to English where
/// their own language carries no wording.
///
/// A generated table can say nothing its rows do not carry, so how the language
/// ASSEMBLES what it counts, and what trips a learner up doing it, is written
/// rather than derived.
struct ReferenceNotes: View {
    let lines: [String]

    @ViewBuilder
    var body: some View {
        if !lines.isEmpty {
            VStack(alignment: .leading, spacing: Theme.spacing.lg) {
                DrillHeading("common.notes")
                VStack(alignment: .leading, spacing: Theme.spacing.md) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        HStack(alignment: .firstTextBaseline, spacing: Theme.spacing.sm) {
                            Text(verbatim: "·")
                                .foregroundStyle(Theme.colors.textSecondary)
                                .accessibilityHidden(true)
                            Text(verbatim: line)
                                .font(Theme.typography.subheadline)
                                .foregroundStyle(Theme.colors.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .panelSurface()
            }
        }
    }
}
