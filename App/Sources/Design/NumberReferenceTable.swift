import SwiftUI
import SprossKern

/// How a language counts, as a table: every band kern names, each row a written
/// value beside the reading the drill grades against. Generated, never authored,
/// so the page cannot claim one reading and mark another.
///
/// One component, two doors: the numbers overview stacks it under its own
/// heading, and the in-run "?" opens it in `NumberReferenceSheet`. Kern names
/// the bands; the heading each band gets is chrome and is resolved here.
struct NumberReferenceTable: View {
    /// The language being learned — the one the table describes.
    let language: String
    /// What the page calls the table, where the door it came through titles it.
    var heading: LocalizedStringKey?
    /// How a row is heard. Left off where a surface has nothing to say it with;
    /// the readings are generated and no catalog lists them, so what answers is
    /// almost always the live voice — and nothing at all where the language has
    /// none, which drops the hint rather than promising a sound.
    var voice: Voice?

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        ReferenceSheet(heading: heading,
                       groups: sections.map {
                           ReferenceGroup(title: Self.bandTitle($0.key), rows: $0.entries)
                       },
                       speak: speak,
                       panel: bandPanel)
    }

    /// Empty for a language kern has no pack for: `reference` requires one and
    /// a Kotlin throw crossing back is a crash, so the absence is checked here
    /// rather than trusted of every caller.
    private var sections: [ReferenceSection] {
        guard Numbers.shared.supports(language: language) else { return [] }
        return Numbers.shared.reference(language: language)
    }

    /// The widest row a paired column still fits on one line, in characters,
    /// and the narrowest panel it fits in — the panel's INNER width, inside its padding.
    /// A 375 pt phone leaves 295 pt there and a 320 pt one only 240 pt,
    /// where even a ten-character row wraps mid-word.
    /// Android holds both as `PAIRED_ROW_CHARS` / `PAIRED_MIN_WIDTH` in `NumberReference.kt`;
    /// the two platforms move together.
    private static let pairedRowChars = 10
    private static let pairedMinWidth: CGFloat = 288

    /// A band of short readings stands in two columns where the page is wide enough to hold
    /// them — the counting words are read at a glance,
    /// and a page that spends a whole line on "vier" is a page of scrolling.
    ///
    /// Two tests, because either alone gets it wrong.
    /// The characters are counted rather than measured:
    /// a `ViewThatFits` pair measures a row narrower than it renders (the trailing spacer),
    /// which picks two columns and then wraps "dreizehn" inside one.
    /// But a count knows nothing of how wide the page is,
    /// so the pair is also proposed at [pairedMinWidth] and kept only where that much fits —
    /// a narrower phone, or a sliver of one beside another app, gets the single column.
    /// Anything past the character bound — the accessibility sizes included — stays single-column too,
    /// where a reading has the whole width to grow into.
    private func columnCount(_ entries: [ReferenceEntry]) -> Int {
        guard typeSize <= .large, entries.count >= 6 else { return 1 }
        let widest = entries.map { $0.value.count + $0.reading.count }.max() ?? 0
        return widest <= Self.pairedRowChars ? 2 : 1
    }

    /// The band's rows, paired where they fit. `ViewThatFits` is asked one question only —
    /// is there [pairedMinWidth] to work with — since the pair's own ideal width is
    /// unmeasurable through the rows' trailing spacer.
    @ViewBuilder
    private func bandPanel(_ entries: [ReferenceEntry]) -> some View {
        if columnCount(entries) == 2 {
            ViewThatFits(in: .horizontal) {
                columns(entries, count: 2)
                    .frame(minWidth: Self.pairedMinWidth)
                columns(entries, count: 1)
            }
        } else {
            columns(entries, count: 1)
        }
    }

    /// A band's rows, split down [count] columns — filled column by column, so
    /// each one still counts upward.
    private func columns(_ entries: [ReferenceEntry], count: Int) -> some View {
        let perColumn = (entries.count + count - 1) / count
        return HStack(alignment: .top, spacing: Theme.spacing.xl) {
            ForEach(0..<count, id: \.self) { column in
                ReferenceRows(rows: Array(entries.dropFirst(column * perColumn).prefix(perColumn)),
                              spacing: Theme.spacing.xs, speak: speak, row: row)
            }
        }
    }

    /// Value and reading on one line. `fixedSize` lets a reading that outgrows
    /// its column wrap instead of truncating — this is the page a learner reads
    /// the language off.
    private func row(_ entry: ReferenceEntry) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.spacing.md) {
            value(entry)
            reading(entry)
            // why: the row stretches to its column, so the tap target is the
            // whole line and not just the width of the words on it.
            Spacer(minLength: 0)
        }
    }

    /// Hearing one row — a tap, so it sounds even while reading aloud is off.
    private func speak(_ entry: ReferenceEntry) -> (() -> Void)? {
        voice?.pronounce(entry.reading)
    }

    private func value(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.value)
            .font(Theme.typography.subheadline)
            .monospacedDigit()
            .foregroundStyle(Theme.colors.textSecondary)
    }

    private func reading(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.reading)
            .font(.system(.title3, design: .rounded, weight: .semibold))
            .foregroundStyle(Theme.colors.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
            .spoken(entry.reading, language: language)
    }

    /// Kern's band key → its heading. Spelled out rather than interpolated: a
    /// `LocalizedStringKey` built from a value is a FORMAT with an argument, so
    /// "numbers.section.\(key)" looks up "numbers.section.%@" and prints the raw
    /// key on screen. A band this build has no wording for still gets its rows —
    /// a new band must be able to land in kern first.
    private static func bandTitle(_ key: String) -> LocalizedStringKey? {
        switch key {
        case "base": return "numbers.section.base"
        case "tens": return "numbers.section.tens"
        case "irregulars": return "numbers.section.irregulars"
        case "compounds": return "numbers.section.compounds"
        case "hundreds": return "numbers.section.hundreds"
        case "places": return "numbers.section.places"
        case "forms": return "numbers.section.forms"
        default: return nil
        }
    }
}

/// The table as a surface of its own — what the in-run "?" opens, so a look-up
/// mid-drill lands on exactly the page the overview shows.
struct NumberReferenceSheet: View {
    let language: String
    /// Names the language where no chrome exonym exists for it.
    var catalog: Catalog?
    /// How a row is heard — passed straight through to the table.
    var voice: Voice?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                NumberReferenceTable(language: language, voice: voice)
                    .padding(Theme.spacing.xl)
            }
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle(Text("numbers.title \(languageName)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(Theme.colors.accent)
    }

    private var languageName: String {
        LanguageNames.display(language, catalog: catalog)
    }
}
