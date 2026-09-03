package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.Screen
import net.spross.app.TrainerStore
import net.spross.app.newCountryDrill

/**
 * The atlas drill: name the country, the people, the language — and say which is spoken
 * where. Typed answers only, in whichever direction the page was started with.
 *
 * Every rule is kern's `CountryDrill`, reached through `CountryDrillFlow`; every pixel is
 * [TypedDrillScreen], the screen this shares with the dates drill. What is left here is
 * the three things that tell the two apart.
 */
@Composable
fun CountryDrillScreen(model: AppModel, reverse: Boolean, fast: Boolean) {
    val stamp = model.box?.joinStamp
    TypedDrillScreen(
        model = model,
        reverse = reverse,
        fast = fast,
        page = TypedDrillPage(
            back = Screen.Countries,
            skill = model.chrome.trainerSkillCountries,
            // One key per PAIR, the same one the page reads its best Sprosse back from.
            key = stamp?.let { TrainerStore.countriesKey(it.source, it.target) },
            open = { onTone, onReleaseFocus ->
                model.newCountryDrill(reverse, fast, onTone, onReleaseFocus)
            },
        ),
    )
}
