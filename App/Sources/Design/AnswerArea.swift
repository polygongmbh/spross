import SwiftUI
import SprossKern

/// What stands under a question card, review and drill alike: kern's `AnswerControls` drawn.
/// Which controls stand is kern's call; this only draws them and hands each tap to `actions`.
/// The field, the correction box, the self-grade row and every button are drawn here once;
/// the one input a drill brings of its own — the tiles — plugs in as `tiles`.
/// An arrangement's bank is the card itself, so its slot draws nothing here.
struct AnswerArea<Tiles: View>: View {
    let controls: AnswerControls
    /// The learner's text in the field the slot asks for; the caller keeps one per field.
    @Binding var text: String
    /// What the field asks for, named in the language it is owed in.
    var placeholder: String
    var focus: FocusState<Bool>.Binding?
    /// The correction box's speaker; nil where the surface has nothing to say it with.
    var correctionVoice: Voice?
    /// Where the Next button's learning-language subtitle is looked up; nil drops it.
    var nextLocale: Locale?
    /// The line under the slot: the self-grade's question, or the write-out's coaching.
    var caption: LocalizedStringKey?
    var actions: AnswerActions
    @ViewBuilder var tiles: (_ options: [String], _ answer: String) -> Tiles

    var body: some View {
        VStack(spacing: Theme.spacing.md) {
            slot
            buttons
        }
        .animation(.cardReveal, value: feedback)
    }

    private var feedback: AnswerInputView.Feedback { .init(controls.fieldFeedback) }

    // MARK: The slot

    @ViewBuilder
    private var slot: some View {
        if let slot = controls.slot {
            switch onEnum(of: slot) {
            case .typed(let typed):
                field(keyboard: typed.digits ? .numbersAndPunctuation : .default, locked: !typed.editable)
                    .id("typed")
            case .writeOut(let writeOut):
                field(keyboard: .default, locked: false)
                    .id("writeOut")
                if let caption { Text(caption).pauseLine() }
                if writeOut.missed {
                    // why: the answer is already on the card, so this points back to it.
                    Text("session.copy.mismatch")
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            case .choices(let choices):
                tiles(choices.options, choices.answer)
            case .arrangement:
                EmptyView()
            case .selfGrade:
                RatingButtonsView(onGrade: actions.selfGrade,
                                  caption: caption ?? "session.rating.question")
            }
        }
    }

    /// `.numbersAndPunctuation` rather than `.numberPad` for a value: only it has a return key,
    /// and Enter is how the answer is checked.
    private func field(keyboard: UIKeyboardType, locked: Bool) -> some View {
        AnswerInputView(text: $text,
                        feedback: feedback,
                        placeholder: placeholder,
                        focus: focus,
                        locked: locked,
                        correctionVoice: correctionVoice,
                        keyboard: keyboard,
                        onSubmit: actions.submit)
            // why: writing the answer out is the answer — every keystroke goes to kern,
            // so a word the learner knows never asks for a confirming tap.
            .onChange(of: text) { _, typed in actions.type?(typed) }
            .onAppear { actions.fieldAppeared() }
    }

    // MARK: The buttons

    @ViewBuilder
    private var buttons: some View {
        if let primary = controls.primary {
            VStack(spacing: Theme.spacing.md) {
                primaryButton(primary)
                if controls.cantListen {
                    Button("session.hear.cantListen", action: actions.cantListen)
                        .font(Theme.typography.caption)
                        .foregroundStyle(Theme.colors.textSecondary)
                }
            }
        }
        if showsConfirm || controls.giveUp != nil {
            VStack(spacing: Theme.spacing.sm) {
                if showsConfirm {
                    NextButton(locale: nextLocale, action: actions.confirm)
                        .transition(.opacity)
                }
                if controls.stop {
                    DrillStopOffer(action: actions.stop)
                }
            }
        }
        switch controls.giveUp {
        case .next:
            NextButton(locale: nextLocale, action: actions.giveUp)
        case .skip:
            // why: always reachable — a step that cannot be left is a trap.
            Button("session.skip", action: actions.giveUp)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
        default:
            EmptyView()
        }
    }

    /// ONE primary action, and kern decides which: a blank submit reveals, a typed one checks.
    /// The label only says which.
    private func primaryButton(_ primary: AnswerControls.Primary) -> some View {
        Button(action: primary == .reveal ? actions.reveal : actions.submit) {
            Text(primary == .reveal || text.isBlankAnswer ? "common.reveal" : "common.check")
                .frame(maxWidth: .infinity)
                .contentTransition(.opacity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .keyboardShortcut(.defaultAction)
        .animation(.easeOut(duration: 0.15), value: text.isBlankAnswer)
    }

    /// A verdict that holds stands until tapped; one kern arms a beat for needs the tap only
    /// where no timer runs — under a screen reader.
    private var showsConfirm: Bool {
        guard let confirm = controls.confirm else { return false }
        return confirm == .always || AutoAdvance.screenReaderOn
    }
}

extension AnswerArea where Tiles == EmptyView {
    init(controls: AnswerControls, text: Binding<String>, placeholder: String = "",
         focus: FocusState<Bool>.Binding? = nil, correctionVoice: Voice? = nil,
         nextLocale: Locale? = nil, caption: LocalizedStringKey? = nil, actions: AnswerActions) {
        self.init(controls: controls, text: text, placeholder: placeholder, focus: focus,
                  correctionVoice: correctionVoice, nextLocale: nextLocale, caption: caption,
                  actions: actions, tiles: { _, _ in EmptyView() })
    }
}

/// Where each control the answer area draws hands its tap. A surface fills in the ones its
/// controls can show; the rest stay inert.
struct AnswerActions {
    /// The Submit primary and Enter in the field.
    var submit: () -> Void = {}
    /// A keystroke, with the field's text after it.
    var type: ((String) -> Void)?
    var reveal: () -> Void = {}
    var confirm: () -> Void = {}
    var giveUp: () -> Void = {}
    var stop: () -> Void = {}
    var selfGrade: (SessionOutcome) -> Void = { _ in }
    var cantListen: () -> Void = {}
    /// The field mounted — where a surface that owns focus claims it.
    var fieldAppeared: () -> Void = {}
}

/// The button that books what the verdict already said, or goes on past a miss.
private struct NextButton: View {
    let locale: Locale?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ActionLabel(key: "common.next", targetLocale: locale)
        }
        .buttonStyle(PrimaryButtonStyle())
        // why: Enter goes on here too (hardware keyboards).
        .keyboardShortcut(.defaultAction)
    }
}
