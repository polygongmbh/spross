import SwiftUI
import SprossKern

/// The alphabet of the language being learned, one card per row of
/// `catalog/alphabet/<lang>.json`: the glyph with its capital, the letter's
/// own name, its IPA, when it takes that value, what it sounds like, and an
/// example word — the name and the word each with a speaker beside them.
/// Reading matter, not a drill: nothing here is graded, and nothing plays
/// unasked. Every tap is a request, so it sounds even while reading aloud is
/// switched off — nobody opens a reference sheet by accident.
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
        // why: and because the row is one element, the two speakers inside it
        // are row ACTIONS rather than targets to hunt for inside the label.
        .accessibilityActions {
            if let speak = speakName(entry) {
                Button("alphabet.speakName", action: speak)
            }
            if let speak = speakExample(entry) {
                Button("alphabet.speakExample", action: speak)
            }
        }
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
            if let speak = speakName(entry) {
                speakButton(speak)
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
                Spacer(minLength: 0)
                if let speak = speakExample(entry) {
                    speakButton(speak)
                }
            }
        } else if let text = entry.exampleText {
            HStack(alignment: .firstTextBaseline, spacing: DL.Space.s) {
                Text(verbatim: text)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                Spacer(minLength: 0)
                if let speak = speakExample(entry) {
                    speakButton(speak)
                }
            }
        }
    }

    // MARK: - Hearing a row

    /// The letter's own NAME — «ер», never the bare glyph, which a synthesizer
    /// reads as anything from a spelling alphabet to a pause. The letters pack
    /// answers it where one exists; the voice where it does not.
    private func speakName(_ entry: AlphabetEntry) -> (() -> Void)? {
        guard let name = entry.name else { return nil }
        return play(model.letterPronunciation(name: name,
                                              glyph: entry.glyph.lowercased(),
                                              lang: language))
    }

    /// The example word, under the same provenance rule the drill follows: a
    /// concept's realization may be answered by that concept's recording, an
    /// `exampleText` by nothing — it carries no slug to look one up with.
    private func speakExample(_ entry: AlphabetEntry) -> (() -> Void)? {
        if let example = model.catalog?.alphabetExample(entry: entry, lang: language) {
            return play(model.formPronunciation(example.text, lang: language))
        }
        return entry.exampleText.flatMap { play(model.spokenPronunciation($0, lang: language)) }
    }

    /// A tap that sounds — nil where the device can neither play nor speak the
    /// form, so the speaker is absent rather than dead.
    private func play(_ pronunciation: Pronunciation?) -> (() -> Void)? {
        guard let pronunciation else { return nil }
        let url = model.audioURL(pronunciation.recordingPath)
        guard Pronouncer.shared.canPronounce(pronunciation, recordingURL: url) else { return nil }
        // why: `.tap` — an explicit request is heard even while reading aloud
        // is switched off, exactly as tapping a word on a card is.
        return { Pronouncer.shared.pronounce(pronunciation, recordingURL: url, trigger: .tap) }
    }

    /// Hidden from VoiceOver on purpose: the row is one element, and hearing
    /// it is offered as a row action instead (`accessibilityActions`).
    private func speakButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: "speaker.wave.2.fill")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlAccent)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHidden(true)
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
