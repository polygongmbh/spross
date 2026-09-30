import SwiftUI
import SprossKern

/// The read-aloud row: the same choice the session's top bar carries (there reduced to the
/// mute button), and the place the tap-to-replay gesture is disclosed — the
/// card itself grows no affordance for it, so the hint line is where it is
/// named. It is also the standing home of the voice-download pointer, which
/// the Home banner only borrows once: dismissed there, it is still findable
/// here.
struct SettingsAudioRow: View {
    let model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.sm) {
            Text("settings.audio.title")
                .font(Theme.typography.headline)
                .foregroundStyle(Theme.colors.textPrimary)
            Picker("settings.audio.title", selection: audioPreferenceBinding) {
                ForEach(audioOptions) { option in
                    Text(optionLabel(option)).tag(option)
                }
            }
            .pickerStyle(.segmented)
            Text(audioHintKey)
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            // why: only where there is a choice to be scoped — a language with one source
            // has nothing to remember per language.
            if audioSources == .both {
                Text("settings.audio.hint.perLanguage")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.textSecondary)
            }
            Toggle("settings.audio.saysMeaning", isOn: Binding(
                get: { Pronouncer.shared.saysMeaning },
                set: { Pronouncer.shared.saysMeaning = $0 }))
                .font(Theme.typography.subheadline)
                .disabled(Pronouncer.shared.muted)
            Text("settings.audio.saysMeaning.hint")
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textSecondary)
            if VoiceUpgradeHint.shared.suggests(language: model.targetLanguage) {
                Label("settings.audio.voiceUpgrade \(targetChromeName)",
                      systemImage: "speaker.wave.2")
                    .font(Theme.typography.caption)
                    .foregroundStyle(Theme.colors.accent)
                    .padding(.top, Theme.spacing.xs)
            }
        }
    }

    /// The three options the row carries, each a combination of the mute switch
    /// and the voice source. The picker alone decides both: there is no state
    /// where a source is chosen but the app is silent.
    private enum AudioPreference: String, CaseIterable, Identifiable {
        case off, recordings, tts
        var id: String { rawValue }
    }

    /// What can carry the learned language's sound here — kern's rule over the
    /// catalog's pack and this device's voice.
    static func sources(_ model: AppModel) -> AudioCapability {
        guard let catalog = model.catalog, let target = model.targetLanguage else { return .none }
        return audioCapability(catalog: catalog, language: target,
                               hasVoice: Pronouncer.shared.canSpeak(language: target))
    }

    private var audioSources: AudioCapability { Self.sources(model) }

    /// What the row offers: one segment per source that can actually answer.
    /// Speech only where the device has a voice — Swahili has none on iOS —
    /// and Recordings only where a pack ships, which English does not have.
    /// Either segment without its source promises a sound nothing can make.
    private var audioOptions: [AudioPreference] {
        let sources = audioSources
        return [.off]
            + (sources.hasRecordings ? [.recordings] : [])
            + (sources.hasVoice ? [.tts] : [])
    }

    private func optionLabel(_ option: AudioPreference) -> LocalizedStringKey {
        switch option {
        case .off: return "settings.audio.option.off"
        case .recordings: return "settings.audio.option.recordings"
        case .tts: return "settings.audio.option.tts"
        }
    }

    /// The hint names the chosen behavior, not the picker as a whole. The
    /// tap-to-replay gesture is disclosed in the No audio line alone — the only
    /// preference where a learner might think the app has gone silent for good.
    private var audioHintKey: LocalizedStringKey {
        switch audioPreferenceBinding.wrappedValue {
        case .off: return "settings.audio.hint.off"
        case .recordings: return "settings.audio.hint.recordings"
        case .tts: return "settings.audio.hint.tts"
        }
    }

    /// `.off` is the read-aloud switch off; the two "on" options are the voice
    /// source of the language being learned, with the switch on. Picking one of
    /// them turns reading aloud back on, so the picker can never leave the app
    /// silent behind a chosen source. A stored Speech that the phone can no
    /// longer answer (a voice uninstalled) reads as Recordings, which is what
    /// would sound anyway.
    private var audioPreferenceBinding: Binding<AudioPreference> {
        Binding(
            get: {
                if Pronouncer.shared.muted { return .off }
                let sources = audioSources
                guard let target = model.targetLanguage, sources.hasVoice,
                      Pronouncer.shared.voiceSource(for: target) == .tts
                else {
                    // A stored source the language cannot answer reads as the other one:
                    // a pack that does not ship is as empty a promise as a missing voice.
                    return sources.hasRecordings ? .recordings : .tts
                }
                return .tts
            },
            set: { preference in
                guard preference != .off else {
                    Pronouncer.shared.setReadAloud(on: false)
                    return
                }
                if let target = model.targetLanguage {
                    Pronouncer.shared.setVoiceSource(preference == .tts ? .tts : .recordings,
                                                     for: target)
                }
                if Pronouncer.shared.muted { Pronouncer.shared.setReadAloud(on: true) }
            }
        )
    }

    /// The target named in the CHROME's language, not its own — the voice hint
    /// is a sentence about the phone's settings, and it reads in the language
    /// the rest of the block does.
    private var targetChromeName: String {
        model.targetLanguage.map {
            LanguageNames.display($0, catalog: model.catalog)
        } ?? "?"
    }
}
