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
    /// none, which drops the speaker rather than showing a dead one.
    var voice: DLVoice?

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                band(section)
            }
        }
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
            columns(section.entries, count: columnCount(section.entries))
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
        }
    }

    /// Two columns for a band of short readings, one for everything else — the
    /// counting words are read at a glance, and a page that spends a whole line
    /// on "vier" is a page of scrolling.
    ///
    /// Decided on the LONGEST row rather than measured: a `ViewThatFits` pair
    /// measures a row narrower than it renders (the trailing spacer), which
    /// picks two columns and then wraps "dreizehn" inside one. The bound is the
    /// widest row a phone fits twice at the default type size, and anything
    /// larger — the accessibility sizes included — stays single-column, where a
    /// reading has the whole width to grow into.
    private func columnCount(_ entries: [ReferenceEntry]) -> Int {
        guard typeSize <= .large, entries.count >= 6 else { return 1 }
        let widest = entries.map { $0.value.count + $0.reading.count }.max() ?? 0
        return widest <= 12 ? 2 : 1
    }

    /// A band's rows, split down [count] columns — filled column by column, so
    /// each one still counts upward.
    ///
    /// The speaker glyph rides the single-column bands only: paired columns are
    /// half as wide, and 26 pt of glyph there is 26 pt the reading needs. It
    /// costs those rows nothing — the row itself is what says the word, and the
    /// wide bands above and below carry the glyph that says so.
    private func columns(_ entries: [ReferenceEntry], count: Int) -> some View {
        let perColumn = (entries.count + count - 1) / count
        return HStack(alignment: .top, spacing: DL.Space.xl) {
            ForEach(0..<count, id: \.self) { column in
                VStack(alignment: .leading, spacing: DL.Space.xs) {
                    ForEach(Array(entries.dropFirst(column * perColumn).prefix(perColumn).enumerated()),
                            id: \.offset) { _, entry in
                        row(entry, showsSpeaker: count == 1)
                    }
                }
            }
        }
    }

    /// Value and reading on one line. `fixedSize` lets a reading that outgrows
    /// its column wrap instead of truncating — this is the page a learner reads
    /// the language off.
    ///
    /// The WHOLE row says it, not a glyph at the end of it: a table is read by
    /// running down the readings, and a speaker that has to be aimed at is a
    /// detour per row. The glyph, where it fits, is the sign that there is
    /// something to hear — and the same target it always was for anyone aiming.
    private func row(_ entry: ReferenceEntry, showsSpeaker: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            value(entry)
            reading(entry)
            Spacer(minLength: 0)
            if let speak = speak(entry), showsSpeaker {
                SpeakerIcon(isPlaying: voice?.isPlaying(entry.reading) ?? false, pronounce: speak)
                    // why: the glyph reserves its own size and no more — its 44 pt
                    // target would set the height of every row in the table, and
                    // the row already IS a target that size.
                    .frame(width: 26, height: 22)
                    // why: the row is one VoiceOver element and hearing it is
                    // offered as its action — a target to hunt for inside the
                    // label is not one.
                    .accessibilityHidden(true)
            }
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
