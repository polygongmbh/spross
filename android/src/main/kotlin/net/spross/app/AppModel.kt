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
import net.spross.kern.model.SessionPlan
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.PronunciationCue
import net.spross.kern.model.emojiCue
import net.spross.kern.model.presentationRole
import net.spross.kern.model.pronunciationCue
import net.spross.kern.model.recognitionPromptForm
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.SessionComposer
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoreFormatException

sealed interface Screen {
    data object Loading : Screen
    data class Onboarding(val editing: Boolean) : Screen
    data object Heute : Screen
    data object Session : Screen
    data object About : Screen
}

data class SessionUi(
    val card: Card?,               // null ⇒ drained: show the summary
    val role: PresentationRole?,
    val promptForm: String?,       // rotated recognition prompt
    /** Which face carries the picture; null when the word has none. */
    val emojiCue: EmojiCue?,
    /**
     * What the prompt says out loud, or null where the card owes that very form —
     * non-null ⇔ kern's cue puts the target on screen from frame one.
     */
    val promptPronunciation: Pronunciation?,
    val segments: List<AnswerTone>,
    val remaining: Int,
    val progress: Float,
    val introduced: Int,
    val strengthened: Int,
    val reviewed: Int,
)

class AppModel(app: Application) : AndroidViewModel(app) {

    private val boxFiles = BoxFiles(File(app.filesDir, "box"))
    private val prefs = app.getSharedPreferences("spross", Context.MODE_PRIVATE)
    private val profile = ProfileStore(prefs)
    private var flow: SessionFlow? = null

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

    /** Device language when it can teach something from the catalog, else en. */
    fun defaultSource(cat: Catalog): String {
        val device = Locale.getDefault().language
        return if (cat.availableTargets(device).isNotEmpty()) device else "en"
    }

    fun coveredSources(cat: Catalog): List<String> =
        cat.languages.keys.filter { cat.availableTargets(it).isNotEmpty() }.sorted()

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
                    StoreCodec.decode(json).join(cards, stamp)
                } catch (_: StoreFormatException) {
                    null // unreadable document: start fresh rather than crash (pre-production)
                }
            }
        }
        val state = restored ?: BoxEngine.bootstrap(cards, BoxConfig(), stamp)
        box = state
        normalizer = AnswerNormalizer(cat.languages.getValue(target))
        persist(state)
        refreshStats()
        screen = Screen.Heute
    }

    fun startSession() {
        val state = box ?: return
        begin(SessionComposer.composeSession(state, now(), tz()))
    }

    fun startExtraSession() {
        val state = box ?: return
        // begin() no-ops on an empty plan — the tap only does nothing when
        // BOTH compositions are empty (box without active cards).
        begin(extraSessionPlan(state, now()))
    }

    private fun begin(plan: SessionPlan) {
        val state = box ?: return
        if (plan.isEmpty) return
        flow = SessionFlow(state, plan)
        refreshSessionUi()
        screen = Screen.Session
    }

    fun answerCurrent(rating: Rating) {
        val active = flow ?: return
        // why: the card is leaving — a word still sounding must not follow the learner
        // onto the next one, the same cut iOS makes in resetCardState().
        pronouncer.stop()
        active.answer(rating, now(), tz())
        box = active.box
        persist(active.box)
        refreshSessionUi()
    }

    fun continueEndless() {
        flow?.continueEndless(now())
        refreshSessionUi()
    }

    fun finishSession() {
        val active = flow ?: return
        pronouncer.stop() // the run is over: nothing keeps talking into Heute
        val ended = active.finish(now(), tz())
        flow = null
        sessionUi = null
        box = ended
        persist(ended)
        refreshStats()
        screen = Screen.Heute
    }

    private fun refreshSessionUi() {
        val active = flow ?: run { sessionUi = null; return }
        val card = active.currentCard()
        val ui = if (card == null) {
            SessionUi(
                card = null, role = null, promptForm = null,
                emojiCue = null, promptPronunciation = null,
                segments = active.segments.toList(), remaining = 0, progress = 1f,
                introduced = active.introduced, strengthened = active.strengthened,
                reviewed = active.answered,
            )
        } else {
            val count = active.reviewCount(card.id)
            val role = presentationRole(card.id, count)
            val promptForm = recognitionPromptForm(card, count)
            SessionUi(
                card = card,
                role = role,
                promptForm = promptForm,
                emojiCue = card.emoji?.let {
                    emojiCue(role, active.isSettled(card.id), count)
                },
                // why: the KERN cue, never `role == Recognize` — one rule, consumed by
                // both apps. The PROMPTED form, so a rotated synonym is heard as itself.
                promptPronunciation = catalog
                    ?.takeIf { pronunciationCue(role) == PronunciationCue.Upfront }
                    ?.pronunciation(card.target.lang, promptForm),
                segments = active.segments.toList(),
                remaining = active.remaining,
                progress = active.progress(),
                introduced = active.introduced,
                strengthened = active.strengthened,
                reviewed = active.answered,
            )
        }
        sessionUi = ui
    }

    private fun refreshStats() {
        val state = box ?: return
        stats = BoxEngine.statistics(state, now(), tz())
        sessionAvailable = !SessionComposer.composeSession(state, now(), tz()).isEmpty
    }

    override fun onCleared() {
        super.onCleared()
        // why: the synthesizer holds a binding to another process and the player a
        // decoded clip — neither may outlive the model that opened them.
        pronouncer.release()
    }

    // why: every answer persists (small doc, IO thread) — process death mid-session
    // then costs at most the in-flight card, matching iOS's debounced-save guarantee.
    private fun persist(state: BoxState) {
        val json = StoreCodec.encode(state)
        val target = state.joinStamp.target
        // why: NonCancellable — a write racing activity teardown must still land.
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { boxFiles.write(target, json) }
    }
}
