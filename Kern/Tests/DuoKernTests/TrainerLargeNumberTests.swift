import Testing
@testable import DuoKern
@testable import DuoKernTrainer

/// Hand-picked assertions for millions and billions (beyond the golden
/// fixture, which stops at 999 999). Canonical forms verified against
/// standard readings in each language.
struct TrainerLargeNumberTests {

    private func display(_ n: Int, _ lang: TrainerLanguage) -> String {
        Trainer.number(n, language: lang).display
    }

    @Test func germanMillionsAndBillions() {
        #expect(display(1_000_000, .german) == "eine Million")
        #expect(display(2_000_000, .german) == "zwei Millionen")
        #expect(display(21_000_000, .german) == "einundzwanzig Millionen")
        #expect(display(1_000_000_000, .german) == "eine Milliarde")
        #expect(display(2_000_000_000, .german) == "zwei Milliarden")
        #expect(display(1_001_000, .german) == "eine Million eintausend")
        #expect(display(1_234_567, .german)
                == "eine Million zweihundertvierunddreißigtausendfünfhundertsiebenundsechzig")
    }

    @Test func swahiliMillionsAndBillions() {
        #expect(display(1_000_000, .swahili) == "milioni moja")
        #expect(display(2_000_000, .swahili) == "milioni mbili")
        #expect(display(1_000_000_000, .swahili) == "bilioni moja")
        #expect(display(5_000_000, .swahili) == "milioni tano")
        #expect(display(1_000_500, .swahili) == "milioni moja na mia tano")
    }

    @Test func ukrainianMillionsAndBillions() {
        #expect(display(1_000_000, .ukrainian) == "один мільйон")
        #expect(display(2_000_000, .ukrainian) == "два мільйони")
        #expect(display(5_000_000, .ukrainian) == "п'ять мільйонів")
        #expect(display(21_000_000, .ukrainian) == "двадцять один мільйон")
        #expect(display(1_000_000_000, .ukrainian) == "один мільярд")
        #expect(display(3_000_000_000, .ukrainian) == "три мільярди")
    }

    /// Every value 0…9_999_999_999 sampled sparsely stays well-formed
    /// (non-empty, no digit fallback for in-range values).
    @Test func largeGeneratorsNeverFallBackToDigits() {
        for lang in TrainerLanguage.allCases {
            for n in [1_000_000, 9_999_999, 10_000_000, 123_456_789,
                      1_000_000_000, 9_999_999_999] {
                let word = display(n, lang)
                #expect(word != String(n), "n=\(n) \(lang) fell back to digits")
                #expect(!word.isEmpty)
            }
        }
    }
}
