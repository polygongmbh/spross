package net.spross.kern.listen

import net.spross.kern.model.shownArticle

/**
 * One turn, whole: every string and every beat the apps need, so neither platform decides any
 * of it.
 *
 * [targetForm] and [sourceForm] are what is SAID and what is shown; [spokenArticle] is the
 * target-side article to say in front of the word, null where the card has none (see
 * `spokenTargetForm`, which builds the string a synthesizer is handed). It is carried
 * separately because a bundled recording says what was recorded and takes no prefix — only
 * the synthesized branch adds it.
 */
data class ListeningTurn(
    val cardId: String,
    val targetForm: String,
    val sourceForm: String,
    val spokenArticle: String?,
    /** Target word → its meaning; long for a held word, short for an unseen one. */
    val recallGapMs: Long,
    /** Meaning → the target word said again. */
    val echoGapMs: Long,
    /** The echo → the next turn's first word. */
    val turnGapMs: Long,
)

/** What the learner (or the lock screen, or a headphone button) does to a run. */
sealed class ListeningIntent {
    /** Open the playlist on its first turn. */
    data object Start : ListeningIntent()

    /** The turn's last beat has played — draw the next one. */
    data object Advance : ListeningIntent()

    /** "Next", asked for: draw the next turn now. */
    data object Skip : ListeningIntent()

    /** "Again": the same turn from its first beat. */
    data object Repeat : ListeningIntent()

    data object TogglePause : ListeningIntent()
    data object Close : ListeningIntent()
}

/**
 * What the reduction asks the platform to do about the sound, which is the only thing outside
 * the run's own state.
 *
 * An effect rather than a state diff, because [ListeningIntent.Repeat] leaves the state
 * identical and must still make the audio fire — a driver watching the turn change would go
 * deaf on exactly the button that asks to hear something.
 */
sealed class ListeningEffect {
    /** Play this turn from its first beat. */
    data class Play(val turn: ListeningTurn) : ListeningEffect()

    /** Silence: a pause, a close, or a run with nothing to say. */
    data object Stop : ListeningEffect()
}

/** The closed result of one intent: the next state plus what it asks for. */
data class ListeningReduction(val state: ListeningRunState, val effects: List<ListeningEffect>)

/**
 * A listening run, whole and immutable — the playlist it walks, the turn on air, and how far
 * into the lap it has got.
 *
 * There is no box here at all: a run answers nothing, so nothing is booked, and the state
 * carries no [net.spross.kern.box.BoxState] to make that structurally true rather than a
 * promise.
 */
data class ListeningRunState(
    /** The playlist in play order, from `ListeningPool.report`. */
    val candidates: List<ListeningCandidate>,
    /** The turn on air; null before [ListeningIntent.Start] and on an empty pool. */
    val turn: ListeningTurn?,
    /** Card ids played SINCE THE LAST LAP, oldest first — uncapped, since the pool bounds it. */
    val heard: List<String>,
    val paused: Boolean,
    /** A run exists from [ListeningIntent.Start] until [ListeningIntent.Close]. */
    val active: Boolean,
    /** Turns drawn so far — what a run's own progress line reads, never a box figure. */
    val played: Int,
) {
    val currentCardId: String? get() = turn?.cardId
}

/**
 * The listening run as pure state plus one reducer — the machine both apps would otherwise
 * re-derive, differently.
 *
 * It needs nothing from outside at all — no clock, because a run has no due dates and books
 * no reviews, and no generator, because the pool arrives already dealt into its play order
 * (`listeningOrder`) and the run only walks it. The same box therefore gives the same run, on
 * both platforms.
 * No default arguments: they do not cross the ObjC boundary, so every entry point is explicit.
 */
object ListeningRun {

    /** No run yet: a closed shell around the pool. */
    fun idle(candidates: List<ListeningCandidate>): ListeningRunState = ListeningRunState(
        candidates = candidates, turn = null, heard = emptyList(),
        paused = false, active = false, played = 0,
    )

    /** The pool was rebuilt under the run (a voice arrived, the box moved) — carry it in. */
    fun withCandidates(state: ListeningRunState, candidates: List<ListeningCandidate>): ListeningRunState =
        state.copy(candidates = candidates)

    fun reduce(state: ListeningRunState, intent: ListeningIntent): ListeningReduction =
        when (intent) {
            ListeningIntent.Start -> start(state)
            ListeningIntent.Advance -> advance(state)
            ListeningIntent.Skip -> skip(state)
            ListeningIntent.Repeat -> repeat(state)
            ListeningIntent.TogglePause -> togglePause(state)
            ListeningIntent.Close -> close(state)
        }

    /** Open on the first turn; an empty pool opens on silence rather than failing. */
    private fun start(state: ListeningRunState): ListeningReduction {
        val opened = idle(state.candidates).copy(active = true)
        return played(draw(opened))
    }

    /**
     * The turn ended by itself. A no-op while paused: the beat chain is stopped, and a stray
     * completion arriving from the audio the pause interrupted must not walk the playlist on.
     */
    private fun advance(state: ListeningRunState): ListeningReduction {
        if (!state.active || state.paused) return unchanged(state)
        return played(draw(state))
    }

    /**
     * "Next", asked for — so it also lifts a pause: reaching for the next word is a request to
     * hear it, and a skip that left the run silent would read as a broken button.
     */
    private fun skip(state: ListeningRunState): ListeningReduction {
        if (!state.active) return unchanged(state)
        return played(draw(state.copy(paused = false)))
    }

    /** "Again" — the same turn, replayed; it lifts a pause for the same reason a skip does. */
    private fun repeat(state: ListeningRunState): ListeningReduction {
        val turn = state.turn
        if (!state.active || turn == null) return unchanged(state)
        return ListeningReduction(state.copy(paused = false), listOf(ListeningEffect.Play(turn)))
    }

    /**
     * Resuming replays the current turn from its first beat rather than resuming mid-word:
     * the run holds beats, not a playhead, and a word cut in half is worth hearing whole.
     */
    private fun togglePause(state: ListeningRunState): ListeningReduction {
        val turn = state.turn
        if (!state.active || turn == null) return unchanged(state)
        val paused = !state.paused
        val effect = if (paused) ListeningEffect.Stop else ListeningEffect.Play(turn)
        return ListeningReduction(state.copy(paused = paused), listOf(effect))
    }

    /** Close the run. The turn stays as it was — the platform may still be animating it away. */
    private fun close(state: ListeningRunState): ListeningReduction =
        ListeningReduction(state.copy(active = false, paused = false), listOf(ListeningEffect.Stop))

    /**
     * Walk to the next turn: the first word of the playlist not yet [ListeningRunState.heard],
     * and where none is left the lap starts over at the head.
     *
     * This is what the old 24-card recency ring bought, kept and made stronger — no word comes
     * back before the WHOLE pool has lapped, rather than merely before two dozen others have.
     * A pool shorter than a run laps cleanly instead of running dry, and a one-word pool keeps
     * saying its word, which is all it can do.
     */
    private fun draw(state: ListeningRunState): ListeningRunState {
        val pool = state.candidates
        if (pool.isEmpty()) return state.copy(turn = null)
        val heard = state.heard.toSet()
        val next = pool.firstOrNull { it.card.id !in heard }
        val picked = next ?: pool.first()
        return state.copy(
            turn = turnFor(picked),
            heard = (if (next == null) emptyList() else state.heard) + picked.card.id,
            played = state.played + 1,
        )
    }

    /** Play what was drawn, or fall silent where there was nothing to draw. */
    private fun played(state: ListeningRunState): ListeningReduction {
        val turn = state.turn ?: return ListeningReduction(state, listOf(ListeningEffect.Stop))
        return ListeningReduction(state, listOf(ListeningEffect.Play(turn)))
    }

    /**
     * The turn a candidate makes. The canonical target form is what plays — listening rotates
     * no synonyms, because a rotated form would need the reveal that a screen gives and sound
     * cannot. The article goes through [shownArticle] all the same, so the rule stays named
     * here rather than assumed: a form that is not the canonical one carries no article.
     */
    private fun turnFor(candidate: ListeningCandidate): ListeningTurn {
        val card = candidate.card
        return ListeningTurn(
            cardId = card.id,
            targetForm = card.target.text,
            sourceForm = card.source.text,
            spokenArticle = shownArticle(card.target.grammar["gender"], card.target.text, card.target.text),
            recallGapMs = recallGap(candidate),
            echoGapMs = ECHO_GAP_MS,
            turnGapMs = TURN_GAP_MS,
        )
    }

    private fun unchanged(state: ListeningRunState) = ListeningReduction(state, emptyList())
}
