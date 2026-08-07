import Foundation

/// What trips a learner up in each language's numbers, two to four lines each.
///
/// TODO(catalog): these are CONTENT, not chrome — they belong in
/// `catalog/drills/<lang>.json` keyed by explanation language, exactly like a
/// frame's `notes`, and this constant goes away with the catalog stage that
/// puts them there (plan: "the prose notes per language + parser + lint").
/// They are hardcoded here only so the overview ships readable rather than with
/// a hole where its second section belongs; no catalog format is invented.
///
/// Keyed language → reader → lines. A reader whose language has no wording gets
/// the English one, the same fallback the alphabet sheet's teaching aids follow.
enum NumbersOverviewNotes {

    /// The notes for `language`, written for a reader of `reader`.
    static func lines(language: String, reader: String) -> [String] {
        guard let byReader = all[language] else { return [] }
        return byReader[reader] ?? byReader["en"] ?? []
    }

    private static let all: [String: [String: [String]]] = [
        "de": [
            "de": [
                "Ab 21 steht der Einer vorn: einundzwanzig, „eins-und-zwanzig“ — so bis 99.",
                "Alles unter einer Million ist ein einziges Wort: einhundertsiebenundvierzig.",
                "6 und 7 verkürzen sich: sechzehn, siebzehn, sechzig, siebzig.",
                "Im Zahlwort heißt die 1 immer ein-: einhundert, eintausend. Allein steht eins.",
            ],
            "en": [
                "From 21 the unit comes first: einundzwanzig, 'one-and-twenty' — and so on to 99.",
                "Everything below a million is a single word: einhundertsiebenundvierzig.",
                "6 and 7 shorten: sechzehn, siebzehn, sechzig, siebzig.",
                "Inside a numeral 1 is always ein-: einhundert, eintausend. Alone it is eins.",
            ],
        ],
        "en": [
            "de": [
                "13 und 30, 15 und 50 klingen ähnlich: thirteen – thirty, fifteen – fifty.",
                "Zehner und Einer bekommen einen Bindestrich: twenty-one.",
                "hundred, thousand, million bleiben ohne -s: two hundred, three million.",
                "Britisch steht and vor dem letzten Teil: three hundred and forty-seven.",
            ],
            "en": [
                "13 and 30, 15 and 50 sound alike: thirteen – thirty, fifteen – fifty.",
                "Tens and units take a hyphen: twenty-one.",
                "hundred, thousand, million never take -s after a number: two hundred.",
                "British English puts and before the last part: three hundred and forty-seven.",
            ],
        ],
        "es": [
            "de": [
                "16 bis 29 sind ein Wort: dieciséis, veintidós. Ab 31 wieder getrennt: treinta y uno.",
                "100 allein ist cien, mit Rest dahinter ciento: ciento uno.",
                "Hunderter richten sich nach dem Geschlecht: doscientos / doscientas; 500, 700, 900 sind unregelmäßig.",
                "Es gibt keine Milliarde: 10⁹ heißt mil millones. Und 1000 ist mil, nie un mil.",
            ],
            "en": [
                "16 to 29 are welded into one word: dieciséis, veintidós. From 31 it splits again: treinta y uno.",
                "100 on its own is cien; with anything after it, ciento: ciento uno.",
                "Hundreds agree in gender: doscientos / doscientas; 500, 700 and 900 are irregular.",
                "There is no short-scale billion: 10⁹ is mil millones. And 1000 is mil, never un mil.",
            ],
        ],
        "sw": [
            "de": [
                "6, 7 und 9 sind aus dem Arabischen entlehnt: sita, saba, tisa — die Nachbarn sind Bantu.",
                "Auch die Zehner ab 20 sind entlehnt und folgen keinem Muster: ishirini, thelathini, arobaini.",
                "Teile verbindet na: ishirini na tano. In langen Zahlen fällt na oft weg — beides gilt.",
                "Erst das Stufenwort, dann die Anzahl: mia mbili (200), elfu tatu (3000).",
            ],
            "en": [
                "6, 7 and 9 are Arabic loans: sita, saba, tisa — their neighbours are Bantu.",
                "The tens from 20 up are borrowed too and follow no pattern: ishirini, thelathini, arobaini.",
                "Parts join with na: ishirini na tano. In long numbers na is often dropped — both count.",
                "The scale word comes first, the count after: mia mbili (200), elfu tatu (3000).",
            ],
        ],
        "uk": [
            "de": [
                "Das Zahlwort bestimmt die Form: 1 одна тисяча, 2–4 дві тисячі, ab 5 п'ять тисяч.",
                "Alles, was auf 11–14 endet, nimmt trotzdem die 5+-Form: одинадцять тисяч.",
                "40 сорок und 90 дев'яносто fallen aus dem Muster — kein -дцять, kein -десят.",
                "тисяча ist feminin (одна, дві), мільйон maskulin (один). 1900 oft nur тисяча дев'ятсот.",
            ],
            "en": [
                "The numeral sets the form: 1 одна тисяча, 2-4 дві тисячі, 5+ п'ять тисяч.",
                "Anything ending in 11-14 still takes the 5+ form: одинадцять тисяч.",
                "40 сорок and 90 дев'яносто break the pattern — no -дцять, no -десят.",
                "тисяча is feminine (одна, дві), мільйон masculine (один). 1900 is often just тисяча дев'ятсот.",
            ],
        ],
    ]
}
