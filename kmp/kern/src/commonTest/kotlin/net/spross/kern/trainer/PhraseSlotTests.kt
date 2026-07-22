package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhraseSlotTests {

    private fun template(id: String): PhraseTemplate =
        PhraseTemplates.all.first { it.id == id }

    // Hand-picked exact instantiations (de → sw)

    @Test
    fun swahiliTrainDeparture() {
        val task = PhraseSlots.instantiate(template("sw-clock-zug"), hour = 20, minute = 0)
        assertEquals("Der Zug fährt um 20:00 Uhr ab.", task.prompt)
        assertEquals("Treni inaondoka saa mbili usiku.", task.display)
        // Day period is optional, so the period-less reading is accepted too.
        assertEquals(
            listOf("Treni inaondoka saa mbili.", "Treni inaondoka saa mbili usiku."),
            task.accepted,
        )
        assertTrue(task.kind == TrainerKind.Clock && task.language == "sw")
        assertEquals("Saa ± 6h · asubuhi/mchana/jioni/usiku optional", task.gloss)
    }

    @Test
    fun swahiliWakeUpLowercasesSaaMidSentence() {
        val task = PhraseSlots.instantiate(template("sw-clock-aufwachen"), hour = 6, minute = 30)
        assertEquals("Ich wache um 06:30 Uhr auf.", task.prompt)
        assertEquals("Ninaamka saa kumi na mbili na nusu asubuhi.", task.display)
    }

    @Test
    fun swahiliPlateCount() {
        val task = PhraseSlots.instantiate(template("sw-num-teller"), value = 347L)
        assertEquals("Wir haben 347 Teller.", task.prompt)
        assertEquals("Tuna sahani mia tatu na arobaini na saba.", task.display)
        assertEquals(listOf("Tuna sahani mia tatu na arobaini na saba."), task.accepted)
        assertEquals(TrainerKind.Numbers, task.kind)
    }

    @Test
    fun swahiliYearSince() {
        val task = PhraseSlots.instantiate(template("sw-year-seit"), value = 2000L)
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
        val task = PhraseSlots.instantiate(template("uk-clock-jetzt"), hour = 14, minute = 0)
        assertEquals("Es ist jetzt 14:00 Uhr.", task.prompt)
        assertEquals("Зараз друга година.", task.display)
        assertEquals(listOf("Зараз друга година.", "Зараз друга."), task.accepted)
        assertTrue(task.kind == TrainerKind.Clock && task.language == "uk")
    }

    @Test
    fun ukrainianClockNowGenericMinutes() {
        val task = PhraseSlots.instantiate(template("uk-clock-jetzt"), hour = 14, minute = 35)
        assertEquals("Es ist jetzt 14:35 Uhr.", task.prompt)
        assertEquals("Зараз друга тридцять п'ять.", task.display)
        assertTrue("Зараз за двадцять п'ять третя." in task.accepted)
    }

    @Test
    fun ukrainianAlarmClockHalfPast() {
        val task = PhraseSlots.instantiate(template("uk-clock-wecker"), hour = 2, minute = 30)
        assertEquals("Der Wecker zeigt 02:30 Uhr.", task.prompt)
        assertEquals("На будильнику пів на третю.", task.display)
        assertTrue("На будильнику пів третьої." in task.accepted)
        assertTrue("На будильнику друга тридцять." in task.accepted)
    }

    @Test
    fun ukrainianPriceKeepsIndeclinableEuro() {
        val task = PhraseSlots.instantiate(template("uk-num-preis"), value = 21L)
        assertEquals("Das kostet 21 Euro.", task.prompt)
        assertEquals("Це двадцять один євро.", task.display)
        // Feminine "двадцять одна" must NOT be accepted before євро
        // (language-review fix: masculineSlot filter).
        assertEquals(listOf("Це двадцять один євро."), task.accepted)
    }

    @Test
    fun masculineSlotFilterKeepsThousandsButDropsFeminineLastWord() {
        val hefte = template("uk-num-hefte")
        val t1000 = PhraseSlots.instantiate(hefte, value = 1000L)
        // "одна тисяча" (feminine multiplier mid-value) and bare "тисяча"
        // stay accepted — only variants ENDING in одна/дві are dropped.
        assertTrue("У мене є одна тисяча зошитів." in t1000.accepted)
        assertTrue("У мене є тисяча зошитів." in t1000.accepted)
        val t21 = PhraseSlots.instantiate(hefte, value = 21L)
        assertEquals(listOf("У мене є двадцять один зошит."), t21.accepted)
    }

    @Test
    fun ukrainianCountedNounAgreement() {
        val hefte = template("uk-num-hefte")
        assertEquals("У мене є двадцять п'ять зошитів.", PhraseSlots.instantiate(hefte, value = 25L).display)
        assertEquals("У мене є двадцять два зошити.", PhraseSlots.instantiate(hefte, value = 22L).display)
        assertEquals("У мене є двадцять один зошит.", PhraseSlots.instantiate(hefte, value = 21L).display)
        assertEquals("У мене є одинадцять зошитів.", PhraseSlots.instantiate(hefte, value = 11L).display)
        assertEquals(
            "У нас є чотири стільці.",
            PhraseSlots.instantiate(template("uk-num-stuehle"), value = 4L).display,
        )
    }

    @Test
    fun ukrainianYearDictation() {
        val task = PhraseSlots.instantiate(template("uk-year-wiederholen"), value = 1978L)
        assertEquals("Wiederholen Sie bitte die Jahreszahl: 1978.", task.prompt)
        assertEquals("Повторіть, будь ласка: одна тисяча дев'ятсот сімдесят вісім.", task.display)
        assertTrue("Повторіть, будь ласка: тисяча дев'ятсот сімдесят вісім." in task.accepted)
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

    // Gloss merging

    @Test
    fun glossesMergeTemplateThenSlot() {
        val task = PhraseSlots.instantiate(template("uk-clock-wecker"), hour = 2, minute = 15)
        val gloss = task.gloss ?: ""
        assertTrue(gloss.startsWith("wörtl.: „Auf dem Wecker [ist] …“ · "))
        assertTrue("чверть на" in gloss)
    }

    // Every accepted slot variant → exactly one accepted sentence

    @Test
    fun everyAcceptedVariantAppearsInExactlyOneSentence() {
        for (template in PhraseTemplates.all) {
            when (template.slotKind) {
                TrainerKind.Clock -> {
                    // Swahili embeds only minutes 0..30 (>30 countdown form is
                    // a standalone predicate — language-review fix).
                    val minutes = if (template.target == "sw") listOf(0, 15, 20, 25, 30)
                    else listOf(0, 15, 20, 30, 35, 45, 55)
                    for (h in listOf(0, 6, 9, 13, 14, 20, 23)) {
                        for (m in minutes) {
                            val slot = Trainer.clock(h, m, template.target)
                            val task = PhraseSlots.instantiate(template, hour = h, minute = m)
                            verifyVariantCoverage(template, slot, task, value = null)
                        }
                    }
                }
                TrainerKind.Numbers, TrainerKind.Years -> {
                    for (v in listOf(1L, 2L, 5L, 11L, 21L, 22L, 25L, 100L, 347L, 1000L, 1978L, 2026L)) {
                        val slot = if (template.slotKind == TrainerKind.Numbers) {
                            Trainer.number(v, template.target)
                        } else {
                            Trainer.year(v, template.target)
                        }
                        val task = PhraseSlots.instantiate(template, value = v)
                        verifyVariantCoverage(template, slot, task, value = v)
                    }
                }
            }
        }
    }

    /**
     * Independent re-rendering of the spec: target sentence with the variant
     * substituted (mid-sentence values lowercase their first letter).
     */
    private fun render(template: PhraseTemplate, variant: String, value: Long?): String {
        val index = template.targetTemplate.indexOf("{slot}")
        if (index < 0) return ""
        val atStart = template.targetTemplate.take(index).isBlank()
        var words = variant
        words.firstOrNull()?.let { f ->
            words = (if (atStart) f.uppercase() else f.lowercase()) + words.substring(1)
        }
        var sentence = template.targetTemplate.replace("{slot}", words)
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
        val expectedVariants = slot.accepted.filter { variant ->
            if (!template.masculineNumeralOnly) return@filter true
            val last = variant.substringAfterLast(' ')
            last != "одна" && last != "дві"
        }
        assertEquals(expectedVariants.size, task.accepted.size, "${template.id}: sentence per variant ($expectedVariants)")
        assertEquals(task.accepted.size, task.accepted.toSet().size, "${template.id}: duplicates")
        for (variant in expectedVariants) {
            val sentence = render(template, variant, value)
            assertEquals(
                1, task.accepted.count { it == sentence },
                "${template.id}: variant „$variant“ missing/duplicated in ${task.accepted}",
            )
        }
        assertEquals(render(template, slot.display, value), task.display)
        assertTrue(task.display in task.accepted)
    }

    // Sampling

    @Test
    fun samplingIsDeterministicAndMatchesInstantiate() {
        for (template in PhraseTemplates.all) {
            val a = Random(0xC0FFEE)
            val b = Random(0xC0FFEE)
            repeat(50) {
                val sampled = PhraseSlots.sample(template, a)
                // Reconstruct with the same-seeded RNG draws.
                val expected: TrainerTask
                if (template.slotKind == TrainerKind.Clock && template.target == "sw") {
                    // Mirrors PhraseSlots.sample's restricted Swahili draw.
                    val hour = b.nextInt(24)
                    val minute = b.nextInt(31)
                    expected = PhraseSlots.instantiate(template, hour = hour, minute = minute)
                } else if (template.slotKind == TrainerKind.Clock) {
                    val slot = Trainer.sample(TrainerKind.Clock, template.target, b)
                    val parts = slot.prompt.split(":").map { it.toInt() }
                    expected = PhraseSlots.instantiate(template, hour = parts[0], minute = parts[1])
                } else {
                    val slot = Trainer.sample(template.slotKind, template.target, b)
                    expected = PhraseSlots.instantiate(template, value = slot.prompt.toLong())
                }
                assertEquals(expected, sampled, template.id)
            }
        }
    }

    // Curated-set invariants

    @Test
    fun curatedSetsAreWellFormed() {
        val deSw = PhraseTemplates.templates(source = "de", target = "sw")
        val deUk = PhraseTemplates.templates(source = "de", target = "uk")
        assertTrue(deSw.size in 10..12)
        assertTrue(deUk.size in 10..12)
        val ids = PhraseTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate template ids")

        for ((target, set) in listOf("sw" to deSw, "uk" to deUk)) {
            assertEquals(TrainerKind.entries.toSet(), set.map { it.slotKind }.toSet(), "de-$target kind coverage")
            for (template in set) {
                assertTrue(template.source == "de" && template.target == target)
                assertEquals(1, occurrences("{slot}", template.sourceTemplate), template.id)
                assertEquals(1, occurrences("{slot}", template.targetTemplate), template.id)
                val hasCountMarker = "{count}" in template.targetTemplate
                assertEquals(template.countForms != null, hasCountMarker, template.id)
                if (template.countForms != null) {
                    assertEquals(TrainerKind.Numbers, template.slotKind, template.id)
                    assertTrue(template.masculineNumeralOnly, template.id)
                }
            }
        }
    }

    private fun occurrences(marker: String, text: String): Int =
        text.split(marker).size - 1
}
