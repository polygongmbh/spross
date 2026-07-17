import DuoKern

/// Curated slot templates per pair. Every non-slot content word on the
/// target side is verified against the seed vocab (audited by
/// PhraseVocabAuditTests); only documented function words go beyond it.
///
/// Composition constraints honored here (do not "improve" without checking):
/// - Swahili clock values start "Saa …" and drop into mid-sentence
///   adverbial position lowercased ("Treni inaondoka saa mbili usiku.").
/// - Ukrainian time-at ("о + Lokativ") does NOT compose with the Trainer's
///   nominative-based clock strings, so Ukrainian clock templates use
///   predicate frames ("Зараз …", "На будильнику …") — fewer, but correct.
/// - Ukrainian year frames ("… geboren", "seit …") would need ordinal +
///   case forms the Trainer doesn't produce; year templates therefore use
///   dictation frames where the bare cardinal reading is natural.
/// - Ukrainian counted nouns agree via `countForms` (masculine nouns only,
///   so the Trainer's canonical masculine numeral stays grammatical).
public enum PhraseTemplates {

    public static func templates(pair: LanguagePair) -> [PhraseTemplate] {
        switch pair {
        case .deSw: return deSw
        case .deUk: return deUk
        }
    }

    public static var all: [PhraseTemplate] { deSw + deUk }

    // MARK: - de-sw

    private static let deSw: [PhraseTemplate] = [
        .init(id: "sw-clock-zug", pair: .deSw,
              deTemplate: "Der Zug fährt um {slot} Uhr ab.",
              targetTemplate: "Treni inaondoka {slot}.",
              slotKind: .clock),
        .init(id: "sw-clock-bus", pair: .deSw,
              deTemplate: "Der Bus kommt um {slot} Uhr.",
              targetTemplate: "Basi linakuja {slot}.",
              slotKind: .clock),
        .init(id: "sw-clock-aufwachen", pair: .deSw,
              deTemplate: "Ich wache um {slot} Uhr auf.",
              targetTemplate: "Ninaamka {slot}.",
              slotKind: .clock),
        .init(id: "sw-clock-essen", pair: .deSw,
              deTemplate: "Wir essen um {slot} Uhr.",
              targetTemplate: "Tunakula {slot}.",
              slotKind: .clock),
        .init(id: "sw-clock-meeting", pair: .deSw,
              deTemplate: "Das Meeting ist um {slot} Uhr.",
              targetTemplate: "Mkutano ni {slot}.",
              slotKind: .clock),
        .init(id: "sw-num-teller", pair: .deSw,
              deTemplate: "Wir haben {slot} Teller.",
              targetTemplate: "Tuna sahani {slot}.",
              slotKind: .numbers),
        .init(id: "sw-num-preis", pair: .deSw,
              deTemplate: "Das kostet {slot} Euro.",
              targetTemplate: "Ni euro {slot}.",
              slotKind: .numbers,
              gloss: "wörtl.: „Es sind … Euro.“"),
        .init(id: "sw-num-wiederholen", pair: .deSw,
              deTemplate: "Wiederhole bitte: {slot}.",
              targetTemplate: "Rudia, tafadhali: {slot}.",
              slotKind: .numbers),
        .init(id: "sw-num-schreiben", pair: .deSw,
              deTemplate: "Schreib bitte: {slot}.",
              targetTemplate: "Andika, tafadhali: {slot}.",
              slotKind: .numbers),
        .init(id: "sw-year-seit", pair: .deSw,
              deTemplate: "Ich lerne seit {slot} Deutsch.",
              // "tangu mwaka …" — a bare cardinal after tangu doesn't read
              // as a year (language-review finding).
              targetTemplate: "Ninajifunza Kijerumani tangu mwaka {slot}.",
              slotKind: .years,
              gloss: "Jahreszahl als Kardinalzahl gelesen — mwaka = Jahr"),
        .init(id: "sw-year-schreiben", pair: .deSw,
              deTemplate: "Schreib bitte die Jahreszahl: {slot}.",
              targetTemplate: "Andika, tafadhali: {slot}.",
              slotKind: .years),
    ]

    // MARK: - de-uk

    private static let deUk: [PhraseTemplate] = [
        .init(id: "uk-clock-jetzt", pair: .deUk,
              deTemplate: "Es ist jetzt {slot} Uhr.",
              targetTemplate: "Зараз {slot}.",
              slotKind: .clock),
        .init(id: "uk-clock-wecker", pair: .deUk,
              deTemplate: "Der Wecker zeigt {slot} Uhr.",
              targetTemplate: "На будильнику {slot}.",
              slotKind: .clock,
              gloss: "wörtl.: „Auf dem Wecker [ist] …“"),
        .init(id: "uk-num-wiederholen", pair: .deUk,
              deTemplate: "Wiederholen Sie bitte: {slot}.",
              targetTemplate: "Повторіть, будь ласка: {slot}.",
              slotKind: .numbers),
        .init(id: "uk-num-schreiben", pair: .deUk,
              deTemplate: "Schreib bitte: {slot}.",
              targetTemplate: "Напиши, будь ласка: {slot}.",
              slotKind: .numbers),
        .init(id: "uk-num-preis", pair: .deUk,
              deTemplate: "Das kostet {slot} Euro.",
              targetTemplate: "Це {slot} євро.",
              slotKind: .numbers,
              gloss: "wörtl.: „Das sind … Euro.“ — євро ist unveränderlich",
              masculineSlot: true),
        .init(id: "uk-num-hefte", pair: .deUk,
              deTemplate: "Ich habe {slot} Hefte.",
              targetTemplate: "У мене є {slot} {count}.",
              slotKind: .numbers,
              gloss: "Zahlwort-Kongruenz: 1 → зошит, 2–4 → зошити, 5+ → зошитів",
              countForms: .init(one: "зошит", few: "зошити", many: "зошитів")),
        .init(id: "uk-num-stuehle", pair: .deUk,
              deTemplate: "Wir haben {slot} Stühle.",
              targetTemplate: "У нас є {slot} {count}.",
              slotKind: .numbers,
              gloss: "Zahlwort-Kongruenz: 1 → стілець, 2–4 → стільці, 5+ → стільців",
              countForms: .init(one: "стілець", few: "стільці", many: "стільців")),
        .init(id: "uk-num-schluessel", pair: .deUk,
              deTemplate: "Ich habe {slot} Schlüssel.",
              targetTemplate: "У мене є {slot} {count}.",
              slotKind: .numbers,
              gloss: "Zahlwort-Kongruenz: 1 → ключ, 2–4 → ключі, 5+ → ключів",
              countForms: .init(one: "ключ", few: "ключі", many: "ключів")),
        .init(id: "uk-year-wiederholen", pair: .deUk,
              deTemplate: "Wiederholen Sie bitte die Jahreszahl: {slot}.",
              targetTemplate: "Повторіть, будь ласка: {slot}.",
              slotKind: .years),
        .init(id: "uk-year-schreiben", pair: .deUk,
              deTemplate: "Schreib bitte die Jahreszahl: {slot}.",
              targetTemplate: "Напиши, будь ласка: {slot}.",
              slotKind: .years),
    ]
}
