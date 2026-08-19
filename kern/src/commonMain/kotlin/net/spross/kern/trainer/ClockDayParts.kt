package net.spross.kern.trainer

import net.spross.kern.box.DayPart
import net.spross.kern.model.Language

/**
 * Which greeting an hour calls for, read off the language's OWN clock words instead of a
 * second table of boundaries.
 *
 * The drill already knows where each language puts the day's seams, and they disagree:
 * Swahili is at *jioni* by four in the afternoon ([SwahiliClock.dayParts]), Spanish still
 * at *de la tarde* at seven ([SpanishClockForms.dayParts]), German at *abends* from six
 * ([GermanClock.dayParts]). Reading them here means a boundary is authored once, and the
 * greeting moves whenever the drill that teaches it does.
 *
 * Two departures, both because reading a time is not greeting a person:
 * the small hours are [DayPart.Night] in every language — the drill says "two in the
 * morning" and nobody is greeted that way — and noon is taken from the hour before it,
 * because the languages that name midday with a word of its own (French *midi*, Italian's
 * deliberate blank) leave the drill nothing at twelve that a greeting could stand on.
 *
 * A language the drills do not cover yet reads on English's hours.
 */
internal fun greetingPart(language: Language?, hour: Int): DayPart {
    if (hour in SMALL_HOURS) return DayPart.Night
    var at = hour
    repeat(HOURS) {
        clockDayParts(language, at).firstNotNullOfOrNull { PERIOD_PARTS[it] }
            ?.let { if (at != NOON) return it }
        at = (at + HOURS - 1) % HOURS
    }
    return DayPart.Day
}

private const val HOURS = 24
private const val NOON = 12
private val SMALL_HOURS = 0..4

private fun clockDayParts(language: Language?, hour: Int): List<String> = when (language) {
    "de" -> GermanClock.dayParts(hour)
    "eo" -> EsperantoClockForms.dayParts(hour, countdown = false)
    "es" -> SpanishClockForms.dayParts(hour, minutes = 0, countdown = false)
    "fr" -> FrenchClockForms.dayParts(hour)
    "it" -> ItalianClockForms.dayParts(hour)
    "sw" -> SwahiliClock.dayParts(hour)
    "uk" -> UkrainianClockForms.dayParts(hour)
    else -> EnglishClockRegisters.dayParts(hour)
}

/**
 * Which greeting each clock word calls for. Keyed by the word rather than by language —
 * they are distinct across the drills — and a word that covers two of our parts follows
 * the GREETING the language would give at that hour: Spanish *de la tarde* is where
 * "¡Buenas tardes!" lives, and *de la noche* already belongs to "¡Buenas noches!".
 */
private val PERIOD_PARTS: Map<String, DayPart> = mapOf(
    // de
    "morgens" to DayPart.Morning, "früh" to DayPart.Morning, "vormittags" to DayPart.Morning,
    "mittags" to DayPart.Day, "nachmittags" to DayPart.Day,
    "abends" to DayPart.Evening, "nachts" to DayPart.Night,
    // en
    "in the morning" to DayPart.Morning, "in the afternoon" to DayPart.Day,
    "in the evening" to DayPart.Evening, "at night" to DayPart.Night,
    // eo
    "matene" to DayPart.Morning, "antaŭtagmeze" to DayPart.Morning,
    "posttagmeze" to DayPart.Day, "vespere" to DayPart.Evening, "nokte" to DayPart.Night,
    // es
    "de la mañana" to DayPart.Morning, "del mediodía" to DayPart.Day, "del día" to DayPart.Day,
    "de la tarde" to DayPart.Evening,
    "de la noche" to DayPart.Night, "de la madrugada" to DayPart.Night,
    // fr
    "du matin" to DayPart.Morning, "de l'après-midi" to DayPart.Day,
    "du soir" to DayPart.Evening, "de la nuit" to DayPart.Night,
    // it
    "di mattina" to DayPart.Morning, "del mattino" to DayPart.Morning,
    "di pomeriggio" to DayPart.Day, "di sera" to DayPart.Evening, "di notte" to DayPart.Night,
    // sw
    "alfajiri" to DayPart.Morning, "asubuhi" to DayPart.Morning,
    "mchana" to DayPart.Day, "alasiri" to DayPart.Day,
    "jioni" to DayPart.Evening,
    "usiku" to DayPart.Night, "usiku wa manane" to DayPart.Night,
    // uk
    "ранку" to DayPart.Morning, "дня" to DayPart.Day,
    "вечора" to DayPart.Evening, "ночі" to DayPart.Night,
)
