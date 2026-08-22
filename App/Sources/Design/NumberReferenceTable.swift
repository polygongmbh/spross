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
    /// How a row is heard. Left off where a surface has nothing to say it with;
    /// the readings are generated and no catalog lists them, so what answers is
    /// almost always the live voice — and nothing at all where the language has
    /// none, which drops the hint rather than promising a sound.
    var voice: DLVoice?

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let bands = sections
        VStack(alignment: .leading, spacing: DL.Space.l) {
            if bands.contains(where: canBeHeard) {
                ReferenceTapHint()
            }
            ForEach(Array(bands.enumerated()), id: \.offset) { _, section in
                band(section)
            }
        }
    }

    /// Whether the device can actually say a band's rows — `DLVoice` hands back
    /// nil where it can neither play nor speak, and a page that stays silent
    /// must not offer to sound.
    private func canBeHeard(_ section: ReferenceSection) -> Bool {
        section.entries.contains { speak($0) != nil }
    }

    /// Empty for a language kern has no pack for: `reference` requires one and
    /// a Kotlin throw crossing back is a crash, so the absence is checked here
    /// rather than trusted of every caller.
    private var sections: [ReferenceSection] {
        guard Trainer.shared.supports(language: language) else { return [] }
        return Trainer.shared.reference(language: language)
    }

    private func band(_ section: ReferenceSection) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            if let title = Self.bandTitle(section.key) {
                Text(title)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    .textCase(.uppercase)
                    .accessibilityAddTraits(.isHeader)
            }
            bandPanel(section)
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
        }
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
    private func bandPanel(_ section: ReferenceSection) -> some View {
        if columnCount(section.entries) == 2 {
            ViewThatFits(in: .horizontal) {
                columns(section.entries, count: 2)
                    .frame(minWidth: Self.pairedMinWidth)
                columns(section.entries, count: 1)
            }
        } else {
            columns(section.entries, count: 1)
        }
    }

    /// A band's rows, split down [count] columns — filled column by column, so
    /// each one still counts upward.
    private func columns(_ entries: [ReferenceEntry], count: Int) -> some View {
        let perColumn = (entries.count + count - 1) / count
        return HStack(alignment: .top, spacing: DL.Space.xl) {
            ForEach(0..<count, id: \.self) { column in
                VStack(alignment: .leading, spacing: DL.Space.xs) {
                    ForEach(Array(entries.dropFirst(column * perColumn).prefix(perColumn).enumerated()),
                            id: \.offset) { _, entry in
                        row(entry)
                    }
                }
            }
        }
    }

    /// Value and reading on one line. `fixedSize` lets a reading that outgrows
    /// its column wrap instead of truncating — this is the page a learner reads
    /// the language off.
    ///
    /// The WHOLE row says it, and nothing on the row says so: a table is read by
    /// running down the readings, and a glyph to aim at is a detour per row. The
    /// hint above the bands discloses the gesture once, for the page.
    private func row(_ entry: ReferenceEntry) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            value(entry)
            reading(entry)
            // why: the row stretches to its column, so the tap target is the
            // whole line and not just the width of the words on it.
            Spacer(minLength: 0)
        }
        // why: one row is one VoiceOver stop — "1 000 → eintausend", not two
        // stops that have to be paired by ear.
        .accessibilityElement(children: .combine)
        .pronounceOnTap(speak(entry))
    }

    /// Hearing one row — a tap, so it sounds even while reading aloud is off.
    private func speak(_ entry: ReferenceEntry) -> (() -> Void)? {
        voice?.pronounce(entry.reading)
    }

    private func value(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.value)
            .font(DL.Fonts.subheadline)
            .monospacedDigit()
            .foregroundStyle(Color.dlTextSecondary)
    }

    private func reading(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.reading)
            .font(.system(.title3, design: .rounded, weight: .semibold))
            .foregroundStyle(Color.dlTextPrimary)
            .fixedSize(horizontal: false, vertical: true)
            .dlSpoken(entry.reading, language: language)
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

/// What tells a reference page's reader that the rows sound: on a page of
/// reading matter the content is the control, so the gesture is disclosed once
/// for the whole page instead of by a glyph on every row. Drawn by the numbers
/// table, the atlas and the box, and only where the device can say the language.
///
/// Silent to VoiceOver: every row already offers hearing it as an action, and a
/// line that exists to be seen is noise when it is read out.
struct ReferenceTapHint: View {
    /// Defaults to the reference tables' own wording; the box names its rows
    /// "words" rather than a table's, so it passes its own key.
    var textKey: LocalizedStringKey = "reference.tapToHear"

    var body: some View {
        HStack(spacing: DL.Space.s) {
            Image(systemName: "speaker.wave.2.fill")
            Text(textKey)
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
        .accessibilityHidden(true)
    }
}

/// The table as a surface of its own — what the in-run "?" opens, so a look-up
/// mid-drill lands on exactly the page the overview shows.
struct NumberReferenceSheet: View {
    let language: String
    /// Names the language where no chrome exonym exists for it.
    var catalog: Catalog?
    /// How a row is heard — passed straight through to the table.
    var voice: DLVoice?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    var body: some View {
        NavigationStack {
            ScrollView {
                NumberReferenceTable(language: language, voice: voice)
                    .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle(Text("numbers.title \(languageName)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(.dlAccent)
    }

    private var languageName: String {
        LanguageNames.display(language, locale: locale, catalog: catalog)
    }
}
