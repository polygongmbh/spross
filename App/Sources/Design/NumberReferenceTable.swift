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
            VStack(alignment: .leading, spacing: DL.Space.xs) {
                ForEach(Array(section.entries.enumerated()), id: \.offset) { _, entry in
                    row(entry)
                }
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
        }
    }

    /// Value and reading on one line, and on two once the type grows.
    /// `fixedSize` lets a long reading wrap instead of truncating — this is the
    /// page a learner reads the language off.
    private func row(_ entry: ReferenceEntry) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
                value(entry)
                reading(entry)
                Spacer(minLength: 0)
            }
            VStack(alignment: .leading, spacing: 0) {
                value(entry)
                reading(entry)
            }
        }
        .padding(.vertical, 2)
        // why: one row is one VoiceOver stop — "1 000 → eintausend", not two
        // stops that have to be paired by ear.
        .accessibilityElement(children: .combine)
    }

    private func value(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.value)
            .font(DL.Fonts.body)
            .monospacedDigit()
            .foregroundStyle(Color.dlTextSecondary)
    }

    private func reading(_ entry: ReferenceEntry) -> some View {
        Text(verbatim: entry.reading)
            .font(DL.Fonts.headline)
            .foregroundStyle(Color.dlTextPrimary)
            .fixedSize(horizontal: false, vertical: true)
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

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    var body: some View {
        NavigationStack {
            ScrollView {
                NumberReferenceTable(language: language)
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
