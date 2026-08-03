package net.spross.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.audio.Pronouncer
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.JoinStamp
import net.spross.kern.model.PresentationRole
import net.spross.kern.model.Rating
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.emojiCue
import net.spross.kern.model.ProducePrompt
import net.spross.kern.model.presentationRole
import net.spross.kern.model.producePrompt
import net.spross.kern.model.pronunciationCue
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerTone
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.SessionEffect
import net.spross.kern.session.SessionIntent
import net.spross.kern.session.SessionOffers
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoreFormatException
import net.spross.kern.store.withProductCalibration

sealed interface Screen {
    data object Loading : Screen
    data class Onboarding(val editing: Boolean) : Screen
    data object Heute : Screen
    data object Session : Screen
    data object About : Screen
    data object LetterDrill : Screen
}

data class SessionUi(
    val card: Card?,               // null ⇒ drained: show the summary
    val role: PresentationRole?,
    val promptForm: String?,       // rotated recognition prompt
    /** Whether a produce turn asks by meaning or by ear; [ProducePrompt.Source] elsewhere. */
    val producePrompt: ProducePrompt = ProducePrompt.Source,
    /** Which face carries the picture; null when the word has none. */
    val emojiCue: EmojiCue?,
    /**
     * What the prompt says out loud, or null where the card owes that very form —
     * non-null ⇔ kern's cue puts the target on screen from frame one.
     */
    val promptPronunciation: Pronunciation?,
    val segments: List<AnswerTone>,
    val remaining: Int,
    /** Summary tallies, in the order the chrome line formats them ([SessionRunState]'s buckets). */
    val introduced: Int,
    val strengthened: Int,
    val reviewed: Int,
    /** Whether an endless refill would yield anything — what "Weiter üben" turns on. */
    val canPracticeMore: Boolean,
)

class AppModel(app: Application) : AndroidViewModel(app) {

    private val boxFiles = BoxFiles(File(app.filesDir, "box"))
    private val prefs = app.getSharedPreferences("spross", Context.MODE_PRIVATE)
    private val profile = ProfileStore(prefs)

    /** The run kern steps; null between sessions. The screen reads [sessionUi] instead. */
    private var sessionRun: SessionRunState? = null

    /** The one door to a spoken target word — review cards now, the drills later. */
    val pronouncer = Pronouncer(app, prefs)

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var box by mutableStateOf<BoxState?>(null)
        private set
    var stats by mutableStateOf<BoxStatistics?>(null)
        private set
    var sessionAvailable by mutableStateOf(false)
        private set

    /** Whether the done card's extra round would come back with anything. */
    var canPracticeExtra by mutableStateOf(false)
        private set
    var sessionUi by mutableStateOf<SessionUi?>(null)
        private set
    var chrome by mutableStateOf(Chrome.forSource("en"))
        private set
    var normalizer: AnswerNormalizer? = null
        private set

    /**
     * Produce grading with the whole join in view: a form the catalog owns
     * elsewhere is that word, never a typo of this card's answer (kern §6).
     * Built per grading pass — one pass over the join, only on a check tap.
     */
    val produceGrader: CatalogAnswerGrader?
        get() {
            val norm = normalizer ?: return null
            val state = box ?: return null
            return CatalogAnswerGrader(norm, state.cards.values.toList())
        }

    private fun now(): Long = System.currentTimeMillis()
    private fun tz(): String = TimeZone.getDefault().id

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Catalog.load(AssetCatalogSource(getApplication<Application>().assets))
            }
            catalog = loaded
            val source = profile.source
            val target = profile.target
            if (source != null && target != null) {
                activate(source, target)
            } else {
                chrome = Chrome.forSource(defaultSource(loaded))
                screen = Screen.Onboarding(editing = false)
            }
        }
    }

    /**
     * The source a fresh install opens with. Kern's rule, over the device's report:
     * asking [Catalog.availableTargets] about an undeclared locale THROWS, so a French
     * or Italian phone used to crash on launch here.
     */
    fun defaultSource(cat: Catalog): String = cat.defaultSource(Locale.getDefault().language)

    fun completeOnboarding(source: String, target: String) {
        profile.set(source, target)
        viewModelScope.launch { activate(source, target) }
    }

    fun editLanguages() {
        screen = Screen.Onboarding(editing = true)
    }

    fun openAbout() {
        screen = Screen.About
    }

    fun closeAbout() {
        screen = Screen.Heute
    }

    /** The letters drill; what it can ask is `letterDrillAvailable`, which gates the chip. */
    fun startLetterDrill() {
        screen = Screen.LetterDrill
    }

    fun closeLetterDrill() {
        // why: nothing may keep talking into Heute — the drill's own screen stops
        // playback as it leaves, and this is the door it leaves by.
        pronouncer.stop()
        screen = Screen.Heute
    }

    fun cancelOnboarding() {
        if (box != null) screen = Screen.Heute
    }

    private suspend fun activate(source: String, target: String) {
        val cat = catalog ?: return
        chrome = Chrome.forSource(source)
        val cards = cat.join(source, target)
        val stamp = JoinStamp(source, target, cat.fingerprint)
        val restored = withContext(Dispatchers.IO) {
            boxFiles.read(target)?.let { json ->
                try {
                    // why: calibration belongs to the BUILD — a box written months ago would
                    // otherwise keep pacing itself by the numbers that shipped with it.
                    StoreCodec.decode(json).join(cards, stamp).withProductCalibration()
                } catch (_: StoreFormatException) {
                    null // unreadable document: start fresh rather than crash (pre-production)
                }
            }
        }
        val state = restored ?: BoxEngine.bootstrap(cards, BoxConfig.product(), stamp)
        box = state
        normalizer = AnswerNormalizer(cat.languages.getValue(target))
        persist(state)
        refreshStats()
        screen = Screen.Heute
    }

    fun startSession() = begin(SessionIntent.Start)

    /**
     * The done card's extra round: kern composes the mixing round itself — everything due,
     * packed vocab within the budget, then pull-aheads — and no-ops when that is empty.
     */
    fun startExtraSession() = begin(SessionIntent.StartExtra)

    private fun begin(intent: SessionIntent) {
        val started = dispatch(intent) ?: return
        // A round that came back empty never took the learner anywhere, and leaves no run
        // behind for the next tap to inherit.
        if (started.currentCardId == null) {
            sessionRun = null
            sessionUi = null
            return
        }
        screen = Screen.Session
    }

    fun answerCurrent(rating: Rating) {
        sessionRun ?: return
        // why: the card is leaving — a word still sounding must not follow the learner
        // onto the next one, the same cut iOS makes in resetCardState().
        pronouncer.stop()
        dispatch(SessionIntent.Answer(rating))
    }

    fun continueEndless() {
        sessionRun ?: return
        dispatch(SessionIntent.ContinueEndless)
    }

    /**
     * Backgrounding mid-run (`SprossActivity.onStop`): the day's answers are booked here
     * or lost with the process. Kern books the not-yet-folded delta only, so the finish
     * that follows cannot count them twice.
     */
    fun foldPartialSession() {
        sessionRun ?: return
        dispatch(SessionIntent.FoldPartial)
    }

    fun finishSession() {
        sessionRun ?: return
        pronouncer.stop() // the run is over: nothing keeps talking into Heute
        dispatch(SessionIntent.Close)
        sessionRun = null
        sessionUi = null
        screen = Screen.Heute
    }

    /**
     * Step the run and honour what it asks for. The whole session machine is kern's;
     * this is the platform half — the clock, the disk, and the observable state.
     */
    private fun dispatch(intent: SessionIntent): SessionRunState? {
        val state = box ?: return null
        // The box may have moved outside the run (a fresh load, settings) — carry it in.
        val current = sessionRun?.let { SessionRun.withBox(it, state) } ?: SessionRun.idle(state)
        val reduction = SessionRun.reduce(current, intent, now(), tz())
        sessionRun = reduction.state
        box = reduction.state.box
        for (effect in reduction.effects) {
            when (effect) {
                is SessionEffect.Persist -> persist(reduction.state.box, effect.immediate)
                SessionEffect.DayBooked -> refreshStats()
            }
        }
        refreshSessionUi()
        return reduction.state
    }

    private fun isConsolidated(cardId: String): Boolean =
        box?.let { BoxEngine.isConsolidated(it, cardId) } == true

    /**
     * Whether the card's own form can be heard RIGHT NOW — the one fact kern's
     * [producePrompt] cannot have. Three ways it cannot, and each keeps the source
     * prompt rather than putting up a card with nothing in it: no recording and no
     * voice, reading aloud switched off, and TalkBack, which suppresses every autoplay
     * so nothing may speak over the screen reader.
     */
    private fun audible(card: Card): Boolean {
        if (pronouncer.muted || pronouncer.readsScreenAloud) return false
        val pronunciation = catalog?.pronunciation(card.target.lang, card.target.text) ?: return false
        return pronouncer.canPronounce(pronunciation)
    }

    private fun refreshSessionUi() {
        val active = sessionRun ?: run { sessionUi = null; return }
        val state = active.box
        val card = active.currentCardId?.let { state.cards[it] }
        val ui = if (card == null) {
            SessionUi(
                card = null, role = null, promptForm = null,
                emojiCue = null, promptPronunciation = null,
                segments = active.segments, remaining = 0,
                introduced = active.newCards, strengthened = active.graduated,
                reviewed = active.reviews,
                canPracticeMore = SessionOffers.canPracticeMore(state, now(), tz()),
            )
        } else {
            val count = state.scheduling[card.id]?.reviewCount ?: 0
            val role = presentationRole(card.id, count)
            val promptForm = recognitionPromptForm(card, count)
            val prompt = producePrompt(card.id, count, isConsolidated(card.id), audible(card))
            SessionUi(
                card = card,
                role = role,
                promptForm = promptForm,
                producePrompt = prompt,
                emojiCue = card.emoji?.let {
                    emojiCue(role, BoxEngine.isSettled(state, card.id), count)
                },
                // why: the KERN cue, never `role == Recognize` — one rule, consumed by
                // both apps. The PROMPTED form, so a rotated synonym is heard as itself.
                promptPronunciation = catalog
                    ?.takeIf { pronunciationCue(role, prompt) == PronunciationCue.Upfront }
                    // why: a sound-prompted produce has NOTHING on screen, so what plays
                    // is the very form it grades against, not the recognition rotation.
                    ?.pronunciation(
                        card.target.lang,
                        if (prompt == ProducePrompt.Sound) card.target.text else promptForm,
                    ),
                segments = active.segments,
                remaining = active.remaining,
                introduced = active.newCards,
                strengthened = active.graduated,
                reviewed = active.reviews,
                canPracticeMore = SessionOffers.canPracticeMore(state, now(), tz()),
            )
        }
        sessionUi = ui
    }

    private fun refreshStats() {
        val state = box ?: return
        stats = BoxEngine.statistics(state, now(), tz())
        sessionAvailable = SessionOffers.sessionAvailable(state, now(), tz())
        canPracticeExtra = SessionOffers.canPracticeMore(state, now(), tz())
    }

    override fun onCleared() {
        super.onCleared()
        // why: the synthesizer holds a binding to another process and the player a
        // decoded clip — neither may outlive the model that opened them.
        pronouncer.release()
    }

    /**
     * Every answer persists (small doc, IO thread) — process death mid-session then costs
     * at most the in-flight card, matching iOS's debounced-save guarantee.
     *
     * [immediate] is the day's fold asking to be on disk BEFORE the caller returns: it is
     * dispatched from `onStop`, where the process may not live long enough for a queued
     * write, and a fold that never reaches disk is no fold.
     */
    private fun persist(state: BoxState, immediate: Boolean = false) {
        val json = StoreCodec.encode(state)
        val target = state.joinStamp.target
        if (immediate) {
            boxFiles.write(target, json)
            return
        }
        // why: NonCancellable — a write racing activity teardown must still land.
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { boxFiles.write(target, json) }
    }
}
