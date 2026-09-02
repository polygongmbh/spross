package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhraseSlotTests {

    private fun frame(target: String, slug: String) = RealFrames.frame(target, slug)

    // Hand-picked exact instantiations (de → sw)

    @Test
    fun swahiliTrainDeparture() {
        val task = PhraseSlots.instantiate(frame("sw", "train-departs-at"), hour = 20, minute = 0)
        assertEquals("Der Zug fährt um 20:00 Uhr ab.", task.prompt)
        assertEquals("Treni inaondoka saa mbili usiku.", task.display)
        // Day period is optional, so the period-less reading is accepted too;
        // the digital time is always accepted alongside the word readings.
        assertEquals(
            listOf(
                "Treni inaondoka saa mbili.",
                "Treni inaondoka saa mbili usiku.",
                "Treni inaondoka 20:00.",
            ),
            task.accepted,
        )
        assertTrue(task.kind == TrainerKind.Clock && task.language == "sw")
        assertEquals("Saa ± 6h", task.gloss)
    }

    @Test
    fun swahiliWakeUpLowercasesSaaMidSentence() {
        val task = PhraseSlots.instantiate(frame("sw", "i-wake-up-at"), hour = 6, minute = 30)
        assertEquals("Ich wache um 06:30 Uhr auf.", task.prompt)
        assertEquals("Ninaamka saa kumi na mbili na nusu alfajiri.", task.display)
    }

    /** The deleted ≤30 rule: the countdown reading embeds like any other. */
    @Test
    fun swahiliClockEmbedsTheCountdownFormPastHalfPast() {
        val task = PhraseSlots.instantiate(frame("sw", "train-departs-at"), hour = 20, minute = 50)
        assertEquals("Der Zug fährt um 20:50 Uhr ab.", task.prompt)
        assertTrue("kasoro" in task.display, task.display)
        assertTrue(task.display in task.accepted)
    }

    @Test
    fun swahiliPlateCount() {
        val task = PhraseSlots.instantiate(frame("sw", "we-have-n-plates"), value = 347L)
        assertEquals("Wir haben 347 Teller.", task.prompt)
        assertEquals("Tuna sahani mia tatu na arobaini na saba.", task.display)
        // "na"-less drill spelling and the digit form are accepted too.
        assertEquals(
            listOf(
                "Tuna sahani mia tatu na arobaini na saba.",
                "Tuna sahani mia tatu arobaini saba.",
                "Tuna sahani 347.",
            ),
            task.accepted,
        )
        assertEquals(TrainerKind.Numbers, task.kind)
    }

    // Grouped prompts inside a sentence

    /**
     * The sentence prompt is filled twice: once with the machine value everything parses,
     * once with the grouped one the learner reads. Both digit forms grade, so copying the
     * separator out of the prompt cannot cost the Sprosse.
     */
    @Test
    fun aLongSlotGroupsInTheDisplayedSentenceAndBothDigitFormsGrade() {
        val task = PhraseSlots.instantiate(frame("sw", "we-have-n-plates"), value = 12345L)
        assertEquals("Wir haben 12345 Teller.", task.prompt)
        assertEquals("Wir haben 12\u202F345 Teller.", task.promptDisplay)
        assertTrue("Tuna sahani 12345." in task.accepted, task.accepted.toString())
        assertTrue("Tuna sahani 12\u202F345." in task.accepted, task.accepted.toString())
    }

    @Test
    fun shortAndClockSlotsLeaveThePromptUntouched() {
        val plates = PhraseSlots.instantiate(frame("sw", "we-have-n-plates"), value = 347L)
        assertEquals(plates.prompt, plates.promptDisplay)
        val departure = PhraseSlots.instantiate(frame("sw", "train-departs-at"), hour = 20, minute = 0)
        assertEquals(departure.prompt, departure.promptDisplay)
        val year = PhraseSlots.instantiate(frame("sw", "learning-since-year"), value = 2000L)
        assertEquals(year.prompt, year.promptDisplay)
    }

    /**
     * Reversed, a phrase shows the target sentence and asks for the bare value —
     * never for the source sentence, which is a translation exercise the drill
     * does not grade.
     */
    @Test
    fun reversingAPhraseAsksForTheSlotValueOnly() {
        val plates = Trainer.reversed(PhraseSlots.instantiate(frame("sw", "we-have-n-plates"), value = 347L))
        assertEquals("Tuna sahani mia tatu na arobaini na saba.", plates.prompt)
        assertEquals(listOf("347"), plates.accepted)

        val departure = Trainer.reversed(
            PhraseSlots.instantiate(frame("sw", "train-departs-at"), hour = 8, minute = 5),
        )
        assertEquals(listOf("08:05", "8:05"), departure.accepted)

        val fraction = Trainer.reversed(
            RealFrames.instantiate(RealFrames.frame("de", "i-need-n-kilo-of-flour", source = "en"), value = 0L),
        )
        assertEquals("Ich brauche ein Viertel Kilo Mehl.", fraction.prompt)
        assertEquals(listOf("1/4"), fraction.accepted)

        for (template in RealFrames.all) {
            val task = RealFrames.instantiate(template, value = 21L)
            val back = Trainer.reversed(task)
            val where = "${template.source}→${template.target} ${template.id}"
            assertEquals(task.display, back.prompt, where)
            assertTrue(back.accepted.isNotEmpty(), where)
            assertTrue(back.display in back.accepted, where)
            assertTrue(
                back.accepted.all { answer -> answer.all { c -> c.isDigit() || c == ':' || c == '/' } },
                "$where: ${back.accepted}",
            )
        }
    }

    @Test
    fun swahiliYearSince() {
        val task = PhraseSlots.instantiate(frame("sw", "learning-since-year"), value = 2000L)
        // Both sides name the TARGET out of their own table: the German prompt says which
        // language is being learned, and the Swahili answer says the same about itself.
        assertEquals("Ich lerne seit 2000 Suaheli.", task.prompt)
        // "tangu mwaka …" — bare cardinal after tangu doesn't read as a year
        // (language-review fix).
        assertEquals("Ninajifunza Kiswahili tangu mwaka elfu mbili.", task.display)
        assertEquals(TrainerKind.Years, task.kind)
        assertEquals("Jahreszahl als Kardinalzahl gelesen — mwaka = Jahr", task.gloss)
    }

    // Hand-picked exact instantiations (de → uk)

    @Test
    fun ukrainianClockNowFullHour() {
        val task = PhraseSlots.instantiate(frame("uk", "it-is-now"), hour = 14, minute = 0)
        assertEquals("Es ist jetzt 14:00 Uhr.", task.prompt)
        assertEquals("Зараз друга година дня.", task.display)
        assertTrue("Зараз друга година." in task.accepted)
        assertTrue("Зараз друга." in task.accepted)
        assertTrue("Зараз чотирнадцята година." in task.accepted)
        assertTrue("Зараз 14:00." in task.accepted)
        assertTrue(task.kind == TrainerKind.Clock && task.language == "uk")
    }

    @Test
    fun ukrainianClockNowGenericMinutes() {
        val task = PhraseSlots.instantiate(frame("uk", "it-is-now"), hour = 14, minute = 35)
        assertEquals("Es ist jetzt 14:35 Uhr.", task.prompt)
        assertEquals("Зараз за двадцять п'ять третя дня.", task.display)
        assertTrue("Зараз за двадцять п'ять третя." in task.accepted)
        assertTrue("Зараз друга тридцять п'ять." in task.accepted)
    }

    @Test
    fun ukrainianAlarmClockHalfPast() {
        val task = PhraseSlots.instantiate(frame("uk", "alarm-clock-shows"), hour = 2, minute = 30)
        assertEquals("Der Wecker zeigt 02:30 Uhr.", task.prompt)
        assertEquals("На будильнику пів на третю ночі.", task.display)
        assertTrue("На будильнику пів третьої." in task.accepted)
        assertTrue("На будильнику друга тридцять." in task.accepted)
    }

    @Test
    fun ukrainianPriceKeepsIndeclinableEuro() {
        val task = PhraseSlots.instantiate(frame("uk", "it-costs-n-euros"), value = 21L)
        assertEquals("Це двадцять один євро.", task.display)
        // Feminine "двадцять одна" must NOT be accepted before євро
        // (language-review fix: masculineNumeral filter).
        assertEquals(listOf("Це двадцять один євро.", "Це 21 євро."), task.accepted)
    }

    @Test
    fun masculineNumeralFilterKeepsThousandsButDropsFeminineLastWord() {
        val notebooks = frame("uk", "i-have-n-notebooks")
        val t1000 = PhraseSlots.instantiate(notebooks, value = 1000L)
        // "одна тисяча" (feminine multiplier mid-value) and bare "тисяча"
        // stay accepted — only variants ENDING in одна/дві are dropped.
        assertTrue("У мене є одна тисяча зошитів." in t1000.accepted)
        assertTrue("У мене є тисяча зошитів." in t1000.accepted)
        val t21 = PhraseSlots.instantiate(notebooks, value = 21L)
        assertEquals(listOf("У мене є двадцять один зошит.", "У мене є 21 зошит."), t21.accepted)
    }

    @Test
    fun ukrainianCountedNounAgreement() {
        val notebooks = frame("uk", "i-have-n-notebooks")
        assertEquals("У мене є двадцять п'ять зошитів.", PhraseSlots.instantiate(notebooks, value = 25L).display)
        assertEquals("У мене є двадцять два зошити.", PhraseSlots.instantiate(notebooks, value = 22L).display)
        assertEquals("У мене є двадцять один зошит.", PhraseSlots.instantiate(notebooks, value = 21L).display)
        assertEquals("У мене є одинадцять зошитів.", PhraseSlots.instantiate(notebooks, value = 11L).display)
        assertEquals(
            "У нас є чотири стільці.",
            PhraseSlots.instantiate(frame("uk", "we-have-n-chairs"), value = 4L).display,
        )
    }

    @Test
    fun ukrainianYearDictation() {
        val task = PhraseSlots.instantiate(frame("uk", "repeat-the-year"), value = 1978L)
        assertEquals("Повторіть, будь ласка, дату: одна тисяча дев'ятсот сімдесят вісім.", task.display)
        assertTrue("Повторіть, будь ласка: тисяча дев'ятсот сімдесят вісім." in task.accepted)
    }

    // Hand-picked exact instantiations (de → en)

    @Test
    fun englishTrainDepartureCrossesTheVariantFrame() {
        val task = PhraseSlots.instantiate(RealFrames.frame("en", "train-departs-at"), hour = 20, minute = 15)
        assertEquals("Der Zug fährt um 20:15 Uhr ab.", task.prompt)
        assertEquals("The train departs at quarter past eight.", task.display)
        // Every reading × both authored frames; the digital time closes each frame's run.
        for (frame in listOf("The train departs at", "The train leaves at")) {
            for (reading in listOf(
                "quarter past eight", "a quarter past eight", "quarter after eight",
                "fifteen past eight", "fifteen minutes past eight", "fifteen after eight",
                "eight fifteen", "quarter past eight in the evening",
                "eight fifteen in the evening", "twenty fifteen", "20:15",
            )) {
                assertTrue("$frame $reading." in task.accepted, "$frame $reading.")
            }
        }
        assertEquals(task.accepted.size, task.accepted.toSet().size)
        assertTrue(task.accepted.last().endsWith("20:15."))
    }

    @Test
    fun englishPlateCountAcceptsTheHyphenlessSpelling() {
        val task = PhraseSlots.instantiate(RealFrames.frame("en", "we-have-n-plates"), value = 22L)
        assertEquals("Wir haben 22 Teller.", task.prompt)
        assertEquals("We have twenty-two plates.", task.display)
        assertEquals(
            listOf(
                "We have twenty-two plates.",
                "We have twenty two plates.",
                "We have 22 plates.",
                "We've got twenty-two plates.",
                "We've got twenty two plates.",
                "We've got 22 plates.",
            ),
            task.accepted,
        )
    }

    // Hand-picked exact instantiations (de → es)

    /** The Spanish reading carries its own copula, so the frame supplies none. */
    @Test
    fun spanishClockNowKeepsTheSingularCopulaAtOne() {
        val task = PhraseSlots.instantiate(RealFrames.frame("es", "it-is-now"), hour = 13, minute = 30)
        assertEquals("Es ist jetzt 13:30 Uhr.", task.prompt)
        assertEquals("Ahora es la una y media de la tarde.", task.display)
        for (sentence in listOf(
            "Ahora es la una y media.",
            "Ahora la una y media de la tarde.",
            "Ahora una y media.",
            "Ahora es la una y treinta.",
            "Ahora es la una treinta.",
            "Ahora 13:30.",
        )) {
            assertTrue(sentence in task.accepted, "$sentence missing from ${task.accepted}")
        }
    }

    /** Apocope and the feminine both grade; the usted frame doubles every one of them. */
    @Test
    fun spanishDictationAcceptsEveryNumeralGenderAndBothRegisters() {
        val task = PhraseSlots.instantiate(RealFrames.frame("es", "write-please"), value = 21L)
        assertEquals("Escribe, por favor: veintiuno.", task.display)
        assertEquals(
            listOf(
                "Escribe, por favor: veintiuno.",
                "Escribe, por favor: veintiún.",
                "Escribe, por favor: veintiuna.",
                "Escribe, por favor: 21.",
                "Escriba, por favor: veintiuno.",
                "Escriba, por favor: veintiún.",
                "Escriba, por favor: veintiuna.",
                "Escriba, por favor: 21.",
            ),
            task.accepted,
        )
    }

    // Fraction slots — the recipe frame, where the fraction is a bare noun

    @Test
    fun germanRecipeFractionDropsInAsANoun() {
        val task = PhraseSlots.instantiate(
            RealFrames.frame("de", "i-need-n-kilo-of-flour", source = "en"), 1L, 4L,
        )
        assertEquals("I need 1/4 of a kilo of flour.", task.prompt)
        assertEquals("Ich brauche ein Viertel Kilo Mehl.", task.display)
        assertEquals(
            listOf("Ich brauche ein Viertel Kilo Mehl.", "Ich brauche 1/4 Kilo Mehl."),
            task.accepted,
        )
        assertEquals(TrainerKind.Fraction, task.kind)
    }

    @Test
    fun englishAndSpanishRecipeFractionsKeepTheirOwnShape() {
        val english = PhraseSlots.instantiate(RealFrames.frame("en", "i-need-n-kilo-of-flour"), 3L, 4L)
        assertEquals("I need three quarters of a kilo of flour.", english.display)
        assertTrue("I need three fourths of a kilo of flour." in english.accepted, english.accepted.toString())
        val spanish = PhraseSlots.instantiate(RealFrames.frame("es", "i-need-n-kilo-of-flour"), 1L, 3L)
        assertEquals("Necesito un tercio de kilo de harina.", spanish.display)
    }

    /**
     * The one rule the slot draw adds over the Forms ladder's: no halves. German and Spanish
     * read 1/2 adjectivally ("ein halb", "medio"), so it would have to agree with the noun
     * beside it — and the only agreement device runs the other way round.
     */
    @Test
    fun aFractionSlotNeverDrawsAHalfAndStaysReduced() {
        val rng = Random(20260807)
        val pattern = Regex("""(\d+)/(\d+)""")
        for (template in RealFrames.all.filter { it.slotKind == TrainerKind.Fraction }) {
            val unitOnly = mutableSetOf<Boolean>()
            for (level in 1..Trainer.maxLevel(TrainerKind.Fraction)) {
                repeat(80) {
                    val task = PhraseSlots.sample(template, level, rng)
                    val (n, d) = pattern.find(task.prompt)!!.destructured
                    val where = "${template.target} L$level: $n/$d"
                    assertTrue(d.toInt() >= 3, where)
                    assertTrue(n.toInt() < d.toInt(), where)
                    assertEquals(1, gcd(n.toInt(), d.toInt()), where)
                    if (level == 1) assertTrue(n == "1" && d.toInt() <= 4, where)
                    if (level == 2) unitOnly += n == "1"
                }
            }
            assertTrue(false in unitOnly, "${template.target}: the top Sprosse never left the unit fractions")
        }
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    // Variant frames

    /** A variant frame is graded, never displayed — one sentence per frame × slot rendering. */
    @Test
    fun variantFrameExpandsIntoAcceptedNeverIntoDisplay() {
        val task = PhraseSlots.instantiate(RealFrames.frame("de", "repeat-please", source = "sw"), value = 7L)
        assertEquals("Wiederholen Sie bitte: sieben.", task.display)
        assertEquals(
            listOf(
                "Wiederholen Sie bitte: sieben.",
                "Wiederholen Sie bitte: 7.",
                "Wiederhole bitte: sieben.",
                "Wiederhole bitte: 7.",
            ),
            task.accepted,
        )
    }

    @Test
    fun syntheticVariantFramesCrossEverySlotRendering() {
        val synthetic = PhraseTemplate(
            id = "test-variants", source = "de", target = "sw",
            sourceTemplate = "Sag {slot}.",
            targetTemplate = "Sema {slot}.",
            slotKind = TrainerKind.Clock,
            acceptedFrames = listOf("Tafadhali sema {slot}."),
        )
        val task = PhraseSlots.instantiate(synthetic, hour = 8, minute = 0)
        assertEquals("Sema saa mbili asubuhi.", task.display)
        assertEquals(
            listOf(
                "Sema saa mbili.", "Sema saa mbili asubuhi.",
                "Sema 08:00.", "Sema 8:00.",
                "Tafadhali sema saa mbili.", "Tafadhali sema saa mbili asubuhi.",
                "Tafadhali sema 08:00.", "Tafadhali sema 8:00.",
            ),
            task.accepted,
        )
    }

    // Casing at sentence start

    /**
     * German readings begin on NOUNS, whose capital is part of the word — the composer
     * lowercased every mid-sentence reading for Swahili's sake and spelled those wrong.
     */
    @Test
    fun germanNounReadingsKeepTheirCapitalMidSentence() {
        val train = RealFrames.frame("de", "train-departs-at", source = "en")
        val midnight = PhraseSlots.instantiate(train, hour = 0, minute = 0)
        assertEquals("Der Zug fährt um Mitternacht ab.", midnight.display)
        assertTrue(midnight.accepted.none { "mitternacht" in it }, midnight.accepted.toString())
        assertEquals(
            "Der Zug fährt um Viertel nach acht ab.",
            PhraseSlots.instantiate(train, hour = 8, minute = 15).display,
        )
        val noon = PhraseSlots.instantiate(RealFrames.frame("de", "it-is-now", source = "uk"), 12, 0)
        assertEquals("Es ist jetzt Mittag.", noon.display)
    }

    @Test
    fun sentenceInitialSlotKeepsCapital() {
        val synthetic = PhraseTemplate(
            id = "test-initial", source = "de", target = "sw",
            sourceTemplate = "{slot} Uhr.",
            targetTemplate = "{slot}, sawa?",
            slotKind = TrainerKind.Clock,
        )
        val task = PhraseSlots.instantiate(synthetic, hour = 20, minute = 0)
        assertEquals("Saa mbili usiku, sawa?", task.display)
    }

    // Note + gloss merging

    @Test
    fun notesMergeTemplateThenSlot() {
        val task = PhraseSlots.instantiate(frame("uk", "alarm-clock-shows"), hour = 2, minute = 15)
        val gloss = task.gloss ?: ""
        assertTrue(gloss.startsWith("wörtl.: „Auf dem Wecker [ist] …“ · "))
        // The frame's note is authored per source language; the slot's names the
        // alternative readings, and does so in the language being answered in.
        assertTrue("також: п'ятнадцять хвилин на третю" in gloss, gloss)
    }

    // Every accepted frame × slot variant → exactly one accepted sentence

    /**
     * Structural sweep over every joined pair, at every clock position and value the drill
     * reaches. Deliberately NOT a second copy of [PhraseSlots.compose] — the exact sentences
     * are pinned by the hand-written examples above, one language at a time. What is asserted
     * here is only what restating the rules could not tell us: nothing repeats, and the
     * sentence the reveal teaches is one of the sentences it grades.
     */
    @Test
    fun everyPairAssemblesDistinctAnswersTheDisplayBelongsTo() {
        for (template in RealFrames.all) {
            val tasks = when (template.slotKind) {
                TrainerKind.Clock ->
                    listOf(0, 6, 9, 13, 14, 20, 23).flatMap { h ->
                        listOf(0, 15, 20, 30, 35, 45, 55).map { m ->
                            PhraseSlots.instantiate(template, hour = h, minute = m)
                        }
                    }
                TrainerKind.Fraction ->
                    listOf(1L to 3L, 1L to 4L, 2L to 3L, 3L to 4L, 5L to 12L)
                        .map { (n, d) -> PhraseSlots.instantiate(template, n, d) }
                else ->
                    listOf(1L, 2L, 5L, 11L, 21L, 22L, 25L, 100L, 347L, 1000L, 1978L, 2026L)
                        .map { PhraseSlots.instantiate(template, value = it) }
            }
            for (task in tasks) {
                val where = "${template.source}→${template.target} ${template.id}"
                assertTrue(task.accepted.isNotEmpty(), where)
                assertEquals(task.accepted.size, task.accepted.toSet().size, "$where: duplicates")
                assertTrue(task.display in task.accepted, "$where: ${task.display}")
            }
        }
    }

    @Test
    fun samplingIsDeterministicAndMatchesInstantiate() {
        for (template in RealFrames.all) {
            val a = Random(0xC0FFEE)
            val b = Random(0xC0FFEE)
            repeat(50) {
                val sampled = PhraseSlots.sample(template, a)
                // Reconstruct with the same-seeded RNG draws, through the Trainer's own
                // sampler — an independent path to the value the composition used.
                val slot = Trainer.sample(template.slotKind, template.target, b)
                val expected = when (template.slotKind) {
                    TrainerKind.Clock -> {
                        val parts = slot.prompt.split(":").map { it.toInt() }
                        PhraseSlots.instantiate(template, hour = parts[0], minute = parts[1])
                    }
                    TrainerKind.Fraction -> {
                        val parts = slot.prompt.split("/").map { it.toLong() }
                        PhraseSlots.instantiate(template, parts[0], parts[1])
                    }
                    else -> PhraseSlots.instantiate(template, value = slot.prompt.toLong())
                }
                assertEquals(expected, sampled, template.id)
            }
        }
    }

    // Join shape (per-file structural rules live in CatalogFrameLintTest)

    @Test
    fun theJoinIsSymmetricAndPerPairUnique() {
        assertTrue(RealFrames.of("de", "sw").size >= 10)
        assertTrue(RealFrames.of("de", "uk").size >= 10)
        // Authoring a language lights up both directions with the same frame set.
        assertEquals(RealFrames.of("de", "sw").map { it.id }, RealFrames.of("sw", "de").map { it.id })
        for (template in RealFrames.all) {
            assertTrue(template.source != template.target, template.id)
            assertTrue(template.sourceTemplate != template.targetTemplate, template.id)
        }
        for ((source, target) in RealFrames.all.map { it.source to it.target }.distinct()) {
            val ids = RealFrames.of(source, target).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "duplicate frame id in $source→$target")
        }
    }

    /**
     * The Forms seal, checked where a frame is BUILT rather than where it is drawn:
     * a number form has no phrase generator, and a template carrying one would only
     * blow up at the first draw, in a run, with a catalog long since shipped.
     */
    @Test
    fun aFormsSlotIsRejectedWhenTheTemplateIsBuilt() {
        assertFailsWith<IllegalArgumentException> {
            PhraseTemplate(
                id = "n-th-place", source = "de", target = "en",
                sourceTemplate = "Ich bin auf Platz {slot}.",
                targetTemplate = "I am in {slot} place.",
                slotKind = TrainerKind.Forms,
            )
        }
    }

    /** `copy` runs the same init, so the seal holds however a template is derived. */
    @Test
    fun aFormsSlotIsRejectedWhenATemplateIsCopiedIntoOne() {
        assertFailsWith<IllegalArgumentException> {
            RealFrames.frame("sw", "we-have-n-plates").copy(slotKind = TrainerKind.Forms)
        }
    }

    /**
     * No marker ever reaches the learner. Agreement is authored per realization, so the
     * language supplying the PROMPT fills its own `{count}` — uk authors it on three frames,
     * which used to surface literally the moment Ukrainian became a source (uk→en, uk→sw).
     */
    @Test
    fun noMarkerSurvivesIntoAnythingTheLearnerSees() {
        val rng = Random(20260802)
        for (template in RealFrames.all) {
            // 21 and 13 straddle the Slavic agreement split (one / many).
            val tasks = listOf(21L, 13L).map { RealFrames.instantiate(template, it, hour = 21) } +
                listOf(PhraseSlots.sample(template, rng))

            for (task in tasks) {
                for (surface in listOf(task.prompt, task.display) + task.accepted) {
                    for (marker in listOf(PhraseTemplate.SLOT_MARKER, PhraseTemplate.COUNT_MARKER)) {
                        assertTrue(
                            marker !in surface,
                            "${template.source}→${template.target} ${template.id}: „$marker“ reached the learner in „$surface“",
                        )
                    }
                }
            }
        }
    }
}
