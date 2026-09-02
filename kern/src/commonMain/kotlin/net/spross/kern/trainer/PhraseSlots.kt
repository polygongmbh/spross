package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language

/**
 * Instantiates a [PhraseTemplate] into a [TrainerTask] by composing with the
 * Trainer slot generators: the source-language prompt gets the digits, the
 * target display gets the canonical words, accepted gets one full sentence
 * per accepted FRAME × accepted slot RENDERING — every authored variant frame
 * crossed with every written-out generator variant plus the digit form(s)
 * (user report: word forms typed mid-sentence were rejected).
 * Pure — sampling takes an injected [Random].
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
        // Exhaustive on purpose: a new kind must fail loudly here rather than
        // silently become a year, which an `else` arm would have made it.
        val slot = when (template.slotKind) {
            // why: drill accepted set — sw speakers routinely drop the "na" connectors
            TrainerKind.Numbers -> template.swahiliNounClass
                ?.let { Trainer.concordedNumber(value, it, template.target) }
                ?: Trainer.drillNumber(value, template.target)
            TrainerKind.Years -> Trainer.year(value, template.target)
            TrainerKind.Clock, TrainerKind.Forms, TrainerKind.Fraction ->
                throw IllegalArgumentException("no phrase slot generator for ${template.slotKind}")
        }
        return compose(template, slot, value)
    }

    /**
     * Fraction templates. Reduced, and never a half — the frame carries the reading as a
     * bare noun and has no way to decline around an adjectival one ([SlotValue.Part]).
     */
    fun instantiate(template: PhraseTemplate, numerator: Long, denominator: Long): TrainerTask {
        require(template.slotKind == TrainerKind.Fraction) { "only a fraction template takes n/d" }
        val slot = Trainer.fraction(numerator, denominator, template.target)
        return compose(template, slot, value = null)
    }

    /**
     * Full-difficulty sampling with the Trainer's ported biases
     * (numbers 10–9999 weighted to 2–3 digits, years around 1950–2050).
     * Clock is the whole face; fractions are everything the language reads.
     */
    fun sample(template: PhraseTemplate, rng: Random): TrainerTask =
        instantiate(template, drawSlot(template.slotKind, template.target, rng))

    /**
     * Level-aware sampling for the gentle sentence-drill ramp: the slot value
     * is drawn with the SAME level semantics as the plain drills (numbers:
     * level = digit count; years: recent decades → historic range; clock:
     * full hours → any minute — see the leveled [Trainer.sample]), then
     * instantiated, so accepted sentences stay identical to [instantiate].
     */
    fun sample(template: PhraseTemplate, level: Int, rng: Random): TrainerTask =
        instantiate(template, drawSlot(template.slotKind, template.target, level, rng))

    /**
     * The drawn value, instantiated. Nothing here reads a value back out of a rendered
     * prompt: `slot.prompt.toLong()` was only ever defined while every slot rendered as
     * digits, and it is the fraction slot that ends that.
     */
    private fun instantiate(template: PhraseTemplate, value: SlotValue): TrainerTask = when (value) {
        is SlotValue.Count -> instantiate(template, value.n)
        is SlotValue.Year -> instantiate(template, value.y)
        is SlotValue.Time -> instantiate(template, value.hour, value.minute)
        is SlotValue.Part -> instantiate(template, value.numerator, value.denominator)
    }

    /**
     * The prompt realization's own counted-noun form. A frame authored with `{count}` is
     * realized that way in every pair, so the side showing the prompt has to fill its own
     * marker — otherwise a uk-source learner reads a literal "{count}".
     */
    private fun sourceCountWord(template: PhraseTemplate, value: Long?): String? =
        template.sourceCountForms?.let { forms -> value?.let(forms::form) }

    // Composition

    internal fun compose(template: PhraseTemplate, slot: TrainerTask, value: Long?): TrainerTask {
        val countWord = template.countForms?.let { forms ->
            require(template.slotKind == TrainerKind.Numbers) { "countForms only compose with Numbers" }
            value?.let(forms::form)
        }

        val sourceCount = sourceCountWord(template, value)
        // why: the prompt side takes DIGITS, which have no case — the answer language's
        // casing rule is passed through it unread rather than looked up twice.
        val prompt = fillTarget(template.sourceTemplate, slot.prompt, sourceCount, template.target)
        val promptDisplay =
            fillTarget(template.sourceTemplate, slot.promptDisplay, sourceCount, template.target)
        val display = fillWords(template, template.targetTemplate, slot.display, countWord)
            ?: fillTarget(template.targetTemplate, slot.display, countWord, template.target)
        val words = slot.accepted.filterNot { isFilteredFeminine(template, it) }.distinct()
        val digits = digitForms(slot)
        val frames = (listOf(template.targetTemplate) + template.acceptedFrames).distinct()
        val accepted = mutableListOf<String>()
        for (frame in frames) {
            // why: a written-out clock reading rewrites the frame around it, a digital one
            // never does — "Es ist jetzt 18:35 Uhr." keeps the Uhr the words absorb.
            for (rendering in words) {
                val sentence = fillWords(template, frame, rendering, countWord) ?: continue
                if (sentence !in accepted) accepted += sentence
            }
            for (rendering in digits) {
                val sentence = fillTarget(frame, rendering, countWord, template.target)
                if (sentence !in accepted) accepted += sentence
            }
        }
        val gloss = listOfNotNull(template.note, slot.gloss).joinToString(" · ")

        return TrainerTask(
            kind = template.slotKind, language = template.target,
            prompt = prompt, accepted = accepted, display = display,
            gloss = gloss.ifEmpty { null },
            promptDisplay = promptDisplay,
        )
    }

    /**
     * Fills one frame with one WRITTEN-OUT slot reading. A clock reading is the whole time
     * expression, so a word right after the slot that the reading already says is absorbed
     * ([TrainerLanguagePack.slotEcho]: "um {slot} Uhr" + "achtzehn Uhr fünfunddreißig" →
     * "um achtzehn Uhr fünfunddreißig"); a reading LEADING with a preposition
     * ([TrainerLanguagePack.readingPrepositions]) composes only where the frame already
     * says it, and is skipped elsewhere. Both words come from the answer language's pack,
     * so a language whose readings carry none of them passes through untouched.
     * Null = this reading does not belong in this frame.
     */
    private fun fillWords(
        template: PhraseTemplate,
        frame: String,
        reading: String,
        countWord: String?,
    ): String? {
        val language = template.target
        if (template.slotKind != TrainerKind.Clock) {
            return fillTarget(frame, reading, countWord, language)
        }
        val pack = Trainer.pack(language)
        val marker = PhraseTemplate.SLOT_MARKER
        val absorbed = pack.slotEcho?.let { frame.replace("$marker $it", marker) } ?: frame
        val preposition = pack.readingPrepositions.firstOrNull { reading.startsWith(it) }
            ?: return fillTarget(absorbed, reading, countWord, language)
        if (!absorbed.substringBefore(marker).endsWith(preposition)) return null
        return fillTarget(absorbed, reading.removePrefix(preposition), countWord, language)
    }

    /**
     * Digit renderings of the slot ("347", "1978"; clock "08:05" and "8:05").
     * A grouped value is offered alongside the plain one: the prompt shows "12 345",
     * so a learner who copies the separator into a sentence answer must not lose the
     * Sprosse to the word-count rule.
     */
    private fun digitForms(slot: TrainerTask): List<String> {
        if (slot.kind != TrainerKind.Clock) return listOf(slot.prompt, slot.promptDisplay).distinct()
        return Trainer.clockDigitForms(slot.prompt)
    }

    /**
     * A feminine numeral ENDING a variant before a masculine counted noun would
     * accept the exact agreement error [PhraseTemplate.masculineNumeral]
     * templates train — drop it wherever accepted sentences are assembled.
     * The rule is here; which words are feminine is Ukrainian's own business
     * ([UkrainianNumbers.FEMININE_ONES]).
     */
    private fun isFilteredFeminine(template: PhraseTemplate, variant: String): Boolean {
        if (!template.masculineNumeralOnly) return false
        return variant.substringAfterLast(' ') in UkrainianNumbers.FEMININE_ONES
    }

    /**
     * Sentence-position-aware substitution: a sentence-initial slot uppercases the value's
     * first letter, and a mid-sentence one lowercases it only where [language] writes its
     * readings sentence-style ([TrainerLanguagePack.readingsCarrySentenceCapital]).
     */
    internal fun fillTarget(
        template: String,
        slotWords: String,
        countWord: String?,
        language: Language,
    ): String {
        var result = template
        val index = result.indexOf(PhraseTemplate.SLOT_MARKER)
        if (index >= 0) {
            val sentenceStart = result.take(index).none { it.isLetter() || it.isDigit() }
            result = result.replaceRange(
                index, index + PhraseTemplate.SLOT_MARKER.length,
                adjustCase(slotWords, sentenceStart, language),
            )
        }
        if (countWord != null) {
            result = result.replace(PhraseTemplate.COUNT_MARKER, countWord)
        }
        return result
    }

    internal fun adjustCase(words: String, sentenceStart: Boolean, language: Language): String {
        val first = words.firstOrNull() ?: return words
        if (sentenceStart) return first.uppercase() + words.substring(1)
        // why: German's readings begin on nouns (Mitternacht, Viertel) — lowercasing one
        // mid-sentence spells it wrong, so only a sentence-cased language gives its capital up.
        if (!Trainer.pack(language).readingsCarrySentenceCapital) return words
        return first.lowercase() + words.substring(1)
    }
}
