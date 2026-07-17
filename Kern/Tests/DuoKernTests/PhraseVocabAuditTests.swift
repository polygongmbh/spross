import Foundation
import Testing
@testable import DuoKern
@testable import DuoKernTrainer

/// Template vocab audit: every non-slot content word in each targetTemplate
/// (and every counted-noun form) must be VERIFIED vocabulary — verbatim in
/// the pair's seed JSON, an inflected form of a seed word (documented map),
/// or one of the documented function/international words below.
struct PhraseVocabAuditTests {

    /// Documented allowlist — function words and international words only.
    private static let allowlist: [LanguagePair: Set<String>] = [
        .deSw: [
            "tuna",  // tu-na „wir haben“: Subjektpräfix tu- + Possessiv-na (Funktionskonstruktion)
            "tangu", // Präposition „seit“
            "euro",  // internationale Währung, „Euro“ auf beiden Seiten
            "mwaka", // „Jahr“ — Kopfnomen der Jahresangabe (tangu mwaka …)
        ],
        .deUk: [
            "нас",   // Personalpronomen „uns“ («у нас є» = wir haben)
            "повторіть", // „wiederholen Sie“ — grounded in the basics starter pack
                         // (phrase moved out of the school area 2026-07-17)
            "євро",  // internationale Währung, unveränderlich
        ],
    ]

    /// Inflected template form → seed lemma. The lemma itself must be
    /// verbatim in the seed (asserted below), so this documents inflection,
    /// never new vocabulary.
    private static let inflectionMap: [LanguagePair: [String: String]] = [
        .deSw: [
            "ninaamka": "kuamka",   // ni-na-amka „ich wache auf“
            "tunakula": "kula",     // tu-na-kula „wir essen“
            "rudia": "kurudia",     // Imperativ „wiederhole“
            "andika": "kuandika",   // Imperativ „schreib“
        ],
        .deUk: [
            "будильнику": "будильник", // Lokativ nach «на»
            "напиши": "писати",        // Imperativ „schreib“
            "зошити": "зошит", "зошитів": "зошит",       // Zählformen
            "стільці": "стілець", "стільців": "стілець", // Zählformen
            "ключів": "ключ",                            // Zählform (ключі steht verbatim im Seed)
        ],
    ]

    @Test(arguments: LanguagePair.allCases)
    func targetTemplateWordsAreVerifiedVocabulary(pair: LanguagePair) throws {
        let seedWords = try seedTargetWords(pair: pair)
        let allow = Self.allowlist[pair] ?? []
        let inflections = Self.inflectionMap[pair] ?? [:]

        for template in PhraseTemplates.templates(pair: pair) {
            var text = template.targetTemplate
                .replacingOccurrences(of: "{slot}", with: " ")
                .replacingOccurrences(of: "{count}", with: " ")
            if let forms = template.countForms {
                text += " \(forms.one) \(forms.few) \(forms.many)"
            }
            for word in tokens(text) {
                let verified = seedWords.contains(word)
                    || allow.contains(word)
                    || inflections[word].map { seedWords.contains($0) } == true
                #expect(verified, "\(template.id): „\(word)“ is not verified seed vocabulary")
            }
        }
    }

    @Test(arguments: LanguagePair.allCases)
    func inflectionLemmasAndAllowlistStaySmallAndGrounded(pair: LanguagePair) throws {
        let seedWords = try seedTargetWords(pair: pair)
        for (form, lemma) in Self.inflectionMap[pair] ?? [:] {
            #expect(seedWords.contains(lemma), "lemma „\(lemma)“ (for „\(form)“) missing from seed")
        }
        #expect((Self.allowlist[pair] ?? []).count <= 4, "allowlist must stay small")
    }

    // MARK: - Seed extraction

    /// All target-language words in the pair's seed JSON
    /// (area titles + noun/verb/phrase translations).
    private func seedTargetWords(pair: LanguagePair) throws -> Set<String> {
        let name = "vocab-\(pair.rawValue)"
        let url = try #require(Bundle.module.url(forResource: name, withExtension: "json",
                                                 subdirectory: "Fixtures"))
        let json = try JSONSerialization.jsonObject(with: try Data(contentsOf: url))
        let root = try #require(json as? [String: Any])
        let areas = try #require(root["areas"] as? [[String: Any]])
        let targetKey = pair == .deSw ? "sw" : "uk"

        var words = Set<String>()
        for area in areas {
            if let title = area["title"] as? [String: String], let t = title[targetKey] {
                words.formUnion(tokens(t))
            }
            for section in ["nouns", "verbs", "phrases"] {
                for item in area[section] as? [[String: Any]] ?? [] {
                    if let text = item[targetKey] as? String {
                        words.formUnion(tokens(text))
                    }
                }
            }
        }
        return words
    }

    /// Lowercase word tokens; apostrophes/hyphens dropped in-word
    /// (mirrors AnswerNormalizer), all other non-letters split.
    private func tokens(_ text: String) -> [String] {
        let joined = text.lowercased().filter { $0 != "'" && $0 != "’" && $0 != "-" }
        return joined
            .map { $0.isLetter ? $0 : " " }
            .reduce(into: "") { $0.append($1) }
            .split(separator: " ")
            .map(String.init)
    }
}
