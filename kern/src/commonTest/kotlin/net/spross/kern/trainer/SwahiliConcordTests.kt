package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Swahili noun-class concord on the cardinals, pinned per class.
 *
 * Golden vectors from Almasi, Fallon & Wared, *Swahili Grammar for Introductory and
 * Intermediate Levels* (UPA 2014), ch. 18 "Numbers"
 * (https://hist.hse.ru/data/2019/06/14/1486229742/18.%20Numbers.pdf):
 * Table 18.1 (p. 188) gives the prefixes — JI-/MA- "no prefix" singular (*jicho moja*) /
 * `ma-` plural (*macho mawili*), KI-/VI- `ki-` (*kiti kimoja*) / `vi-` (*viti viwili*);
 * p. 187 gives the stem rule ("The number 2 is the only number that changes form before
 * having a prefix attached") and the single-digit-column rule.
 * Corroborated per stem by Wiktionary's declension tables for `-wili`, `moja`, `tatu`,
 * `nne`, `tano`, `nane`.
 *
 * Vectors marked ATTESTED quote a phrase printed in a source; the rest are derived from
 * those two rules. The derived ones are safe because the geminate and the multiplier cases
 * — the only two places the derivation could bend — are themselves attested below.
 */
class SwahiliConcordTests {

    private fun kiVi(n: Long) = SwahiliConcord.cardinal(n, SwahiliConcord.NounClass.KI_VI)

    private fun jiMa(n: Long) = SwahiliConcord.cardinal(n, SwahiliConcord.NounClass.JI_MA)

    /** KI-VI takes `ki-` on one and `vi-` on the rest — *kiti kimoja*, *viti viwili*. */
    @Test
    fun theKiViClassPrefixesKiOnOneAndViOnEveryOtherStem() {
        assertEquals("kimoja", kiVi(1)) // ATTESTED: "kiti kimoja - one chair" (Table 18.1)
        assertEquals("viwili", kiVi(2)) // ATTESTED: "viti viwili - two chairs" (Table 18.1)
        assertEquals("vitatu", kiVi(3)) // ATTESTED: "Vichwa vitatu - three heads" (p. 187)
        assertEquals("vinne", kiVi(4))
        assertEquals("vitano", kiVi(5)) // ATTESTED: "vikombe vitano vya chai"
        assertEquals("vinane", kiVi(8))
    }

    /** JI-MA leaves the singular bare — Table 18.1 spells the prefix "No prefix". */
    @Test
    fun theJiMaClassLeavesOneBareAndPrefixesMaOnEveryOtherStem() {
        assertEquals("moja", jiMa(1)) // ATTESTED: "jicho moja - one eye" (Table 18.1)
        assertEquals("mawili", jiMa(2)) // ATTESTED: "macho mawili - two eyes" (Table 18.1)
        assertEquals("matatu", jiMa(3)) // ATTESTED: "Matunda matatu"
        assertEquals("manne", jiMa(4)) // ATTESTED: "mayai ... ishirini na manne"
        assertEquals("matano", jiMa(5)) // ATTESTED: "Magari matano - five cars" (p. 187)
        assertEquals("manane", jiMa(8)) // ATTESTED: "Magari tisini na manane" (p. 187)
    }

    /**
     * The whole reason this file cannot be built on [SwahiliNumbers]: its `2` is already
     * the N-class's own concorded output, so *mbili* is a sibling of *viwili*, not a base
     * for it. Getting this backwards would spell every other class through a nasal mutation
     * it never had.
     */
    @Test
    fun twoIsTheOneStemThatChangesShapeSoMbiliIsNotABase() {
        assertEquals("mbili", SwahiliNumbers.cardinal(2))
        assertEquals("viwili", kiVi(2))
        assertEquals("mawili", jiMa(2))
        // ...and the geminate of 4 survives the prefix rather than collapsing to *vine.
        assertEquals("vinne", kiVi(4))
        assertEquals("manne", jiMa(4))
    }

    /** 6/7/9 are Arabic loans: no class ever touches them. */
    @Test
    fun theArabicLoansNeverAgreeWithAnything() {
        for (n in listOf(6L, 7L, 9L)) {
            assertEquals(SwahiliNumbers.cardinal(n), kiVi(n))
            assertEquals(SwahiliNumbers.cardinal(n), jiMa(n))
        }
    }

    /**
     * Concord applies in the single-digit column "even if part of a larger number"
     * (p. 187) — and the singular/plural choice follows THAT digit, not the total, which
     * is why 101 takes the singular `ki-` while 98 takes the plural `ma-`.
     */
    @Test
    fun onlyTheTrailingOnesWordAgreesInACompound() {
        // ATTESTED: "Vitabu mia moja na kimoja" (101 books) — note the leading "mia moja"
        // stays bare while the final "moja" takes the SINGULAR prefix.
        assertEquals("mia moja na kimoja", kiVi(101))
        // ATTESTED: "Magari tisini na manane" (98 cars).
        assertEquals("tisini na manane", jiMa(98))
        assertEquals("ishirini na kimoja", kiVi(21))
        assertEquals("mia moja na vinane", kiVi(108))
        assertEquals("elfu moja na mawili", jiMa(1002))
    }

    /**
     * A numeral multiplying *mia* or *elfu* agrees with those, never with the counted noun —
     * ATTESTED by "mabegi ya plastiki mia nane bilioni", where the same stem that concords
     * in the ones column stays bare in multiplier position. Every round value falls out of
     * the same test, since its final digit is zero.
     */
    @Test
    fun aMultiplierOfMiaOrElfuStaysBare() {
        assertEquals("mia nane", kiVi(800))
        assertEquals("mia nane", jiMa(800))
        assertEquals("mia moja", kiVi(100))
        assertEquals("ishirini", kiVi(20))
        assertEquals("elfu mbili", jiMa(2000))
        assertEquals("kumi", jiMa(10))
    }

    /**
     * Past 9999 the sources stop agreeing on where agreement lands (*elfu kumi na nne*),
     * so the reading is left plain rather than invented. A leveled draw can reach here —
     * the numbers ladder goes to ten digits — so it must be a quiet no-op, never a throw.
     */
    @Test
    fun readingsPastTheSourcedCeilingAreLeftPlain() {
        for (n in listOf(0L, -4L, 10_014L, 1_000_000L)) {
            assertEquals(SwahiliNumbers.cardinal(n), kiVi(n))
            assertEquals(SwahiliNumbers.cardinal(n), jiMa(n))
        }
        // ...but everything up to the ceiling still concords, however long the reading.
        assertEquals("elfu tisa na mia tisa na tisini na vinne", kiVi(9994))
    }

    /**
     * The `na`-less spelling stays accepted, as it is in the plain drill — but the
     * unconcorded form never is, because that is the error the frame exists to train.
     */
    @Test
    fun theAcceptedSetDropsConnectorsButNeverTheAgreement() {
        val variants = SwahiliConcord.acceptedVariants(101, SwahiliConcord.NounClass.KI_VI)
        assertTrue("mia moja na kimoja" in variants)
        assertTrue("mia moja kimoja" in variants)
        assertFalse(variants.any { "moja" == it.substringAfterLast(' ') })
        assertEquals(listOf("vinne"), SwahiliConcord.acceptedVariants(4, SwahiliConcord.NounClass.KI_VI))
    }

    /** The frame primitive end to end: the class reaches the rendered sentence. */
    @Test
    fun aClassMarkedFrameRendersTheConcordedNumeral() {
        val chairs = PhraseTemplate(
            id = "we-have-n-chairs", source = "de", target = "sw",
            sourceTemplate = "Wir haben {slot} Stühle.", targetTemplate = "Tuna viti {slot}.",
            slotKind = TrainerKind.Numbers, swahiliNounClass = SwahiliConcord.NounClass.KI_VI,
        )
        assertEquals("Tuna viti vinne.", PhraseSlots.instantiate(chairs, 4).display)
        assertEquals("Tuna viti mia moja na kimoja.", PhraseSlots.instantiate(chairs, 101).display)
        assertTrue("Tuna viti vinne." in PhraseSlots.instantiate(chairs, 4).accepted)
        // An N-class frame carries no class and is untouched by any of this.
        val plates = chairs.copy(id = "we-have-n-plates", targetTemplate = "Tuna sahani {slot}.", swahiliNounClass = null)
        assertEquals("Tuna sahani nne.", PhraseSlots.instantiate(plates, 4).display)
    }
}
