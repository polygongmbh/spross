import SwiftUI

// MARK: - AnswerInputView
//
// Typed-answer field with inline feedback. The field itself only ever shows
// what the LEARNER typed; where the right answer goes depends on how close they
// came:
// - correct → subtle green glow + checkmark, nothing else to say
// - almost  → amber throughout, and the box below carries the form that was
//             owed, at full size and audible
// - revealed → amber edge; the CARD carries the answer, so nothing here does

struct AnswerInputView: View {

    /// Why a correct answer still owes the learner a look: a slip of spelling,
    /// or — where the question was the sound — the form that actually played.
    enum AlmostReason: Equatable {
        case typo
        case heard

        var caption: LocalizedStringKey {
            switch self {
            case .typo: return "session.almost.typo"
            case .heard: return "session.almost.heard"
            }
        }
    }

    enum Feedback: Equatable {
        case neutral
        case correct
        /// Graded correct, but not cleanly. Amber runs the whole state — edge,
        /// checkmark and box agree — so the green glow stays what it always
        /// was: the clean answer's alone.
        case almost(correctForm: String, reason: AlmostReason)
        /// The word was not produced at all. Carries nothing: the CARD holds
        /// the answer in every case, so a copy here could only contradict it.
        case revealed

        /// Graded correct — cleanly or nearly. Callers deciding whether an
        /// answer STANDS must ask this rather than `== .correct`, or a near
        /// miss silently stops counting as answered.
        var isAccepted: Bool {
            switch self {
            case .correct, .almost: return true
            case .neutral, .revealed: return false
            }
        }
    }

    @Binding var text: String
    var feedback: Feedback = .neutral
    var placeholder: String = "Antwort eingeben …"
    /// Session views own focus so the keyboard is up the moment a card
    /// appears; standalone use falls back to the internal focus state.
    var focus: FocusState<Bool>.Binding?
    /// Overrides the feedback-driven disable (`.revealed` locks by default) —
    /// a produce miss keeps the field open so the learner can retype the
    /// word they just saw revealed.
    var locked: Bool?
    /// Says the form the box below carries — nil where it can neither be played
    /// nor spoken, which drops the speaker rather than showing a dead one.
    var pronounceCorrection: (() -> Void)?
    var correctionIsPlaying: Bool = false
    var onSubmit: () -> Void = {}

    @FocusState private var fallbackFocus: Bool

    var body: some View {
        VStack(spacing: DL.Space.m) {
            // why: a locked, empty field is not an input — it has nothing of the
            // learner's to show and cannot be typed into, so the placeholder and
            // the border were an invitation the field could not honour.
            if !isInert {
                inputField
            }
            if case .almost(let form, let reason) = feedback {
                correctionBox(form: form, caption: reason.caption)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeOut(duration: 0.25), value: feedback)
    }

    /// Revealed, locked and empty: the card is carrying the answer and there is
    /// nothing of the learner's here to keep on screen.
    private var isInert: Bool {
        isRevealed && (locked ?? true) && text.trimmingCharacters(in: .whitespaces).isEmpty
    }

    // MARK: Input field

    private var inputField: some View {
        HStack(spacing: DL.Space.s) {
            TextField(placeholder, text: $text)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused(focus ?? $fallbackFocus)
                .onSubmit(onSubmit)
                // why: on wrong answers Enter/tap must advance the session
                // instead; on correct the field stays enabled so the keyboard
                // does not bounce during the 1.2 s auto-advance.
                .disabled(locked ?? isRevealed)
            statusIcon
        }
        .padding(.horizontal, DL.Space.l)
        .frame(minHeight: 56)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .strokeBorder(borderColor, lineWidth: feedback == .neutral ? 1 : 2)
        )
        .shadow(
            color: feedback == .correct ? Color.dlSuccess.opacity(0.35) : .clear,
            radius: 10
        )
    }

    /// The checkmark says ACCEPTED and rides both correct states; its colour
    /// says how cleanly. A reveal gets no mark at all — the amber edge already
    /// carries the state, and the lightbulb that used to sit here read as a
    /// button it never was.
    @ViewBuilder
    private var statusIcon: some View {
        switch feedback {
        case .neutral, .revealed:
            EmptyView()
        case .correct, .almost:
            Image(systemName: "checkmark.circle.fill")
                .font(.title3)
                .foregroundStyle(borderColor)
                .accessibilityLabel("a11y.correct")
        }
    }

    private var isRevealed: Bool {
        if case .revealed = feedback { return true }
        return false
    }

    private var borderColor: Color {
        switch feedback {
        // why: the field's edge is a control boundary, not a decorative
        // hairline — the softer dlSeparator hides it against the surface.
        case .neutral: return .dlBorderStrong
        case .correct: return .dlSuccess
        case .almost, .revealed: return .dlAmber
        }
    }

    // MARK: Correction box (warm, never red)

    /// The form the learner owed, at a size worth reading and with the speaker
    /// that says it. This is the whole point of the box over a subtitle line: a
    /// correction is the one word on screen the learner most needs to take in,
    /// and a 12 pt italic afterthought is not how that lands.
    ///
    /// Leading-aligned, so no mirrored ballast is needed — `DLSpokenWord`
    /// centres, which is a different problem.
    private func correctionBox(form: String, caption: LocalizedStringKey) -> some View {
        HStack(spacing: DL.Space.m) {
            Image(systemName: "arrow.turn.down.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.dlAmber)
            VStack(alignment: .leading, spacing: DL.Space.xs) {
                Text(caption)
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlTextSecondary)
                Text(form)
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            if let pronounceCorrection {
                SpeakerIcon(size: .small,
                            isPlaying: correctionIsPlaying,
                            pronounce: pronounceCorrection)
                    .accessibilityLabel("a11y.pronounce")
            }
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.control, style: .continuous)
                .fill(Color.dlAmber.opacity(0.14))
        )
        // why: combined so VoiceOver reads "Fast! Korrekte Schreibweise, X" as
        // one thing — but the speaker keeps its own action, so it stays
        // reachable rather than being folded into the label.
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Previews

private struct AnswerInputPreviewHost: View {
    @State private var neutral = ""
    @State private var right = "kisu"
    @State private var slip = "kisuu"
    @State private var wrong = "kijiko"
    @State private var empty = ""

    var body: some View {
        VStack(spacing: DL.Space.xl) {
            AnswerInputView(text: $neutral, feedback: .neutral)
            AnswerInputView(text: $right, feedback: .correct)
            AnswerInputView(text: $slip,
                            feedback: .almost(correctForm: "kisu", reason: .typo),
                            pronounceCorrection: {})
            AnswerInputView(text: $wrong, feedback: .revealed)
            // Inert: revealed, locked and empty — the field renders nothing at
            // all, so this row is deliberately blank.
            AnswerInputView(text: $empty, feedback: .revealed)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.dlBackground)
    }
}

#Preview("All states") {
    AnswerInputPreviewHost()
}

#Preview("All states · dark") {
    AnswerInputPreviewHost()
        .preferredColorScheme(.dark)
}
