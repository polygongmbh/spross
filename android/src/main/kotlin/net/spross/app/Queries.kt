package net.spross.app

import android.app.Application
import java.util.Locale
import net.spross.app.ui.AreaNaming
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.CardGrowth
import net.spross.kern.catalog.Catalog

/**
 * Where ONE word stands on the growth ladder, for a surface holding that word —
 * null where the join does not carry it. Stamped with the model's clock like
 * every other box read, so two surfaces never disagree about the day.
 */
fun AppModel.cardGrowth(cardId: String): CardGrowth? =
    box?.let { BoxEngine.cardGrowth(it, cardId, now(), tz()) }

/**
 * The source a fresh install opens with. Kern's rule, over the device's report:
 * asking [Catalog.availableTargets] about an undeclared locale THROWS, so a French
 * or Italian phone used to crash on launch here.
 */
fun AppModel.defaultSource(cat: Catalog): String = cat.defaultSource(Locale.getDefault().language)

/**
 * The name the device suggests for the onboarding field, where it is named after
 * somebody at all ([DeviceName]). Asked once, on the screen that offers it — nothing
 * is stored until the learner leaves it standing.
 */
fun AppModel.suggestedLearnerName(): String? =
    DeviceName.suggestedLearnerName(getApplication<Application>().contentResolver)

/**
 * What a shelf is CALLED to this learner — the browser's own rule ([AreaNaming]),
 * so the cue an ambiguous prompt carries and the heading it stands under in the box
 * can never disagree about the name of an area.
 */
fun AppModel.areaTitle(area: String): String {
    val cat = catalog
    val source = box?.joinStamp?.source
    return AreaNaming(
        chrome = chrome,
        catalogTitle = { if (source == null) null else cat?.areaTitle(it, source) },
        catalogSubtitle = { if (source == null) null else cat?.areaSubtitle(it, source) },
        catalogEmoji = { cat?.areaEmoji(it) },
    ).title(area)
}
