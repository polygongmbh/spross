import Testing
@testable import DuoKern

/// Hand-picked Ukrainian assertions (new content, not in the prototype).
/// Expected strings are documented here for external verification;
/// canonical = masculine counting form, feminine (одна/дві) accepted.
struct TrainerUkrainianTests {

    private func number(_ n: Int) -> TrainerTask { Trainer.number(n, language: .ukrainian) }
    private func clock(_ h: Int, _ m: Int) -> TrainerTask { Trainer.clock(hour: h, minute: m, language: .ukrainian) }

    @Test func basicNumbers() {
        let expected: [Int: String] = [
            0: "нуль", 1: "один", 2: "два", 3: "три", 4: "чотири",
            5: "п'ять", 6: "шість", 7: "сім", 8: "вісім", 9: "дев'ять",
            10: "десять", 11: "одинадцять", 12: "дванадцять", 13: "тринадцять",
            14: "чотирнадцять", 15: "п'ятнадцять", 16: "шістнадцять",
            17: "сімнадцять", 18: "вісімнадцять", 19: "дев'ятнадцять", 20: "двадцять",
        ]
        for (n, word) in expected {
            #expect(number(n).display == word, "n=\(n)")
        }
    }

    @Test func feminineVariantsForOneAndTwo() {
        #expect(number(1).accepted == ["один", "одна"])
        #expect(number(2).accepted == ["два", "дві"])
        #expect(number(21).accepted == ["двадцять один", "двадцять одна"])
        #expect(number(32).accepted == ["тридцять два", "тридцять дві"])
        // teens never take the feminine split
        #expect(number(11).accepted == ["одинадцять"])
        #expect(number(12).accepted == ["дванадцять"])
    }

    @Test func tensAndHundreds() {
        let expected: [Int: String] = [
            21: "двадцять один", 32: "тридцять два", 40: "сорок",
            45: "сорок п'ять", 67: "шістдесят сім", 89: "вісімдесят дев'ять",
            90: "дев'яносто", 99: "дев'яносто дев'ять",
            100: "сто", 101: "сто один", 111: "сто одинадцять",
            200: "двісті", 300: "триста", 400: "чотириста", 500: "п'ятсот",
            345: "триста сорок п'ять", 999: "дев'ятсот дев'яносто дев'ять",
        ]
        for (n, word) in expected {
            #expect(number(n).display == word, "n=\(n)")
        }
    }

    @Test func thousandAgreement() {
        // 1 тисяча / 2–4 тисячі / 5+ тисяч, always with feminine multiplier
        let expected: [Int: String] = [
            1000: "одна тисяча", 2000: "дві тисячі", 3000: "три тисячі",
            4000: "чотири тисячі", 5000: "п'ять тисяч", 11000: "одинадцять тисяч",
            12000: "дванадцять тисяч", 21000: "двадцять одна тисяча",
            22000: "двадцять дві тисячі", 25000: "двадцять п'ять тисяч",
            100_000: "сто тисяч", 111_000: "сто одинадцять тисяч",
            1001: "одна тисяча один", 2345: "дві тисячі триста сорок п'ять",
            15690: "п'ятнадцять тисяч шістсот дев'яносто",
            999_999: "дев'ятсот дев'яносто дев'ять тисяч дев'ятсот дев'яносто дев'ять",
        ]
        for (n, word) in expected {
            #expect(number(n).display == word, "n=\(n)")
        }
        // "тисяча" without "одна" is an accepted colloquial reading for 1xxx
        #expect(number(1000).accepted.contains("тисяча"))
        #expect(number(1978).accepted.contains("тисяча дев'ятсот сімдесят вісім"))
    }

    @Test func yearsUsePlainCardinalReading() {
        let task = Trainer.year(1978, language: .ukrainian)
        #expect(task.display == "одна тисяча дев'ятсот сімдесят вісім")
        #expect(task.accepted.contains("тисяча дев'ятсот сімдесят вісім"))
        #expect(Trainer.year(2026, language: .ukrainian).display == "дві тисячі двадцять шість")
    }

    @Test func clockPatterns() {
        // exact hour: ordinal + година, bare ordinal accepted
        let two = clock(14, 0)
        #expect(two.display == "друга година")
        #expect(two.accepted.contains("друга"))
        #expect(clock(0, 0).display == "дванадцята година")
        #expect(clock(13, 0).display == "перша година")

        // half past: пів на + accusative of next hour
        #expect(clock(2, 30).display == "пів на третю")
        #expect(clock(11, 30).display == "пів на дванадцяту")
        #expect(clock(12, 30).display == "пів на першу")

        // quarter past: чверть на + accusative; variant "п'ятнадцять по <locative>"
        let quarter = clock(2, 15)
        #expect(quarter.display == "чверть на третю")
        #expect(quarter.accepted.contains("п'ятнадцять по другій"))
        #expect(clock(12, 15).display == "чверть на першу")

        // quarter to: за чверть + nominative of next hour
        #expect(clock(2, 45).display == "за чверть третя")
        #expect(clock(23, 45).display == "за чверть дванадцята")
        #expect(clock(2, 45).accepted.contains("за п'ятнадцять третя"))

        // generic minutes: digital reading, plus по/за variants
        let d35 = clock(14, 35)
        #expect(d35.display == "друга тридцять п'ять")
        #expect(d35.accepted.contains("за двадцять п'ять третя"))
        let d10 = clock(14, 10)
        #expect(d10.display == "друга десять")
        #expect(d10.accepted.contains("десять по другій"))
        #expect(clock(9, 55).accepted.contains("за п'ять десята"))
    }
}
