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
import net.spross.app.reminder.ReminderStanding
import net.spross.app.reminder.ReminderStore
import net.spross.app.widget.WordWidget
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.answerDays
import net.spross.kern.box.mergeAnswerDays
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.countryDrillContent
import net.spross.kern.catalog.dateDrillContent
import net.spross.kern.model.JoinStamp
import net.spross.kern.reminder.ReminderMode
import net.spross.kern.session.AnswerNormalizer
import net.spross.kern.session.SessionEffect
import net.spross.kern.session.SessionIntent
import net.spross.kern.session.SessionRun
import net.spross.kern.session.SessionRunState
import net.spross.kern.store.BoxChange
import net.spross.kern.store.StoreFormatException

class AppModel(app: Application) : AndroidViewModel(app) {

    internal val store: BoxStore = BoxStore(
        BoxFiles(File(app.filesDir, "box")),
        redrawWidget = { WordWidget.refresh(app) },
        replanReminders = { reminders.replan(it, store.everyLanguage(), now(), tz()) },
    )
    private val prefs = app.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
    internal val profile = ProfileStore(prefs)

    /** The practice reminders the learner chose, re-armed on every save of the box. */
    val reminders = ReminderStanding(app, ReminderStore(prefs))

    /**
     * What to call the learner, or null where no name was given — what the greeting knows
     * about who it greets, kept per person and not per pair ([ProfileStore.name]).
     */
    var learnerName by mutableStateOf(profile.name)
        private set

    /** The run kern steps; null between sessions. The screen reads [sessionUi] instead. */
    internal var sessionRun: SessionRunState? = null
        private set

    /** The one door to a spoken target word — review cards and every drill. */
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
     * Whether the listening card stands (kern's `listeningOffered`). Held as state rather than
     * asked per composition because `canSpeak` is an uncached call into the TTS engine on this
     * platform, and Home reads this on every frame it stands on; [refreshListening] takes it
     * again on every foreground, which is when an installed voice can have appeared.
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
    /** The numbers the box surfaces read, taken again whenever the box moves. */
    val numbers = BoxNumbers()

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
     * Debug launches only: the age of a fabricated box ([net.spross.kern.design.SampleTrees])
     * that stands in the Trees picture and on a round's summary instead of this one.
     */
    var sampleTreesAge: Double? = null

    /** A challenge code a link opened the app with, until the numbers page takes it ([openChallengeLink]). */
    var pendingChallengeCode by mutableStateOf<String?>(null)

    /**
     * Whether the round on screen still owes the learner the three lines that teach it
     * ([SessionCoach]). Armed when onboarding opens that round, cleared when it closes,
     * and in memory only — an app killed in between is simply back without the coaching.
     */
    var coachActive by mutableStateOf(false)
        private set
    var sessionUi by mutableStateOf<SessionUi?>(null)
        private set
    var chrome by mutableStateOf(Chrome.forSource("en"))
        private set

    /** The active pair's review grading; null before a pair is joined. */
    var grading: PairGrading? = null
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

    // internal: the box surfaces stamp their own verbs with it (`FeedbackActions.kt`),
    // and a second reading of the clock is how two of them end up disagreeing.
    internal fun now(): Long = System.currentTimeMillis()
    internal fun tz(): String = TimeZone.getDefault().id

    /** Takes the learner's reminder choice and arms it at once, without waiting for the next save. */
    fun chooseReminders(mode: ReminderMode, setMinutes: List<Int>) {
        reminders.choose(mode, setMinutes, now())
        replanReminders()
    }

    /** why: on every return, so a day that has turned since the last save drops what it already answered. */
    fun replanReminders() {
        val state = box ?: return
        viewModelScope.launch(Dispatchers.IO) { reminders.replan(state, store.everyLanguage(), now(), tz()) }
    }

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
     * than behind one more button on Home. A language change from the settings passes
     * false: it must not raise a session over the screen you were reading, and it stays
     * on the screen it was made from.
     */
    fun completeOnboarding(source: String, target: String, thenPractice: Boolean = false) {
        profile.set(source, target)
        val from = screen
        viewModelScope.launch {
            activate(source, target, if (thenPractice) Screen.Home else from)
            if (!thenPractice) return@launch
            // why: the coaching arms with the round that actually opens — an install with
            // nothing to practice yet must not carry it into some later round.
            if (numbers.homeStanding?.offer?.hasRound == true) {
                coachActive = true
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
        listeningOffered = stamp != null && net.spross.kern.listen.listeningOffered(
            hasWords = box?.cards?.isNotEmpty() == true,
            source = audioSources(stamp.source),
            target = audioSources(stamp.target),
        )
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
            val saved = try {
                withContext(Dispatchers.IO) { store.load(target) }
            } catch (unreadable: StoreFormatException) {
                // why: a box that exists but cannot be read must never read as an EMPTY one —
                // opening it as one would hand the learner a fresh box and hide the loss, so
                // Home says so instead and the file on disk is left exactly as it stands.
                Log.w("Spross", "box for $target unreadable: ${unreadable.message}", unreadable)
                loadFailure = unreadable.message ?: "StoreFormatException"
                // why: Home is the one screen that carries the failure card, so a load that
                // failed goes there whatever the caller asked for.
                screen = Screen.Home
                return
            }
            // why: the join builds every card the profile holds, replaying the logs re-applies
            // every answer ever given, and the atlas walks the whole country manifest — none of
            // it belongs on the thread that has to draw the first frame. The hub reads the atlas
            // and the calendars on every composition, so they are built here, once per pair.
            val (opened, pairAtlas, pairDates) = withContext(Dispatchers.Default) {
                Triple(
                    BoxEngine.open(saved, cat.join(source, target), JoinStamp(source, target, cat.fingerprint)),
                    cat.countryDrillContent(source, target),
                    cat.dateDrillContent(source, target),
                )
            }
            // why: resolved before `box` is published, so the Box screen's first recomposition
            // against the new box already carries matching stats — an IO hop between the two
            // let Compose draw the new box against the outgoing language's stats, which is
            // what jumbled its scroll.
            val days = withContext(Dispatchers.IO) { store.answerDays(excluding = target, tzId = tz()) }
            loadFailure = null
            box = opened.box
            atlas = pairAtlas
            dates = pairDates
            grading = PairGrading(
                words = AnswerNormalizer(cat.languages.getValue(target)),
                meanings = AnswerNormalizer(cat.languages.getValue(source)),
            )
            otherLanguagesAnswerDays = days
            refreshStats()
            refreshListening()
            withContext(Dispatchers.IO) { store.saveNow(opened.box, opened.save) }
            screen = landing
        } finally {
            switchingLanguage = false
        }
    }

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
        save(next, BoxChange.Stamped.saveScope)
    }

    /**
     * The one door a box SURFACE changes the box through — queuing a shelf, unsuspending a
     * word, a word of one's own, a reset. The change itself is kern's: the caller hands
     * back what a [BoxEngine] call returned, and this is the platform half of it, the
     * observable state and the disk and the numbers Home reads.
     *
     * Anything that touches a SCHEDULE goes through the run instead ([dispatch]) —
     * every answer is a review, and only kern's session machine books one.
     */
    fun updateBox(change: (BoxState) -> BoxState) {
        val state = box ?: return
        val next = change(state)
        box = next
        save(next, BoxChange.Changed.saveScope)
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
        coachActive = false
    }

    /**
     * Step the run and honor what it asks for. The whole session machine is kern's;
     * this is the platform half — the clock, the disk, and the observable state.
     */
    internal fun dispatch(intent: SessionIntent): SessionRunState? {
        val state = box ?: return null
        // The box may have moved outside the run (a fresh load, settings) — carry it in.
        val current = sessionRun?.let { SessionRun.withBox(it, state) } ?: SessionRun.idle(state)
        val reduction = SessionRun.reduce(current, intent, now(), tz())
        sessionRun = reduction.state
        box = reduction.state.box
        for (effect in reduction.effects) {
            when (effect) {
                is SessionEffect.Save -> save(reduction.state.box, effect.scope)
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
        grading?.retire()
        numbers.refresh(state, now(), tz(), otherLanguagesAnswerDays, catalog?.oppositePairs.orEmpty())
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
