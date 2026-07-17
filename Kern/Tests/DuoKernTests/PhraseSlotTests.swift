import Testing
@testable import DuoKern

/// Deterministic SplitMix64 (local copy; the ones in other test files are private).
private struct SplitMix64: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

struct PhraseSlotTests {

    private func template(_ id: String) -> PhraseTemplate {
        PhraseTemplates.all.first { $0.id == id }!
    }

    // MARK: - Hand-picked exact instantiations (de-sw)

    @Test func swahiliTrainDeparture() {
        let task = PhraseSlots.instantiate(template: template("sw-clock-zug"), hour: 20, minute: 0)
        #expect(task.prompt == "Der Zug fährt um 20:00 Uhr ab.")
        #expect(task.display == "Treni inaondoka saa mbili usiku.")
        #expect(task.accepted == ["Treni inaondoka saa mbili usiku."])
        #expect(task.kind == .clock && task.language == .swahili)
        #expect(task.gloss == SwahiliClock.gloss)
    }

    @Test func swahiliWakeUpLowercasesSaaMidSentence() {
        let task = PhraseSlots.instantiate(template: template("sw-clock-aufwachen"), hour: 6, minute: 30)
        #expect(task.prompt == "Ich wache um 06:30 Uhr auf.")
        #expect(task.display == "Ninaamka saa kumi na mbili na nusu asubuhi.")
    }

    @Test func swahiliPlateCount() {
        let task = PhraseSlots.instantiate(template: template("sw-num-teller"), value: 347)
        #expect(task.prompt == "Wir haben 347 Teller.")
        #expect(task.display == "Tuna sahani mia tatu na arobaini na saba.")
        #expect(task.accepted == ["Tuna sahani mia tatu na arobaini na saba."])
        #expect(task.kind == .numbers)
    }

    @Test func swahiliYearSince() {
        let task = PhraseSlots.instantiate(template: template("sw-year-seit"), value: 2000)
        #expect(task.prompt == "Ich lerne seit 2000 Deutsch.")
        #expect(task.display == "Ninajifunza Kijerumani tangu elfu mbili.")
        #expect(task.kind == .years)
        #expect(task.gloss == "Jahreszahl als Kardinalzahl gelesen")
    }

    // MARK: - Hand-picked exact instantiations (de-uk)

    @Test func ukrainianClockNowFullHour() {
        let task = PhraseSlots.instantiate(template: template("uk-clock-jetzt"), hour: 14, minute: 0)
        #expect(task.prompt == "Es ist jetzt 14:00 Uhr.")
        #expect(task.display == "Зараз друга година.")
        #expect(task.accepted == ["Зараз друга година.", "Зараз друга."])
        #expect(task.kind == .clock && task.language == .ukrainian)
    }

    @Test func ukrainianClockNowGenericMinutes() {
        let task = PhraseSlots.instantiate(template: template("uk-clock-jetzt"), hour: 14, minute: 35)
        #expect(task.prompt == "Es ist jetzt 14:35 Uhr.")
        #expect(task.display == "Зараз друга тридцять п'ять.")
        #expect(task.accepted.contains("Зараз за двадцять п'ять третя."))
    }

    @Test func ukrainianAlarmClockHalfPast() {
        let task = PhraseSlots.instantiate(template: template("uk-clock-wecker"), hour: 2, minute: 30)
        #expect(task.prompt == "Der Wecker zeigt 02:30 Uhr.")
        #expect(task.display == "На будильнику пів на третю.")
        #expect(task.accepted.contains("На будильнику пів третьої."))
        #expect(task.accepted.contains("На будильнику друга тридцять."))
    }

    @Test func ukrainianPriceKeepsIndeclinableEuro() {
        let task = PhraseSlots.instantiate(template: template("uk-num-preis"), value: 21)
        #expect(task.prompt == "Das kostet 21 Euro.")
        #expect(task.display == "Це двадцять один євро.")
        #expect(task.accepted == ["Це двадцять один євро.", "Це двадцять одна євро."])
    }

    @Test func ukrainianCountedNounAgreement() {
        let hefte = template("uk-num-hefte")
        #expect(PhraseSlots.instantiate(template: hefte, value: 25).display
                == "У мене є двадцять п'ять зошитів.")
        #expect(PhraseSlots.instantiate(template: hefte, value: 22).display
                == "У мене є двадцять два зошити.")
        #expect(PhraseSlots.instantiate(template: hefte, value: 21).display
                == "У мене є двадцять один зошит.")
        #expect(PhraseSlots.instantiate(template: hefte, value: 11).display
                == "У мене є одинадцять зошитів.")
        #expect(PhraseSlots.instantiate(template: template("uk-num-stuehle"), value: 4).display
                == "У нас є чотири стільці.")
    }

    @Test func ukrainianYearDictation() {
        let task = PhraseSlots.instantiate(template: template("uk-year-wiederholen"), value: 1978)
        #expect(task.prompt == "Wiederholen Sie bitte die Jahreszahl: 1978.")
        #expect(task.display == "Повторіть, будь ласка: одна тисяча дев'ятсот сімдесят вісім.")
        #expect(task.accepted.contains("Повторіть, будь ласка: тисяча дев'ятсот сімдесят вісім."))
    }

    // MARK: - Casing at sentence start

    @Test func sentenceInitialSlotKeepsCapital() {
        let synthetic = PhraseTemplate(id: "test-initial", pair: .deSw,
                                       deTemplate: "{slot} Uhr.",
                                       targetTemplate: "{slot}, sawa?",
                                       slotKind: .clock)
        let task = PhraseSlots.instantiate(template: synthetic, hour: 20, minute: 0)
        #expect(task.display == "Saa mbili usiku, sawa?")
    }

    // MARK: - Gloss merging

    @Test func glossesMergeTemplateThenSlot() {
        let task = PhraseSlots.instantiate(template: template("uk-clock-wecker"), hour: 2, minute: 15)
        let gloss = task.gloss ?? ""
        #expect(gloss.hasPrefix("wörtl.: „Auf dem Wecker [ist] …“ · "))
        #expect(gloss.contains("чверть на"))
    }

    // MARK: - Every accepted slot variant → exactly one accepted sentence

    @Test func everyAcceptedVariantAppearsInExactlyOneSentence() {
        for template in PhraseTemplates.all {
            switch template.slotKind {
            case .clock:
                for h in [0, 6, 9, 13, 14, 20, 23] {
                    for m in [0, 15, 20, 30, 35, 45, 55] {
                        let slot = Trainer.clock(hour: h, minute: m, language: template.targetLanguage)
                        let task = PhraseSlots.instantiate(template: template, hour: h, minute: m)
                        verifyVariantCoverage(template: template, slot: slot, task: task, value: nil)
                    }
                }
            case .numbers, .years:
                for v in [1, 2, 5, 11, 21, 22, 25, 100, 347, 1000, 1978, 2026] {
                    let slot = template.slotKind == .numbers
                        ? Trainer.number(v, language: template.targetLanguage)
                        : Trainer.year(v, language: template.targetLanguage)
                    let task = PhraseSlots.instantiate(template: template, value: v)
                    verifyVariantCoverage(template: template, slot: slot, task: task, value: v)
                }
            }
        }
    }

    /// Independent re-rendering of the spec: target sentence with the variant
    /// substituted (mid-sentence values lowercase their first letter).
    private func render(_ template: PhraseTemplate, variant: String, value: Int?) -> String {
        guard let range = template.targetTemplate.range(of: "{slot}") else { return "" }
        let atStart = template.targetTemplate[..<range.lowerBound]
            .trimmingCharacters(in: .whitespaces).isEmpty
        var words = variant
        if let f = words.first {
            words = (atStart ? String(f).uppercased() : String(f).lowercased()) + words.dropFirst()
        }
        var sentence = template.targetTemplate.replacingOccurrences(of: "{slot}", with: words)
        if let forms = template.countForms, let value {
            sentence = sentence.replacingOccurrences(of: "{count}", with: forms.form(for: value))
        }
        return sentence
    }

    private func verifyVariantCoverage(template: PhraseTemplate, slot: TrainerTask,
                                       task: TrainerTask, value: Int?) {
        #expect(task.accepted.count == slot.accepted.count,
                "\(template.id): sentence per variant (\(slot.accepted))")
        #expect(Set(task.accepted).count == task.accepted.count, "\(template.id): duplicates")
        for variant in slot.accepted {
            let sentence = render(template, variant: variant, value: value)
            #expect(task.accepted.filter { $0 == sentence }.count == 1,
                    "\(template.id): variant „\(variant)“ missing/duplicated in \(task.accepted)")
        }
        #expect(task.display == render(template, variant: slot.display, value: value))
        #expect(task.accepted.contains(task.display))
    }

    // MARK: - Sampling

    @Test func samplingIsDeterministicAndMatchesInstantiate() {
        for template in PhraseTemplates.all {
            var a = SplitMix64(state: 0xC0FFEE)
            var b = SplitMix64(state: 0xC0FFEE)
            for _ in 0..<50 {
                let sampled = PhraseSlots.sample(template: template, using: &a)
                // Reconstruct via the underlying Trainer sample + instantiate.
                let slot = Trainer.sample(kind: template.slotKind,
                                          language: template.targetLanguage, using: &b)
                let expected: TrainerTask
                if template.slotKind == .clock {
                    let parts = slot.prompt.split(separator: ":").compactMap { Int($0) }
                    expected = PhraseSlots.instantiate(template: template,
                                                       hour: parts[0], minute: parts[1])
                } else {
                    expected = PhraseSlots.instantiate(template: template, value: Int(slot.prompt)!)
                }
                #expect(sampled == expected, "\(template.id)")
            }
        }
    }

    // MARK: - Curated-set invariants

    @Test func curatedSetsAreWellFormed() {
        let deSw = PhraseTemplates.templates(pair: .deSw)
        let deUk = PhraseTemplates.templates(pair: .deUk)
        #expect((10...12).contains(deSw.count))
        #expect((10...12).contains(deUk.count))
        let ids = PhraseTemplates.all.map(\.id)
        #expect(Set(ids).count == ids.count, "duplicate template ids")

        for (pair, set) in [(LanguagePair.deSw, deSw), (.deUk, deUk)] {
            #expect(Set(set.map(\.slotKind)) == Set(TrainerKind.allCases), "\(pair) kind coverage")
            for template in set {
                #expect(template.pair == pair)
                #expect(occurrences(of: "{slot}", in: template.deTemplate) == 1, "\(template.id)")
                #expect(occurrences(of: "{slot}", in: template.targetTemplate) == 1, "\(template.id)")
                let hasCountMarker = template.targetTemplate.contains("{count}")
                #expect(hasCountMarker == (template.countForms != nil), "\(template.id)")
                if template.countForms != nil {
                    #expect(template.slotKind == .numbers, "\(template.id)")
                }
            }
        }
    }

    private func occurrences(of marker: String, in text: String) -> Int {
        text.components(separatedBy: marker).count - 1
    }
}
