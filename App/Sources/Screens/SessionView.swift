import SwiftUI
import SprossKern

/// A card caught mid-turn for its report sheet: the word, and the answer the report
/// travels with, taken as the menu was tapped.
private struct ReportedCard: Identifiable {
    let card: Card
    let input: String
    var id: String { card.id }
}


/// Full-screen session. The role a card is SHOWN in comes from Kern per
/// card + log count (one schedule, alternating presentation):
/// PRODUCE prompts the source side and grades typed target input
/// ("Aufdecken" without typing falls back to self-grading);
/// RECOGNIZE prompts one rotated target form and is reveal + self-grade
/// only — never typed, bar the first exposure's write-it-out.
/// Presented as a full-screen cover.
///
/// What an answer is WORTH, which beat it earns and what a miss opens is kern's
/// `TurnMachine`: every event becomes a `TurnIntent`, and what comes back is the
/// whole next state plus the only side effects this screen takes.
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
    /// Says the card's prompt and its answer, and is what the advance beat waits out.
    @State var reader = Reader()
    /// The card whose report sheet is up, with the answer as it stood when the
    /// menu was tapped — the field itself has moved on by the time it presents.
    @State private var reporting: ReportedCard?
    /// The conversation offer taken from the completion screen (`BriefingSheet`).
    @State private var briefing = false
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
            if model.sessionCompleted, let summary = model.sessionSummary {
                SessionSummaryView(parts: summary.parts,
                                      grownArea: summary.grownArea,
                                      garden: model.garden,
                                      grownAreaLabel: summary.grownArea.map {
                                          "\(model.areaEmoji($0.after.area)) \(model.areaTitle($0.after.area))"
                                      } ?? "",
                                      headline: summary.headline,
                                      canPracticeMore: model.canPracticeMore,
                                      restSuggested: summary.restSuggested,
                                      onTalk: model.hasBriefing ? { briefing = true } : nil,
                                      onPractice: { model.continueEndless() },
                                      onDone: { model.closeSession() })
            } else {
                SessionScaffold.running(endless: model.sessionEndless,
                                        position: model.sessionPosition,
                                        total: max(model.sessionTotal, 1),
                                        outcomes: model.sessionSegments,
                                        showsMuteButton: true,
                                        onClose: { model.closeSession() }) {
                    scaffoldContent
                }
            }
        }
        // why: on the Group rather than on the scaffold's content — the offer it
        // answers stands on the completion screen, which is the other branch.
        .sheet(isPresented: $briefing) {
            BriefingSheet(model: model).environment(\.locale, locale)
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
            readAloud()
        }
        .onChange(of: turn?.settled ?? false) { _, _ in readAloud() }
        .onAppear {
            // why: the card-change hook does not see the FIRST card, so the
            // first turn (and with it the recall clock) begins here.
            ensureTurn()
            // why: pays the process's first audio-session activation with an
            // inaudible clip, here where nothing is typed — never on a produce
            // reveal that carries the keyboard.
            Pronouncer.shared.warmUp()
            Sound.warmUp()
            readAloud()
        }
        .onDisappear {
            autoAdvance?.cancel()
            focusRetry?.cancel()
            reader.hush()
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
                Sound.uitestProbe()
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
                .tint(Theme.colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Card + controls

    private func cardContent(_ card: Card) -> some View {
        let role = model.presentationRole(for: card.id)
        return ScrollView {
            VStack(spacing: Theme.spacing.md) {
                // ZStack so outgoing and incoming card overlap during the flip
                // instead of stacking; .id gives each card its own identity.
                ZStack {
                    if let question = turn?.question {
                        // why: the input, the button and the keyboard share this
                        // screen with the card — the picture goes beside the words.
                        QuestionCardView(question: question, surface: .review, voice: model.cardVoice,
                                         areaTitle: model.areaTitle)
                            .id(question.key)
                            .transition(reduceMotion ? .opacity : .cardFlip)
                            // why: only once the answer is out — before it, the learner has
                            // not seen the translation they would be reporting, and a menu
                            // over the prompt is a menu over a question. A typo's hold counts:
                            // its correction stands even though the card never expands.
                            .contextMenu { if answerOut { cardMenu(card) } }
                    }
                }
                if model.coachActive,
                   let line = SessionCoach.recognizeLine(role: role, revealed: revealed) {
                    Text(line).pauseLine()
                }
                if let turn { answerArea(turn) }
            }
            .padding(.bottom, Theme.spacing.lg)
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
        if !model.isReportable(card.id) {
            EmptyView()
        } else if model.reportedIssue(for: card.id) == nil {
            Button("report.action", systemImage: "exclamationmark.bubble") {
                // why: the field moves on — the turn primes it past a refused answer and
                // empties it as the card advances — so the answer is taken NOW and
                // carried into the sheet.
                reporting = ReportedCard(card: card, input: answerForReport)
            }
        } else {
            // Reopening, not withdrawing: the form carries the drop, and a learner
            // with more to say should not have to withdraw the report to say it.
            Button("report.edit", systemImage: "text.bubble") {
                reporting = ReportedCard(card: card, input: answerForReport)
            }
        }
        Button("box.card.suspend", systemImage: "moon.zzz") {
            // why: the round moves on with it — being made to rate a word one has just
            // said should never be asked again is the exact busywork this removes.
            // resetCardState first, so the incoming card never renders the outgoing
            // card's reveal for a frame (same reason `commit` does).
            resetCardState()
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .cardFlip) {
                model.suspendCurrentCard()
            }
        }
    }

    /// What the self-grade row stands under: the first round's coaching while it is
    /// owed, else the standing question. One slot, one line — both paths to the row
    /// (recognize, and produce's blank reveal) read it.
    // why: internal, not private — SessionView+Answer captions the row with it.
    var gradeCaption: LocalizedStringKey {
        model.coachActive ? SessionCoach.gradeCaption : "session.rating.question"
    }

    /// It matters here for the step "Unbekannt" opens: the write-it-out field
    /// mounts in the same frame as the request (`AnswerFocus`).
    func focusAnswerField() {
        AnswerFocus.claim($answerFocused, retry: &focusRetry)
    }

    // The controls under the card live in SessionView+Answer.swift; the turn
    // they drive — dispatch, kern's effects, and what the screen reads off the
    // result — is SessionView+Turn.swift.
}
