package net.spross.kern.trainer

import kotlin.test.Test

/**
 * The vocabulary spec for Ukrainian's number forms, authored from Український правопис 2019
 * and the УДХТУ «Числівник» booklet.
 * It is never read back off the generator: a reading that changes here is a claim about the
 * language, not about the code.
 * The helpers it asserts through are `NumberFormsFixture`'s.
 */
class TrainerUkrainianFormsTests {

    @Test
    fun ukrainianNegativesCarryTheCardinalsOwnVariants() {
        assertCanonical("uk", NumberValue.Negative(7), "мінус сім")
        assertCanonical("uk", NumberValue.Negative(21), "мінус двадцять один")
        assertAccepts("uk", NumberValue.Negative(21), "мінус двадцять одна")
        assertCanonical("uk", NumberValue.Negative(1000), "мінус одна тисяча")
        assertAccepts("uk", NumberValue.Negative(1000), "мінус тисяча")
    }

    @Test
    fun ukrainianDecimalsNameThePlaceAndTakeTheFeminineWholePart() {
        assertCanonical("uk", NumberValue.Decimal(3, "7"), "три цілих сім десятих")
        assertCanonical("uk", NumberValue.Decimal(1, "5"), "одна ціла п'ять десятих")
        assertCanonical("uk", NumberValue.Decimal(0, "9"), "нуль цілих дев'ять десятих")
        assertCanonical("uk", NumberValue.Decimal(2, "34"), "дві цілих тридцять чотири сотих")
        assertCanonical("uk", NumberValue.Decimal(21, "1"), "двадцять одна ціла одна десята")
        assertCanonical("uk", NumberValue.Decimal(91, "01"), "дев'яносто одна ціла одна сота")
        assertAccepts("uk", NumberValue.Decimal(3, "7"), "три цілих і сім десятих")
    }

    /** The place comes from the digit COUNT, so a leading or trailing zero survives it. */
    @Test
    fun ukrainianDecimalPlacesFollowTheDigitStringNotItsValue() {
        assertCanonical("uk", NumberValue.Decimal(0, "05"), "нуль цілих п'ять сотих")
        assertCanonical("uk", NumberValue.Decimal(3, "40"), "три цілих сорок сотих")
        assertCanonical("uk", NumberValue.Decimal(11, "002"), "одинадцять цілих дві тисячних")
        assertAccepts("uk", NumberValue.Decimal(11, "002"), "одинадцять цілих дві тисячні")
    }

    /** Ukrainian has one decimal register, and "кома" is the mark's name, not a reading. */
    @Test
    fun ukrainianDecimalsNeverReadTheCommaAloud() {
        assertRejects(
            "uk", NumberValue.Decimal(3, "7"),
            "три кома сім", "три крапка сім",
        )
    }

    @Test
    fun ukrainianPercentAgreesWithTheLastDigit() {
        assertCanonical("uk", NumberValue.Percent(1), "один відсоток")
        assertCanonical("uk", NumberValue.Percent(2), "два відсотки")
        assertCanonical("uk", NumberValue.Percent(5), "п'ять відсотків")
        assertCanonical("uk", NumberValue.Percent(11), "одинадцять відсотків")
        assertCanonical("uk", NumberValue.Percent(14), "чотирнадцять відсотків")
        assertCanonical("uk", NumberValue.Percent(21), "двадцять один відсоток")
        assertCanonical("uk", NumberValue.Percent(45), "сорок п'ять відсотків")
        assertCanonical("uk", NumberValue.Percent(100), "сто відсотків")
        assertAccepts("uk", NumberValue.Percent(45), "сорок п'ять процентів")
        assertRejects("uk", NumberValue.Percent(1), "одна відсоток")
    }

    @Test
    fun ukrainianMultiplicativesCountRaziv() {
        assertCanonical("uk", NumberValue.Multiplicative(1), "один раз")
        assertAccepts("uk", NumberValue.Multiplicative(1), "раз")
        assertCanonical("uk", NumberValue.Multiplicative(2), "два рази")
        assertAccepts("uk", NumberValue.Multiplicative(2), "двічі")
        assertCanonical("uk", NumberValue.Multiplicative(3), "три рази")
        assertAccepts("uk", NumberValue.Multiplicative(3), "тричі")
        assertCanonical("uk", NumberValue.Multiplicative(5), "п'ять разів")
        assertCanonical("uk", NumberValue.Multiplicative(11), "одинадцять разів")
        assertCanonical("uk", NumberValue.Multiplicative(21), "двадцять один раз")
        assertCanonical("uk", NumberValue.Multiplicative(100), "сто разів")
    }

    /** "N-fold" is a factor, not a count of occasions, and "раза" belongs after півтора. */
    @Test
    fun ukrainianMultiplicativesRefuseTheFoldAdverbs() {
        assertRejects("uk", NumberValue.Multiplicative(2), "удвічі", "два раза")
        assertRejects("uk", NumberValue.Multiplicative(3), "утричі")
        assertRejects("uk", NumberValue.Multiplicative(4), "учетверо")
    }

    @Test
    fun ukrainianFractionsPutTheDenominatorInTheGenitivePlural() {
        assertCanonical("uk", NumberValue.Fraction(1, 2), "одна друга")
        assertCanonical("uk", NumberValue.Fraction(1, 8), "одна восьма")
        assertCanonical("uk", NumberValue.Fraction(1, 12), "одна дванадцята")
        assertCanonical("uk", NumberValue.Fraction(2, 3), "дві третіх")
        assertCanonical("uk", NumberValue.Fraction(3, 4), "три четвертих")
        assertCanonical("uk", NumberValue.Fraction(2, 7), "дві сьомих")
        assertCanonical("uk", NumberValue.Fraction(5, 6), "п'ять шостих")
        assertCanonical("uk", NumberValue.Fraction(7, 12), "сім дванадцятих")
        assertCanonical("uk", NumberValue.Fraction(9, 10), "дев'ять десятих")
    }

    /** The 2007 edition's nominative plural for numerators 2–4 is still in wide print. */
    @Test
    fun ukrainianFractionsAlsoTakeTheOlderNominativePlural() {
        assertAccepts("uk", NumberValue.Fraction(2, 3), "дві треті")
        assertAccepts("uk", NumberValue.Fraction(3, 4), "три четверті")
        assertAccepts("uk", NumberValue.Fraction(2, 7), "дві сьомі")
        assertRejects("uk", NumberValue.Fraction(5, 6), "п'ять шості")
    }

    /** The -ин suffix already means one, so the noun grades bare and never after "одна". */
    @Test
    fun ukrainianUnitFractionNounsGradeOnlyBare() {
        assertAccepts("uk", NumberValue.Fraction(1, 2), "половина")
        assertAccepts("uk", NumberValue.Fraction(1, 3), "третина")
        assertAccepts("uk", NumberValue.Fraction(1, 4), "чверть")
        assertRejects("uk", NumberValue.Fraction(1, 3), "одна третина")
        assertRejects("uk", NumberValue.Fraction(1, 4), "одна чверть")
        assertRejects("uk", NumberValue.Fraction(1, 2), "пів")
    }

    @Test
    fun ukrainianOrdinalsInflectOnlyTheLastWord() {
        assertCanonical("uk", NumberValue.Ordinal(1), "перший")
        assertAccepts("uk", NumberValue.Ordinal(1), "перша", "перше")
        assertCanonical("uk", NumberValue.Ordinal(3), "третій")
        assertAccepts("uk", NumberValue.Ordinal(3), "третя", "третє")
        assertCanonical("uk", NumberValue.Ordinal(4), "четвертий")
        assertCanonical("uk", NumberValue.Ordinal(6), "шостий")
        assertCanonical("uk", NumberValue.Ordinal(7), "сьомий")
        assertCanonical("uk", NumberValue.Ordinal(8), "восьмий")
        assertCanonical("uk", NumberValue.Ordinal(13), "тринадцятий")
        assertCanonical("uk", NumberValue.Ordinal(20), "двадцятий")
        assertCanonical("uk", NumberValue.Ordinal(21), "двадцять перший")
        assertAccepts("uk", NumberValue.Ordinal(21), "двадцять перша", "двадцять перше")
        assertCanonical("uk", NumberValue.Ordinal(40), "сороковий")
        assertCanonical("uk", NumberValue.Ordinal(50), "п'ятдесятий")
        assertCanonical("uk", NumberValue.Ordinal(90), "дев'яностий")
        assertCanonical("uk", NumberValue.Ordinal(99), "дев'яносто дев'ятий")
        assertCanonical("uk", NumberValue.Ordinal(100), "сотий")
    }
}
