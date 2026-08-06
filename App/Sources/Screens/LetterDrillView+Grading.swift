import SwiftUI
import SprossKern

/// Grading of the letter drill — and the run-through hooks that drive it.
/// State lives on LetterDrillView; split out purely for file size, the way the
/// slot drill splits its own grading off.
///
/// A tile and a typed glyph are Kern's business (`gradeLetter`, exact after
/// normalization — a one-glyph answer with a typo budget grades nothing).
/// Dictation is not: what was SPOKEN is the only right answer, so the whole
/// catalog has to be in view to tell a slip of that word from a different word
/// entirely (`kufunga` / `kufungua`), and the card the grader is handed keeps
/// the REAL card's identity so the learner's own concept is never reported back
/// to them as somebody else's.
extension LetterDrillView {

    /// One attempt per tile question — a second tap after the answer is in
    /// would be a retry, and the ramp has no verdict for that.
    func choose(_ glyph: String, answer: String) {
        guard chosen == nil, feedback == .neutral else { return }
        Pronouncer.shared.stop()
        chosen = glyph
        guard glyph == answer else {
            feedback = .revealed
            DLSound.wrong()
            return
        }
        feedback = .correct
        DLSound.correct()
        // why: AutoAdvance skips the timer under a screen reader — it
        // truncates the correctness announcement and moves the screen under
        // the user (§6.1 renders "Weiter" there instead).
        AutoAdvance.scheduleExplicit(&autoAdvance) { advance(correct: true, clean: true) }
    }

    /// "Aufdecken" on an empty field: the CARD carries the answer — the gap
    /// closes over it — and the question books as a miss. The field stays
    /// empty, which is what makes it disappear: it has nothing of the
    /// learner's to show, and typing the answer in for them would put the same
    /// word on screen twice.
    func reveal(_ task: LetterDrillTask) {
        Pronouncer.shared.stop()
        DLSound.reveal()
        withAnimation { feedback = .revealed }
    }

    func submit(_ task: LetterDrillTask) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard feedback == .neutral, !trimmed.isEmpty else { return }
        Pronouncer.shared.stop()
        switch verdict(trimmed, task: task) {
        case .clean:
            feedback = .correct
            DLSound.correct()
            AutoAdvance.scheduleExplicit(&autoAdvance) { advance(correct: true, clean: true) }
        case .typo(let corrected):
            // why: no auto-advance on a slip — the pause shows the proper
            // spelling, and "Weiter" then books it amber.
            feedback = .almost(correctForm: corrected, reason: .typo)
            DLSound.correct()
            typoCorrection = corrected
            answerFocused = false
        case .heard(let spoken):
            feedback = .almost(correctForm: spoken, reason: .heard)
            DLSound.correct()
            heardInstead = spoken
            // why: both amber holds wait for a tap, and a held keyboard
            // covers the button they wait for.
            answerFocused = false
        case .wrong:
            feedback = .revealed
            DLSound.wrong()
        }
    }

    /// What a typed answer earns. Amber (`typo`, `heard`) moves the ramp
    /// neither way — it is neither a win to bank nor a miss to punish.
    private enum Verdict {
        case clean
        case typo(String)
        /// A form this very card accepts, but not the one that played.
        case heard(String)
        case wrong
    }

    private func verdict(_ trimmed: String, task: LetterDrillTask) -> Verdict {
        guard task.stage == .dictation else {
            return LetterDrill.shared.gradeLetter(input: trimmed, task: task) ? .clean : .wrong
        }
        guard let card = model.box?.cards[task.answerRef], let grader = dictationGrader else {
            return LetterDrill.shared.gradeLetter(input: trimmed, task: task) ? .clean : .wrong
        }
        let graded = grader.grade(input: trimmed,
                                  card: LetterDrill.shared.dictationGradingCard(card: card, task: task))
        if case .exact = onEnum(of: graded) { return .clean }
        // why: BEFORE the grader's own verdict — the review flow explicitly
        // teaches these forms ("auch: …"), so a synonym of the dictated word is
        // never wrong and never somebody else's word. It simply is not what
        // played, and the correction box says which form did.
        if alsoAccepted(trimmed, of: card) { return .heard(task.display) }
        switch onEnum(of: graded) {
        case .typo(let typo): return .typo(typo.corrected)
        case .exact, .otherWord, .wrong: return .wrong
        }
    }

    /// A form the REAL card lists as a synonym or a variant.
    private func alsoAccepted(_ input: String, of card: Card) -> Bool {
        let typed = speechKey(form: input)
        return (card.target.synonyms + card.target.variants).contains { speechKey(form: $0) == typed }
    }

    /// The strict drill normalizer with the whole join in view: a per-word slip
    /// budget alone would accept `kufungua` for `kufunga`, and only the
    /// catalog-wide grader withdraws that credit.
    private var dictationGrader: CatalogAnswerGrader? {
        guard let info = model.languageInfo(language), let box = model.box else { return nil }
        let normalizer = AnswerNormalizer(answerLanguage: info,
                                          articleLeniency: false,
                                          maxTyposPerWord: KotlinInt(int: 1))
        return CatalogAnswerGrader(normalizer: normalizer, cards: Array(box.cards.values))
    }
}

#if DEBUG
/// Run-through hooks (UserDefaults launch arguments), in the shape the slot
/// drill and the pronunciation probe already use: they drive the screen and
/// PRINT the states the checklist asserts, because playback itself cannot be
/// observed from outside the process.
extension LetterDrillView {

    func uitestStart() {
        let defaults = UserDefaults.standard
        if let prefill = defaults.string(forKey: "uitest-input") { input = prefill }
        if defaults.bool(forKey: "uitest-submit"), let task = current {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(600))
                submit(task)
            }
        }
        if let pick = defaults.string(forKey: "uitest-letters-choose") { uitestChoose(pick) }
        if defaults.bool(forKey: "uitest-letters-replay") { uitestReplay() }
        if defaults.bool(forKey: "uitest-letters-probe") { uitestBox("open") }
    }

    /// `-uitest-letters-replay` — taps the replay button once the field has
    /// taken focus, which is the whole assertion: hearing the question again
    /// must not take the keyboard away from a learner mid-word.
    private func uitestReplay() {
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(1200))
            replayAction?()
        }
    }

    /// `-uitest-letters-choose right|wrong` — taps a tile after 0.6 s.
    private func uitestChoose(_ pick: String) {
        guard let task = current, let choices = task.choices,
              let glyph = pick == "wrong" ? choices.first(where: { $0 != task.display }) : task.display
        else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(600))
            choose(glyph, answer: task.display)
        }
    }

    /// Every fire, with everything the autoplay assertions need: that the FIRST
    /// question of a fresh run played at all, on which trigger, and — the one
    /// gate this drill still has — whether a screen reader was standing.
    func uitestPlay(_ task: LetterDrillTask, pronunciation: Pronunciation,
                    trigger: Pronouncer.Trigger) {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        let gated = trigger != .tap && screenReaderOn
        let name = switch trigger {
        case .auto: "auto"
        case .essential: "essential"
        case .tap: "tap"
        }
        // why: the analysis index rides along — the letters are the recordings
        // it exists for, and this is where a run shows it reached the player.
        print("""
            LetterDrill probe: play \(name) \
            stage \(task.stage.name) level \(level) kind \(task.promptKind.name) \
            text "\(task.promptText)" recording \(pronunciation.recordingPath ?? "none") \
            index \(pronunciation.gain) dB/\(pronunciation.leadMs) ms \
            screenReader \(screenReaderOn) \
            → \(gated ? "SUPPRESSED" : "played")
            """)
    }

    /// The replay tap must leave the keyboard where it was.
    func uitestFocus() {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        print("LetterDrill probe: after replay tap, answer field focused \(answerFocused)")
    }

    /// The no-FSRS proof: the same figures on the way in and on the way out.
    func uitestBox(_ moment: String) {
        guard UserDefaults.standard.bool(forKey: "uitest-letters-probe") else { return }
        guard let stats = model.stats else {
            print("LetterDrill probe: box \(moment) — none")
            return
        }
        print("""
            LetterDrill probe: box \(moment) active \(stats.activeCards) due \(stats.dueCards) \
            consolidated \(stats.consolidatedCards) reviewsToday \(model.today.map { Int($0.reviews) } ?? -1)
            """)
    }
}
#endif
