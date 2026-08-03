package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.app.LanguagePicker.Selection
import net.spross.app.LanguagePicker.TargetChoice
import net.spross.kern.catalog.AvailableTarget
import net.spross.kern.model.LanguageInfo

class LanguagePickerTest {

    // en↔de and en↔sw are joinable both ways; uk teaches nothing (asymmetric).
    private val coverage = mapOf(
        "en" to listOf(
            AvailableTarget("de", "Deutsch", 900),
            AvailableTarget("sw", "Kiswahili", 300),
            AvailableTarget("uk", "Українська", 120),
        ),
        "de" to listOf(
            AvailableTarget("en", "English", 880),
            AvailableTarget("sw", "Kiswahili", 250),
        ),
        "sw" to listOf(AvailableTarget("en", "English", 310)),
    )

    private fun targetsOf(code: String) = coverage[code].orEmpty()

    /** A row names the language in its own words first — a flag alone is not identifiable. */
    @Test
    fun rowLabelCarriesTheEndonymAndTheExonym() {
        val uk = LanguageInfo(code = "uk", name = "Українська", englishName = "Ukrainian", flag = "🇺🇦")
        assertEquals("🇺🇦 Українська · Ukrainian", LanguagePicker.rowLabel("uk", uk))
    }

    @Test
    fun oneNameIsEnoughWhereBothAgree() {
        val en = LanguageInfo(code = "en", name = "English", englishName = "English", flag = "🇬🇧")
        assertEquals("🇬🇧 English", LanguagePicker.rowLabel("en", en))
    }

    @Test
    fun rowLabelFallsBackToUppercasedCode() {
        assertEquals("XX", LanguagePicker.rowLabel("xx", null))
    }

    @Test
    fun targetChoicesIncludeCurrentSourceWithSwappedPairCount() {
        val choices = LanguagePicker.targetChoices(Selection("en", "de"), ::targetsOf)
        assertEquals(
            listOf(
                TargetChoice("de", 900),
                TargetChoice("en", 880), // the swapped pair de→en, not en→de
                TargetChoice("sw", 300),
                TargetChoice("uk", 120),
            ),
            choices,
        )
    }

    @Test
    fun targetChoicesOmitSourceWhileNoTargetIsChosen() {
        val choices = LanguagePicker.targetChoices(Selection("en", null), ::targetsOf)
        assertEquals(listOf("de", "sw", "uk"), choices.map { it.code })
    }

    @Test
    fun targetChoicesOmitSourceWhenSwappedPairIsNotJoinable() {
        val choices = LanguagePicker.targetChoices(Selection("en", "uk"), ::targetsOf)
        assertEquals(listOf("de", "sw", "uk"), choices.map { it.code })
    }

    @Test
    fun pickingTheSourceOnTheTargetSideSwaps() {
        assertEquals(Selection("de", "en"), LanguagePicker.pickTarget(Selection("en", "de"), "en"))
    }

    @Test
    fun pickingAnotherTargetJustRetargets() {
        assertEquals(Selection("en", "sw"), LanguagePicker.pickTarget(Selection("en", "de"), "sw"))
    }

    @Test
    fun pickingTheTargetOnTheSourceSideSwaps() {
        assertEquals(
            Selection("de", "en"),
            LanguagePicker.pickSource(Selection("en", "de"), "de", ::targetsOf),
        )
    }

    @Test
    fun sourceChangeKeepsAStillValidTarget() {
        assertEquals(
            Selection("de", "sw"),
            LanguagePicker.pickSource(Selection("en", "sw"), "de", ::targetsOf),
        )
    }

    @Test
    fun sourceChangeFallsBackToFirstTargetWhenPickTurnsInvalid() {
        // uk is learnable from en but not from sw → fall back to sw's first target.
        assertEquals(
            Selection("sw", "en"),
            LanguagePicker.pickSource(Selection("en", "uk"), "sw", ::targetsOf),
        )
    }
}
