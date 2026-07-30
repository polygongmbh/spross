package net.spross.kern.session

import net.spross.kern.model.Rating

/**
 * Self-graded review → FSRS rating.
 *
 * A self-grade carries two signals: what the learner says about the word, and
 * how long the word took to come. The learner owns the verdict — the clock is
 * never allowed to contradict it, because only the learner knows whether a fast
 * answer was actually solid or whether a slow one was just an interruption.
 * All the clock decides is whether a word the learner DID know came instantly.
 *
 * So Easy is not a verdict anyone can pick; it is earned by answering fast.
 * That removes the standing temptation to grade a session shorter (policy: kern
 * README §5 — breadth of exposure over perfect single-word retention), and it
 * spends the one distinction learners are genuinely bad at self-reporting on the
 * one measurement that is free.
 *
 * No upper cut-off is needed: the clock only ever upgrades, so a learner who
 * walks away mid-card simply gets the rating the button said.
 */
object SelfGrading {

    /** What the learner says happened, before the clock refines it. */
    enum class Verdict { Unknown, Tough, Knew }

    // Field-calibratable. The window covers reading the prompt AND the recall
    // attempt, so it scales with prompt length — a phrase costs reading time
    // before recall even starts, and a flat budget would score every long card
    // as slow.
    const val INSTANT_BASE_MS = 1500L
    const val INSTANT_PER_CHAR_MS = 40L

    fun instantBudgetMs(promptChars: Int): Long =
        INSTANT_BASE_MS + INSTANT_PER_CHAR_MS * promptChars

    /**
     * [elapsedMs] is the recall attempt — prompt shown until the learner asked
     * to see the answer — not the time spent picking a button afterwards, which
     * measures thumb travel rather than memory.
     * A non-positive value means unmeasured and never earns Easy.
     */
    fun rating(verdict: Verdict, elapsedMs: Long, promptChars: Int): Rating = when (verdict) {
        Verdict.Unknown -> Rating.Again
        Verdict.Tough -> Rating.Hard
        Verdict.Knew ->
            if (elapsedMs > 0 && elapsedMs <= instantBudgetMs(promptChars)) Rating.Easy
            else Rating.Good
    }
}
