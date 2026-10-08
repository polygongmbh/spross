import Foundation
import SwiftUI
import SprossKern

/// Language display names for chrome. WHICH name is Kern's ruling
/// (`LanguageChoices.name`) — this only hands it the catalog entry, so the two
/// phones cannot start calling the same language two different things.
enum LanguageNames {
    static func display(_ code: String, catalog: Catalog?) -> String {
        LanguageChoices.shared.name(code: code, info: catalog?.languages[code])
    }

    /// Kept as its own name for the sentence chrome that reads better spelling out
    /// what it means; it resolves to the same one word.
    static func native(_ code: String, catalog: Catalog?) -> String {
        display(code, catalog: catalog)
    }

    /// Language PICKER rows ("🇺🇦 Українська · Ukrainian") and the collapsed
    /// dropdown label. Both forms belong to `LanguageChoices`; all these two do
    /// is hand it the catalog entry for the code.
    static func pickerRow(_ code: String, catalog: Catalog?) -> String {
        LanguageChoices.shared.pickerRow(code: code, info: catalog?.languages[code])
    }

    static func pickerLabel(_ code: String, catalog: Catalog?) -> String {
        LanguageChoices.shared.pickerLabel(code: code, info: catalog?.languages[code])
    }
}

/// Surfaces that name a language in their own chrome, and label a typed-answer
/// field with it. One implementation, so two screens can never start naming the
/// same language two different ways.
// why: @MainActor — three of the four conformers read the name out of the
// AppModel's catalog, which is main-actor isolated.
@MainActor
protocol LanguageNaming {
    var locale: Locale { get }
    /// Where a language with no chrome exonym finds its own name.
    var namingCatalog: Catalog? { get }
}

extension LanguageNaming {
    func languageName(_ code: String) -> String {
        LanguageNames.display(code, catalog: namingCatalog)
    }

    /// "Auf Suaheli …" — what the answer field asks for. A runtime `%@`, so it
    /// resolves through `ChromeStrings` rather than the environment locale.
    func answerPlaceholder(_ code: String) -> String {
        String(format: ChromeStrings.string("session.answer.placeholder %@", locale: locale),
               languageName(code))
    }

    /// Naming the language is right only while the answer is WORDS: a value —
    /// a date in digits, a reading written back as a numeral — is written the
    /// same way in either language, and "Auf Español …" over a number pad asks
    /// for the wrong thing.
    func answerPlaceholder(_ code: String, digits: Bool) -> String {
        digits
            ? ChromeStrings.string("numbers.answer.placeholder", locale: locale)
            : answerPlaceholder(code)
    }
}

extension Card {
    /// Leading list marker: the seed emoji when present, else a neutral
    /// per-kind category glyph (verbs/phrases carry no seed emoji). Used only
    /// for row rhythm in lists — the card face shows the seed emoji or nothing.
    var displayEmoji: String {
        if let emoji, !emoji.isEmpty { return emoji }
        return CardKt.kindEmoji(kind: kind)
    }
}

/// Target-side grammar rendering (contract §2): article and plural lines
/// render only for the TARGET realization; suffix plurals dictionary-style,
/// sentinel values via localized chrome strings.
enum CardDisplay {

    /// The realization's authored article (de `gender` carries the article
    /// itself: "der"/"die"/"das").
    private static func article(of realization: Realization) -> String? {
        realization.grammar["gender"]
    }

    /// The article a card face may show in front of `shown`, with the gender it
    /// marks — both rules are the box's (`model/Article.kt`): a rotated synonym
    /// is a different word, so the card's article steps aside rather than
    /// mislabel it, and which article marks which gender is stated there once.
    static func articleLabel(of realization: Realization, shown: String) -> Theme.Article? {
        guard let article = shownArticle(article: article(of: realization),
                                         shownForm: shown,
                                         targetText: realization.text)
        else { return nil }
        return Theme.Article(article,
                             gender: Theme.Gender(articleGender(article: article, lang: realization.lang)))
    }

    /// The article the VOICE says in front of `shown`, or nil where there is
    /// none to say — the audio twin of `articleLabel`, and the same ruling
    /// (`shownArticle`): a rotated synonym may carry another gender, so it is
    /// spoken bare rather than wrong. What the string becomes is kern's
    /// `spokenTargetForm`, which both the voice and the `articles{}` recording
    /// lookup are handed.
    static func spokenArticle(of realization: Realization, shown: String) -> String? {
        shownArticle(article: article(of: realization), shownForm: shown,
                     targetText: realization.text)
    }

    /// "auch: Amt / Verwaltung" — the realization's remaining family beyond
    /// `shown`, for reveal display. Which forms are left is kern's
    /// (`model/DisplayText.kt`); the label and the " / " are chrome.
    static func alternates(of realization: Realization, shown: String,
                           locale: Locale) -> String? {
        alsoLine(SprossKern.alternates(realization: realization, shown: [shown], forms: realization.forms),
                 locale: locale)
    }

    /// "also: die Lehrerin ♀ / …" — each form with the marker that says which one it is.
    static func alsoLine(_ family: [Alternate], locale: Locale) -> String? {
        guard !family.isEmpty else { return nil }
        let forms = family.map { alternate in
            alternate.marker.map { "\(alternate.text) \(marker($0, locale: locale))" } ?? alternate.text
        }
        return String(format: ChromeStrings.string("session.grammar.also %@", locale: locale),
                      forms.joined(separator: " / "))
    }

    /// A form tag as the badge reads it: gender as its glyph, the rest as the grammar's abbreviation.
    static func marker(_ tag: FormTag, locale: Locale) -> String {
        tag.parts.map { formGlyph(part: $0) ?? abbreviation($0, locale: locale) }.joined(separator: " ")
    }

    /// `marker` as a screen reader says it: the gender glyphs by name.
    static func markerSpoken(_ tag: FormTag, locale: Locale) -> String {
        tag.parts.map { part in
            switch part {
            case "f": ChromeStrings.string("a11y.glyph.feminineForm", locale: locale)
            case "m": ChromeStrings.string("a11y.glyph.masculineForm", locale: locale)
            case "n": ChromeStrings.string("a11y.glyph.neuterForm", locale: locale)
            default: abbreviation(part, locale: locale)
            }
        }.joined(separator: " ")
    }

    private static func abbreviation(_ part: String, locale: Locale) -> String {
        switch part {
        case "pl": ChromeStrings.string("form.marker.pl", locale: locale)
        case "nom": ChromeStrings.string("form.marker.nom", locale: locale)
        case "gen": ChromeStrings.string("form.marker.gen", locale: locale)
        case "dat": ChromeStrings.string("form.marker.dat", locale: locale)
        case "acc": ChromeStrings.string("form.marker.acc", locale: locale)
        case "ins": ChromeStrings.string("form.marker.ins", locale: locale)
        case "loc": ChromeStrings.string("form.marker.loc", locale: locale)
        case "voc": ChromeStrings.string("form.marker.voc", locale: locale)
        default: String(format: ChromeStrings.string("form.marker.class %@", locale: locale), part)
        }
    }
}
