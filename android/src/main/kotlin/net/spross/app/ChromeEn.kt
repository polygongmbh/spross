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
    override val homeGreetingMorningAddressee = "early bird"
    override val homeGreetingNightAddressee = "night owl"
    override val homeDoneExtraRound = "One more round?"
    override val homeDoneTitle = "Done for today"
    override val boxCardDue = "due"
    override val onboardingWelcome = "Welcome to Spross!"
    override val onboardingKnownQuestion = "Which language do you speak?"
    override val onboardingLearningQuestion = "Which language are you learning?"
    override val settingsKnownTitle = "I speak"
    override val settingsLearningTitle = "I'm learning"
    override val onboardingNameQuestion = "How should Spross greet you?"
    override val onboardingStart = "Let's go!"
    override val commonBack = "Back"
    override val onboardingWhyTitle = "What Spross is for"
    override val onboardingWhyBreadthTitle = "Sown, not crammed"
    override val onboardingWhyBreadthBody = "A few new words a day, spaced so they stay. " +
        "Better to know many words a little than a few perfectly."
    override val onboardingWhyCompanionTitle = "A companion, not a replacement"
    override val onboardingWhyCompanionBody = "Spross doesn't replace your course or your " +
        "language partner. Your vocabulary keeps growing between conversations, so you have " +
        "more to say next time."
    override val onboardingWhyGrammarTitle = "Grammar comes from speaking"
    override val onboardingWhyGrammarBody = "Spross doesn't teach grammar, just the gender a " +
        "word carries. Where you actually use the words, the rest takes care of itself."
    override val onboardingFirstRoundTitle = "Your first round"
    override val onboardingFirstRoundRecognize = "When Spross shows you a word, take a " +
        "moment. Does it ring a bell?"
    override val onboardingFirstRoundGrade = "Then you reveal and say how well you knew it. " +
        "Honest beats generous: your answer decides when you see the word again."
    override val onboardingFirstRoundWrite = "When you don't know a word, you can write it " +
        "out once to solidify your memory."
    override val commonCheck = "Check"
    override val sessionReveal = "Reveal"
    override val commonNext = "Next"
    override val sessionGrammarAlso = "also: %s"
    override val sessionOtherWord = "By the way: %1\$s means “%2\$s”"
    override val sessionAnswerPlaceholder = "In %s …"
    override val sessionRatingQuestion = "How well did you know it?"
    override val sessionCoachRecognize = "Take a moment. Does it ring a bell?"
    override val sessionCoachGrade = "Answer honestly — it decides when you see it again."
    override val sessionCoachWrite = "Write it once, so it sinks in."
    override val sessionRatingHard = "Shaky"
    override val sessionRatingGood = "Knew it"
    override val sessionRatingUnknown = "Not at all"
    override val sessionDoneTitle = "All done!"
    override val sessionDoneKeepPracticing = "Keep practicing"
    override val commonDone = "Done"
    override val sessionGrammarPluralEquals = "= pl."
    override val sessionGrammarPluralOnly = "pl. only"
    override val sessionGrammarPlural = "pl. %s"
    override val a11yGlyphFeminineForm = "Feminine form"
    override val a11yActionReadAloud = "Read words aloud"
    override val a11yStateOn = "on"
    override val a11yStateOff = "off"
    override val a11yActionPronounce = "Pronounce"
    override val settingsAbout = "About"
    override val settingsFeedback = "Send feedback"
    override val settingsUpdateButton = "Updates"
    override val settingsUpdateTitle = "Newer versions"
    override val settingsUpdateOffer = "Obtainium watches this app's releases and offers " +
        "each new version as it appears. Without it, every update is a download by hand."
    override val settingsUpdateObtainium = "Get Obtainium"
    override val settingsUpdateDownload = "Download directly"
    override val settingsAudioTitle = "Read words aloud"
    override val settingsAudioOptionOff = "No audio"
    override val settingsAudioOptionRecordings = "Recordings"
    override val settingsAudioOptionTts = "Speech"
    override val settingsAudioHintOff = "No words are read aloud. Tapping a word still " +
        "speaks it."
    override val settingsAudioHintRecordings = "Bundled recordings first, the system voice " +
        "for the rest."
    override val settingsAudioHintTts = "Every word in the system voice — always the same " +
        "sound, article included."
    override val creditsTitle = "Legal & licenses"
    override val creditsRecordings = "%d recordings"
    override val creditsUnmodified = "Recordings shipped unmodified"
    override val creditsCommonsNote = "Recordings from Wikimedia Commons"
    override val legalTitle = "Legal notice"
    override val legalCompany = "Polygon GmbH"
    override val legalAddressValue = "Bamberger Str. 43\n96215 Lichtenfels, Germany"
    override val legalDirectorLabel = "Represented by"
    override val legalDirectorValue = "Janek Janetzko"
    override val legalRegisterLabel = "Register"
    override val legalRegisterValue = "Amtsgericht Coburg, HRB 7580"
    override val legalVatLabel = "VAT ID"
    override val legalVatValue = "DE457826625"
    override val legalContactLabel = "Contact"
    override val legalPrivacy = "Privacy policy"
    override val trainerHubTitle = "Sprossen"
    override val trainerHubSubtitle = "Free practice — no schedule, no limit"
    override val a11ySuffixPractice = " practice, in %s"
    override val trainerSkillLetters = "Letters"
    override val lettersAskHear = "Which letter is this?"
    override val lettersAskSpell = "What's missing in the word?"
    override val lettersAskDictation = "Write what you hear"
    override val a11yGlyphLetter = "Letter %s"
    override val a11yActionReplayPrompt = "Play it again"
    override val lettersPromptInLanguage = "in %s"
    override val trainerRung = "Sprosse %s"
    override val trainerRunStreak = "🔥 %s in a row"
    override val sessionAlmostTypo = "Almost! Correct spelling"
    override val sessionAlmostHeard = "You heard"
    override val lettersMutedTitle = "Sound is off"
    override val lettersMutedEnable = "Turn sound on"
    override val trainerResultTasksDoneOne = "%d task 🎯"
    override val trainerResultTasksDone = "%d tasks 🎯"
    override val trainerResultBestStreak = "Best streak: 🔥 %s in a row"
    override val a11yVerdictCorrect = "Correct"
    override val a11yVerdictAlmost = "Almost correct"
    override val a11yVerdictWrong = "Wrong"
    override val a11yVerdictNotAnswered = "Not answered"
    override val commonClose = "Close"
    override val trainerSkillNumbers = "Numbers"
    override val numbersTitle = "Numbers · %s"
    override val lettersTitle = "Letters · %s"
    override val trainerOverviewPractice = "Practice"
    override val trainerOverviewStart = "Start"
    override val trainerReferenceTapToHear = "Every row speaks when tapped"
    override val boxTapToHear = "Words speak when tapped"
    override val boxCardNoAudio = "No audio for this word"
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
    override val trainerVariantClock = "Time"
    override val trainerVariantPhrases = "Sentences"
    override val trainerVariantForms = "Forms"
    override val trainerModifierReverse = "Reversed"
    override val trainerModifierReverseHint = "The reading is shown, the digits are owed."
    override val trainerModifierFast = "Fast"
    override val trainerModifierFastHint = "One clean answer per Sprosse instead of two."
    override val trainerModifierMix = "Mixed up"
    override val trainerModifierMixHint = "The direction flips every task, and forms grow to " +
        "the size the numbers reached."
    override val numbersCombineLocked = "Several in one run, once everything is unlocked."
    override val numbersUnlock = "Unlocks at:"
    override val numbersRungOne = "🔢 %d digit"
    override val numbersRung = "🔢 %d digits"
    override val trainerRunRecord = "Record %s"
    override val a11yCountStreakInARow = "Streak: %s in a row"
    override val a11ySuffixRecord = ", record %s"
    override val numbersAnswerPlaceholder = "In digits …"
    override val numbersNewPlace = "New place: %s"
    override val numbersLookup = "Look up numbers"
    override val trainerResultNewRecord = "New record!"
    override val lettersStageChoiceEasy = "Four tiles"
    override val lettersStageChoiceEasyHint = "Find the letter you heard among four"
    override val lettersStageChoiceConfusable = "Lookalike tiles"
    override val lettersStageChoiceConfusableHint = "The same choice, between letters that " +
        "are easy to mix up"
    override val lettersStageTyped = "Typing"
    override val lettersStageTypedHint = "Write the letter yourself, with nothing to pick from"
    override val lettersStageDictation = "Dictation"
    override val lettersStageDictationHint = "Write whole words from your own box by ear"
    override val lettersStageDictationLocked = "Needs more consolidated words this device " +
        "can read out"
    override val lettersStageEntry = "your run starts here"
    override val lettersUnavailable = "This device cannot say a letter yet — that needs a " +
        "voice for the language."
    override val lettersAlphabetTitle = "Alphabet"
    override val lettersAlphabetSpeakName = "Hear the name"
    override val lettersAlphabetSpeakExample = "Hear the example"
    override val trainerSkillCountries = "Countries"
    override val countriesTitle = "Countries · %s"
    override val countriesReference = "The atlas"
    override val countriesPace = "Every run opens at Sprosse 1 and climbs on with every " +
        "clean answer."
    override val countriesBest = "Best yet: Sprosse %s"
    override val countriesFastHint = "One clean answer per Sprosse instead of three."
    override val countriesReverseHint = "Asks in %s, you answer in %s."
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
    override val countriesAskCountry = "What is this country called?"
    override val countriesAskFlag = "Which country is this?"
    override val countriesAskLanguage = "What is this language called?"
    override val countriesAskNationality = "What are these people called?"
    override val countriesAskSpokenIn = "Which language is spoken there?"
    override val countriesAskSpokenWhere = "Where is this language spoken?"
    override val trainerSkillDates = "Dates"
    override val datesTitle = "Dates · %s"
    override val datesReference = "The calendar"
    override val datesPace = "Every run opens at Sprosse 1 and climbs on with every clean " +
        "answer."
    override val datesBest = "Best yet: Sprosse %s"
    override val datesFastHint = "One clean answer per Sprosse instead of three."
    override val datesReverseHint = "Asks in %s, you answer in %s — names only, the date " +
        "itself stays forward."
    override val dateRungs = listOf(
        "The weekdays",
        "The months",
        "The day of the month",
        "Day and month",
        "The whole date",
        "The date with its year",
    )
    override val dateRungHints = listOf(
        "The week's seven names, asked one at a time.",
        "The twelve month names, mixed in with the weekdays.",
        "3 becomes third — the number a date needs.",
        "3/3 becomes March third.",
        "With the weekday in front: Mon, 3/3.",
        "Plus the year, read out in words.",
    )
    override val datesAskWeekday = "What is this weekday called?"
    override val datesAskMonth = "What is this month called?"
    override val datesAskDay = "How is this day read?"
    override val datesAskDate = "How is this date read?"
    override val boxTitle = "The box"
    override val boxDoor = "Box"
    override val boxSubtitle = "%1\$s of %2\$s cards in progress"
    override val boxOwnShelf = "Your own words"
    override val boxOwnWordExplainer = "Your own words are yours alone. A growing catalog " +
        "never touches them."
    override val boxShelfPack = "Add to box (%s)"
    override val boxShelfPacked = "All packed"
    override val boxCardPack = "Pack this word"
    override val boxCardUnpack = "Unpack this word"
    override val boxShelfUnpack = "Take out of box (%s)"
    override val boxCardQueued = "Sown"
    override val boxCardSuspended = "Paused"
    override val boxCardWake = "Wake"
    override val boxCardSleep = "Stop asking this"
    override val boxCardActions = "What you can do with this word"
    override val boxCardForget = "Reset progress"
    override val boxCardOwnFrom = "Make your own word"
    override val reportReported = "Reported"
    override val progressConsolidatedCount = "%s consolidated"
    override val progressLearningCount = "%s growing"
    override val boxAreaPhrasesLockedShort = "%d sentences"
    override val boxAreaPhrasesLocked = "%d sentences locked"
    override val a11yStateExpanded = "expanded"
    override val a11yStateCollapsed = "collapsed"
    override val boxPhaseLearning = "Fresh"
    override val boxPhaseRelearning = "Shaky"
    override val boxPhaseSettled = "Growing"
    override val boxPhaseConsolidated = "Grown"
    override val boxSearchButton = "Search"
    override val boxSearchPlaceholder = "Word or area"
    override val boxSearchHint = "Words in either language, and the names of the areas."
    override val boxSearchAreas = "Areas"
    override val boxSearchWords = "Words"
    override val boxSearchNothing = "Nothing for “%s” in the box."
    override val boxSearchWriteOwn = "Write “%s” yourself"
    override val boxSearchClear = "Clear search"
    override val boxOwnWordTitle = "Your own word"
    override val boxOwnWordInLanguage = "In %s"
    override val boxOwnWordPicture = "Picture (optional)"
    override val boxOwnWordAdd = "Add"
    override val boxOwnWordRemove = "Delete word"
    override val boxOwnWordEdit = "Edit word"
    override val boxOwnWordSave = "Save"
    override val boxOwnWordSwap = "Swap direction"
    override val boxOwnWordExplainerSuggestion = "One side only: the word is kept as a " +
        "suggestion and not asked yet."
    override val reportAction = "Report a problem"
    override val reportEdit = "Edit report"
    override val reportDismiss = "Withdraw report"
    override val reportTitle = "Report a problem"
    override val reportSend = "Report"
    override val reportComment = "What's wrong? (optional)"
    override val reportTyped = "You typed:"
    override val reportExplainer = "Goes to whoever maintains the catalog. It changes " +
        "nothing about the word’s schedule."
    override val boxOwnTitle = "Your own content"
    override val boxOwnReported = "Reported"
    override val boxOwnWordAddAction = "Add a word"
    override val boxOwnWordNeedsTranslation = "Needs translation"
    override val reportExportCopy = "Copy"
    override val reportExportSend = "Send"
    override val reportExportScopeNew = "Only what is new"
    override val reportExportScopeAll = "Everything"
    override val settingsTitle = "Settings"
    override val settingsNameTitle = "Your name"
    override val settingsNamePlaceholder = "Name or nickname"
    override val settingsNameHint = "Spross greets you by it. Left empty, the greeting goes " +
        "without one."
    override val settingsProfileHint = "Switching the language you speak keeps all your " +
        "progress; each language you learn has its own box."
    override val settingsRestartTutorialButton = "Restart tutorial"
    override val settingsRestartTutorialHint = "Shows the introduction again — your " +
        "languages and progress stay."
    override val settingsResetButton = "Reset %s …"
    override val settingsResetHint = "Deletes progress and history — your own words and your " +
        "other languages stay."
    override val settingsResetConfirm = "Delete all learning progress for %s and start over " +
        "with the first words?"
    override val commonCancel = "Cancel"
    override val commonReset = "Reset"
    override val sessionCopyPlaceholder = "Write it once in %s …"
    override val sessionCopyMismatch = "Not quite — the word is right above."
    override val sessionSkip = "Skip"
    override val sessionHearCantListen = "Can't listen right now?"
    override val sessionCardPosition = "Card %1\$s of %2\$s"
    override val a11yCountSessionTally = "%1\$s correct, %2\$s hard, %3\$s wrong"
    override val homeDoneCaughtUp = "Nothing's due right now"
    override val homeTallyReviews = "%d reviews"
    override val homeTallyReviewsOne = "%d review"
    override val homeTallyNewCards = "%d newbies"
    override val homeTallyNewCardsOne = "%d newbie"
    override val homeTallyNewWordsOnly = "%s new words"
    override val homeTallyConsolidated = "%s solidified"
    override val homeTallyAhead = "%d refreshers"
    override val homeTallyAheadOne = "%d refresher"
    override val homeDonePacked = "Your packed words are in the next round."
    override val homeDoneTomorrowFresh = "Fresh cards tomorrow. See you then! 👋"
    override val homeDoneTomorrowDue = "Tomorrow you can review %d cards."
    override val headlineReviews = listOf(
        "Back to your words",
        "A few words are up again",
        "Reach into the box",
        "Time for a refresher",
    )
    override val headlineWarmUp = listOf(
        "A few old friends",
    )
    override val headlineFreshSet = listOf(
        "Up for some new words?",
        "Time to sow seeds!",
        "A few seeds to sow",
    )
    override val headlineStreak = listOf(
        "Don't let the streak go cold",
        "One round keeps the flame alive",
        "Still time to rekindle today",
    )
    override val homeTallySomeCards = "A few cards for you."
    override val homeOfferHeldBack = "%d more ready for you."
    override val homeOfferStart = "Let's go!"
    override val homeOfferShortRound = "Just a short one?"
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
    override val sessionDoneTallyNew = "%s new"
    override val sessionDoneTallyNewOnly = "%s new words"
    override val sessionDoneTallyConsolidated = "%s consolidated"
    override val sessionDoneTallyReviewed = "%s reviewed"
    override val sessionDoneTallyAllDone = "All done"
    override val sessionDoneRestHint = "Not much is sticking today — a tired head keeps " +
        "nothing. Tomorrow will go easier."
    override val sessionDoneStreakRecord = "Your longest run yet!"
    override val sessionDoneGrowthGrew = "Some of it sticks today too"
    override val sessionDoneGrowthOpened = "Your first words in good soil"
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
    override val progressLast14Days = "Last 14 days"
    override val a11yCountActivity14Days = "Activity over the last 14 days: practiced on %d " +
        "days"
    override val a11yCountStreakDays = "Streak: %d days"
    override val a11yCountStreakDaysOne = "Streak: %d day"
    override val commonDayOne = "day"
    override val commonDayOther = "days"
    override val widgetAwaitingTitle = "Open Spross"
    override val widgetAwaitingBody = "for fresh words"
}
