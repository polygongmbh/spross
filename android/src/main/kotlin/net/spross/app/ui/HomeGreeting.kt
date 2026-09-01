package net.spross.app.ui

import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.kern.box.DayPart
import net.spross.kern.box.chromePart
import net.spross.kern.box.dayPart
import net.spross.kern.box.partVariant

/**
 * "Habari za asubuhi, Tim!", "Tayari kujifunza, Nachteule?", "Ein Feierabend mit Suaheli?" —
 * two registers for the line over the day's card: the language speaking for itself, or the
 * known language asking about it. Either way the line carries the language, which is what
 * the header is for.
 *
 * Which stretch of the day it is and which of the candidates this one takes are kern's
 * ([dayPart], [chromePart], [partVariant]); the words are the catalog's and the chrome's.
 * The screen's own name stands in only while no profile names a language.
 */
fun greeting(model: AppModel): String {
    val target = model.box?.joinStamp?.target ?: return model.chrome.homeTitle
    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault().id
    // The target's own hours for its own lines: four in the afternoon is still Tag in
    // German and already jioni in Swahili. The chrome half keeps a fixed schedule instead
    // — a "night owl" reads as one at the same local hour no matter which language the
    // learner's own is (kern's [chromePart]).
    val targetPart = dayPart(now, tz, target)
    val chromePart = chromePart(now, tz)
    val lines = greetingLines(model, targetPart, chromePart, target)
    return lines[partVariant(now, tz, target, lines.size)]
}

/**
 * Everything the app could say right now, the language's own lines first — they both greet
 * and teach, and they are the only ones that can address the learner: by name, or by the
 * word the hour lends when no name is known.
 */
private fun greetingLines(model: AppModel, targetPart: DayPart, chromePart: DayPart, target: String): List<String> {
    val chrome = model.chrome
    val address = model.learnerName ?: when (chromePart) {
        DayPart.Morning -> chrome.homeGreetingMorningAddressee
        DayPart.Night -> chrome.homeGreetingNightAddressee
        else -> null
    }
    val lines = model.catalog?.spokenLines(target, targetPart, address).orEmpty().toMutableList()
    val named = model.languageName(target)
    val chromeLines = when (chromePart) {
        DayPart.Morning -> chrome.greetMorning
        DayPart.Day -> chrome.greetDay
        DayPart.Evening -> chrome.greetEvening
        DayPart.Night -> chrome.greetNight
    }
    return chromeLines.mapTo(lines) { it.format(named) }
}
