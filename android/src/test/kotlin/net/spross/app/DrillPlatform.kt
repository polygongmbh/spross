package net.spross.app

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.Realization
import net.spross.kern.session.ToneKind

/**
 * The platform half of a drill flow, recorded: everything a flow hands outside the run —
 * the tones, the focus releases and the silences — with the screen reader switched by hand.
 * The drill wiring tests read the beat off the flow instead of running one.
 */
internal class DrillPlatform {
    val tones = mutableListOf<ToneKind>()
    var focusReleases = 0
    var silences = 0
    var screenReader = false
}

/** A de→sw card the drills can draw on, with nothing but its two texts. */
internal fun grownWord(id: String, text: String) = Card(
    id = id,
    kind = CardKind.Noun,
    area = "test",
    emoji = null,
    seedIndex = 0,
    components = emptyList(),
    source = Realization(lang = "de", text = "das $id"),
    target = Realization(lang = "sw", text = text),
)
