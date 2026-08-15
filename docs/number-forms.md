# Number forms — what each language reads

The numbers drill asks six things beyond the plain cardinal:
a negative, a decimal, a percentage, a multiplicative, a fraction and an ordinal.
This is the one home for what each language reads for them,
and for the source that decided it.

Everything else about the forms is owned elsewhere:
how a value is drawn and how its prompt is written are `../kern/docs/build.md`,
and how the readings are held apart from one another is `../kern/README.md` § 6 (the forms sweep).
The code is one `<Lang>Forms.kt` per language
under `../kern/src/commonMain/kotlin/net/spross/kern/trainer/`,
each declaring that pack's `FormLimits`;
the canonical and refused readings below are pinned by `TrainerFormsTests`.

## How to read an entry

- **Canonical** is what the reveal shows — `formReading`'s first element,
  and the only reading a learner is ever taught.
- **Also graded** is accepted and never shown:
  a correct answer in a register the app does not choose to teach.
- **Refused** is a reading a learner will genuinely reach for that must grade *wrong*,
  because accepting it would teach the error its source names.
  Refusals are listed only where they exist.

## Reach

| | Forms drilled | Fraction denominators | Ordinals | Decimal mark |
|---|---|---|---|---|
| de | all six | 2–12 | 1–100 | `,` |
| en | all six | 2–12 | 1–100 | `.` |
| es | all six | 2–12 | **1–12** | `,` |
| it | all six | 2–12 | 1–100 | `,` |
| sw | five — **no ordinal** | **2–4** | — | `.` |
| uk | all six | 2–12 | 1–100 | `,` |

The ladder intersects its rung with the pack's reach,
so a form a language cannot read is never drawn.
An exclusion costs the learner nothing but the rung
they would otherwise have spent on an invention.

## German

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `minus sieben` | — |
| Decimal | `drei Komma vier fünf` | the run-together `drei Komma fünfundvierzig` |
| Percent | `ein Prozent`, `einhundert Prozent` | `hundert Prozent` |
| Multiplicative | `dreimal` | `drei Mal`, `hundertmal` |
| Fraction | `ein Drittel`, `ein halb` | `einhalb`, `die Hälfte`, `ein Siebentel` |
| Ordinal | `zwanzigste` | `-er` / `-en` / `-es`, `siebente`, `hundertste` |

- The numeral before a noun is **`ein`, never `eins`** — `ein Prozent`, `einmal`, `ein Drittel`.
  `eins` survives only in the decimal, where nothing follows it (`eins Komma fünf`).
- The run-together decimal is suppressed on a leading zero:
  `null Komma fünf` is a different number from `null Komma null fünf`.
- The fraction noun is the ordinal stem plus `-el`,
  so it composes with the ordinal generator rather than repeating it.
  `Zweitel` is *veraltet*, so `1/2` is suppletive;
  and because `1 ≤ n < d`, `d == 2` forces `n == 1`, so no plural of `halb` exists to author.
- `siebte`/`siebente` and `Siebtel`/`Siebentel` are a genuine source split —
  Duden heads `siebte`, DWDS heads `siebente` and marks the other a Nebenform —
  so both grade and neither source is treated as deciding.
- Two range edges are load-bearing and will break if the reach is widened:
  the fraction noun's `-tel` → `-stel` switch starts at denominator 20,
  and the ordinal's `-te` → `-ste` switch is governed by the **last cardinal component**,
  not the value (`101.` is `hunderterste` again).

Sources: [Duden, Zahlwörter und ihre Schreibung](https://www.duden.de/sprachwissen/sprachratgeber/Zahlw%C3%B6rter-und-ihre-Schreibung)
· [Duden, mal/Mal](https://www.duden.de/sprachwissen/sprachratgeber/malMal)
· [Duden, Schreibung der Ordnungszahlen](https://www.duden.de/sprachwissen/sprachratgeber/Schreibung-der-Ordnungszahlen)
· [Duden „siebte"](https://www.duden.de/rechtschreibung/siebte) vs [DWDS „siebente"](https://www.dwds.de/wb/siebente)
· [Wikipedia, Zahlwort](https://de.wikipedia.org/wiki/Zahlwort) (fraction noun = ordinal stem + -el)
· [Wiktionary, Zweitel](https://de.wiktionary.org/wiki/Zweitel).

## English

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `minus seven` | `negative seven` |
| Decimal | `three point four five` | `oh` for a zero digit, `nought` and a dropped whole-part zero |
| Percent | `forty-five percent` | `per cent`, `a hundred percent`, bare `hundred percent` |
| Multiplicative | `once`, `twice`, `three times` | `one time`, `two times`, `thrice` |
| Fraction | `one half`, `one quarter`, `two thirds` | `a third`, `one fourth`, bare `half`/`quarter`, the hyphenated `two-thirds` |
| Ordinal | `twenty-first` | `twenty first`, `a hundredth` |

- Everything routes through `EnglishNumbers.spellings()`,
  which adds the spaced twin of every hyphenated compound.
  That is not cosmetic: the comparison pipeline **deletes** hyphens rather than spacing them,
  so `twenty-first` and a learner's `twenty first` are two unrelated strings.
- For the same reason a fraction's hyphenated twin is written the other way round,
  from the spaced canonical — and **only over the numeral**:
  `a-third` would normalize to `athird`, one edit from `third`,
  which is another prompt in the same drill.
- English gets **no run-together decimal**: `three point forty-five` is not standard,
  so the asymmetry with German and Spanish is encoded per pack rather than shared.
- `halves` is deliberately unauthored —
  `1 ≤ n < d` makes `d == 2` force `n == 1`,
  so the plural is unreachable and would only give the sweep work.
- `thrice` is described as largely obsolete by its own source: accepted, never shown.

Sources: [Wikipedia, English numerals](https://en.wikipedia.org/wiki/English_numerals)
(the point-then-digits rule, the ordinal/partitive identity, `thrice`)
· [Wiktionary, per cent](https://en.wiktionary.org/wiki/per_cent)
· [Names for the number 0 in English](https://en.wikipedia.org/wiki/Names_for_the_number_0_in_English).

## Spanish

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `menos siete` | — | — |
| Decimal | `tres coma cuatro cinco` | `punto` for the mark, the run-together `cuarenta y cinco` | — |
| Percent | `veintiuno por ciento` | `cien por cien`, `ciento por ciento` (100 % only) | `veintiún por ciento`, `porciento` |
| Multiplicative | `una vez`, `veintiuna veces` | — | `un vez`, `veintiún veces` |
| Fraction | `un tercio`, `dos tercios` | `una tercera parte`, `la mitad`, `medio`, `un undécimo` for `un onceavo` | — |
| Ordinal | `undécimo` | feminine `-a`, `decimoprimero`, `décimo primero` | `onceavo` as an ordinal, bare `primer`/`tercer` |

- **The number 1 is read three different ways inside this one pack** —
  `uno por ciento` (unapocopated),
  `una vez` (feminine, because *vez* is),
  `un tercio` (apocopated before a masculine noun) —
  which is why every arm names its `SpanishNumbers.Form` instead of taking the default cardinal.
- The two refusals are the point of the drill rather than strictness:
  RAE states outright that *uno* does not apocopate before `por ciento`,
  and the DPD calls *el onceavo aniversario* incorrect because the `-avo` forms are fractional only.
  Both are one keystroke from a correct answer,
  so quietly accepting them would teach the documented error.
- `primer`/`tercer` belong immediately before a masculine noun, and a bare prompt has no noun.
  Where the apocope goes is exactly what a Spanish ordinal drill is for, so neither grades.
- Both decimal marks grade, because RAE's *Ortografía* admits both —
  *coma* in Spain, Argentina, Chile, Colombia and Peru,
  *punto* in Mexico, Central America and the Caribbean.
  The **prompt** still has to pick one (`decimalMark` is a single `Char`),
  so a Mexican learner is shown `3,7`.
- Ordinals stop at 12, and not because `septuagésimo` is long:
  the *Nueva gramática* records "una marcada tendencia a evitar el uso de los ordinales
  más allá de los correspondientes a la segunda o tercera decenas" —
  past that speakers say the cardinal (*el piso veinte*),
  so drilling `vigésimo primero` would teach a register nobody uses.
  12 is also the seam where the etymological `undécimo`/`duodécimo` stop.
  Fractions need no such cap: `-avo` is productive and `onceavo`/`doceavo` are school vocabulary.

Sources: [RAE, veintiuna personas / veintiuno por ciento](https://www.rae.es/espanol-al-dia/veintiuna-personas-veintiuno-por-ciento)
· [DPD, ordinales](https://www.rae.es/dpd/ordinales)
· [DPD, fraccionarios](https://www.rae.es/dpd/fraccionarios)
· [RAE, los números decimales y el separador decimal](https://www.rae.es/ortograf%C3%ADa/los-n%C3%BAmeros-decimales-y-el-separador-decimal)
· [RAE, la expresión de los porcentajes](https://www.rae.es/ortograf%C3%ADa/la-expresi%C3%B3n-de-los-porcentajes)
· [Nueva gramática, numerales ordinales](https://www.rae.es/gram%C3%A1tica/sintaxis/numerales-ordinales-i-aspectos-l%C3%A9xicos-y-morfol%C3%B3gicos).

## Italian

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `meno sette` | — | — |
| Decimal | `tre virgola quattro cinque` | the run-together `tre virgola quarantacinque` | `punto` for the mark |
| Percent | `ventuno per cento` | — | `ventun per cento`, `percento` |
| Multiplicative | `una volta`, `ventun volte` | `ventuno volte` | `uno volta`, `doppio` |
| Fraction | `un terzo`, `due terzi`, `un mezzo` | `mezzo`, `la metà`, `metà` | — |
| Ordinal | `ventunesimo`, `ventitreesimo` | the feminine `-a` | `ventitresimo`, `ventisesimo` |

- **Everything below a million is one word**, so the whole spelling rule lives in the SEAMS,
  and the generator applies them rather than tabulating the results:
  a ten drops its final vowel before the two vowel-initial units and only those
  (`ventuno`, `ventotto`, `quarantotto`);
  `cento` drops its own only before another `o` (`centotto`, `centottanta`, but `centouno`, `centoundici`);
  `mille` and `-mila` drop nothing (`milleotto`, `duemilaotto`);
  and a compound ending in `tre` carries the stress, and therefore the accent (`ventitré`, `centotré`, `milletré`).
- **`centuno` is the one recorded spelling this pack leaves out.**
  Dictionaries give both it and `centouno` for 101, but `centuno` sits a single substitution from `ventuno`,
  so a drill accepting it would take 21 for 101 —
  and `centouno` says the number with nothing given up.
  The twin that could NOT be avoided is `ventotto` ↔ `centotto`,
  where both spellings are the canonical reading of their own number;
  it is gated in `TypoBridgeSweep.KNOWN_BRIDGES`, and again as `ventesimo` ↔ `centesimo` in the forms space.
- **`uno` needs the noun that the bare prompt has not got.**
  It agrees with a feminine one (`una volta`), apocopates before any noun (`ventun volte`, `ventun minuti`)
  and stays whole in front of a preposition (`uno per cento`) —
  three readings of one numeral, which is why each form builds its own
  instead of taking the cardinal as it stands.
  The cardinal itself is the citation form: `21` reads `ventuno`,
  and a learner who writes the apocope into a counted sentence is one deletion away, so the answer books amber.
- **`-esimo` is productive**, so Italian needs no ordinal cap of the Spanish kind:
  the suffix eats the cardinal's last vowel except where that vowel is stressed (`ventitreesimo`)
  or part of a diphthong (`ventiseiesimo`), and reaches any value the drill draws.
  The fraction nouns ARE those ordinals from three up, so the two tables can never diverge.
- **`virgola` is the only decimal mark.**
  Italian writes the comma and prints the dot as the thousands separator,
  so reading `punto` would name a different number — unlike Spanish, where both marks are regional and both grade.
  The run-together reading of the fractional part is ordinary Italian
  (`il tre virgola quarantacinque per cento`) and grades beside the digit-by-digit one,
  suppressed on a leading zero where it would say another number.
- **`per cento` is two words** and takes no apocope:
  `per` is a preposition, not the noun `uno` would shorten in front of.
  One-word `percento` and `ventun per cento` are both one keystroke from a correct answer,
  so accepting either would teach the mistake.

Sources: [Treccani, *La grammatica italiana*, «numerali»](https://www.treccani.it/enciclopedia/numerali_(La-grammatica-italiana)/)
· [Treccani, *La grammatica italiana*, «aggettivi numerali»](https://www.treccani.it/enciclopedia/aggettivi-numerali_(La-grammatica-italiana)/)
· [Treccani, Vocabolario, «volta»](https://www.treccani.it/vocabolario/volta/)
· [Accademia della Crusca, consulenza linguistica](https://accademiadellacrusca.it/it/consulenza).

## Swahili

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `hasi saba` | `saba hasi`, `minus saba` |
| Decimal | `tatu nukta saba` | `pointi` for the mark |
| Percent | `asilimia arobaini na tano` | `arobaini na tano kwa mia` |
| Multiplicative | `mara tatu` | `maradufu` (2 only) |
| Fraction | `nusu`, `theluthi`, `robo tatu` | `thuluthi`, an explicit `moja`, `sehemu moja ya tatu` |
| Ordinal | *excluded* | — |

Every reading composes over `SwahiliNumbers.acceptedVariants`, never over `cardinal` alone,
so the `na`-less spelling speakers routinely use
grades behind `hasi`/`asilimia`/`mara` exactly as it does in the plain drill.
Decimal digits are read through `cardinal` so a zero comes out `sifuri`
rather than the empty string the digit table holds.

**Ordinals are excluded, structurally.**
A Swahili ordinal is `-a kwanza`,
and that leading dash is a required associative concord slot the counted noun fills —
*mwanafunzi **wa** kwanza*, *kitabu **cha** pili*, *duka **la** tatu*.
A bare `20.` supplies no noun, so any prefix would be an invention shown to the learner as fact;
that every published source cites ordinals with the slot still empty
is the lexicographers saying the same thing.
Ordinals arrive with a noun-bearing frame or not at all —
the same missing primitive `backlog.md` already names, a numeral-side agreement field.

**Denominators stop at 4.**
`nusu`, `theluthi` and `robo` are the everyday words;
past them the sources give three mutually incompatible systems
(Almasi's `sehemu … ya/za …` periphrasis, a full Arabic unit series, and `n kwa d`),
and several of the Arabic words double as money or tax terms in modern use —
*thumuni* is a ⅛-shilling coin, *ushuri* is tax, *robo* is also a 25-cent coin.
Grading one series right would teach the other two wrong.

Two refusals worth naming.
`kasoro` is subtractive "less" and needs a minuend (*saa tatu kasorobo*),
and the glossaries map "minus" to `kutoa`, i.e. to the operation —
so neither reads a negative *value*.
`desimali` is the noun for a decimal number, not the spoken mark.

Sources: Almasi et al., *Swahili Grammar for Introductory and Intermediate Levels* (UPA 2014),
[ch. 19](https://hist.hse.ru/data/2019/06/14/1486230008/19.%20More%20About%20Swahili%20Numbers.pdf)
— ordinal concord, fractions, the reversed percentage word order, `nukta`/`pointi`
· [TIE Std 5 *Hisabati*](https://fliphtml5.com/rwbnv/iymz/Std_5_Hisabati/149/)
(`0.01` = *sifuri nukta sifuri moja*, and the dot as the mark)
· [TIE Std 4 *Hisabati*](https://fliphtml5.com/rwbnv/zbwv/Std_4_Hisabati/)
(`2 × 1` = *kuzidisha*, so `mara` is unambiguous)
· [NYSED elementary](https://docs.steinhardt.nyu.edu/pdfs/metrocenter/atn293/elemath/elementary_math_swahili.pdf)
and [middle-school](https://docs.steinhardt.nyu.edu/pdfs/metrocenter/atn293/msmath/middle_school_6-8_math_swahili.pdf)
maths glossaries — corroboration only, never a sole source:
the elementary one renders "fifths" as `-a hamsini` (fifty)
· [Wiktionary, hasi](https://en.wiktionary.org/wiki/hasi) · [kasoro](https://en.wiktionary.org/wiki/kasoro)
· [University of Kansas, Kiswahili lesson 14b](https://kiswahili.ku.edu/sites/kiswahili/files/documents/lessons/lesson_14.pdf)
(the Arabic unit series this pack declines to use).

## Ukrainian

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `мінус сім` | the feminine unit variant | — |
| Decimal | `дві цілих тридцять чотири сотих` | an `і` between the halves, the 2007 nominative plural | `дві кома тридцять чотири` |
| Percent | `п'ять відсотків` | `процент` | — |
| Multiplicative | `три рази` | `двічі` (2), `тричі` (3), bare `раз` (1) | `удвічі`/`утричі`, `раза` |
| Fraction | `одна друга`, `дві третіх` | `дві треті`, bare `половина`/`третина`/`чверть` | `одна третина`, bare `пів` |
| Ordinal | `двадцять перший` | feminine and neuter | the plural `-і` |

- **There is no `кома` register.**
  German reads 3,5 as *drei Komma fünf* and Ukrainian does not:
  *кома* is the NAME of the punctuation mark,
  and even the uk.wikipedia article about the comma reads its own example
  «п'ять цілих вісім десятих».
  No normative, pedagogical or journalistic source attests the other reading,
  so `цілих/десятих` is the only one that grades.
- The decimal's whole part and every fraction numerator are **feminine** —
  the elided heads *ціла* and *частина* are —
  so they go through `UkrainianNumbers.feminine`, never `cardinal`.
  Everything counted goes through `UkrainianNumbers.agree`, the pack's single agreement device;
  the `ціла/цілих` split is a two-way use of that three-way helper rather than a second device.
- The place name comes from the fraction digits' **string length**,
  so a leading zero survives with no special case:
  `3,05` is *три цілих п'ять сотих* and `3,40` is *три цілих сорок сотих*.
- The two правопис editions genuinely disagree on numerators 2–4:
  2019 §107 gives the genitive plural (`дві третіх`), 2007 §72 the nominative (`дві треті`),
  and both are in wide circulation — the same split shows up twice inside one UDHTU booklet.
  The current edition decides what is shown; the older form grades and is never marked wrong.
- `удвічі`/`утричі` mean *twofold*, a factor rather than a count of occasions,
  so accepting them would teach a conflation.
  `раза` is the genitive singular that belongs after *півтора* and after fractional quantities.
  `пів` is an indeclinable numeral requiring a following genitive noun (§36),
  so it never stands alone as an answer,
  and `одна третина` is wrong because the `-ин` suffix already carries the singularity.
- The default reach of 1–100 is load-bearing for Ukrainian ordinals rather than incidental:
  only the last word is ordinal up to there,
  but 24 000th is the single welded adjective `двадцятичотирьохтисячний`,
  which the last-word rule cannot produce.
- Every reading uses the ASCII apostrophe `U+0027`, matching `UkrainianNumbers.kt`.
  A `U+2019` slipping into a pack or a fixture
  silently fails every `п'ять`/`дев'ять` comparison.

Sources: [Український правопис 2019 §107](https://slovnyk.ua/pravopys.php?prav_par=107)
· [the 2007 §72 text still in circulation](https://pravopys.net/sections/72/)
· ДВНЗ УДХТУ, [«Числівник»](https://udhtu.edu.ua/wp-content/uploads/2017/08/cb7b5dcf87b7fe87daaf74e8ede427f3.pdf)
(the decimal reading, the fraction rule, the full ordinal table, the noun-agreement rule)
· [НУШ grade-5 maths](https://www.miyklas.com.ua/p/matematika-nush-serednya-shkola/5-klas/drobovi-chisla-i-diyi-z-nimi-428886/desiatkovii-drib-zapis-desiatkovikh-drobiv-429028/re-02cf5d0d-4ee1-491b-bbc9-f8d8ee2b5ef8)
(the accepted `і` between the halves)
· [uk.wikipedia, Кома](https://uk.wikipedia.org/wiki/Кома_(розділовий_знак))
(reads its own example without saying *кома*)
· [goroh.pp.ua, раз](https://www.goroh.pp.ua/Слововживання/раз)
· [відсоток](https://goroh.pp.ua/Слововживання/відсоток)
· [onlinecorrector, раза](https://onlinecorrector.com.ua/раза/)
· [Правопис 2019 §36 on пів](https://webpen.com.ua/pages/Morphology_and_spelling/orthography_words_with_piv-poly.html).

## Still unverified

- **Swahili's negative word order.**
  `hasi` is an invariable adjective, so nothing blocks it before the numeral,
  and every corpus instance of a spelled-out negative *value* puts it first —
  but the only such corpus is a LibreTexts translation of unverified provenance
  (it leaves "integers" untranslated mid-sentence).
  Everything else is attributive *namba hasi*, which does not settle the bare-numeral case.
  It wants a native check, and the reference page is where a wrong answer would be most public.
  The mirror order grades meanwhile,
  so a learner applying the ordinary noun-adjective rule is accepted either way.
- **Swahili `mara moja` for 1×** is genuinely ambiguous:
  its commonest everyday sense is "immediately".
  A multiplicative floor of 2 for sw would settle it,
  but `FormLimits` carries no per-form numeric range and adding one is a ladder change,
  so the ambiguity is drilled rather than invented around.
- **Which Italian multiplicative leads.**
  `ventun volte` is the apocope the grammars prescribe before a noun and `ventuno volte` is current beside it;
  both grade, and the choice of which one the reveal teaches rests on that prescription
  rather than on a frequency count, so it wants a native check.
- **Ukrainian's missing `кома` register** is an argument from absence of evidence.
  The positive claim it rests on — that `цілих/десятих` is the reading — is unanimous;
  the negative one is only as good as the sweep that found nothing,
  and a single attested source would reopen it.
