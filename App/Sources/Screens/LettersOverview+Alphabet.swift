import SwiftUI
import SprossKern

/// The alphabet half of the letters overview, one card per row of
/// `catalog/alphabet/<lang>.json`: the glyph with its capital, the letter's own
/// name, its IPA, when it takes that value, what it sounds like, and an example
/// word — the name and the word each with a speaker beside them. Reading matter,
/// not a drill: nothing here is graded, and nothing plays unasked. Every tap is
/// a request, so it sounds even while reading aloud is switched off — nobody
/// opens a reference sheet by accident.
///
/// The name line and the example line each say their own word wherever they are
/// tapped.
/// Their speakers stay, where the reference tables dropped theirs:
/// a row here holds TWO sounds and the prose `rule` rows hold none,
/// so a glyph standing beside a line is what says which line has one.
///
/// Rows are whatever the file holds, in authored order: single letters,
/// digraphs (`sch`), the same glyph twice under different ids (`ch-ich` /
/// `ch-ach`), and prose `rule` rows that state an orthography rule rather than a
/// grapheme. Where the file declares `sections`, they head their runs of rows;
/// where it declares none (uk, whose order IS its alphabet) the table is the
/// flat list it always was.
///
/// Teaching aids follow the READER, with one fallback rule for both maps:
/// `hints[source] ?? hints["en"]`, `context[source] ?? context["en"]`. The
/// Ukrainian rows carry en-only hints while their contextual rows carry de+en —
/// a reader of neither must not be handed a hint whose context silently
/// vanished.
///
/// State lives on LettersOverview; split out purely for file size.
extension LettersOverview {

    @ViewBuilder
    var alphabetSection: some View {
        if alphabet != nil {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                heading("trainer.alphabet")
                VStack(alignment: .leading, spacing: DL.Space.m) {
                    if sections.isEmpty {
                        ForEach(entries) { entry in
                            row(entry)
                        }
                    } else {
                        ForEach(sections, id: \.id) { section in
                            sectionHeading(section)
                            ForEach(alphabet?.entries(of: section.id) ?? []) { entry in
                                row(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - One section

    /// A section title, on the same reader fallback its rows' hints use. Sections are
    /// authored per file: a language whose order IS its alphabet (uk) declares none and
    /// the table stays the flat list it always was.
    @ViewBuilder
    private func sectionHeading(_ section: AlphabetSection) -> some View {
        if let title = reader(section.titles) {
            Text(verbatim: title)
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextSecondary)
                .padding(.top, DL.Space.m)
                .accessibilityAddTraits(.isHeader)
        }
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
            if let name = displayName(entry) {
                Text(verbatim: name)
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextSecondary)
            }
            if let name = entry.name, let speak = speakName(entry) {
                speaker(form: name, speak)
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
        .saysOnTap(speakName(entry))
    }

    /// The name, unless the glyph column already says it: German Ü is NAMED "Ü" and
    /// Ukrainian а is named «а», so printing both makes the row stutter. Hiding it costs
    /// nothing — a letter's name is there to be HEARD, and the speaker stays either way.
    private func displayName(_ entry: AlphabetEntry) -> String? {
        guard let name = entry.name else { return nil }
        let shown = [entry.glyph.lowercased(), (entry.upper ?? "").lowercased()]
        return shown.contains(name.lowercased()) ? nil : name
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
        let catalogued = model.catalog?.alphabetExample(entry: entry, lang: language)
        if let text = catalogued?.text ?? entry.exampleText {
            HStack(alignment: .firstTextBaseline, spacing: DL.Space.s) {
                if let emoji = catalogued?.emoji {
                    Text(verbatim: emoji)
                        .font(DL.Fonts.body)
                        .accessibilityHidden(true)
                }
                Text(verbatim: text)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                if let slug = catalogued?.slug, let meaning = meaning(of: slug) {
                    Text(verbatim: "· \(meaning)")
                        .font(DL.Fonts.subheadline)
                        .foregroundStyle(Color.dlTextSecondary)
                }
                Spacer(minLength: 0)
                if let speak = speakExample(entry) {
                    speaker(form: text, speak)
                }
            }
            .saysOnTap(speakExample(entry))
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

    /// The example word, under the provenance rule the drill follows: the lookup is
    /// keyed by the FORM, so a recording only ever plays over the word it says.
    /// An `exampleText` carries no slug, but the manifest's `texts{}` records those
    /// forms directly — `sechs`, `pero`/`perro` — and the voice is the fallback,
    /// not the rule, for reference material this central.
    private func speakExample(_ entry: AlphabetEntry) -> (() -> Void)? {
        if let example = model.catalog?.alphabetExample(entry: entry, lang: language) {
            return play(model.formPronunciation(example.text, lang: language))
        }
        guard let text = entry.exampleText else { return nil }
        return play(model.formPronunciation(text, lang: language)
            ?? model.spokenPronunciation(text, lang: language))
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

    /// The sign that this line has a sound, and the aimed target for it.
    /// It keeps the full 44 pt target:
    /// the row's two speakers sit far enough apart to afford one.
    ///
    /// Hidden from VoiceOver on purpose: the row is one element, and hearing
    /// it is offered as a row action instead (`accessibilityActions`).
    /// [form] is what the tap plays, so the pulse follows the sound it started.
    private func speaker(form: String, _ speak: @escaping () -> Void) -> some View {
        SpeakerIcon(size: .small,
                    isPlaying: model.isPronouncing(form, lang: language),
                    pronounce: speak)
            .accessibilityHidden(true)
    }

    // MARK: - Catalog reads

    // why: internal — LettersOverview gates the whole section on the file
    // existing, which is the same registry rule the hub's chip reads.
    var alphabet: Alphabet? { model.catalog?.alphabet(lang: language) }

    private var sections: [AlphabetSection] { alphabet?.sections ?? [] }

    private var entries: [AlphabetEntry] { alphabet?.entries ?? [] }

    /// What the example MEANS to this reader — null wherever the reader's
    /// language does not realize the concept, and then simply left out. An
    /// alphabet is not a join: the word must stand for everyone.
    private func meaning(of slug: String) -> String? {
        model.catalog?.exampleMeaning(slug: slug, lang: model.sourceLanguage)
    }

    private func reader(_ aid: [String: String]) -> String? {
        aid[model.sourceLanguage] ?? aid["en"]
    }
}
