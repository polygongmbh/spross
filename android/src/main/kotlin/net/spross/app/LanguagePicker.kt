package net.spross.app

import net.spross.kern.catalog.AvailableTarget
import net.spross.kern.model.LanguageInfo

/**
 * Pure onboarding picker logic (iOS OnboardingView parity):
 * rows render "🇩🇪 German" — flag + English exonym, chrome-locale-independent —
 * and neither side hides the other's pick: choosing it swaps the selections.
 */
object LanguagePicker {

    data class Selection(val source: String, val target: String?)

    data class TargetChoice(val code: String, val conceptCount: Int)

    /** Picker row label: flag + English exonym from languages.json. */
    fun rowLabel(code: String, info: LanguageInfo?): String =
        info?.let { "${it.flag} ${it.englishName}" } ?: code.uppercase()

    /**
     * Learnable targets PLUS the current source — picking it swaps the pair
     * (its count is the swapped pair's, which is symmetric).
     */
    fun targetChoices(
        sel: Selection,
        targetsOf: (String) -> List<AvailableTarget>,
    ): List<TargetChoice> {
        val choices = targetsOf(sel.source)
            .map { TargetChoice(it.code, it.conceptCount) }
            .toMutableList()
        sel.target?.let { target ->
            targetsOf(target).firstOrNull { it.code == sel.source }
                ?.let { choices += TargetChoice(sel.source, it.conceptCount) }
        }
        return choices.sortedBy { it.code }
    }

    /** Source tap: the other side's pick swaps; else re-source, keeping the target valid. */
    fun pickSource(
        sel: Selection,
        code: String,
        targetsOf: (String) -> List<AvailableTarget>,
    ): Selection {
        if (code == sel.target) return swap(sel)
        val targets = targetsOf(code)
        val target = sel.target.takeIf { t -> targets.any { it.code == t } }
            ?: targets.firstOrNull()?.code
        return Selection(code, target)
    }

    /** Target tap: the other side's pick swaps; else just re-target. */
    fun pickTarget(sel: Selection, code: String): Selection =
        if (code == sel.source) swap(sel) else Selection(sel.source, code)

    /** Exchange the two selections; no-op while no target is chosen. */
    private fun swap(sel: Selection): Selection =
        sel.target?.let { Selection(source = it, target = sel.source) } ?: sel
}
