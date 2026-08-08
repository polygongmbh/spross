package net.spross.kern.catalog

import net.spross.kern.model.Language
import net.spross.kern.model.LanguageInfo

/**
 * The language pair a learner picks: how the two pickers name it, how a tap changes it,
 * and which language the chrome around them is written in.
 *
 * Neither side hides the other's pick — choosing the language the other side holds SWAPS
 * the selections instead of refusing the tap, so a pair set backwards is fixed in one
 * move rather than two.
 */
object LanguageChoices {

    /** The pair under edit; [target] is null until a target has been chosen. */
    data class Selection(val source: Language, val target: Language?)

    /** The languages the apps carry chrome (UI string tables) for. */
    val CHROME_LANGUAGES: Set<Language> = setOf("de", "en")

    /**
     * The target picker's rows: every target learnable from [Selection.source],
     * plus the source itself so that picking it can swap the pair.
     *
     * The swap row is offered only where the SWAPPED pair (target → source) is
     * actually joinable — a target that teaches nothing back is no swap to offer,
     * and there is nothing to swap at all while no target is chosen.
     * Rows sort by code, which the catalog's own order does not.
     */
    fun targetChoices(catalog: Catalog, selection: Selection): List<Language> {
        val choices = catalog.availableTargets(selection.source).map { it.code }.toMutableList()
        val target = selection.target
        if (target != null && catalog.availableTargets(target).any { it.code == selection.source }) {
            choices += selection.source
        }
        return choices.sorted()
    }

    /**
     * A tap in the SOURCE picker.
     *
     * Picking the language the target side holds swaps the pair;
     * otherwise the source changes, and the target is kept where it stays learnable
     * under the new source, else falls back to that source's first available target —
     * the target list is source-dependent, and a selection left invalid teaches nothing.
     */
    fun pickSource(catalog: Catalog, selection: Selection, code: Language): Selection {
        if (code == selection.target) return swapped(selection)
        val targets = catalog.availableTargets(code)
        val target = selection.target?.takeIf { kept -> targets.any { it.code == kept } }
            ?: targets.firstOrNull()?.code
        return Selection(code, target)
    }

    /** A tap in the TARGET picker: the source's own language swaps the pair, anything else re-targets. */
    fun pickTarget(selection: Selection, code: Language): Selection =
        if (code == selection.source) swapped(selection) else Selection(selection.source, code)

    /**
     * A picker ROW: "🇺🇦 Українська · Ukrainian" — the flag, the language's own name, and the English exonym.
     *
     * Both names, because a flag beside a script the reader cannot read is easy to mistake
     * for a neighbouring language, while the endonym is how a speaker of it finds their own row.
     * Collapsed to one name where the two agree ("🇬🇧 English"),
     * and to the uppercased code where the catalog knows no such language.
     */
    fun pickerRow(code: Language, info: LanguageInfo?): String {
        val language = info ?: return code.uppercase()
        if (language.name == language.englishName) return "${language.flag} ${language.name}"
        return "${language.flag} ${language.name} · ${language.englishName}"
    }

    /**
     * The collapsed form a dropdown wears as its own label, having half a row to live in:
     * flag plus English exonym — the shorter of the two names, and the one that stays identifiable to everyone.
     */
    fun pickerLabel(code: Language, info: LanguageInfo?): String {
        val language = info ?: return code.uppercase()
        return "${language.flag} ${language.englishName}"
    }

    /**
     * The chrome a profile whose KNOWN language is [source] reads: itself where chrome exists for it, else English.
     *
     * Onboarding asks this too — with no box yet the known language is the device's,
     * so the first screen greets in it and then follows whatever the learner picks.
     */
    fun chromeLanguage(source: Language): Language = if (hasChrome(source)) source else "en"

    /**
     * Whether chrome exists for [language] at all.
     *
     * The immersion subtitle — an action button captioned in the language being LEARNED —
     * asks this instead of [chromeLanguage], because it has no fallback by design:
     * absent means no subtitle, never an English one, which would caption a German button in the wrong language.
     */
    fun hasChrome(language: Language): Boolean = language in CHROME_LANGUAGES

    /** Exchanges the two sides; a no-op while no target is chosen — there is nothing to exchange yet. */
    private fun swapped(selection: Selection): Selection =
        selection.target?.let { Selection(source = it, target = selection.source) } ?: selection
}
