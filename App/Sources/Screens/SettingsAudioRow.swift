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
                ForEach(audioSources.preferenceOptions, id: \.self) { option in
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

    /// What can carry the learned language's sound here — kern's rule over the
    /// catalog's pack and this device's voice.
    static func sources(_ model: AppModel) -> AudioCapability {
        guard let catalog = model.catalog, let target = model.targetLanguage else { return .none }
        return audioCapability(catalog: catalog, language: target,
                               hasVoice: Pronouncer.shared.canSpeak(language: target))
    }

    private var audioSources: AudioCapability { Self.sources(model) }

    private func optionLabel(_ option: AudioPreference) -> LocalizedStringKey {
        switch option {
        case .off: return "settings.audio.option.off"
        case .recordings: return "settings.audio.option.recordings"
        case .speech: return "settings.audio.option.tts"
        }
    }

    /// The hint names the chosen behavior, not the picker as a whole. The
    /// tap-to-replay gesture is disclosed in the No audio line alone — the only
    /// preference where a learner might think the app has gone silent for good.
    private var audioHintKey: LocalizedStringKey {
        switch audioPreferenceBinding.wrappedValue {
        case .off: return "settings.audio.hint.off"
        case .recordings: return "settings.audio.hint.recordings"
        case .speech: return "settings.audio.hint.tts"
        }
    }

    /// Kern's mapping (`AudioPreference`) over the mute and this language's
    /// stored source. Picking a source turns reading aloud back on only where
    /// it was off, so a read-aloud that follows the phone keeps following it.
    private var audioPreferenceBinding: Binding<AudioPreference> {
        Binding(
            get: {
                let prefersSpeech = model.targetLanguage
                    .map { Pronouncer.shared.voiceSource(for: $0) == .tts } ?? false
                return audioSources.preference(muted: Pronouncer.shared.muted,
                                               prefersSpeech: prefersSpeech)
            },
            set: { preference in
                guard !preference.mutes else {
                    Pronouncer.shared.setReadAloud(on: false)
                    return
                }
                if let target = model.targetLanguage {
                    Pronouncer.shared.setVoiceSource(preference.prefersSpeech ? .tts : .recordings,
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
