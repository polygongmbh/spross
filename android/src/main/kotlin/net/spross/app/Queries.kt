package net.spross.app

import android.app.Application
import java.util.Locale
import net.spross.app.ui.AreaNaming
import net.spross.kern.box.AreaGrowth
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.CardGrowth
import net.spross.kern.box.growthByArea
import net.spross.kern.catalog.Catalog
import net.spross.kern.design.AreaTree
import net.spross.kern.design.SampleTrees

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
fun AppModel.areaTitle(area: String): String = areaNaming().title(area)

/** The emoji an area wears — the same naming rule as [areaTitle]. */
fun AppModel.areaEmoji(area: String): String = areaNaming().emoji(area)

private fun AppModel.areaNaming(): AreaNaming {
    val cat = catalog
    val source = box?.joinStamp?.source
    return AreaNaming(
        chrome = chrome,
        catalogTitle = { if (source == null) null else cat?.areaTitle(it, source) },
        catalogSubtitle = { if (source == null) null else cat?.areaSubtitle(it, source) },
        catalogEmoji = { cat?.areaEmoji(it) },
    )
}

/**
 * One tree per area the box holds, in the box browser's order ([BoxBrowser.areaNames]) —
 * or the fabricated box a debug launch asked for ([AppModel.sampleTreesAge]).
 * A walk over every card, so the Trees picture asks once per change to the box.
 */
/** The garden every tree grows in ([AreaTree.garden]). */
val AppModel.garden: String get() = AreaTree.garden(learnerName, box?.joinStamp?.target.orEmpty())

fun AppModel.composedAreaGrowth(): List<AreaGrowth> {
    sampleTreesAge?.let { return SampleTrees.trees(it) }
    val state = box ?: return emptyList()
    val cat = catalog ?: return emptyList()
    val numbers = stats ?: return emptyList()
    val byArea = growthByArea(state, BoxEngine.growth(state, now(), tz()))
    return BoxBrowser.areaNames(cat, numbers).mapNotNull { byArea[it] }
}
