package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("Saa ± 6h · asubuhi/mchana/jioni/usiku optional", task.gloss)
    }

    @Test
    fun swahiliWakeUpLowercasesSaaMidSentence() {
        val task = PhraseSlots.instantiate(frame("sw", "i-wake-up-at"), hour = 6, minute = 30)
        assertEquals("Ich wache um 06:30 Uhr auf.", task.prompt)
        assertEquals("Ninaamka saa kumi na mbili na nusu asubuhi.", task.display)
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

    @Test
    fun swahiliYearSince() {
        val task = PhraseSlots.instantiate(frame("sw", "learning-since-year"), value = 2000L)
        assertEquals("Ich lerne seit 2000 Deutsch.", task.prompt)
        // "tangu mwaka …" — bare cardinal after tangu doesn't read as a year
        // (language-review fix).
        assertEquals("Ninajifunza Kijerumani tangu mwaka elfu mbili.", task.display)
        assertEquals(TrainerKind.Years, task.kind)
        assertEquals("Jahreszahl als Kardinalzahl gelesen — mwaka = Jahr", task.gloss)
    }

    // Hand-picked exact instantiations (de → uk)

    @Test
    fun ukrainianClockNowFullHour() {
        val task = PhraseSlots.instantiate(frame("uk", "it-is-now"), hour = 14, minute = 0)
        assertEquals("Es ist jetzt 14:00 Uhr.", task.prompt)
        assertEquals("Зараз друга година.", task.display)
        assertEquals(listOf("Зараз друга година.", "Зараз друга.", "Зараз 14:00."), task.accepted)
        assertTrue(task.kind == TrainerKind.Clock && task.language == "uk")
    }

    @Test
    fun ukrainianClockNowGenericMinutes() {
        val task = PhraseSlots.instantiate(frame("uk", "it-is-now"), hour = 14, minute = 35)
        assertEquals("Es ist jetzt 14:35 Uhr.", task.prompt)
        assertEquals("Зараз друга тридцять п'ять.", task.display)
        assertTrue("Зараз за двадцять п'ять третя." in task.accepted)
    }

    @Test
    fun ukrainianAlarmClockHalfPast() {
        val task = PhraseSlots.instantiate(frame("uk", "alarm-clock-shows"), hour = 2, minute = 30)
        assertEquals("Der Wecker zeigt 02:30 Uhr.", task.prompt)
        assertEquals("На будильнику пів на третю.", task.display)
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
        assertEquals("Повторіть, будь ласка: одна тисяча дев'ятсот сімдесят вісім.", task.display)
        assertTrue("Повторіть, будь ласка: тисяча дев'ятсот сімдесят вісім." in task.accepted)
    }

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
                "Sema saa mbili.", "Sema saa mbili asubuhi.", "Sema 08:00.", "Sema 8:00.",
                "Tafadhali sema saa mbili.", "Tafadhali sema saa mbili asubuhi.",
                "Tafadhali sema 08:00.", "Tafadhali sema 8:00.",
            ),
            task.accepted,
        )
    }

    // Casing at sentence start

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
        assertTrue("чверть на" in gloss)
    }

    // Every accepted frame × slot variant → exactly one accepted sentence

    @Test
    fun everyAcceptedVariantAppearsInExactlyOneSentence() {
        for (template in RealFrames.all) {
            when (template.slotKind) {
                TrainerKind.Clock ->
                    for (h in listOf(0, 6, 9, 13, 14, 20, 23)) {
                        for (m in listOf(0, 15, 20, 30, 35, 45, 55)) {
                            val slot = Trainer.clock(h, m, template.target)
                            val task = PhraseSlots.instantiate(template, hour = h, minute = m)
                            verifyVariantCoverage(template, slot, task, value = null)
                        }
                    }
                TrainerKind.Numbers, TrainerKind.Years ->
                    for (v in listOf(1L, 2L, 5L, 11L, 21L, 22L, 25L, 100L, 347L, 1000L, 1978L, 2026L)) {
                        val slot = if (template.slotKind == TrainerKind.Numbers) {
                            Trainer.drillNumber(v, template.target)
                        } else {
                            Trainer.year(v, template.target)
                        }
                        val task = PhraseSlots.instantiate(template, value = v)
                        verifyVariantCoverage(template, slot, task, value = v)
                    }
            }
        }
    }

    /**
     * Independent re-rendering of the spec: one target frame with the variant
     * substituted (mid-sentence values lowercase their first letter).
     */
    private fun render(frame: String, template: PhraseTemplate, variant: String, value: Long?): String {
        val index = frame.indexOf("{slot}")
        if (index < 0) return ""
        val atStart = frame.take(index).isBlank()
        var words = variant
        words.firstOrNull()?.let { f ->
            words = (if (atStart) f.uppercase() else f.lowercase()) + words.substring(1)
        }
        var sentence = frame.replace("{slot}", words)
        val forms = template.countForms
        if (forms != null && value != null) {
            sentence = sentence.replace("{count}", forms.form(value))
        }
        return sentence
    }

    private fun verifyVariantCoverage(
        template: PhraseTemplate,
        slot: TrainerTask,
        task: TrainerTask,
        value: Long?,
    ) {
        // masculineNumeralOnly templates drop feminine-final variants by design.
        val written = slot.accepted.filter { variant ->
            if (!template.masculineNumeralOnly) return@filter true
            val last = variant.substringAfterLast(' ')
            last != "одна" && last != "дві"
        }
        // The digit rendering(s) are accepted after the word variants
        // (clock: zero-padded and bare-hour digital time).
        val digits = if (template.slotKind == TrainerKind.Clock) {
            val bare = slot.prompt.substringBefore(':').toInt().toString() +
                ":" + slot.prompt.substringAfter(':')
            listOf(slot.prompt, bare).distinct()
        } else {
            listOf(slot.prompt)
        }
        val renderings = (written + digits).distinct()
        val frames = listOf(template.targetTemplate) + template.acceptedFrames
        val expected = frames
            .flatMap { frame -> renderings.map { render(frame, template, it, value) } }
            .distinct()
        assertEquals(expected, task.accepted, "${template.id}: sentence per frame × variant")
        assertEquals(task.accepted.size, task.accepted.toSet().size, "${template.id}: duplicates")
        assertEquals(render(template.targetTemplate, template, slot.display, value), task.display)
        assertTrue(task.display in task.accepted)
    }

    // Sampling

    @Test
    fun samplingIsDeterministicAndMatchesInstantiate() {
        for (template in RealFrames.all) {
            val a = Random(0xC0FFEE)
            val b = Random(0xC0FFEE)
            repeat(50) {
                val sampled = PhraseSlots.sample(template, a)
                // Reconstruct with the same-seeded RNG draws.
                val expected = if (template.slotKind == TrainerKind.Clock) {
                    val slot = Trainer.sample(TrainerKind.Clock, template.target, b)
                    val parts = slot.prompt.split(":").map { it.toInt() }
                    PhraseSlots.instantiate(template, hour = parts[0], minute = parts[1])
                } else {
                    val slot = Trainer.sample(template.slotKind, template.target, b)
                    PhraseSlots.instantiate(template, value = slot.prompt.toLong())
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
}
