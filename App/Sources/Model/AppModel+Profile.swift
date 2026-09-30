import Foundation
import SprossKern

// The profile: which pair the box is for, how it changes, and the chrome locale it reads in.

extension AppModel {

    // MARK: - The pair

    var sourceLanguage: String {
        box?.joinStamp.source
            ?? UserDefaults.standard.string(forKey: Self.sourceLanguageKey)
            ?? defaultSource
    }

    var targetLanguage: String? { box?.joinStamp.target }

    /// What this device reports it reads — the one fact Kern cannot have.
    static var deviceLanguage: String {
        Locale.current.language.languageCode?.identifier ?? Catalog.companion.FALLBACK_SOURCE
    }

    /// The source a fresh install opens with (contract §1) — Kern's ruling over
    /// the catalog, so a device language nothing can be taught from still lands
    /// on a source that teaches.
    var defaultSource: String {
        catalog?.defaultSource(deviceLanguage: Self.deviceLanguage)
            ?? Catalog.companion.FALLBACK_SOURCE
    }

    func languageInfo(_ code: String) -> LanguageInfo? {
        catalog?.languages[code]
    }

    // MARK: - Changing the pair

    /// Switch the known language in place — every schedule survives (keys are
    /// card ids); non-joining entries turn inert and revive on switch-back.
    func switchSource(_ newSource: String) {
        guard let box, let catalog, box.joinStamp.source != newSource,
              catalog.languages[newSource] != nil, newSource != box.joinStamp.target
        else { return }
        let cards = catalog.join(source: newSource, target: box.joinStamp.target)
        let stamp = JoinStamp(source: newSource, target: box.joinStamp.target,
                              catalogFingerprint: catalog.fingerprint)
        let next = BoxEngine.shared.rejoin(state: box, cards: cards, joinStamp: stamp)
        self.box = next
        UserDefaults.standard.set(newSource, forKey: Self.sourceLanguageKey)
        save(next, BoxChange.changed.saveScope)
        refreshTrainerContent()
        refreshStats()
        refreshAudibility()
        recomposeSessionIfStale()
    }

    func switchTarget(_ newTarget: String) {
        guard let box, box.joinStamp.target != newTarget else { return }
        let source = box.joinStamp.source
        Task { await activate(source: source, target: newTarget) }
    }

    /// Picking the OTHER side's language swaps the pair. Both boxes survive:
    /// the current target's box is already persisted on disk, and `activate`
    /// loads (or bootstraps) the new target's box re-joined under the new
    /// source — schedules are per-target documents keyed by card id.
    func swapLanguages() {
        guard let stamp = box?.joinStamp else { return }
        // why: stamp.source != stamp.target always holds, so the swapped pair
        // keeps the invariant and `activate` accepts it.
        Task { await activate(source: stamp.target, target: stamp.source) }
    }

    // MARK: - UI-chrome locale

    /// Locale for UI chrome, derived from the profile's KNOWN language when
    /// chrome exists for it; other sources read English until their UIs are
    /// authored. Which languages those are, and the fallback, is kern's
    /// (`LanguageChoices`).
    var knownLocale: Locale { Self.chromeLocale(source: sourceLanguage) }

    /// The chrome language for a known language. Onboarding uses it too —
    /// with no box yet, `sourceLanguage` is the device language (when the
    /// catalog covers it), so the very first screen greets in it and then
    /// follows whatever the user picks.
    static func chromeLocale(source: String) -> Locale {
        Locale(identifier: LanguageChoices.shared.chromeLanguage(source: source))
    }

    /// Immersion: the language being LEARNED, but only when we have chrome for
    /// it — so an action button can show its word in the target language as a
    /// subtitle. nil = no immersion subtitle, which is why this asks
    /// `hasChrome` rather than `chromeLanguage`: the fallback would caption a
    /// button in the wrong language.
    var targetChromeLocale: Locale? {
        guard let target = targetLanguage,
              LanguageChoices.shared.hasChrome(language: target)
        else { return nil }
        return Locale(identifier: target)
    }
}
