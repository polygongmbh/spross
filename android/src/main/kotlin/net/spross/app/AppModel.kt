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
import net.spross.app.audio.CueSounds
import net.spross.app.audio.Pronouncer
import net.spross.app.listen.ListeningDriver
import net.spross.app.ui.AreaNaming
import net.spross.app.widget.WordWidget
import net.spross.kern.box.ACTIVITY_WINDOW_DAYS
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.ShelfCounts
import net.spross.kern.box.mergeDailyStats
import net.spross.kern.box.streakWindow
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.listen.ListeningCandidate
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.Card
import net.spross.kern.model.DayStats
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
import net.spross.kern.model.shownArticle
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.SessionEffect
import net.spross.kern.session.SessionIntent
import net.spross.kern.listen.ListeningPool
import net.spross.kern.session.SessionOffers
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState
import net.spross.kern.snapshot.WidgetSnapshotBuilder
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoreFormatException
import net.spross.kern.store.withProductCalibration
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.TrainerMode

sealed interface Screen {
    data object Loading : Screen
    data object Onboarding : Screen
    data object Heute : Screen
    data object Session : Screen
    data object About : Screen

    /**
     * A listening run: a full screen like every other Android run, and the one made
     * entirely of sound. Back mirrors its ✕.
     */
    data object Listening : Screen

    /** The Zahlen page: the picks and the button first, the reference under them. */
    data object Numbers : Screen

    /** The Buchstaben page: the stages and the button first, the alphabet under them. */
    data object Letters : Screen

    /** The Länder page: the rungs and the button first, the atlas table under them. */
    data object Countries : Screen

    /** A slot run, carrying the spec the page it was started from spelled. */
    data class Trainer(val mode: TrainerMode) : Screen

    data object LetterDrill : Screen

    /**
     * An atlas run, carrying the two things the page settled before it opened: which way
     * round the questions are asked, and whether a rung falls on one clean win.
     */
    data class CountryDrill(val reverse: Boolean, val fast: Boolean) : Screen

    /**
     * The box browser. [area] is the shelf it opens UNFOLDED — the screen was reached by
     * naming that area, from a search hit or a tree — and null opens where the learner
     * left off ([net.spross.kern.box.BoxBrowser.defaultExpandedGroupId]).
     */
    data class Box(val area: String? = null) : Screen
}

data class SessionUi(
    val card: Card?,               // null ⇒ drained: show the summary
    val role: PresentationRole?,
    val promptForm: String?,       // rotated recognition prompt
    /** Whether a produce turn asks by meaning or by ear; [ProducePrompt.Source] elsewhere. */
    // layer-ok: the drained branch has no produce turn — every real one carries kern's cue
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
    val segments: List<AnswerOutcome>,
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
    private val prefs = app.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val profile = ProfileStore(prefs)

    /**
     * What to call the learner, or null where no name was given — what the greeting knows
     * about who it greets, kept per person and not per pair ([ProfileStore.name]).
     */
    var learnerName by mutableStateOf(profile.name)
        private set

    /** The run kern steps; null between sessions. The screen reads [sessionUi] instead. */
    private var sessionRun: SessionRunState? = null

    /** The one door to a spoken target word — review cards and both drills. */
    val pronouncer = Pronouncer(app, prefs)

    /** The verdict chimes, loaded here so the first answer of a session pays no decode. */
    val cues = CueSounds(app)

    /**
     * The listening run — a playlist over the learner's own words that asks nothing.
     *
     * It lives beside the session run rather than inside it because it is not one: it books
     * no review, writes no schedule and moves no streak, so it holds no [BoxState] at all.
     */
    val listening = ListeningDriver(app, this)

    /**
     * What a listening run may draw from on THIS device, swept on activation and on every
     * foreground ([listeningReport]) rather than per composition — it is a catalog walk, and
     * the Heute card asks for it on every frame it stands on.
     */
    var listeningPool by mutableStateOf<List<ListeningCandidate>>(emptyList())
        private set

    /**
     * The Werkstatt's standing: the climbed ladder, what the letter drill can ask here, and
     * what the last run came to. Its store is kern-keyed SharedPreferences — a drill touches
     * no card and no schedule, so none of it is box state.
     */
    val werkstatt = Werkstatt(TrainerStore(prefs))

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var box by mutableStateOf<BoxState?>(null)
        private set
    var stats by mutableStateOf<BoxStatistics?>(null)
        private set

    /**
     * The atlas joined for this profile, or null where the pair has no drill at all —
     * kern's registry by file, and the whole of what the Länder chip gates on.
     *
     * Joined ONCE, as the profile activates: the hub, the page and the run read the very
     * same rows, so the manifest is never walked per composition and the table can never
     * drift from what the run grades against.
     */
    var atlas by mutableStateOf<CountryDrillContent?>(null)
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

    /**
     * What each shelf's two pack controls would do, every area at once
     * (`BoxBrowser.shelfCounts`).
     *
     * The browser draws both numbers on every shelf it lists, and asked one shelf at a
     * time each answer scans and sorts the whole box — so a screenful cost sixty walks
     * per frame. Refreshed with the rest of the numbers, never per composition.
     */
    var shelfCounts by mutableStateOf<Map<String, ShelfCounts>>(emptyMap())
        private set

    /**
     * Whether a round still owes the learner the three lines that teach it
     * ([SessionCoach]). Armed when onboarding opens that round, cleared when it closes,
     * and in memory only — an app killed in between is simply back without the coaching.
     */
    var coachPending by mutableStateOf(false)
        private set

    /**
     * Whether the round on screen still owes its coaching lines ([SessionCoach]) — the
     * round onboarding opened, from its first card to the last one it hands out.
     */
    val coachActive: Boolean
        get() = coachPending

    /**
     * What the self-grade row stands under: the first round's coaching while it is owed,
     * else the standing question. One slot, one line — both paths to the row (recognize,
     * and produce's blank reveal) read it.
     */
    val gradeCaption: String
        get() = if (coachActive) chrome.coachGrade else chrome.ratingQuestion
    var sessionUi by mutableStateOf<SessionUi?>(null)
        private set
    var chrome by mutableStateOf(Chrome.forSource("en"))
        private set
    var normalizer: AnswerNormalizer? = null
        private set

    /**
     * The SOURCE language's grading, for the one turn typed in it: a card asked by ear
     * owes what the word MEANS, and the articles and typo budget it is measured under
     * are that language's own (`kern/docs/presentation.md`).
     */
    var meaningNormalizer: AnswerNormalizer? = null
        private set

    /**
     * `dailyStats` from every OTHER target-language box on disk — the box's streak
     * is one commitment across every language the learner studies, not one per
     * language ([net.spross.kern.box.mergeDailyStats]). Reloaded whenever [activate]
     * switches languages; those files only change while THEY are the active target,
     * so a per-answer disk read for each of them would be wasted work.
     */
    private var otherLanguagesDailyStats: List<Map<String, DayStats>> = emptyList()

    /**
     * Produce grading with the whole join in view: a form the catalog owns
     * elsewhere is that word, never a typo of this card's answer (`kern/docs/grading.md`).
     * One pass over every accepted form the join carries — thousands of normalized
     * strings — so it is built on the first turn that asks and kept until
     * [refreshStats] retires it. A card that arrives after the box moved is still
     * graded against the box standing now: everything that can move the join
     * refreshes the numbers with it.
     */
    private var cachedProduceGrader: CatalogAnswerGrader? = null

    val produceGrader: CatalogAnswerGrader?
        get() {
            cachedProduceGrader?.let { return it }
            val norm = normalizer ?: return null
            val state = box ?: return null
            return CatalogAnswerGrader(norm, state.cards.values.toList())
                .also { cachedProduceGrader = it }
        }

    // internal: the box surfaces stamp their own verbs with it (`FeedbackActions.kt`),
    // and a second reading of the clock is how two of them end up disagreeing.
    internal fun now(): Long = System.currentTimeMillis()
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
                screen = Screen.Onboarding
            }
        }
    }

    /**
     * The source a fresh install opens with. Kern's rule, over the device's report:
     * asking [Catalog.availableTargets] about an undeclared locale THROWS, so a French
     * or Italian phone used to crash on launch here.
     */
    fun defaultSource(cat: Catalog): String = cat.defaultSource(Locale.getDefault().language)

    /**
     * The pair is settled. [thenPractice] is the FIRST-RUN path only — the picker is the
     * last question the app asks, so the round it was made for opens straight away rather
     * than behind one more button on Heute. A language change from the box's settings
     * passes false: it must not raise a session over the screen you were reading.
     */
    fun completeOnboarding(source: String, target: String, thenPractice: Boolean = false) {
        profile.set(source, target)
        viewModelScope.launch {
            activate(source, target)
            if (!thenPractice) return@launch
            // why: the coaching arms with the round that actually opens — an install with
            // nothing to practice yet must not carry it into some later round.
            if (sessionAvailable) {
                coachPending = true
                startSession()
            }
        }
    }

    /** Blank clears it: the store trims, and an empty name is simply no name. */
    fun renameLearner(raw: String?) {
        profile.name = raw
        learnerName = profile.name
    }

    /**
     * The name the device suggests for the onboarding field, where it is named after
     * somebody at all ([DeviceName]). Asked once, on the screen that offers it — nothing
     * is stored until the learner leaves it standing.
     */
    fun suggestedLearnerName(): String? =
        DeviceName.suggestedLearnerName(getApplication<Application>().contentResolver)

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

    /**
     * The hub's three entries. Each opens a PAGE, never a run: reading matter and the
     * drill it prepares you for are one surface, and the run is what the page is opened
     * for, so the picks and the button sit above the reading.
     *
     * The ladder is re-read on the way in — a run closed earlier may have opened a rung —
     * and last night's figures are not news, so the result tile starts clear.
     */
    fun openNumbers() {
        werkstatt.clearResult()
        refreshWerkstatt()
        screen = Screen.Numbers
    }

    fun openLetters() {
        werkstatt.clearResult()
        refreshWerkstatt()
        screen = Screen.Letters
    }

    fun openCountries() {
        werkstatt.clearResult()
        refreshWerkstatt()
        screen = Screen.Countries
    }

    /** Back to Heute from any of them — nothing may keep talking into it. */
    fun closeOverview() {
        pronouncer.stop()
        screen = Screen.Heute
    }

    /**
     * The listening card's standing — whether there is anything to hear at all. The pool
     * already grows a thin selection as far as the content allows, so whatever is left IS
     * all there is, and a short one simply laps.
     */
    val listeningOffered: Boolean
        get() = listeningPool.isNotEmpty()

    /**
     * Re-sweeps what listening can play, and carries the result into a run already going.
     * A voice installed in Settings while the app slept turns the card on without a relaunch.
     */
    fun refreshListening() {
        val state = box ?: return
        val cat = catalog ?: return
        val stamp = state.joinStamp
        // why: the voice table belongs to the synthesizer and is read here; the sweep it
        // feeds walks the whole join asking the catalog for BOTH sides' audio — some
        // sixteen hundred lookups on a full profile — and runs on every foreground.
        val hasTarget = pronouncer.canSpeak(stamp.target)
        val hasSource = pronouncer.canSpeak(stamp.source)
        viewModelScope.launch {
            val report = withContext(Dispatchers.Default) {
                ListeningPool.report(
                    cat, state, stamp.source, stamp.target,
                    hasTargetVoice = hasTarget, hasSourceVoice = hasSource,
                )
            }
            listeningPool = report.candidates
            listening.refresh(report.candidates)
        }
    }

    /**
     * Opens the playlist. Nothing else may be talking into it — a word left sounding from
     * the screen behind would land in the middle of the run's first turn.
     */
    fun startListening() {
        if (listeningPool.isEmpty()) return
        pronouncer.stop()
        listening.start(listeningPool)
        screen = Screen.Listening
    }

    /** The ✕, Back, and the bedtime running out all arrive here. */
    fun closeListening() {
        listening.stop()
        screen = Screen.Heute
    }

    fun startTrainerRun(mode: TrainerMode) {
        screen = Screen.Trainer(mode)
    }

    fun startLetterDrill() {
        screen = Screen.LetterDrill
    }

    /**
     * An atlas run. Both switches are the page's to settle — Fast has a price and the page
     * has already checked it — so the run only obeys them.
     */
    fun startCountryDrill(reverse: Boolean, fast: Boolean) {
        screen = Screen.CountryDrill(reverse, fast)
    }

    /**
     * A closed run has no screen of its own: its figures travel back to the page that
     * started it, which wears them as one tile above the picks.
     *
     * [summary] null ⇒ nothing was answered; the run simply closes. The ladder is re-read
     * because a closing run books the rungs it stood on, and the rows behind it are stale
     * the moment it leaves.
     */
    fun finishDrill(back: Screen, summary: DrillRunSummary?, title: String) {
        pronouncer.stop()
        werkstatt.show(summary, title)
        refreshWerkstatt()
        screen = back
    }

    /**
     * What the two pages read: the climbed ladder, and what the letter drill can ask on
     * THIS device. Recomputed rather than cached — a rung opens as a run closes, and a
     * voice may be installed in Settings while the app sleeps
     * (`SprossActivity.onResume` calls this too).
     *
     * Never on the way to Heute: the Werkstatt card gates on file presence alone, and this
     * is a catalog sweep no start-up should pay for.
     */
    fun refreshWerkstatt() {
        val stamp = box?.joinStamp ?: return
        werkstatt.readLadder(stamp.target)
        werkstatt.seeLetters(letterReport())
        werkstatt.readCountries(stamp.source, stamp.target)
    }

    private suspend fun activate(source: String, target: String) {
        val cat = catalog ?: return
        chrome = Chrome.forSource(source)
        val stamp = JoinStamp(source, target, cat.fingerprint)
        val stored = withContext(Dispatchers.IO) { boxFiles.read(target) }
        // why: the join builds every card the profile holds, the decode parses every
        // schedule and every review ever logged, and the atlas walks the whole country
        // manifest — none of it belongs on the thread that has to draw the first frame.
        val loaded = withContext(Dispatchers.Default) {
            val cards = cat.join(source, target)
            val restored = stored?.let { json ->
                try {
                    // why: calibration belongs to the BUILD — a box written months ago would
                    // otherwise keep pacing itself by the numbers that shipped with it.
                    StoreCodec.decode(json).join(cards, stamp).withProductCalibration()
                } catch (_: StoreFormatException) {
                    null // unreadable document: start fresh rather than crash (pre-production)
                }
            }
            // why: the pair only changes here — the hub reads the atlas on every
            // composition, and a sweep per frame is one no start-up should pay.
            Pair(
                restored ?: BoxEngine.bootstrap(cards, BoxConfig.product(), stamp),
                cat.countryDrillContent(source, target),
            )
        }
        val (state, joinedAtlas) = loaded
        box = state
        atlas = joinedAtlas
        normalizer = AnswerNormalizer(cat.languages.getValue(target))
        meaningNormalizer = AnswerNormalizer(cat.languages.getValue(source))
        // why: only a box that did not exist yet owes the disk anything here. A re-join is
        // derived from what is already stored and reproduces itself on the next launch.
        if (stored == null) persist(state)
        otherLanguagesDailyStats = withContext(Dispatchers.IO) { loadOtherLanguagesDailyStats(cat, target) }
        refreshStats()
        refreshListening()
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

    /**
     * `dailyStats` for every catalog language except [target], read straight off
     * disk (no catalog join needed for a day tally). A sibling box that is missing
     * or fails to decode is skipped — its own load path surfaces the real error
     * when the learner switches to it.
     */
    private fun loadOtherLanguagesDailyStats(cat: Catalog, target: String): List<Map<String, DayStats>> =
        cat.languages.keys.filter { it != target }.mapNotNull { language ->
            boxFiles.read(language)?.let { json ->
                try {
                    StoreCodec.decode(json).dailyStats
                } catch (_: StoreFormatException) {
                    null
                }
            }
        }

    fun startSession() = begin(SessionIntent.Start)

    /**
     * The done card's extra round: kern composes the mixing round itself — everything due,
     * packed vocab within the budget, then pull-aheads — and no-ops when that is empty.
     */
    fun startExtraSession() = begin(SessionIntent.StartExtra)

    /**
     * The session card's short round: the day's own round taken short — its due work
     * alone, a round's worth of it — and a no-op when that is empty.
     */
    fun startShortSession() = begin(SessionIntent.StartShort)

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

    /**
     * Take the card on screen out of the round: suspend it and step past with no rating
     * at all. Never a grade — the learner is saying it should not be ASKED, not that
     * they failed it ([SessionIntent.SuspendCurrent]).
     */
    fun suspendCurrentCard() {
        sessionRun ?: return
        pronouncer.stop()
        dispatch(SessionIntent.SuspendCurrent)
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
        // why: one round is what the coaching is for, and leaving is what says it was
        // read — a learner who quits after two cards still comes back to a quiet screen.
        coachPending = false
    }

    /**
     * Step the run and honor what it asks for. The whole session machine is kern's;
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
                is SessionEffect.Persist ->
                    persist(reduction.state.box, effect.immediate, widget = effect.immediate)
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
     * [producePrompt] cannot have. Four ways it cannot, and each keeps the source
     * prompt rather than putting up a card with nothing in it: no recording and no
     * voice, reading aloud switched off, a device turned down or muted by its own
     * volume, and TalkBack, which suppresses every autoplay so nothing may speak over
     * the screen reader.
     */
    private fun audible(card: Card): Boolean {
        if (pronouncer.muted || pronouncer.readsScreenAloud || pronouncer.deviceSilenced) return false
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
                // why: `DayBooked` precedes this in [dispatch], so [canPracticeExtra] was
                // taken against the box this summary is for — asking again would compose
                // the same round a second time.
                canPracticeMore = canPracticeExtra,
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
                        // why: the prompted form's own article — `shownArticle` withholds it
                        // from a rotated synonym, so only the canonical form hears one.
                        shownArticle(
                            CardDisplay.article(card.target),
                            if (prompt == ProducePrompt.Sound) card.target.text else promptForm,
                            card.target.text,
                        ),
                    ),
                segments = active.segments,
                remaining = active.remaining,
                introduced = active.newCards,
                strengthened = active.graduated,
                reviewed = active.reviews,
                // why: only the finished round shows this, and composing a whole round
                // to fill a field no card on screen reads is a pause between cards.
                canPracticeMore = canPracticeExtra,
            )
        }
        sessionUi = ui
    }

    private fun refreshStats() {
        val state = box ?: return
        // why: the grader snapshots the join, and this runs wherever the join, the
        // queue or the profile's languages can have moved — the one place it goes stale.
        cachedProduceGrader = null
        stats = BoxEngine.statistics(state, now(), tz(), otherLanguagesDailyStats)
        // why: the strip reads the same merged days the streak does — a day worked in
        // another language is still a day worked, on the picture as well as the count.
        activityWindow = streakWindow(
            mergeDailyStats(otherLanguagesDailyStats + state.dailyStats),
            ACTIVITY_WINDOW_DAYS,
            now(),
            tz(),
        )
        sessionAvailable = SessionOffers.sessionAvailable(state, now(), tz())
        canPracticeExtra = SessionOffers.canPracticeMore(state, now(), tz())
        shelfCounts = BoxBrowser.shelfCounts(state)
    }

    override fun onCleared() {
        super.onCleared()
        // why: a run holds the audio focus and a chain of armed beats — neither may
        // outlive the model, or the phone is left with a playlist nobody owns.
        listening.stop()
        // why: the synthesizer holds a binding to another process and the players their
        // decoded clips — none of it may outlive the model that opened them.
        pronouncer.release()
        cues.release()
    }

    /**
     * Every answer persists (small doc, IO thread) — process death mid-session then costs
     * at most the in-flight card, matching iOS's debounced-save guarantee.
     *
     * [immediate] is the day's fold asking to be on disk BEFORE the caller returns: it is
     * dispatched from `onStop`, where the process may not live long enough for a queued
     * write, and a fold that never reaches disk is no fold.
     *
     * [widget] rebuilds the tile's snapshot. Off for the answers inside a round and only
     * those: building it walks the exposure ranking, every active card and every day the
     * box has tallied, and the tile's worth is long-term exposure — a round's staleness
     * does not touch it, while a rebuild per card is the same order of work as the box
     * document itself (`kern/docs/snapshots.md`).
     */
    private fun persist(state: BoxState, immediate: Boolean = false, widget: Boolean = true) {
        val target = state.joinStamp.target
        val stamp = now()
        if (immediate) {
            boxFiles.write(target, StoreCodec.encode(state))
            if (widget) {
                boxFiles.writeWidgetSnapshot(widgetSnapshot(state, stamp))
                nudgeWidget()
            }
            return
        }
        // why: NonCancellable — a write racing activity teardown must still land.
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            // why: the encode is the expensive half — the whole document, every schedule
            // and every review ever logged — and it belongs on this thread with the
            // write, not on the one that has to draw the next card.
            boxFiles.write(target, StoreCodec.encode(state))
            if (widget) {
                boxFiles.writeWidgetSnapshot(widgetSnapshot(state, stamp))
                WordWidget.refresh(getApplication())
            }
        }
    }

    /**
     * Redraw of the placed tiles, for the path that cannot wait for one.
     *
     * `updateAll` suspends and `onStop` returns before it could finish; the snapshot is
     * already on disk by then, so a nudge that loses the race costs nothing but
     * promptness — the tile's own update period redraws it either way.
     */
    private fun nudgeWidget() {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            WordWidget.refresh(getApplication())
        }
    }

    /**
     * What the home-screen widget draws, resolved HERE because the widget cannot run
     * the join (`kern/docs/snapshots.md`) — it decodes this and nothing else.
     * Carries the other languages' days for the same reason Heute's strip does: the
     * run is one commitment across every box.
     */
    private fun widgetSnapshot(state: BoxState, nowEpochMillis: Long): String =
        WidgetSnapshotBuilder.build(
            state,
            nowEpochMillis,
            otherLanguagesDailyStats = otherLanguagesDailyStats,
        )
}
