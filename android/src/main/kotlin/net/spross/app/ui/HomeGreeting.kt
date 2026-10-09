package net.spross.app.ui

import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.kern.box.Addressee
import net.spross.kern.box.DayPart
import net.spross.kern.box.GreetingPlan

/**
 * "Habari za asubuhi, Tim!", "Tayari kujifunza, Nachteule?", "Ein Feierabend mit Suaheli?" —
 * the line over the day's card. Which stretch of the day each register is in, whom the
 * language's own lines address and which line this one takes are kern's ([GreetingPlan]);
 * the words are the catalog's and the chrome's.
 */
fun greeting(model: AppModel, target: String): String {
    val chrome = model.chrome
    val plan = GreetingPlan(System.currentTimeMillis(), TimeZone.getDefault().id, target, model.learnerName != null)
    val address = when (plan.address) {
        Addressee.Learner -> model.learnerName
        Addressee.MorningWord -> chrome.homeGreetingMorningAddressee
        Addressee.NightWord -> chrome.homeGreetingNightAddressee
        Addressee.Nobody -> null
    }
    val spoken = model.catalog?.spokenLines(target, plan.targetPart, address).orEmpty()
    val chromeLines = when (plan.chromePart) {
        DayPart.Morning -> chrome.greetMorning
        DayPart.Day -> chrome.greetDay
        DayPart.Evening -> chrome.greetEvening
        DayPart.Night -> chrome.greetNight
    }
    val line = plan.pick(spoken.size, chromeLines.size)
    return if (line.spoken) spoken[line.index] else chromeLines[line.index].format(model.languageName(target))
}
