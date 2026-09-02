import SwiftUI

// MARK: - DLSpokenWord
//
// A centered word with the speaker that says it. Every surface that reveals a
// target-language answer renders it through here — a review card, a drill card,
// a dictation — so "the answer" is one thing with one affordance rather than a
// treatment each screen reinvents.
//
// The icon is MIRRORED: a hidden, inert copy on the leading edge. Without the
// ballast the word sits visibly off the one above it, because the two faces of
// a card carry different accessories (the target side has the speaker, the
// source side does not).

struct DLSpokenWord<Word: View>: View {
    /// nil where the word can neither be played nor spoken — the icon and its
    /// ballast both drop, so a word with nothing to hear centers on its own
    /// instead of leaning against an affordance that does nothing.
    var pronounce: (() -> Void)?
    var isPlaying: Bool = false
    /// Rides beside the speaker and mirrors with it (the ♀ badge).
    var badge: AnyView?
    @ViewBuilder var word: Word

    var body: some View {
        if pronounce != nil || badge != nil {
            HStack(spacing: Theme.spacing.sm) {
                accessories
                    .hidden()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                word
                accessories
            }
        } else {
            word
        }
    }

    /// The speaker keeps its 44 pt tap target but reserves only the glyph in
    /// layout, overhanging into the gap — at full width it would cost the word
    /// 104 pt of its line once mirrored.
    private var accessories: some View {
        HStack(spacing: Theme.spacing.sm) {
            if let pronounce {
                SpeakerIcon(size: .small, isPlaying: isPlaying, pronounce: pronounce)
                    .accessibilityLabel("a11y.action.pronounce")
                    .frame(width: 26)
            }
            badge
        }
    }
}

// MARK: - DLVoice

/// Saying a form out loud, asked BY the form rather than handed in already
/// resolved — for the surfaces that hold MANY forms and cannot know in advance
/// which one a tap will want: the correction box, a reference table's rows.
///
/// nil back — nothing recorded and no voice for the language — drops the
/// speaker rather than showing a dead one.
struct DLVoice {
    let pronounce: (String) -> (() -> Void)?
    let isPlaying: (String) -> Bool
}

extension View {
    /// Tap-to-replay where the row or the line IS the target, rather than a glyph
    /// beside it — a reference table's rows, the box list, the produce narration lines.
    ///
    /// For the view that IS the accessibility element:
    /// attach it AFTER the row has combined
    /// (`accessibilityElement(children: .combine)`),
    /// so the action lands on the one element the row became
    /// instead of on a child it swallows.
    /// A line INSIDE such a row takes `saysOnTap` instead —
    /// the element above it already names the sound,
    /// and a second copy only offers the rotor the same action twice.
    ///
    /// No 44 pt floor: a floor per line would set the height of every row
    /// and space the whole table out.
    @ViewBuilder
    func pronounceOnTap(_ pronounce: (() -> Void)?) -> some View {
        if let pronounce {
            saysOnTap(pronounce)
                .accessibilityAction(named: Text("a11y.action.pronounce"), pronounce)
        } else {
            self
        }
    }

    /// The tap alone, for a line that is NOT the accessibility element —
    /// the alphabet's name and example lines,
    /// whose row combines them and names both sounds itself.
    @ViewBuilder
    func saysOnTap(_ pronounce: (() -> Void)?) -> some View {
        if let pronounce {
            contentShape(Rectangle())
                .onTapGesture(perform: pronounce)
        } else {
            self
        }
    }
}

// MARK: - The language a word is written in

extension Text {
    /// The word, tagged with the language it is written in, so VoiceOver says
    /// it in that language's voice instead of spelling a Ukrainian word out in
    /// German. nil where the caller knows no language to tag it with.
    ///
    /// It matters most where autoplay is off by design: a VoiceOver session
    /// never autoplays (nothing may speak over the screen reader), so this
    /// reading is the only pronunciation the learner gets.
    static func spoken(_ text: String, language: String?) -> Text? {
        guard let language else { return nil }
        var label = AttributedString(text)
        label.languageIdentifier = language
        return Text(label)
    }
}

extension View {
    /// Reads this view as `text` said in `language` — every card that puts a
    /// word on screen tags it the same way. Untagged where no language is
    /// known: a label repeating what is already written buys nothing.
    @ViewBuilder
    func dlSpoken(_ text: String, language: String?) -> some View {
        if let label = Text.spoken(text, language: language) {
            accessibilityLabel(label)
        } else {
            self
        }
    }
}

// MARK: - Previews

#Preview("Spoken word") {
    VStack(spacing: Theme.spacing.xl) {
        DLSpokenWord(pronounce: {}) {
            Text(verbatim: "billete")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.accent)
        }
        DLSpokenWord(pronounce: {}, isPlaying: true) {
            Text(verbatim: "son las tres y cuarto")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.accent)
                .multilineTextAlignment(.center)
        }
        // Nothing to hear: no icon, no ballast — the word centers on its own.
        DLSpokenWord {
            Text(verbatim: "elfu mbili")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.accent)
        }
    }
    .padding(Theme.spacing.xl)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.colors.background)
}
