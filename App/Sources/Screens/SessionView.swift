import SwiftUI
import SprossKern

/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to self-grading);
/// RECOGNIZE prompts one rotated target form and is reveal + self-grade
/// only — never typed, bar the first exposure's write-it-out
/// (SessionView+Copy.swift). Presented as a full-screen cover.
///
/// What an answer is WORTH, which beat it earns and what a miss opens is kern's
/// `TurnMachine`: every event becomes a `TurnIntent`, and what comes back is the
/// whole next state plus the only side effects this screen takes.
/// A card caught mid-turn for its report sheet: the word, and what stood in the
/// answer field at that moment.
private struct ReportedCard: Identifiable {
    let card: Card
    let input: String
    var id: String { card.id }
}

struct SessionView: View, LanguageNaming {
    @Bindable var model: AppModel

    /// The turn under way, whole: where the answer stands, what the card
    /// shows, and which write-out it still owes. Nil only with no card up.
    // why: internal, not private — SessionView+Turn/Produce/Copy/Audio
    // (file-size splits) drive and render this same turn from their extensions.
    @State var turn: TurnState?
    /// Rebuilt with each turn: the grader snapshots the join, so a card that
    /// arrives after the box moved is graded against the box standing now.
    // why: internal, not private — SessionView+Turn owns the reduce loop that
    // builds and reads it.
    @State var machine: TurnMachine?
    /// The learner's TEXT stays platform-owned — kern is handed it in intents
    /// and may prime it, but never holds it. Only ever one field is mounted,
    /// so the answer and the write-out keep their own.
    @State var input = ""
    @State var copyInput = ""
    @State var autoAdvance: Task<Void, Never>?
    /// The card whose word has already been said. The one-shot autoplay guard
    /// (SessionView+Audio.swift) — stored here because a SwiftUI extension
    /// cannot carry state of its own.
    @State var pronouncedCardID: String?
    /// The card whose report sheet is up, with the answer as it stood when the
    /// menu was tapped — the field itself has moved on by the time it presents.
    @State private var reporting: ReportedCard?
    /// Owned here (not in AnswerInputView) so whichever field is on screen —
    /// the answer field or the write-out step's — takes focus the moment it
    /// mounts. Only ever one of them is mounted at a time.
    @FocusState var answerFocused: Bool
    @State var focusRetry: Task<Void, Never>?
    // why: internal, not private — the card flip is animated from both here and
    // the commit in SessionView+Turn.
    @Environment(\.accessibilityReduceMotion) var reduceMotion
    @Environment(\.locale) var locale
    var namingCatalog: Catalog? { model.catalog }

    var body: some View {
        Group {
            if model.sessionCompleted {
                SessionCompletionView(newCount: model.sessionNew,
                                      graduatedCount: model.sessionGraduated,
                                      reviewCount: model.sessionReviews,
                                      streakDays: model.stats?.streakDays ?? 0,
                                      streakIsRecord: model.streakIsRecord,
                                      grownArea: model.sessionGrowth,
                                      canPracticeMore: model.canPracticeMore,
                                      restSuggested: model.today?.recallStrained ?? false,
                                      onPractice: { model.continueEndless() },
                                      onDone: { model.closeSession() })
            } else {
                SessionScaffold(position: model.sessionPosition,
                                total: max(model.sessionTotal, 1),
                                outcomes: model.sessionSegments,
                                showsMuteButton: true,
                                onClose: { model.closeSession() }) {
                    scaffoldContent
                }
            }
        }
        // why: the order is normative — reset stops whatever is sounding and
        // clears the one-shot guard, focus lands before anything is played,
        // and only then does the new card speak. Autoplay placed ahead of the
        // reset would be killed by it on the same frame.
        .onChange(of: currentCardID) { _, _ in
            // why: safety net only — an answer already begins the next turn
            // BEFORE the switch, so no card can render one frame revealed.
            resetCardState()
            // why: a field carried over from the previous card is not
            // re-mounted, so nothing else would re-assert focus for it.
            focusAnswerField()
            if let card = model.currentCard { autoplayPrompt(card) }
        }
        .onChange(of: produceAudioTrigger) { was, now in
            if !was, now { autoplayProduceReveal() }
        }
        .onAppear {
            // why: the card-change hook does not see the FIRST card, so the
            // first turn (and with it the recall clock) begins here.
            ensureTurn()
            // why: pays the process's first audio-session activation with an
            // inaudible clip, here where nothing is typed — never on a produce
            // reveal that carries the keyboard.
            Pronouncer.shared.warmUp()
            DLSound.warmUp()
            if let card = model.currentCard { autoplayPrompt(card) }
        }
        .onDisappear {
            autoAdvance?.cancel()
            focusRetry?.cancel()
            Pronouncer.shared.stop()
        }
        #if DEBUG
        // UI-test hooks: `-uitest-reveal 1` shows the first card revealed,
        // `-uitest-sound 1` plays each feedback sound with a console probe,
        // `-uitest-pronounce <form>` says one form and prints which branch said it.
        .onAppear {
            let defaults = UserDefaults.standard
            if defaults.bool(forKey: "uitest-reveal") {
                // why: revealed at the prompt's own timestamp — a card up
                // revealed measured no recall, and an unmeasured span must
                // not be the one that earns Easy.
                dispatch(TurnIntent.Reveal.shared, at: ensureTurn()?.promptShownAtMillis)
            }
            if defaults.bool(forKey: "uitest-sound") {
                DLSound.uitestProbe()
            }
            if let form = defaults.string(forKey: "uitest-pronounce") {
                uitestPronounce(form)
            }
        }
        #endif
    }

    // why: internal, not private — the audio extension reads it to drop a
    // delayed word whose card has already gone.
    var currentCardID: String? { model.currentCardId }

    /// VoiceOver and Switch Control both make a timed screen change hostile:
    /// it truncates the correctness announcement and moves the page under the
    /// user. Where either runs, an explicit "Weiter" replaces the beat.
    var screenReaderOn: Bool { AutoAdvance.screenReaderOn }

    @ViewBuilder
    private var scaffoldContent: some View {
        if let card = model.currentCard {
            cardContent(card)
        } else {
            ProgressView()
                .tint(.dlAccent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Card + controls

    private func cardContent(_ card: Card) -> some View {
        let role = model.presentationRole(for: card.id)
        return ScrollView {
            VStack(spacing: DL.Space.m) {
                // ZStack so outgoing and incoming card overlap during the flip
                // instead of stacking; .id gives each card its own identity.
                ZStack {
                    VocabCardView(
                        emoji: card.emoji,
                        emojiCue: model.emojiCue(for: card),
                        prompt: promptSide(card, role: role),
                        answer: answerSide(card, role: role),
                        note: card.target.note ?? card.source.note,
                        revealed: cardRevealed,
                        // why: the input, the button and the keyboard share this
                        // screen with the card — the picture goes beside the words.
                        arrangement: .beside
                    )
                    .id(card.id)
                    .transition(reduceMotion ? .opacity : .dlCardFlip)
                    // why: only once the answer is out — before it, the learner has
                    // not seen the translation they would be reporting, and a menu
                    // over the prompt is a menu over a question.
                    .contextMenu { if cardRevealed { cardMenu(card) } }
                }
                if model.coachActive,
                   let line = SessionCoach.recognizeLine(role: role, revealed: revealed) {
                    Text(line).dlPauseLine()
                }
                controls(card, role: role)
            }
            .padding(.bottom, DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.never)
        .sheet(item: $reporting) { reported in
            ReportIssueSheet(model: model, card: reported.card, learnerInput: reported.input)
                .environment(\.locale, locale)
        }
    }

    /// The two things a learner can say about the word in front of them, and they
    /// are unrelated: one is about the CATALOG being wrong, the other about this
    /// word not being worth their time. Neither implies the other, so neither is a
    /// step in the other's flow.
    @ViewBuilder
    private func cardMenu(_ card: Card) -> some View {
        if model.reportedIssue(for: card.id) == nil {
            Button("report.action", systemImage: "exclamationmark.bubble") {
                // why: the field empties as the turn advances, so what they typed is
                // taken NOW and carried into the sheet.
                reporting = ReportedCard(card: card, input: input)
            }
        } else {
            Button("report.dismiss", systemImage: "checkmark.bubble") {
                model.dismissReportedIssue(cardID: card.id)
            }
        }
        Button("box.sleep", systemImage: "moon.zzz") {
            // why: the round moves on with it — being made to rate a word one has just
            // said should never be asked again is the exact busywork this removes.
            // resetCardState first, so the incoming card never renders the outgoing
            // card's reveal for a frame (same reason `commit` does).
            resetCardState()
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .dlCardFlip) {
                model.suspendCurrentCard()
            }
        }
    }

    /// Grammar (article coloring, plural) renders TARGET-side only; on
    /// recognition it decorates the prompt only when the canonical form is
    /// the one prompted (synonym rotations carry no citation grammar).
    private func promptSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            // why: a consolidated word is sometimes asked by ear alone — the
            // meaning is withheld ON PURPOSE, so no cue rides along with it
            // either; what stands is the replay glyph and nothing else.
            //
            // The word takes the glyph's place once there is nothing left to
            // withhold: the learner said they cannot listen, or the answer is
            // out and the spelling is what the reveal owes. It then renders as
            // any other target word does — article, plural, and the speaker.
            if askedByEar(card) {
                let written = promptInText || cardRevealed
                return .init(text: card.target.text,
                             article: written
                                 ? CardDisplay.articleLabel(of: card.target, shown: card.target.text)
                                 : nil,
                             plural: written
                                 ? CardDisplay.plural(of: card.target, locale: locale)
                                 : nil,
                             language: model.targetLanguage,
                             pronounce: pronounceAction(for: card.target.text),
                             isPlaying: isPronouncing(card.target.text),
                             listening: !written)
            }
            return .init(text: card.source.text,
                         // why: the area title IS the disambiguating cue, in the source
                         // language — it is a plain name, so nothing is trimmed off it.
                         context: card.promptAmbiguous ? model.areaTitle(card.area) : nil,
                         femMarker: card.promptFeminineMarker)
        case .recognize:
            // why: deliberately NO context cue here — the prompt is the target form, so
            // any cue precise enough to disambiguate would reveal the answer (same
            // reasoning as the emoji matrix). Self-grading absorbs the ambiguity.
            let form = model.promptForm(for: card)
            let canonical = form == card.target.text
            return .init(text: form,
                         article: CardDisplay.articleLabel(of: card.target, shown: form),
                         plural: canonical ? CardDisplay.plural(of: card.target, locale: locale) : nil,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: form),
                         isPlaying: isPronouncing(form))
        }
    }

    /// The reveal always shows the full family: produce reveals the target
    /// citation + synonyms; recognize reveals the source meaning (synonyms
    /// joined informatively) + the remaining target forms as "auch: …".
    private func answerSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            let meaning = ([card.source.text] + card.source.synonyms).joined(separator: " / ")
            let alternates = CardDisplay.alternates(of: card.target,
                                                    shown: card.target.text,
                                                    locale: locale)
            // why: a card asked by ear owes the MEANING back, so its reveal is
            // shaped like the recognition one — the answer where the answer
            // goes, and the word that played standing above it in writing
            // (`promptSide`), which is where a retype has something to finish
            // against rather than a prompt to copy.
            if askedByEar(card) {
                return .init(text: meaning,
                             alternates: alternates,
                             femMarker: card.promptFeminineMarker)
            }
            return .init(text: card.target.text,
                         article: CardDisplay.articleLabel(of: card.target,
                                                           shown: card.target.text),
                         plural: CardDisplay.plural(of: card.target, locale: locale),
                         alternates: alternates,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: card.target.text),
                         isPlaying: isPronouncing(card.target.text))
        case .recognize:
            let meaning = ([card.source.text] + card.source.synonyms).joined(separator: " / ")
            return .init(text: meaning,
                         alternates: CardDisplay.alternates(of: card.target,
                                                            shown: model.promptForm(for: card),
                                                            locale: locale),
                         femMarker: card.promptFeminineMarker)
        }
    }

    /// A field is on screen only where there is something to type: produce
    /// before its blank self-grade, and the write-out step. Recognize brings up
    /// none of its own — iOS drops the keyboard for a hidden field anyway, so
    /// pretending otherwise only cost reliable focus.
    @ViewBuilder
    private func controls(_ card: Card, role: PresentationRole) -> some View {
        if let step = turn?.copyStep {
            copyControls(step)
        } else {
            VStack(spacing: 0) {
                if role == .produce, !produceFieldHidden {
                    answerField(card)
                }
                switch role {
                case .recognize: recognizeControls
                case .produce: produceButtons(card)
                }
            }
        }
    }

    /// What the self-grade row stands under: the first round's coaching while it is
    /// owed, else the standing question. One slot, one line — both paths to the row
    /// (recognize, and produce's blank reveal) read it.
    // why: internal, not private — SessionView+Produce mounts the same row.
    var gradeCaption: LocalizedStringKey {
        model.coachActive ? SessionCoach.gradeCaption : "rating.question"
    }

    /// It matters here for the step "Unbekannt" opens: the write-it-out field
    /// mounts in the same frame as the request (`AnswerFocus`).
    func focusAnswerField() {
        AnswerFocus.claim($answerFocused, retry: &focusRetry)
    }

    /// Comprehension check: reveal, then honest self-grade —
    /// never typed, so no schedule is ever graded against a language it
    /// wasn't learned with. The very first exposure takes this path too: the
    /// word is prompted before it is taught, so a learner who already knows it
    /// gets the moment to recall it (contract §3).
    @ViewBuilder
    private var recognizeControls: some View {
        if revealed {
            RatingButtonsView(onGrade: { dispatch(TurnIntent.SelfGrade(verdict: $0.verdict)) },
                              caption: gradeCaption)
        } else {
            Button {
                dispatch(TurnIntent.Reveal.shared)
            } label: {
                Text("session.reveal")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
            .keyboardShortcut(.defaultAction)
        }
    }

    // Produce controls live in SessionView+Produce.swift, the write-out step in
    // SessionView+Copy.swift; the turn they both drive — dispatch, kern's
    // effects, and what the screen reads off the result — is SessionView+Turn.swift.
}
