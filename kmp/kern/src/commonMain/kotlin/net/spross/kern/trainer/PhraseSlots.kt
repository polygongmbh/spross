package net.spross.kern.trainer

import kotlin.random.Random

/**
 * Instantiates a [PhraseTemplate] into a [TrainerTask] by composing with the
 * Trainer slot generators: the source-language prompt gets the digits, the
 * target display gets the canonical words, accepted gets one full sentence
 * per accepted slot variant. Pure — sampling takes an injected [Random].
 */
object PhraseSlots {

    /**
     * Clock templates. Any minute is read out by the language clocks; the
     * source prompt shows the digital time ("… um 14:35 Uhr …"). Swahili
     * templates only accept minutes ≤ 30: the >30 countdown form
     * ("saa tatu kasoro dakika …") reads awkwardly when embedded
     * (language-review finding).
     */
    fun instantiate(template: PhraseTemplate, hour: Int, minute: Int): TrainerTask {
        require(template.slotKind == TrainerKind.Clock) { "hour/minute instantiation requires a Clock template" }
        require(template.target != "sw" || minute <= 30) { "Swahili phrase templates embed only minutes 0..30" }
        val slot = Trainer.clock(hour, minute, template.target)
        return compose(template, slot, value = null)
    }

    /** Number and year templates. */
    fun instantiate(template: PhraseTemplate, value: Long): TrainerTask {
        require(template.slotKind != TrainerKind.Clock) { "clock templates take hour/minute" }
        val slot = when (template.slotKind) {
            TrainerKind.Numbers -> Trainer.number(value, template.target)
            else -> Trainer.year(value, template.target)
        }
        return compose(template, slot, value)
    }

    /**
     * Full-difficulty sampling with the Trainer's ported biases
     * (numbers 10–9999 weighted to 2–3 digits, years around 1950–2050).
     * Clock is the leveled sampler at max level: any hour and minute,
     * intersected with the template constraint (Swahili minutes 0..30).
     */
    fun sample(template: PhraseTemplate, rng: Random): TrainerTask {
        if (template.slotKind == TrainerKind.Clock) {
            return sample(template, Trainer.maxLevel(TrainerKind.Clock), rng)
        }
        val slot = Trainer.sample(template.slotKind, template.target, rng)
        // why: slot.prompt is the Trainer's numeric contract ("347"/"1978"),
        // so counted-noun agreement can reuse the sampled value exactly.
        return compose(template, slot, slot.prompt.toLong())
    }

    /**
     * Level-aware sampling for the gentle sentence-drill ramp: the slot value
     * is drawn with the SAME level semantics as the plain drills (numbers:
     * level = digit count; years: recent decades → historic range; clock:
     * full hours → any minute — see the leveled [Trainer.sample]), then
     * instantiated, so accepted sentences stay identical to [instantiate].
     * Clock templates intersect the level's minute set with the template
     * constraint (Swahili embeds only minutes 0..30, so level 2 quarters
     * become {0, 15, 30} and level 4 caps at :30).
     */
    fun sample(template: PhraseTemplate, level: Int, rng: Random): TrainerTask {
        if (template.slotKind == TrainerKind.Clock) {
            return instantiate(template, rng.nextInt(24), drawMinute(template, level, rng))
        }
        val slot = Trainer.sample(template.slotKind, template.target, level, rng)
        // why: slot.prompt is the Trainer's numeric contract ("347"/"1978"),
        // so counted-noun agreement can reuse the sampled value exactly.
        return instantiate(template, value = slot.prompt.toLong())
    }

    /** Leveled minute set ∩ template constraint (see [sample]). */
    private fun drawMinute(template: PhraseTemplate, level: Int, rng: Random): Int {
        val cap = if (template.target == "sw") 30 else 59
        return when (level.coerceIn(1, Trainer.maxLevel(TrainerKind.Clock))) {
            1 -> 0
            2 -> intArrayOf(0, 15, 30, 45).filter { it <= cap }.let { it[rng.nextInt(it.size)] }
            3 -> rng.nextInt(31)
            else -> rng.nextInt(cap + 1)
        }
    }

    // Reverse (target sentence shown, source language typed)

    /**
     * Reverse drill for learners of the SOURCE language (product: target ==
     * de): prompt is the target sentence in words ("У мене є двадцять один
     * зошит."), the answer is the source sentence with the value in digits
     * ("Ich habe 21 Hefte." / "… um 20:00 Uhr …"). Digits keep typing fast;
     * clock answers accept both zero-padded "08:00" and bare "8:00" forms.
     */
    fun reverseInstantiate(template: PhraseTemplate, hour: Int, minute: Int): TrainerTask {
        val forward = instantiate(template, hour, minute)
        val padded = "${pad2(hour)}:${pad2(minute)}"
        val bare = "$hour:${pad2(minute)}"
        val accepted = mutableListOf(template.sourceTemplate.replace(PhraseTemplate.SLOT_MARKER, padded))
        if (bare != padded) {
            accepted += template.sourceTemplate.replace(PhraseTemplate.SLOT_MARKER, bare)
        }
        return TrainerTask(
            kind = template.slotKind, language = template.source,
            prompt = forward.display, accepted = accepted,
            display = accepted[0], gloss = forward.gloss,
        )
    }

    fun reverseInstantiate(template: PhraseTemplate, value: Long): TrainerTask {
        val forward = instantiate(template, value)
        val sentence = template.sourceTemplate.replace(PhraseTemplate.SLOT_MARKER, value.toString())
        return TrainerTask(
            kind = template.slotKind, language = template.source,
            prompt = forward.display, accepted = listOf(sentence),
            display = sentence, gloss = forward.gloss,
        )
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

        val prompt = template.sourceTemplate.replace(PhraseTemplate.SLOT_MARKER, slot.prompt)
        val display = fillTarget(template.targetTemplate, slot.display, countWord)
        val accepted = mutableListOf<String>()
        for (variant in slot.accepted) {
            // Feminine numeral before a masculine counted noun would accept
            // the exact agreement error these templates train — drop it.
            if (template.masculineNumeralOnly) {
                val last = variant.substringAfterLast(' ')
                if (last == "одна" || last == "дві") continue
            }
            val sentence = fillTarget(template.targetTemplate, variant, countWord)
            if (sentence !in accepted) accepted += sentence
        }
        val gloss = listOfNotNull(template.gloss, slot.gloss).joinToString(" · ")

        return TrainerTask(
            kind = template.slotKind, language = template.target,
            prompt = prompt, accepted = accepted, display = display,
            gloss = gloss.ifEmpty { null },
        )
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
