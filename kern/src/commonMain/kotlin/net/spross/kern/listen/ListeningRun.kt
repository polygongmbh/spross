package net.spross.kern.listen

import net.spross.kern.model.article
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

    /**
     * "Back": the turn on air from its first beat; asked again before the run moves on, the
     * turn before it.
     */
    data object Back : ListeningIntent()

    data object TogglePause : ListeningIntent()
    data object Close : ListeningIntent()
}

/**
 * What the reduction asks the platform to do about the sound, which is the only thing outside
 * the run's own state.
 *
 * An effect rather than a state diff, because a first [ListeningIntent.Back] leaves the turn
 * as it was and must still make the audio fire — a driver watching the turn change would go
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
 * A listening run, whole and immutable — the playlist it walks, the turn on air, and where in
 * it the run stands.
 *
 * There is no box here at all: a run answers nothing, so nothing is booked, and the state
 * carries no [net.spross.kern.box.BoxState] to make that structurally true rather than a
 * promise.
 */
data class ListeningRunState(
    /** The playlist in play order: from `ListeningPool.report`, and re-dealt at its end. */
    val candidates: List<ListeningCandidate>,
    /** The turn on air; null before [ListeningIntent.Start] and on an empty pool. */
    val turn: ListeningTurn?,
    /** Where in the playlist the turn on air stands; -1 before [ListeningIntent.Start]. */
    val position: Int,
    val paused: Boolean,
    /** A run exists from [ListeningIntent.Start] until [ListeningIntent.Close]. */
    val active: Boolean,
    /** Turns drawn so far; it salts each re-deal, so no two deals of one run match. */
    val played: Int,
    /** A Back restarted the turn on air and nothing has moved since, so the next one steps back. */
    val rewound: Boolean,
)

/**
 * The listening run as pure state plus one reducer — the machine both apps would otherwise
 * re-derive, differently.
 *
 * It needs nothing from outside at all — no clock, because a run has no due dates and books
 * no reviews, and no generator, because the pool arrives already dealt into its play order
 * (`listeningOrder`) and the run walks it, dealing the same words again at its end. The same
 * box therefore gives the same run, on both platforms.
 * No default arguments: they do not cross the ObjC boundary, so every entry point is explicit.
 */
object ListeningRun {

    /** No run yet: a closed shell around the pool. */
    fun idle(candidates: List<ListeningCandidate>): ListeningRunState = ListeningRunState(
        candidates = candidates, turn = null, position = -1,
        paused = false, active = false, played = 0, rewound = false,
    )

    fun reduce(state: ListeningRunState, intent: ListeningIntent): ListeningReduction =
        when (intent) {
            ListeningIntent.Start -> start(state)
            ListeningIntent.Advance -> advance(state)
            ListeningIntent.Skip -> skip(state)
            ListeningIntent.Back -> back(state)
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

    /**
     * "Back" — the way a music player's is: the first press restarts the word on air, since
     * the one just missed is the likelier ask, and a press after it steps to the turn before.
     * The head of a deal has nothing before it and restarts instead. It lifts a pause for the
     * same reason a skip does.
     */
    private fun back(state: ListeningRunState): ListeningReduction {
        if (!state.active || state.turn == null) return unchanged(state)
        val position = if (state.rewound && state.position > 0) state.position - 1 else state.position
        return played(state.at(position).copy(paused = false, rewound = true))
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
     * Walk to the next turn: the playlist's next entry, and past its end the head of a fresh
     * deal of the same words.
     *
     * Repeats are the deal's business, never the run's — `listeningOrder` already brings a
     * shaky word back where it should — so the run only keeps its place. A run that outlasts
     * its deal hears a new order rather than the old one again, and a word that would open it
     * straight after closing the last one goes to its end instead.
     */
    private fun draw(state: ListeningRunState): ListeningRunState {
        if (state.candidates.isEmpty()) return state.copy(turn = null)
        val next = state.position + 1
        val drawn = if (next < state.candidates.size) {
            state.at(next)
        } else {
            val dealt = listeningOrder(state.candidates.distinctBy { it.card.id }, state.played.toLong())
            val fresh = if (dealt.size > 1 && dealt.first().card.id == state.turn?.cardId) dealt.drop(1) + dealt.first() else dealt
            state.copy(candidates = fresh).at(0)
        }
        return drawn.copy(played = state.played + 1, rewound = false)
    }

    private fun ListeningRunState.at(position: Int): ListeningRunState =
        copy(turn = turnFor(candidates[position]), position = position)

    /** Play what was drawn, or fall silent where there was nothing to draw. */
    private fun played(state: ListeningRunState): ListeningReduction {
        val turn = state.turn ?: return ListeningReduction(state, listOf(ListeningEffect.Stop))
        return ListeningReduction(state, listOf(ListeningEffect.Play(turn)))
    }

    /**
     * The turn a candidate makes. The canonical target form is what plays — listening rotates
     * no `teaches`, because a rotated form would need the reveal that a screen gives and sound
     * cannot. The article goes through [shownArticle] all the same, so the rule stays named
     * here rather than assumed: a form that is not the canonical one carries no article.
     */
    private fun turnFor(candidate: ListeningCandidate): ListeningTurn {
        val card = candidate.card
        return ListeningTurn(
            cardId = card.id,
            targetForm = card.target.text,
            sourceForm = card.source.text,
            spokenArticle = shownArticle(card.target.article, card.target.text, card.target.text),
            recallGapMs = recallGap(candidate),
            echoGapMs = ECHO_GAP_MS,
            turnGapMs = TURN_GAP_MS,
        )
    }

    private fun unchanged(state: ListeningRunState) = ListeningReduction(state, emptyList())
}
