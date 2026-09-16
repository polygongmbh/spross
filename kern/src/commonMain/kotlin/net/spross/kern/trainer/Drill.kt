package net.spross.kern.trainer

/**
 * The six entries free practice is made of, in the order the hub offers them.
 *
 * The ONE place the roster is written down. The hub's chips, what each entry gates on and
 * the string key that names it all derive from this list, so a seventh drill is one entry
 * here and a compiler error at every place that has to answer for it.
 *
 * It names the entries and nothing else: a glyph, a title, a route and a layout are the
 * platform's, and a drill's own machinery stands under its own topic prefix
 * (`CountryDrill`, `WordScrambleRun`; `docs/drills.md`).
 */
enum class Drill {
    Numbers,
    Letters,
    Countries,
    Dates,
    WordScramble,
    SentenceScramble,
}
