package net.spross.app

/**
 * The English chrome table.
 *
 * Also the one every source without chrome of its own falls back to ([Chrome.forSource]).
 *
 * GENERATED from App/Sources/Resources/Localizable.xcstrings by scripts/chrome.py —
 * do not edit. Change the wording in the String Catalog, run `scripts/chrome.py --fix`,
 * and both phones say the same sentence by construction.
 */
internal object ChromeEn : Chrome {
    override val greetMorning = listOf(
        "Up for some %s?",
        "A bit of %s to start the day?",
        "%s for the early bird?",
    )
    override val greetDay = listOf(
        "A quick bit of %s?",
        "Some %s in between?",
    )
    override val greetEvening = listOf(
        "Winding down with %s?",
        "A bit more %s tonight?",
    )
    override val greetNight = listOf(
        "A quiet bit of %s?",
        "Late-night %s?",
        "%s for the night owl?",
    )
    override val greetMorningAddressee = "early bird"
    override val greetNightAddressee = "night owl"
    override val heuteTitle = "Today"
    override val practice = "Practice"
    override val extraRound = "One more round?"
    override val doneToday = "Done for today"
    override val dueLabel = "due"
    override val newLabel = "new"
    override val consolidatedLabel = "consolidated"
    override val freshLabel = "fresh"
    override val chooseTitle = "Welcome to Spross!"
    override val chooseSubtitle = "What are you here for?"
    override val iSpeak = "I speak"
    override val iLearn = "I'm learning"
    override val learnerNameQuestion = "How should Spross greet you?"
    override val letsGo = "Let's go!"
    override val back = "Back"
    override val whyTitle = "What Spross is for"
    override val whyBreadthTitle = "Sown, not crammed"
    override val whyBreadthBody = "A few new words a day, spaced so they stay. Better to " +
        "know many words a little than a few perfectly."
    override val whyCompanionTitle = "A companion, not a replacement"
    override val whyCompanionBody = "Spross doesn't replace your course or your language " +
        "partner. Your vocabulary keeps growing between conversations, so you have more to " +
        "say next time."
    override val whyGrammarTitle = "Grammar comes from speaking"
    override val whyGrammarBody = "Spross doesn't teach grammar, just the gender a word " +
        "carries. Where you actually use the words, the rest takes care of itself."
    override val firstRoundTitle = "Your first round"
    override val firstRoundRecognize = "When Spross shows you a word, take a moment. Does it " +
        "ring a bell?"
    override val firstRoundGrade = "Then you reveal and say how well you knew it. Honest " +
        "beats generous: your answer decides when you see the word again."
    override val firstRoundWrite = "When you don't know a word, you can write it out once to " +
        "solidify your memory."
    override val check = "Check"
    override val reveal = "Reveal"
    override val next = "Next"
    override val also = "also: %s"
    override val typoNote = "Small typo – still counts!"
    override val otherWordNote = "By the way: %1\$s means “%2\$s”"
    override val answerPlaceholder = "In %s …"
    override val ratingQuestion = "How well did you know it?"
    override val coachRecognize = "Take a moment. Does it ring a bell?"
    override val coachGrade = "Answer honestly — it decides when you see it again."
    override val coachWrite = "Write it once, so it sinks in."
    override val hard = "Shaky"
    override val good = "Knew it"
    override val unknown = "Not at all"
    override val sessionDone = "All done!"
    override val keepPracticing = "Keep practicing"
    override val finish = "Done"
    override val pluralEquals = "= pl."
    override val pluralOnly = "pl. only"
    override val pluralForm = "pl. %s"
    override val readAloud = "Read words aloud"
    override val stateOn = "on"
    override val stateOff = "off"
    override val pronounce = "Pronounce"
    override val aboutButton = "About"
    override val updateButton = "Updates"
    override val updateOfferTitle = "Newer versions"
    override val updateOfferBody = "Obtainium watches this app's releases and offers each " +
        "new version as it appears. Without it, every update is a download by hand."
    override val updateViaObtainium = "Get Obtainium"
    override val updateDownload = "Download directly"
    override val audioToggle = "Read words aloud"
    override val audioOptionOff = "No audio"
    override val audioOptionRecordings = "Recordings"
    override val audioOptionTts = "Speech"
    override val audioHintOff = "No words are read aloud. Tapping a word still speaks it."
    override val audioHintRecordings = "Bundled recordings first, the system voice for the " +
        "rest."
    override val audioHintTts = "Every word in the system voice — always the same sound, " +
        "article included."
    override val creditsTitle = "Legal & licenses"
    override val creditsRecordings = "%d recordings"
    override val creditsUnmodified = "Recordings shipped unmodified"
    override val creditsCommons = "Recordings from Wikimedia Commons"
    override val trainingTitle = "Sprossen"
    override val trainingSubtitle = "Free practice — no schedule, no limit"
    override val lettersTitle = "Letters"
    override val lettersHear = "Which letter is this?"
    override val lettersSpell = "What's missing in the word?"
    override val lettersDictation = "Write what you hear"
    override val letterChoice = "Letter %s"
    override val replayPrompt = "Play it again"
    override val promptInLanguage = "in %s"
    override val level = "Sprosse %s"
    override val streak = "🔥 %s in a row"
    override val almostTypo = "Almost! Correct spelling"
    override val almostHeard = "You heard"
    override val audioOff = "Sound is off"
    override val enableSound = "Turn sound on"
    override val tasksDoneOne = "%d task 🎯"
    override val tasksDone = "%d tasks 🎯"
    override val bestStreak = "Best streak: 🔥 %s in a row"
    override val answerCorrect = "Correct"
    override val answerAlmost = "Almost correct"
    override val answerWrong = "Wrong"
    override val close = "Close"
    override val numbersTitle = "Numbers"
    override val numbersPage = "Numbers · %s"
    override val lettersPage = "Letters · %s"
    override val overviewPractice = "Practice"
    override val overviewStart = "Start"
    override val tapToHear = "Every row speaks when tapped"
    override val boxTapToHear = "Words speak when tapped"
    override val boxNoAudio = "No audio for this word"
    override val numbersReference = "How this language counts"
    override val numbersNotes = "What to watch out for"
    override val numberSections = mapOf(
        "base" to "Zero to fifteen",
        "tens" to "The tens",
        "irregulars" to "Sixteen to thirty",
        "compounds" to "Put together",
        "hundreds" to "The hundreds",
        "places" to "Thousand, million, billion",
        "forms" to "Beyond counting",
    )
    override val variantClock = "Time"
    override val variantPhrases = "Sentences"
    override val variantForms = "Forms"
    override val modifierReverse = "Reversed"
    override val modifierReverseHint = "The reading is shown, the digits are owed."
    override val modifierFast = "Fast"
    override val modifierFastHint = "One clean answer per Sprosse instead of two."
    override val modifierMix = "Mixed up"
    override val modifierMixHint = "The direction flips every task, and forms grow to the " +
        "size the numbers reached."
    override val combineLocked = "Several in one run, once everything is unlocked."
    override val unlockPrefix = "Unlocks at:"
    override val digitsOne = "🔢 %d digit"
    override val digitsMany = "🔢 %d digits"
    override val record = "Record %s"
    override val streakSpoken = "Streak: %s in a row"
    override val recordSpoken = ", record %s"
    override val answerDigits = "In digits …"
    override val newPlace = "New place: %s"
    override val lookUp = "Look up numbers"
    override val newRecord = "New record!"
    override val stageChoiceEasy = "Four tiles"
    override val stageChoiceEasyHint = "Find the letter you heard among four"
    override val stageChoiceConfusable = "Lookalike tiles"
    override val stageChoiceConfusableHint = "The same choice, between letters that are easy " +
        "to mix up"
    override val stageTyped = "Typing"
    override val stageTypedHint = "Write the letter yourself, with nothing to pick from"
    override val stageDictation = "Dictation"
    override val stageDictationHint = "Write whole words from your own box by ear"
    override val stageDictationLocked = "Needs more consolidated words this device can read " +
        "out"
    override val stageEntry = "your run starts here"
    override val lettersUnavailable = "This device cannot say a letter yet — that needs a " +
        "voice for the language."
    override val alphabetTitle = "Alphabet"
    override val alphabetSpeakName = "Hear the name"
    override val alphabetSpeakExample = "Hear the example"
    override val countriesTitle = "Countries"
    override val countriesPage = "Countries · %s"
    override val countriesReference = "The atlas"
    override val countriesPace = "Every run opens at Sprosse 1 and climbs on by itself."
    override val countriesBest = "Furthest so far: Sprosse %s"
    override val countriesFastHint = "One clean answer per Sprosse instead of three."
    override val countriesReverseHint = "The question comes in %s, the answer is owed in %s."
    override val countryRungs = listOf(
        "The countries of your languages",
        "The names of the languages",
        "The people",
        "The app's languages",
        "What is spoken there?",
        "Common languages of the world",
        "The flag alone",
        "Less common countries and languages",
        "From language to country",
    )
    override val countryRungHints = listOf(
        "What is a country one of your languages is at home in called?",
        "Plus: what is the language itself called?",
        "Plus: what are the people from there called?",
        "The circle widens — the same questions, more countries.",
        "Plus: which language is spoken in this country?",
        "More countries again, the same questions.",
        "Plus: which country is this, from its flag alone? Not in a reversed run.",
        "The rest of what the atlas knows.",
        "Plus: where is this language spoken?",
    )
    override val countryTiers = listOf(
        "Your languages",
        "The app's languages",
        "Common languages",
        "Less common languages",
    )
    override val countryAskCountry = "What is this country called?"
    override val countryAskFlag = "Which country is this?"
    override val countryAskLanguage = "What is this language called?"
    override val countryAskNationality = "What are these people called?"
    override val countryAskSpokenIn = "Which language is spoken there?"
    override val countryAskSpokenWhere = "Where is this language spoken?"
    override val boxTitle = "The box"
    override val boxNav = "Box"
    override val boxSubtitle = "%1\$s of %2\$s cards in progress"
    override val ownWordsTitle = "Your own words"
    override val ownWordsExplainer = "Your own words stand in an area of their own. A " +
        "growing catalog never touches them."
    override val packArea = "Add to box (%s)"
    override val packDone = "All packed"
    override val packWord = "Pack this word"
    override val unpackWord = "Unpack this word"
    override val dequeueArea = "Take out of box (%s)"
    override val queuedWord = "Sown"
    override val suspended = "Paused"
    override val wake = "Wake"
    override val progressConsolidated = "%s consolidated"
    override val progressLearning = "%s growing"
    override val phrasesLocked = "%d sentences"
    override val phrasesLockedSpoken = "%d sentences locked"
    override val stateExpanded = "expanded"
    override val stateCollapsed = "collapsed"
    override val phaseLearning = "Fresh"
    override val phaseRelearning = "Shaky"
    override val phaseSettled = "Growing"
    override val phaseConsolidated = "Grown"
    override val search = "Search"
    override val searchPlaceholder = "Word or area"
    override val searchHint = "Words in either language, and the names of the areas."
    override val searchAreas = "Areas"
    override val searchWords = "Words"
    override val searchNothing = "Nothing for “%s” in the box."
    override val searchWriteOwn = "Write “%s” yourself"
    override val searchClear = "Clear search"
    override val ownWordTitle = "Your own word"
    override val ownWordInLanguage = "In %s"
    override val ownWordPicture = "Picture (optional)"
    override val ownWordAdd = "Add"
    override val ownWordRemove = "Delete word"
    override val settingsTitle = "Settings"
    override val learnerNameTitle = "Your name"
    override val learnerNamePlaceholder = "Name or nickname"
    override val learnerNameHint = "Spross greets you by it. Left empty, the greeting goes " +
        "without one."
    override val profileHint = "Switching the language you speak keeps all your progress; " +
        "each language you learn has its own box."
    override val resetButton = "Reset %s …"
    override val resetHint = "Deletes progress and history — your own words and your other " +
        "languages stay."
    override val resetConfirm = "Delete all learning progress for %s and start over with the " +
        "first words?"
    override val cancel = "Cancel"
    override val reset = "Reset"
    override val copyPrompt = "Write it once in %s …"
    override val copyMismatch = "Not quite — the word is right above."
    override val skipStep = "Skip"
    override val cantListen = "Can't listen right now?"
    override val caughtUpTitle = "Nothing's due right now"
    override val dayReviews = "%d reviews"
    override val dayReviewsOne = "%d review"
    override val dayNewCards = "%d newbies"
    override val dayNewCardsOne = "%d newbie"
    override val dayNewWordsOnly = "%s new words"
    override val dayConsolidated = "%s solidified"
    override val dayAhead = "%d refreshers"
    override val dayAheadOne = "%d refresher"
    override val tomorrowPacked = "Your packed words are in the next round."
    override val tomorrowFresh = "Fresh cards tomorrow. See you then! 👋"
    override val tomorrowDue = "Tomorrow you can review %d cards."
    override val headlineReviews = listOf(
        "Your round is ready",
        "A stack is ready",
        "The box has something for you",
    )
    override val headlineWarmUp = listOf(
        "Time for a refresher",
        "A reunion with familiar words",
        "A quick pass through the familiar",
    )
    override val headlineFreshSet = listOf(
        "Up for some new words?",
        "Time to sow seeds!",
        "A few seeds to sow",
    )
    override val sessionSomeCards = "A few cards for you."
    override val sessionHeldBack = "%d more cards are still waiting for you."
    override val sessionStart = "Let's go!"
    override val sessionShortRound = "Just a short one?"
    override val listenTitle = "Hear your words"
    override val listenSubtitle = "Reinforces shaky words first — optionally in the " +
        "background and with a sleep timer"
    override val listenPause = "Pause"
    override val listenResume = "Resume"
    override val listenSkip = "Next word"
    override val listenRepeat = "Again"
    override val listenTimer = "Timer"
    override val listenMinutesLeft = "%d min"
    override val errorTitle = "Oops"
    override val errorCatalogMissing = "The content could not be loaded. (catalog missing " +
        "from the app bundle)"
    override val errorContentUnavailable = "The content could not be loaded. (%s)"
    override val errorUnknownProfile = "Unknown language profile (%1\$s → %2\$s)."
    override val errorResetFailed = "Reset failed. (%s)"
    override val roundNew = "%s new"
    override val roundNewOnly = "%s new words"
    override val roundConsolidated = "%s consolidated"
    override val roundReviewed = "%s reviewed"
    override val roundAllDone = "All done"
    override val restHint = "Not much is sticking today — a tired head keeps nothing. " +
        "Tomorrow will go easier."
    override val streakRecord = "Your longest run yet!"
    override val growthGrew = "Some of it sticks today too"
    override val growthOpened = "Your first words in good soil"
    override val growthBlooming = listOf(
        "Saying what you sowed",
        "You've brought this into bloom",
        "Your tending shows",
    )
    override val growthSown = listOf(
        "You've sown here",
        "You've got this sprouting",
        "You're planting, word by word",
    )
    override val growthGrown = listOf(
        "You've grown what you can say",
        "Your words root deeper now",
        "Words are settling in",
    )
    override val last14Days = "Last 14 days"
    override val activityDays = "Activity over the last 14 days: practiced on %d days"
    override val streakDays = "Streak: %d days"
    override val streakDaysOne = "Streak: %d day"
    override val dayOne = "day"
    override val dayMany = "days"
    override val widgetAwaitingTitle = "Open Spross"
    override val widgetAwaitingBody = "for fresh words"
}
