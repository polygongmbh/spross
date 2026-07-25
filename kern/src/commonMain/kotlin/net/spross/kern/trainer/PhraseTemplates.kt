package net.spross.kern.trainer

import net.spross.kern.model.Language

/**
 * Curated slot templates per (source, target) profile. Every non-slot content
 * word on the target side is verified against the catalog join (audited by
 * PhraseVocabAuditTests); only documented function words go beyond it.
 *
 * Composition constraints honored here (do not "improve" without checking):
 * - Swahili clock values start "Saa …" and drop into mid-sentence
 *   adverbial position lowercased ("Treni inaondoka saa mbili usiku.").
 * - Ukrainian time-at ("о + Lokativ") does NOT compose with the Trainer's
 *   nominative-based clock strings, so Ukrainian clock templates use
 *   predicate frames ("Зараз …", "На будильнику …") — fewer, but correct.
 * - Ukrainian year frames ("… geboren", "seit …") would need ordinal +
 *   case forms the Trainer doesn't produce; year templates therefore use
 *   dictation frames where the bare cardinal reading is natural.
 * - Ukrainian counted nouns agree via `countForms` (masculine nouns only,
 *   so the Trainer's canonical masculine numeral stays grammatical).
 */
object PhraseTemplates {

    fun templates(source: Language, target: Language): List<PhraseTemplate> =
        all.filter { it.source == source && it.target == target }

    private val deSw: List<PhraseTemplate> = listOf(
        PhraseTemplate(
            id = "sw-clock-zug", source = "de", target = "sw",
            sourceTemplate = "Der Zug fährt um {slot} Uhr ab.",
            targetTemplate = "Treni inaondoka {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "sw-clock-bus", source = "de", target = "sw",
            sourceTemplate = "Der Bus kommt um {slot} Uhr.",
            targetTemplate = "Basi linakuja {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "sw-clock-aufwachen", source = "de", target = "sw",
            sourceTemplate = "Ich wache um {slot} Uhr auf.",
            targetTemplate = "Ninaamka {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "sw-clock-essen", source = "de", target = "sw",
            sourceTemplate = "Wir essen um {slot} Uhr.",
            targetTemplate = "Tunakula {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "sw-clock-meeting", source = "de", target = "sw",
            sourceTemplate = "Das Meeting ist um {slot} Uhr.",
            targetTemplate = "Mkutano ni {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "sw-num-teller", source = "de", target = "sw",
            sourceTemplate = "Wir haben {slot} Teller.",
            targetTemplate = "Tuna sahani {slot}.",
            slotKind = TrainerKind.Numbers,
        ),
        PhraseTemplate(
            id = "sw-num-preis", source = "de", target = "sw",
            sourceTemplate = "Das kostet {slot} Euro.",
            targetTemplate = "Ni euro {slot}.",
            slotKind = TrainerKind.Numbers,
            gloss = "wörtl.: „Es sind … Euro.“",
        ),
        PhraseTemplate(
            id = "sw-num-wiederholen", source = "de", target = "sw",
            sourceTemplate = "Wiederhole bitte: {slot}.",
            targetTemplate = "Rudia, tafadhali: {slot}.",
            slotKind = TrainerKind.Numbers,
        ),
        PhraseTemplate(
            id = "sw-num-schreiben", source = "de", target = "sw",
            sourceTemplate = "Schreib bitte: {slot}.",
            targetTemplate = "Andika, tafadhali: {slot}.",
            slotKind = TrainerKind.Numbers,
        ),
        PhraseTemplate(
            id = "sw-year-seit", source = "de", target = "sw",
            sourceTemplate = "Ich lerne seit {slot} Deutsch.",
            // "tangu mwaka …" — a bare cardinal after tangu doesn't read
            // as a year (language-review finding).
            targetTemplate = "Ninajifunza Kijerumani tangu mwaka {slot}.",
            slotKind = TrainerKind.Years,
            gloss = "Jahreszahl als Kardinalzahl gelesen — mwaka = Jahr",
        ),
        PhraseTemplate(
            id = "sw-year-schreiben", source = "de", target = "sw",
            sourceTemplate = "Schreib bitte die Jahreszahl: {slot}.",
            targetTemplate = "Andika, tafadhali: {slot}.",
            slotKind = TrainerKind.Years,
        ),
    )

    private val deUk: List<PhraseTemplate> = listOf(
        PhraseTemplate(
            id = "uk-clock-jetzt", source = "de", target = "uk",
            sourceTemplate = "Es ist jetzt {slot} Uhr.",
            targetTemplate = "Зараз {slot}.",
            slotKind = TrainerKind.Clock,
        ),
        PhraseTemplate(
            id = "uk-clock-wecker", source = "de", target = "uk",
            sourceTemplate = "Der Wecker zeigt {slot} Uhr.",
            targetTemplate = "На будильнику {slot}.",
            slotKind = TrainerKind.Clock,
            gloss = "wörtl.: „Auf dem Wecker [ist] …“",
        ),
        PhraseTemplate(
            id = "uk-num-wiederholen", source = "de", target = "uk",
            sourceTemplate = "Wiederholen Sie bitte: {slot}.",
            targetTemplate = "Повторіть, будь ласка: {slot}.",
            slotKind = TrainerKind.Numbers,
        ),
        PhraseTemplate(
            id = "uk-num-schreiben", source = "de", target = "uk",
            sourceTemplate = "Schreib bitte: {slot}.",
            targetTemplate = "Напиши, будь ласка: {slot}.",
            slotKind = TrainerKind.Numbers,
        ),
        PhraseTemplate(
            id = "uk-num-preis", source = "de", target = "uk",
            sourceTemplate = "Das kostet {slot} Euro.",
            targetTemplate = "Це {slot} євро.",
            slotKind = TrainerKind.Numbers,
            gloss = "wörtl.: „Das sind … Euro.“ — євро ist unveränderlich",
            masculineSlot = true,
        ),
        PhraseTemplate(
            id = "uk-num-hefte", source = "de", target = "uk",
            sourceTemplate = "Ich habe {slot} Hefte.",
            targetTemplate = "У мене є {slot} {count}.",
            slotKind = TrainerKind.Numbers,
            gloss = "Zahlwort-Kongruenz: 1 → зошит, 2–4 → зошити, 5+ → зошитів",
            countForms = PhraseTemplate.CountForms(one = "зошит", few = "зошити", many = "зошитів"),
        ),
        PhraseTemplate(
            id = "uk-num-stuehle", source = "de", target = "uk",
            sourceTemplate = "Wir haben {slot} Stühle.",
            targetTemplate = "У нас є {slot} {count}.",
            slotKind = TrainerKind.Numbers,
            gloss = "Zahlwort-Kongruenz: 1 → стілець, 2–4 → стільці, 5+ → стільців",
            countForms = PhraseTemplate.CountForms(one = "стілець", few = "стільці", many = "стільців"),
        ),
        PhraseTemplate(
            id = "uk-num-schluessel", source = "de", target = "uk",
            sourceTemplate = "Ich habe {slot} Schlüssel.",
            targetTemplate = "У мене є {slot} {count}.",
            slotKind = TrainerKind.Numbers,
            gloss = "Zahlwort-Kongruenz: 1 → ключ, 2–4 → ключі, 5+ → ключів",
            countForms = PhraseTemplate.CountForms(one = "ключ", few = "ключі", many = "ключів"),
        ),
        PhraseTemplate(
            id = "uk-year-wiederholen", source = "de", target = "uk",
            sourceTemplate = "Wiederholen Sie bitte die Jahreszahl: {slot}.",
            targetTemplate = "Повторіть, будь ласка: {slot}.",
            slotKind = TrainerKind.Years,
        ),
        PhraseTemplate(
            id = "uk-year-schreiben", source = "de", target = "uk",
            sourceTemplate = "Schreib bitte die Jahreszahl: {slot}.",
            targetTemplate = "Напиши, будь ласка: {slot}.",
            slotKind = TrainerKind.Years,
        ),
    )

    val all: List<PhraseTemplate> = deSw + deUk
}
