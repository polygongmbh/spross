package net.spross.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spross.app.audio.CueSounds
import net.spross.app.audio.Pronouncer
import net.spross.app.listen.ListeningDriver
import net.spross.kern.box.ACTIVITY_WINDOW_DAYS
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.ShelfCounts
import net.spross.kern.box.answerDays
import net.spross.kern.box.mergeAnswerDays
import net.spross.kern.catalog.AudioCapability
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.SessionEffect
import net.spross.kern.session.SessionIntent
import net.spross.kern.session.SessionOffers
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState

class AppModel(app: Application) : AndroidViewModel(app) {

    internal val disk = BoxDisk(BoxFiles(File(app.filesDir, "box")))
    private val prefs = app.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val profile = ProfileStore(prefs)

    /**
     * What to call the learner, or null where no name was given — what the greeting knows
     * about who it greets, kept per person and not per pair ([ProfileStore.name]).
     */
    var learnerName by mutableStateOf(profile.name)
        private set

    /** The run kern steps; null between sessions. The screen reads [sessionUi] instead. */
    internal var sessionRun: SessionRunState? = null
        private set

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
     * Whether the listening card stands: a box with words in it, and both sides of a turn
     * having SOMETHING that can say them ([AudioCapability]).
     *
     * Still no sweep — two map lookups and two voice probes, never the walk of the join that
     * dealing the playlist is. Held as state rather than asked per composition because
     * `canSpeak` is an uncached call into the TTS engine on this platform, and Home reads
     * this on every frame it stands on; [refreshListening] takes it again on every
     * foreground, which is when an installed voice can have appeared.
     *
     * The playlist itself is dealt in [startListening], where a run is what needs it.
     */
    var listeningOffered by mutableStateOf(false)
        private set

    /**
     * The free-practice standing. Its store is kern-keyed SharedPreferences — a drill
     * touches no card and no schedule, so none of it is box state.
     */
    val trainer = TrainerStanding(TrainerStore(prefs))

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var box by mutableStateOf<BoxState?>(null)
        private set

    /**
     * Why the box on disk could not be read, where it could not — the reason the load-error
     * card on Home carries. Null whenever the box standing here is the one that was asked for.
     */
    var loadFailure by mutableStateOf<String?>(null)
        private set

    /**
     * Set for the span of [activate] — a source/target switch re-joins the catalog and
     * re-walks the whole box, which takes a beat. The settings pickers show this as a
     * spinner rather than blocking input, since [box] itself stays on the outgoing
     * language until the new one is fully ready.
     */
    var switchingLanguage by mutableStateOf(false)
        private set
    var stats by mutableStateOf<BoxStatistics?>(null)
        private set

    /**
     * The atlas joined for this profile, or null where the pair has no drill at all —
     * kern's registry by file, and the whole of what the Countries chip gates on.
     *
     * Joined ONCE, as the profile activates: the hub, the page and the run read the very
     * same rows, so the manifest is never walked per composition and the table can never
     * drift from what the run grades against.
     */
    var atlas by mutableStateOf<CountryDrillContent?>(null)
        private set

    /**
     * The two calendars joined for this profile, or null where the pair has no dates
     * drill — the atlas rule again: registry by file, joined ONCE as the profile
     * activates, and the whole of what the Dates chip gates on.
     */
    var dates by mutableStateOf<DateDrillContent?>(null)
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
        get() = if (coachActive) chrome.sessionCoachGrade else chrome.sessionRatingQuestion
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
     * Answers per day from every OTHER target-language box on disk — the box's streak
     * is one commitment across every language the learner studies, not one per
     * language ([net.spross.kern.box.mergeAnswerDays]). Reloaded whenever [activate]
     * switches languages; those files only change while THEY are the active target,
     * so a per-answer disk read for each of them would be wasted work.
     */
    var otherLanguagesAnswerDays by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

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
    internal fun tz(): String = TimeZone.getDefault().id

    /** How a surface outside this file changes what is on screen — the screen stays the model's. */
    internal fun navigate(to: Screen) {
        screen = to
    }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Catalog.load(AssetCatalogSource(getApplication<Application>().assets))
            }
            catalog = loaded
            val source = profile.source
            val target = profile.target
            if (source != null && target != null) {
                activate(source, target, Screen.Home)
            } else {
                chrome = Chrome.forSource(defaultSource(loaded))
                screen = Screen.Onboarding
            }
        }
    }

    /**
     * The pair is settled. [thenPractice] is the FIRST-RUN path only — the picker is the
     * last question the app asks, so the round it was made for opens straight away rather
     * than behind one more button on Home. A language change from the box's settings
     * passes false: it must not raise a session over the screen you were reading, and it
     * comes back to the box it was made in — with no area, since the old one may not
     * exist in the new join.
     */
    fun completeOnboarding(source: String, target: String, thenPractice: Boolean = false) {
        profile.set(source, target)
        viewModelScope.launch {
            activate(source, target, if (thenPractice) Screen.Home else Screen.Box())
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
     * Re-asks whether anything can say this pair. Cheap by construction — the catalog's packs
     * are a map lookup and the voice table is two probes — so it rides the foreground, which
     * is where a voice installed in Settings while the app slept turns up.
     */
    fun refreshListening() {
        val stamp = box?.joinStamp
        listeningOffered = stamp != null && box?.cards?.isNotEmpty() == true &&
            !audioSources(stamp.source).silent && !audioSources(stamp.target).silent
    }

    /**
     * Join the pair and stand the box up on it. [landing] is where the learner ends up —
     * the caller knows where they came FROM, and this has no back stack to read it off.
     */
    internal suspend fun activate(source: String, target: String, landing: Screen) {
        val cat = catalog ?: return
        switchingLanguage = true
        try {
            chrome = Chrome.forSource(source)
            val joined = disk.join(cat, source, target, tz()).getOrElse { unreadable ->
                // why: a box that exists but cannot be read must never read as an EMPTY one —
                // bootstrapping here would hand the learner a fresh box and hide the loss, so
                // Home says so instead and the file on disk is left exactly as it stands.
                Log.w("Spross", "box for $target unreadable: ${unreadable.message}", unreadable)
                loadFailure = unreadable.message ?: "StoreFormatException"
                // why: Home is the one screen that carries the failure card, so a load that
                // failed goes there whatever the caller asked for.
                screen = Screen.Home
                return
            }
            loadFailure = null
            box = joined.box
            atlas = joined.atlas
            dates = joined.dates
            normalizer = AnswerNormalizer(cat.languages.getValue(target))
            meaningNormalizer = AnswerNormalizer(cat.languages.getValue(source))
            otherLanguagesAnswerDays = joined.otherLanguagesAnswerDays
            refreshStats()
            refreshListening()
            // why: only a box that did not exist yet owes the disk anything here. A re-join
            // is derived from what is already stored and reproduces itself on the next launch.
            if (joined.fresh) persist(joined.box)
            screen = landing
        } finally {
            switchingLanguage = false
        }
    }

    /**
     * The one door a box SURFACE changes the box through — packing a shelf, waking a
     * word, a word of one's own, a reset. The change itself is kern's: the caller hands
     * back what a [BoxEngine] call returned, and this is the platform half of it, the
     * observable state and the disk and the numbers Home reads.
     *
     * Anything that touches a SCHEDULE goes through the run instead ([dispatch]) —
     * every answer is a review, and only kern's session machine books one.
     */
    /**
     * Apply a change nothing derived reads, and let it ride out with the next save.
     *
     * The counterpart to [updateBox], for the change that moves no card, no schedule and
     * no tally: there is nothing for `refreshStats` to take again, and nothing new for the
     * widget to show.
     */
    fun stampBox(change: (BoxState) -> BoxState) {
        val state = box ?: return
        val next = change(state)
        box = next
        persist(next, widget = false)
    }

    fun updateBox(change: (BoxState) -> BoxState) {
        val state = box ?: return
        val next = change(state)
        box = next
        persist(next)
        refreshStats()
    }

    fun finishSession() {
        sessionRun ?: return
        pronouncer.stop() // the run is over: nothing keeps talking into Home
        dispatch(SessionIntent.Close)
        dropRun()
        screen = Screen.Home
        // why: one round is what the coaching is for, and leaving is what says it was
        // read — a learner who quits after two cards still comes back to a quiet screen.
        coachPending = false
    }

    /**
     * Step the run and honor what it asks for. The whole session machine is kern's;
     * this is the platform half — the clock, the disk, and the observable state.
     */
    internal fun dispatch(intent: SessionIntent, blocking: Boolean = false): SessionRunState? {
        val state = box ?: return null
        // The box may have moved outside the run (a fresh load, settings) — carry it in.
        val current = sessionRun?.let { SessionRun.withBox(it, state) } ?: SessionRun.idle(state)
        val reduction = SessionRun.reduce(current, intent, now(), tz())
        sessionRun = reduction.state
        box = reduction.state.box
        for (effect in reduction.effects) {
            when (effect) {
                is SessionEffect.Persist ->
                    persist(reduction.state.box, widget = effect.immediate, blocking = blocking)
                SessionEffect.DayBooked -> refreshStats()
            }
        }
        refreshSessionUi()
        return reduction.state
    }

    /** Leaves no run behind: nothing on screen reads one, and the next start begins idle. */
    internal fun dropRun() {
        sessionRun = null
        sessionUi = null
    }

    private fun refreshSessionUi() {
        sessionUi = sessionRun?.let { sessionUiFor(it) }
    }

    private fun refreshStats() {
        val state = box ?: return
        // why: the grader snapshots the join, and this runs wherever the join, the
        // queue or the profile's languages can have moved — the one place it goes stale.
        cachedProduceGrader = null
        stats = BoxEngine.statistics(state, now(), tz(), otherLanguagesAnswerDays)
        // why: the strip reads the same merged days the streak does — a day worked in
        // another language is still a day worked, on the picture as well as the count.
        activityWindow = BoxEngine.activityWindow(
            state,
            ACTIVITY_WINDOW_DAYS,
            now(),
            tz(),
            otherLanguagesAnswerDays,
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
}
