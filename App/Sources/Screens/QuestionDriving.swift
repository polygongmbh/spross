import SwiftUI
import SprossKern

/// What a question screen is driven through, the review session and every drill alike:
/// kern's question, the controls under it and what it says aloud, the learner's text,
/// and the intents the answer area hands back. `SessionView` and `DrillRunning` adopt it,
/// and a screen's body is `questionPage` — the card on its stage, the answer area under it.
///
/// Only what both drive the same way stands here. A review's write-out text and rating,
/// a drill's pause, Sprosse and close, and when each screen claims the keyboard
/// stay with the screen that has them. The Android twin is `QuestionFlow`.
@MainActor
protocol QuestionDriving {
    /// The question on screen; nil while there is none to ask.
    var question: Question? { get }
    /// What stands under it — kern's call.
    var controls: AnswerControls? { get }
    /// What the question says aloud, and when.
    var reading: Reading? { get }
    /// The text in the field the slot asks for.
    var answerText: Binding<String> { get }
    /// Where each control under the card hands its tap — the intents put to kern.
    var answerActions: AnswerActions { get }
    /// What says the question and its answer, and what an armed beat waits on
    /// while the answer sounds.
    var reader: Reader { get }
    /// Where the voice looks an answer up; nil in a preview, which says nothing.
    var voiceModel: AppModel? { get }
    /// The beat kern armed, until it fires or is canceled.
    var autoAdvance: Task<Void, Never>? { get nonmutating set }
}

extension QuestionDriving {

    /// Says whatever of the reading has not been said yet; the reader fires each side once.
    func readAloud() {
        reader.follow(reading, model: voiceModel)
    }

    /// Arms kern's beat. AutoAdvance skips the timer under a screen reader — it truncates
    /// the correctness announcement and moves the screen under the user — and the controls
    /// show the confirm tap there instead.
    func armAdvance(_ beat: AdvanceBeat, then advance: @escaping @MainActor () -> Void) {
        // why: the beat also waits out the answer being said, or the next question's
        // silence would cut the word off.
        AutoAdvance.schedule(beat, &autoAdvance, holding: { await reader.saidAnswer() },
                             action: advance)
    }

    /// The question screen's body: the card on the stage that flips between questions,
    /// and the answer area under it, in one scrolling column.
    func questionPage<Card: View, Area: View>(
        spacing: CGFloat = Theme.spacing.md,
        @ViewBuilder card: @escaping (Question) -> Card,
        @ViewBuilder area: (AnswerControls) -> Area
    ) -> some View {
        ScrollView {
            VStack(spacing: spacing) {
                if let question, let controls {
                    QuestionStage(key: question.key) { card(question) }
                    area(controls)
                }
            }
            .padding(.bottom, Theme.spacing.lg)
        }
        .scrollBounceBehavior(.basedOnSize)
        // why: a tap on the card or a tile must not drop the keyboard the answer is typed on.
        .scrollDismissesKeyboard(.never)
    }
}

extension AnswerArea {
    /// The answer area over a driver: its text and its intents, under `controls`.
    init(driver: some QuestionDriving, controls: AnswerControls, placeholder: String = "",
         focus: FocusState<Bool>.Binding? = nil, correctionVoice: Voice? = nil,
         nextLocale: Locale?, caption: LocalizedStringKey? = nil,
         @ViewBuilder tiles: @escaping (_ options: [String], _ answer: String) -> Tiles) {
        self.init(controls: controls, text: driver.answerText, placeholder: placeholder,
                  focus: focus, correctionVoice: correctionVoice, nextLocale: nextLocale,
                  caption: caption, actions: driver.answerActions, tiles: tiles)
    }
}

extension AnswerArea where Tiles == EmptyView {
    init(driver: some QuestionDriving, controls: AnswerControls, placeholder: String = "",
         focus: FocusState<Bool>.Binding? = nil, correctionVoice: Voice? = nil,
         nextLocale: Locale?, caption: LocalizedStringKey? = nil) {
        self.init(driver: driver, controls: controls, placeholder: placeholder, focus: focus,
                  correctionVoice: correctionVoice, nextLocale: nextLocale, caption: caption,
                  tiles: { _, _ in EmptyView() })
    }
}
