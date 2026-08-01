import SwiftUI
import SprossKern

/// The alphabet of the language being learned, one card per row of
/// `catalog/alphabet/<lang>.json`: the glyph with its capital, the letter's
/// own name, its IPA, when it takes that value, what it sounds like, and an
/// example word. Reading matter, not a drill — nothing here is graded, and
/// nothing is spoken yet.
///
/// Rows are whatever the file holds, in authored order: single letters,
/// digraphs (`sch`), the same glyph twice under different ids (`ch-ich` /
/// `ch-ach`), and prose `rule` rows that state an orthography rule rather
/// than a grapheme.
///
/// Teaching aids follow the READER, with one fallback rule for both maps:
/// `hints[source] ?? hints["en"]`, `context[source] ?? context["en"]`. The
/// Ukrainian rows carry en-only hints while their contextual rows carry de+en
/// — a reader of neither must not be handed a hint whose context silently
/// vanished.
struct AlphabetSheetView: View {
    let model: AppModel
    /// Which alphabet — the language being learned, never the reader's.
    let language: String

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: DL.Space.m) {
                    ForEach(entries) { entry in
                        row(entry)
                    }
                }
                .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle(Text("alphabet.title \(languageName)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(.dlAccent)
    }

    // MARK: - One entry

    private func row(_ entry: AlphabetEntry) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            header(entry)
            if let context = reader(entry.context) {
                Text(verbatim: context)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            if let hint = reader(entry.hints) {
                Text(verbatim: hint)
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            example(entry)
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        // why: one row is one VoiceOver stop, read in the order it stands —
        // glyph, name, context, hint, example. Thirty-five separate elements
        // to swipe through is not a reference sheet.
        .accessibilityElement(children: .combine)
    }

    /// Glyph and capital large, the name beside it, the IPA trailing.
    private func header(_ entry: AlphabetEntry) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.m) {
            Text(verbatim: glyphs(entry))
                .font(glyphFont(entry))
                .foregroundStyle(Color.dlTextPrimary)
            if let name = entry.name {
                Text(verbatim: name)
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            Spacer(minLength: 0)
            if let ipa = entry.ipa {
                Text(verbatim: "[\(ipa)]")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                    // why: phonetic symbols read out by a chrome-language voice
                    // are noise — the hint says the same thing in words.
                    .accessibilityHidden(true)
            }
        }
    }

    /// "А а" where a case pair exists, else the glyph alone.
    private func glyphs(_ entry: AlphabetEntry) -> String {
        guard let upper = entry.upper else { return entry.glyph }
        return "\(upper) \(entry.glyph)"
    }

    /// A rule row's "glyph" is a list of them (uk's `б д з ж г`) and prose
    /// besides — it takes the heading size, not the display size a single
    /// grapheme is set in.
    private func glyphFont(_ entry: AlphabetEntry) -> Font {
        entry.kind == .rule
            ? DL.Fonts.title
            : .system(size: 34, weight: .bold, design: .rounded)
    }

    /// The example word, in the fallback chain the schema promises: the
    /// alphabet language's OWN realization of the slug — with the concept's
    /// emoji, and its meaning where the reader's language realizes it too —
    /// else the verbatim `exampleText`, else nothing, and the IPA above
    /// carries the row alone.
    @ViewBuilder
    private func example(_ entry: AlphabetEntry) -> some View {
        if let example = model.catalog?.alphabetExample(entry: entry, lang: language) {
            HStack(alignment: .firstTextBaseline, spacing: DL.Space.s) {
                if let emoji = example.emoji {
                    Text(verbatim: emoji)
                        .font(DL.Fonts.body)
                        .accessibilityHidden(true)
                }
                Text(verbatim: example.text)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                if let meaning = meaning(of: example.slug) {
                    Text(verbatim: "· \(meaning)")
                        .font(DL.Fonts.subheadline)
                        .foregroundStyle(Color.dlTextSecondary)
                }
            }
        } else if let text = entry.exampleText {
            Text(verbatim: text)
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
        }
    }

    // MARK: - Catalog reads

    private var entries: [AlphabetEntry] {
        model.catalog?.alphabet(lang: language)?.entries ?? []
    }

    /// What the example MEANS to this reader — null wherever the reader's
    /// language does not realize the concept, and then simply left out. An
    /// alphabet is not a join: the word must stand for everyone.
    private func meaning(of slug: String) -> String? {
        model.catalog?.exampleMeaning(slug: slug, lang: model.sourceLanguage)
    }

    private func reader(_ aid: [String: String]) -> String? {
        aid[model.sourceLanguage] ?? aid["en"]
    }

    private var languageName: String {
        LanguageNames.display(language, locale: locale, catalog: model.catalog)
    }
}
