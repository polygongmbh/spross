import Foundation
import DuoKern

/// Instantiates a `PhraseTemplate` into a `TrainerTask` by composing with
/// the Trainer slot generators: German prompt gets the digits, target
/// display gets the canonical words, accepted gets one full sentence per
/// accepted slot variant. Pure — sampling takes an injected RNG.
public enum PhraseSlots {

    /// Clock templates. Minutes are rounded by `Trainer.clock` (nearest 5);
    /// the German prompt shows the rounded digital time ("… um 14:35 Uhr …").
    /// Swahili templates only accept minutes ≤ 30: the >30 countdown form
    /// ("saa tatu kasoro dakika …") reads awkwardly when embedded
    /// (language-review finding).
    public static func instantiate(template: PhraseTemplate, hour: Int, minute: Int) -> TrainerTask {
        precondition(template.slotKind == .clock, "hour/minute instantiation requires a .clock template")
        precondition(template.pair != .deSw || minute <= 30,
                     "Swahili phrase templates embed only minutes 0...30")
        let slot = Trainer.clock(hour: hour, minute: minute, language: template.targetLanguage)
        return compose(template: template, slot: slot, value: nil)
    }

    /// Number and year templates.
    public static func instantiate(template: PhraseTemplate, value: Int) -> TrainerTask {
        precondition(template.slotKind != .clock, "clock templates take hour/minute")
        let slot = template.slotKind == .numbers
            ? Trainer.number(value, language: template.targetLanguage)
            : Trainer.year(value, language: template.targetLanguage)
        return compose(template: template, slot: slot, value: value)
    }

    /// Deterministic sampling with the Trainer's ported biases
    /// (numbers 10–9999 weighted to 2–3 digits, years around 1950–2050,
    /// clock any hour in 5-minute steps).
    public static func sample(template: PhraseTemplate,
                              using rng: inout some RandomNumberGenerator) -> TrainerTask {
        if template.slotKind == .clock, template.pair == .deSw {
            // Swahili embeds only minutes 0...30 (see instantiate).
            let hour = Int(rng.next() % 24)
            let minute = Int(rng.next() % 31)
            return instantiate(template: template, hour: hour, minute: minute)
        }
        let slot = Trainer.sample(kind: template.slotKind, language: template.targetLanguage, using: &rng)
        // why: slot.prompt is the Trainer's numeric contract ("347"/"1978"),
        // so counted-noun agreement can reuse the sampled value exactly.
        let value = template.slotKind == .clock ? nil : Int(slot.prompt)
        return compose(template: template, slot: slot, value: value)
    }

    // MARK: - Reverse (target sentence shown, German typed)

    /// Reverse drill for learners of German: prompt is the TARGET sentence in
    /// words ("У мене є двадцять один зошит."), the answer is the German
    /// sentence with the value in digits ("Ich habe 21 Hefte." / "… um 20:00
    /// Uhr …"). Digits keep typing fast; clock answers accept both zero-padded
    /// and bare-hour digital forms.
    public static func reverseInstantiate(template: PhraseTemplate, hour: Int, minute: Int) -> TrainerTask {
        let forward = instantiate(template: template, hour: hour, minute: minute)
        let padded = String(format: "%02d:%02d", hour, minute)
        let bare = String(format: "%d:%02d", hour, minute)
        var accepted = [template.deTemplate.replacingOccurrences(of: PhraseTemplate.slotMarker, with: padded)]
        if bare != padded {
            accepted.append(template.deTemplate.replacingOccurrences(of: PhraseTemplate.slotMarker, with: bare))
        }
        return TrainerTask(kind: template.slotKind, language: .german,
                           prompt: forward.display, accepted: accepted,
                           display: accepted[0], gloss: forward.gloss)
    }

    public static func reverseInstantiate(template: PhraseTemplate, value: Int) -> TrainerTask {
        let forward = instantiate(template: template, value: value)
        let german = template.deTemplate.replacingOccurrences(of: PhraseTemplate.slotMarker, with: String(value))
        return TrainerTask(kind: template.slotKind, language: .german,
                           prompt: forward.display, accepted: [german],
                           display: german, gloss: forward.gloss)
    }

    public static func reverseSample(template: PhraseTemplate,
                                     using rng: inout some RandomNumberGenerator) -> TrainerTask {
        let forward = sample(template: template, using: &rng)
        if template.slotKind == .clock {
            let parts = forward.prompt.components(separatedBy: CharacterSet.decimalDigits.inverted)
                .filter { !$0.isEmpty }.compactMap { Int($0) }
            return reverseInstantiate(template: template, hour: parts.count > 1 ? parts[parts.count - 2] : 0,
                                      minute: parts.last ?? 0)
        }
        let digits = forward.prompt.filter(\.isNumber)
        return reverseInstantiate(template: template, value: Int(digits) ?? 0)
    }

    // MARK: - Composition

    static func compose(template: PhraseTemplate, slot: TrainerTask, value: Int?) -> TrainerTask {
        let countWord: String?
        if let forms = template.countForms {
            precondition(template.slotKind == .numbers, "countForms only compose with .numbers")
            countWord = value.map(forms.form(for:))
        } else {
            countWord = nil
        }

        let prompt = template.deTemplate
            .replacingOccurrences(of: PhraseTemplate.slotMarker, with: slot.prompt)
        let display = fillTarget(template.targetTemplate, slotWords: slot.display, countWord: countWord)
        var accepted: [String] = []
        for variant in slot.accepted {
            // Feminine numeral before a masculine counted noun would accept
            // the exact agreement error these templates train — drop it.
            if template.masculineSlot,
               let last = variant.split(separator: " ").last,
               last == "одна" || last == "дві" { continue }
            let sentence = fillTarget(template.targetTemplate, slotWords: variant, countWord: countWord)
            if !accepted.contains(sentence) { accepted.append(sentence) }
        }
        let gloss = [template.gloss, slot.gloss].compactMap { $0 }.joined(separator: " · ")

        return TrainerTask(kind: template.slotKind, language: template.targetLanguage,
                           prompt: prompt, accepted: accepted, display: display,
                           gloss: gloss.isEmpty ? nil : gloss)
    }

    /// Sentence-position-aware substitution: mid-sentence slots lowercase the
    /// value's first letter (Trainer's Swahili clock strings start "Saa …"),
    /// sentence-initial slots uppercase it.
    static func fillTarget(_ template: String, slotWords: String, countWord: String?) -> String {
        var result = template
        if let range = result.range(of: PhraseTemplate.slotMarker) {
            let sentenceStart = result[..<range.lowerBound]
                .allSatisfy { !$0.isLetter && !$0.isNumber }
            result.replaceSubrange(range, with: adjustCase(slotWords, sentenceStart: sentenceStart))
        }
        if let countWord {
            result = result.replacingOccurrences(of: PhraseTemplate.countMarker, with: countWord)
        }
        return result
    }

    static func adjustCase(_ words: String, sentenceStart: Bool) -> String {
        guard let first = words.first else { return words }
        let head = sentenceStart ? String(first).uppercased() : String(first).lowercased()
        return head + words.dropFirst()
    }
}
