package net.spross.kern.trainer

import kotlin.random.Random

/**
 * Instantiates a [PhraseTemplate] into a [TrainerTask] by composing with the
 * Trainer slot generators: the source-language prompt gets the digits, the
 * target display gets the canonical words, accepted gets one full sentence
 * per accepted FRAME × accepted slot RENDERING — every authored variant frame
 * crossed with every written-out generator variant plus the digit form(s), in
 * both drill directions (user report: word forms typed mid-sentence were
 * rejected). Pure — sampling takes an injected [Random].
 */
object PhraseSlots {

    /**
     * Clock templates. Any minute is read out by the language clocks; the
     * source prompt shows the digital time ("… um 14:35 Uhr …").
     */
    fun instantiate(template: PhraseTemplate, hour: Int, minute: Int): TrainerTask {
        require(template.slotKind == TrainerKind.Clock) { "hour/minute instantiation requires a Clock template" }
        val slot = Trainer.clock(hour, minute, template.target)
        return compose(template, slot, value = null)
    }

    /** Number and year templates. */
    fun instantiate(template: PhraseTemplate, value: Long): TrainerTask {
        require(template.slotKind != TrainerKind.Clock) { "clock templates take hour/minute" }
        val slot = when (template.slotKind) {
            // why: drill accepted set — sw speakers routinely drop the "na" connectors
            TrainerKind.Numbers -> Trainer.drillNumber(value, template.target)
            else -> Trainer.year(value, template.target)
        }
        return compose(template, slot, value)
    }

    /**
     * Full-difficulty sampling with the Trainer's ported biases
     * (numbers 10–9999 weighted to 2–3 digits, years around 1950–2050).
     * Clock is the leveled sampler at max level: any hour and any minute.
     */
    fun sample(template: PhraseTemplate, rng: Random): TrainerTask {
        if (template.slotKind == TrainerKind.Clock) {
            return sample(template, Trainer.maxLevel(TrainerKind.Clock), rng)
        }
        val slot = Trainer.sample(template.slotKind, template.target, rng)
        // why: slot.prompt is the Trainer's numeric contract ("347"/"1978"),
        // so instantiate rebuilds the identical task from the sampled value.
        return instantiate(template, value = slot.prompt.toLong())
    }

    /**
     * Level-aware sampling for the gentle sentence-drill ramp: the slot value
     * is drawn with the SAME level semantics as the plain drills (numbers:
     * level = digit count; years: recent decades → historic range; clock:
     * full hours → any minute — see the leveled [Trainer.sample]), then
     * instantiated, so accepted sentences stay identical to [instantiate].
     */
    fun sample(template: PhraseTemplate, level: Int, rng: Random): TrainerTask {
        if (template.slotKind == TrainerKind.Clock) {
            return instantiate(template, rng.nextInt(24), Trainer.clockMinute(level, rng))
        }
        val slot = Trainer.sample(template.slotKind, template.target, level, rng)
        // why: slot.prompt is the Trainer's numeric contract ("347"/"1978"),
        // so counted-noun agreement can reuse the sampled value exactly.
        return instantiate(template, value = slot.prompt.toLong())
    }

    // Reverse (target sentence shown, source language typed)

    /**
     * Reverse drill for learners of the SOURCE language (product: source ==
     * de): prompt is the target sentence in words ("У мене є двадцять один
     * зошит."), the answer is the source sentence. Digits stay the canonical
     * fast path ("Ich habe 21 Hefte." / "… um 20:00 Uhr …", zero-padded and
     * bare), and every written-out source reading is accepted alongside
     * ("… um achtzehn Uhr fünfunddreißig …") — each in the canonical frame and
     * in every [PhraseTemplate.acceptedSourceFrames] variant.
     */
    fun reverseInstantiate(template: PhraseTemplate, hour: Int, minute: Int): TrainerTask {
        val forward = instantiate(template, hour, minute)
        val frames = sourceFrames(template)
        val accepted = mutableListOf<String>()
        for (frame in frames) {
            for (digits in listOf("${pad2(hour)}:${pad2(minute)}", "$hour:${pad2(minute)}")) {
                val sentence = frame.replace(PhraseTemplate.SLOT_MARKER, digits)
                if (sentence !in accepted) accepted += sentence
            }
        }
        if (Trainer.supports(template.source)) {
            val readings = Trainer.clock(hour, minute, template.source).accepted
            for (frame in frames) {
                for (reading in readings) {
                    val sentence = sourceClockSentence(frame, reading) ?: continue
                    if (sentence !in accepted) accepted += sentence
                }
            }
        }
        return reverseTask(template, forward, accepted)
    }

    fun reverseInstantiate(template: PhraseTemplate, value: Long): TrainerTask {
        val forward = instantiate(template, value)
        val frames = sourceFrames(template)
        val countWord = sourceCountWord(template, value)
        val accepted = mutableListOf<String>()
        for (frame in frames) {
            val sentence = fillTarget(frame, value.toString(), countWord)
            if (sentence !in accepted) accepted += sentence
        }
        if (Trainer.supports(template.source)) {
            val readings = when (template.slotKind) {
                TrainerKind.Numbers -> Trainer.drillNumber(value, template.source).accepted
                else -> Trainer.year(value, template.source).accepted
            }.filterNot { isFilteredFeminine(template, it) }
            for (frame in frames) {
                for (reading in readings) {
                    val sentence = fillTarget(frame, reading, countWord)
                    if (sentence !in accepted) accepted += sentence
                }
            }
        }
        return reverseTask(template, forward, accepted)
    }

    /** Canonical source frame first — it is what the reverse drill displays. */
    private fun sourceFrames(template: PhraseTemplate): List<String> =
        (listOf(template.sourceTemplate) + template.acceptedSourceFrames).distinct()

    /**
     * The prompt realization's own counted-noun form. A frame authored with `{count}` is
     * realized that way in every pair, so the side showing the prompt has to fill its own
     * marker — otherwise a uk-source learner reads a literal "{count}".
     */
    private fun sourceCountWord(template: PhraseTemplate, value: Long?): String? =
        template.sourceCountForms?.let { forms -> value?.let(forms::form) }

    private fun reverseTask(
        template: PhraseTemplate,
        forward: TrainerTask,
        accepted: List<String>,
    ): TrainerTask = TrainerTask(
        kind = template.slotKind, language = template.source,
        prompt = forward.display, accepted = accepted,
        display = accepted[0], gloss = forward.gloss,
    )

    /**
     * Source-sentence substitution for a written-out clock reading: the
     * reading is the WHOLE time expression, so a literal " Uhr" right after
     * the slot is absorbed ("um {slot} Uhr" + "achtzehn Uhr fünfunddreißig" →
     * "um achtzehn Uhr fünfunddreißig"); a reading itself starting "um "
     * composes only where the frame already says "um " (the duplicate is
     * dropped) and is skipped elsewhere — "Es ist jetzt um acht." is not a
     * time statement.
     */
    private fun sourceClockSentence(sourceFrame: String, reading: String): String? {
        val marker = PhraseTemplate.SLOT_MARKER
        val frame = sourceFrame.replace("$marker Uhr", marker)
        var words = reading
        if (words.startsWith("um ")) {
            if (!frame.substringBefore(marker).endsWith("um ")) return null
            words = words.removePrefix("um ")
        }
        return frame.replace(marker, words)
    }

    fun reverseSample(template: PhraseTemplate, rng: Random): TrainerTask =
        reverseOf(template, sample(template, rng))

    /** Level-aware reverse drill — same value ramp as the leveled [sample]. */
    fun reverseSample(template: PhraseTemplate, level: Int, rng: Random): TrainerTask =
        reverseOf(template, sample(template, level, rng))

    private fun reverseOf(template: PhraseTemplate, forward: TrainerTask): TrainerTask {
        if (template.slotKind == TrainerKind.Clock) {
            val parts = Regex("""\d+""").findAll(forward.prompt).map { it.value.toInt() }.toList()
            val hour = if (parts.size > 1) parts[parts.size - 2] else 0
            val minute = parts.lastOrNull() ?: 0
            return reverseInstantiate(template, hour, minute)
        }
        val digits = forward.prompt.filter { it.isDigit() }
        return reverseInstantiate(template, digits.toLongOrNull() ?: 0L)
    }

    // Composition

    internal fun compose(template: PhraseTemplate, slot: TrainerTask, value: Long?): TrainerTask {
        val countWord = template.countForms?.let { forms ->
            require(template.slotKind == TrainerKind.Numbers) { "countForms only compose with Numbers" }
            value?.let(forms::form)
        }

        val prompt = fillTarget(template.sourceTemplate, slot.prompt, sourceCountWord(template, value))
        val display = fillTarget(template.targetTemplate, slot.display, countWord)
        val renderings = (slot.accepted.filterNot { isFilteredFeminine(template, it) } + digitForms(slot))
            .distinct()
        val frames = (listOf(template.targetTemplate) + template.acceptedFrames).distinct()
        val accepted = mutableListOf<String>()
        for (frame in frames) {
            for (rendering in renderings) {
                val sentence = fillTarget(frame, rendering, countWord)
                if (sentence !in accepted) accepted += sentence
            }
        }
        val gloss = listOfNotNull(template.note, slot.gloss).joinToString(" · ")

        return TrainerTask(
            kind = template.slotKind, language = template.target,
            prompt = prompt, accepted = accepted, display = display,
            gloss = gloss.ifEmpty { null },
        )
    }

    /** Digit renderings of the slot ("347", "1978"; clock "08:05" and "8:05"). */
    private fun digitForms(slot: TrainerTask): List<String> {
        if (slot.kind != TrainerKind.Clock) return listOf(slot.prompt)
        val bare = slot.prompt.substringBefore(':').toInt().toString() + ":" + slot.prompt.substringAfter(':')
        return listOf(slot.prompt, bare).distinct()
    }

    /**
     * Feminine numeral ENDING a variant (одна/дві) before a masculine counted
     * noun would accept the exact agreement error [PhraseTemplate.masculineNumeral]
     * templates train — drop it wherever accepted sentences are assembled.
     */
    private fun isFilteredFeminine(template: PhraseTemplate, variant: String): Boolean {
        if (!template.masculineNumeralOnly) return false
        val last = variant.substringAfterLast(' ')
        return last == "одна" || last == "дві"
    }

    /**
     * Sentence-position-aware substitution: mid-sentence slots lowercase the
     * value's first letter (Trainer's Swahili clock strings start "Saa …"),
     * sentence-initial slots uppercase it.
     */
    internal fun fillTarget(template: String, slotWords: String, countWord: String?): String {
        var result = template
        val index = result.indexOf(PhraseTemplate.SLOT_MARKER)
        if (index >= 0) {
            val sentenceStart = result.take(index).none { it.isLetter() || it.isDigit() }
            result = result.replaceRange(
                index, index + PhraseTemplate.SLOT_MARKER.length,
                adjustCase(slotWords, sentenceStart),
            )
        }
        if (countWord != null) {
            result = result.replace(PhraseTemplate.COUNT_MARKER, countWord)
        }
        return result
    }

    internal fun adjustCase(words: String, sentenceStart: Boolean): String {
        val first = words.firstOrNull() ?: return words
        val head = if (sentenceStart) first.uppercase() else first.lowercase()
        return head + words.substring(1)
    }
}
