import Testing
@testable import DuoKern
@testable import DuoKernTrainer

struct TrainerHintsTests {

    @Test func placeValueHintsCoverEveryDigitLength() {
        // 1 digit has no place word; 2…10 all do, for every language.
        for lang in TrainerLanguage.allCases {
            #expect(Trainer.placeValueHint(digits: 1, language: lang) == nil)
            for digits in 2...Trainer.maxLevel(kind: .numbers) {
                #expect(Trainer.placeValueHint(digits: digits, language: lang) != nil,
                        "\(lang) missing place hint for \(digits) digits")
            }
            #expect(Trainer.placeValueHint(digits: 11, language: lang) == nil)
        }
    }

    @Test func placeValueHintsAreTheExpectedWords() {
        #expect(Trainer.placeValueHint(digits: 3, language: .german) == "hundert")
        #expect(Trainer.placeValueHint(digits: 4, language: .swahili) == "elfu")
        #expect(Trainer.placeValueHint(digits: 7, language: .swahili) == "milioni")
        #expect(Trainer.placeValueHint(digits: 10, language: .german) == "Milliarde")
    }

    @Test func tensReferenceIsSwahiliOnly() {
        #expect(Trainer.tensReference(language: .german) == nil)
        #expect(Trainer.tensReference(language: .ukrainian) == nil)
        let sw = Trainer.tensReference(language: .swahili)
        #expect(sw?.count == 9)
        #expect(sw?.first == "10 kumi")
        #expect(sw?.contains("30 thelathini") == true)
        #expect(sw?.last == "90 tisini")
    }
}
