import Foundation
import SwiftUI
import SprossKern

/// Language display names for chrome. WHICH name is Kern's ruling
/// (`LanguageChoices.name`) — this only hands it the catalog entry, so the two
/// phones cannot start calling the same language two different things.
enum LanguageNames {
    static func display(_ code: String, locale: Locale, catalog: Catalog?) -> String {
        LanguageChoices.shared.name(code: code, info: catalog?.languages[code])
    }

    /// Kept as its own name for the sentence chrome that reads better spelling out
    /// what it means; it resolves to the same one word.
    static func native(_ code: String, catalog: Catalog?) -> String {
        display(code, locale: .current, catalog: catalog)
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
        LanguageNames.display(code, locale: locale, catalog: namingCatalog)
    }

    /// "Auf Suaheli …" — what the answer field asks for. A runtime `%@`, so it
    /// resolves through `DLChrome` rather than the environment locale.
    func answerPlaceholder(_ code: String) -> String {
        String(format: DLChrome.string("session.answer.placeholder %@", locale: locale),
               languageName(code))
    }
}

extension Card {
    /// A form this card lists as a synonym or a variant — the right word, just
    /// not the one that played. The rule and its reasons live in kern
    /// (`session/SpokenAnswer.kt`); this is the shape the drills reach for.
    func alsoAccepts(_ input: String) -> Bool {
        SprossKern.alsoAccepts(card: self, input: input)
    }

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
    static func articleLabel(of realization: Realization, shown: String) -> DLArticle? {
        guard let article = shownArticle(article: article(of: realization),
                                         shownForm: shown,
                                         targetText: realization.text)
        else { return nil }
        return DLArticle(article, gender: DLGender(articleGender(article: article)))
    }

    /// The article the VOICE says in front of `shown`, or nil where there is
    /// none to say — the audio twin of `articleLabel`, and the same ruling
    /// (`shownArticle`): a rotated synonym may carry another gender, so it is
    /// spoken bare rather than wrong. What the string becomes is kern's
    /// `spokenTargetForm`, applied on the synthesized branch alone.
    static func spokenArticle(of realization: Realization, shown: String) -> String? {
        shownArticle(article: article(of: realization), shownForm: shown,
                     targetText: realization.text)
    }

    /// "der Kühlschrank" — citation form with its article where present.
    static func citation(of realization: Realization) -> String {
        guard let article = article(of: realization) else { return realization.text }
        return "\(article) \(realization.text)"
    }

    /// Labeled plural line. Which authored value is a sentinel and which resolves
    /// against the word is kern's (`model/DisplayText.kt`); the labels each one
    /// wears ("Pl. …", "= Pl.", "nur Pl.") are chrome.
    static func plural(of realization: Realization, locale: Locale) -> String? {
        guard let plural = pluralForm(realization: realization) else { return nil }
        switch onEnum(of: plural) {
        case .sameAsSingular: return DLChrome.string("session.grammar.plural.equals", locale: locale)
        case .pluralOnly: return DLChrome.string("session.grammar.plural.only", locale: locale)
        case .form(let form):
            return String(format: DLChrome.string("session.grammar.plural %@", locale: locale), form.text)
        }
    }

    /// "auch: Amt / Verwaltung" — the realization's remaining family beyond
    /// `shown`, for reveal display. Which forms are left is kern's
    /// (`model/DisplayText.kt`); the label and the " / " are chrome.
    static func alternates(of realization: Realization, shown: String,
                           locale: Locale) -> String? {
        let family = SprossKern.alternates(realization: realization, shown: [shown])
        guard !family.isEmpty else { return nil }
        return String(format: DLChrome.string("session.grammar.also %@", locale: locale),
                      family.joined(separator: " / "))
    }
}
