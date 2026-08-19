package net.spross.app.ui

import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.kern.box.DayPart
import net.spross.kern.box.dayPart
import net.spross.kern.box.partVariant

/**
 * "Habari za asubuhi, Tim!", "Tayari kujifunza, Nachteule?", "Ein Feierabend mit Suaheli?" —
 * two registers for the line over the day's card: the language speaking for itself, or the
 * known language asking about it. Either way the line carries the language, which is what
 * the header is for.
 *
 * Which stretch of the day it is and which of the candidates this one takes are kern's
 * ([dayPart], [partVariant]); the words are the catalog's and the chrome's. The screen's own
 * name stands in only while no profile names a language.
 */
fun greeting(model: AppModel): String {
    val target = model.box?.joinStamp?.target ?: return model.chrome.heuteTitle
    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault().id
    // The language's own hours: four in the afternoon is still Tag in German and already
    // jioni in Swahili (kern's [dayPart]).
    val part = dayPart(now, tz, target)
    val lines = greetingLines(model, part, target)
    return lines[partVariant(now, tz, target, lines.size)]
}

/**
 * Everything the app could say right now, the language's own lines first — they both greet
 * and teach, and they are the only ones that can address the learner: by name, or by the
 * word the hour lends when no name is known.
 */
private fun greetingLines(model: AppModel, part: DayPart, target: String): List<String> {
    val chrome = model.chrome
    val address = model.learnerName ?: when (part) {
        DayPart.Morning -> chrome.greetMorningAddressee
        DayPart.Night -> chrome.greetNightAddressee
        else -> null
    }
    val lines = model.catalog?.spokenLines(target, part, address).orEmpty().toMutableList()
    val named = model.languageName(target)
    val chromeLines = when (part) {
        DayPart.Morning -> chrome.greetMorning
        DayPart.Day -> chrome.greetDay
        DayPart.Evening -> chrome.greetEvening
        DayPart.Night -> chrome.greetNight
    }
    return chromeLines.mapTo(lines) { it.format(named) }
}
