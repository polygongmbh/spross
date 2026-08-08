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
import net.spross.app.ui.AreaNaming
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.streakWindow
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

    /**
     * The box browser. [area] is the shelf it opens UNFOLDED — the screen was reached by
     * naming that area, from a search hit or a tree — and null opens where the learner
     * left off ([net.spross.kern.box.BoxBrowser.defaultExpandedGroupId]).
     */
    data class Box(val area: String? = null) : Screen
}

/**
 * How far back the activity strip looks. The window is the strip's whole subject, so the
 * number lives with the state it shapes rather than with the drawing of it.
 */
const val ACTIVITY_WINDOW_DAYS: Int = 14

data class SessionUi(
    val card: Card?,               // null ⇒ drained: show the summary
    val role: PresentationRole?,
    val promptForm: String?,       // rotated recognition prompt
    /** Whether a produce turn asks by meaning or by ear; [ProducePrompt.Source] elsewhere. */
    val producePrompt: ProducePrompt = ProducePrompt.Source,
    /** `reviewCount == 0` — the word is being taught, so a miss is still written out. */
    val firstExposure: Boolean = false,
    /** A word that already sticks is never slowed down by a write-out. */
    val consolidated: Boolean = false,
    /** Which face carries the picture; null when the word has none. */
    val emojiCue: EmojiCue?,
    /**
     * What the prompt says out loud, or null where the card owes that very form —
     * non-null ⇔ kern's cue puts the target on screen from frame one.
     */
    val promptPronunciation: Pronunciation?,
    val segments: List<AnswerTone>,
    val remaining: Int,
    /** What the round bought ([SessionRunState]'s buckets); the summary spells the non-zero parts. */
    val introduced: Int,
    val strengthened: Int,
    val reviewed: Int,
    /** Whether an endless refill would yield anything — what "Weiter üben" turns on. */
    val canPracticeMore: Boolean,
    /** The day streak the finish names, and whether it stands at its all-time best. */
    val streakDays: Int = 0,
    val streakIsRecord: Boolean = false,
    /**
     * Today's recall is far enough under what the schedule expects that more reps buy
     * little — the box saying so plainly, where a round that only celebrates would be
     * contradicted by the next one.
     */
    val restSuggested: Boolean = false,
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

    /**
     * The trailing fortnight the activity strip draws, oldest day first.
     * Kern walks it beside the streak number, so a strip and a flame cannot disagree —
     * refreshed with the rest of the numbers, never re-derived per composition.
     */
    var activityWindow by mutableStateOf<List<ActivityDay>>(emptyList())
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

    /**
     * The box browser. [area] names the shelf to open unfolded, for the surfaces that
     * reach the box BY an area; null opens wherever the learner left off.
     */
    fun openBox(area: String? = null) {
        screen = Screen.Box(area)
    }

    fun closeBox() {
        // why: the browser's rows speak on tap — nothing may keep talking into Heute.
        pronouncer.stop()
        screen = Screen.Heute
    }

    /**
     * What a shelf is CALLED to this learner — the browser's own rule ([AreaNaming]),
     * so the cue an ambiguous prompt carries and the heading it stands under in the box
     * can never disagree about the name of an area.
     */
    fun areaTitle(area: String): String {
        val cat = catalog
        val source = box?.joinStamp?.source
        return AreaNaming(
            chrome = chrome,
            catalogTitle = { if (source == null) null else cat?.areaTitle(it, source) },
            catalogSubtitle = { if (source == null) null else cat?.areaSubtitle(it, source) },
            catalogEmoji = { cat?.areaEmoji(it) },
        ).title(area)
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

    /**
     * The one door a box SURFACE changes the box through — packing a shelf, waking a
     * word, a word of one's own, a reset. The change itself is kern's: the caller hands
     * back what a [BoxEngine] call returned, and this is the platform half of it, the
     * observable state and the disk and the numbers Heute reads.
     *
     * Anything that touches a SCHEDULE goes through the run instead ([dispatch]) —
     * every answer is a review, and only kern's session machine books one.
     */
    fun updateBox(change: (BoxState) -> BoxState) {
        val state = box ?: return
        val next = change(state)
        box = next
        persist(next)
        refreshStats()
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
                // why: the day is folded and the numbers refreshed before this runs
                // (`DayBooked` precedes it in [dispatch]), so the finish names the streak
                // the answer just extended rather than the one it started with.
                streakDays = stats?.streak ?: 0,
                streakIsRecord = stats?.let { SessionRun.streakIsRecord(it) } == true,
                restSuggested = BoxEngine.today(state, now(), tz()).recallStrained,
            )
        } else {
            val count = state.scheduling[card.id]?.reviewCount ?: 0
            val role = presentationRole(card.id, count)
            val promptForm = recognitionPromptForm(card, count)
            val consolidated = isConsolidated(card.id)
            val prompt = producePrompt(card.id, count, consolidated, audible(card))
            SessionUi(
                card = card,
                role = role,
                promptForm = promptForm,
                producePrompt = prompt,
                // The two facts the turn's write-out rule is decided on, read where the
                // count already is: a word being taught is written once as it is met,
                // and one that already sticks — consolidated, the one landed bar — is
                // never slowed down.
                firstExposure = count == 0,
                consolidated = consolidated,
                emojiCue = card.emoji?.let { emojiCue(role, consolidated) },
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
        activityWindow = streakWindow(state.dailyStats, ACTIVITY_WINDOW_DAYS, now(), tz())
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
