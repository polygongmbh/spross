package net.spross.kern.trainer

/**
 * The seven entries free practice is made of, in the order the hub offers them.
 *
 * The ONE place the roster is written down. The hub's chips, their faces, what each entry
 * gates on and the string key that names it all derive from this list, so an eighth drill is
 * one entry here and a compiler error at every place that has to answer for it.
 *
 * It names the entries and the face each wears ([emoji]); a title, a route and a layout are the
 * platform's, and a drill's own machinery stands under its own topic prefix
 * (`CountryDrill`, `WordScrambleRun`; `docs/drills.md`).
 */
enum class Drill(val emoji: String) {
    Letters("🔤"),

    /**
     * Numbers earn a drill of their own: number facts learned in one language are retrieved
     * fastest in that language and do not carry over for free (Spelke & Tsivkin 2001, *Cognition* 78(1)).
     */
    Numbers(numbersReadingEmoji(NumbersReading.Cardinal)),
    Dates("📅"),
    Countries("🌍"),
    SentenceScramble("🧩"),
    WordScramble("🔀"),
    Opposites("↔️"),
}
