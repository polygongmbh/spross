# Number forms — what each language reads
What each language reads for the numbers drill's seven forms beyond the cardinal (negative, decimal, percentage, multiplicative, fraction, ordinal, price) and the source that decided it.
Neighbors: drawing and prompting `../kern/docs/trainer.md`, grading `../kern/docs/grading.md`, a date's numeral `date-readings.md`.

The code is one `<Lang>Forms.kt` per language
under `../kern/src/commonMain/kotlin/net/spross/kern/trainer/`,
each declaring that pack's `FormLimits`;
the canonical and refused readings below are pinned by `Numbers<Language>FormsTests`,
one spec file per language.

## How to read an entry

- **Canonical** is what the reveal shows — `formReading`'s first element,
  and the only reading a learner is ever taught.
- **Also graded** is accepted and never shown:
  a correct answer in a register the app does not choose to teach.
- **Refused** is a reading a learner will genuinely reach for that must grade *wrong*,
  because accepting it would teach the error its source names.
  Refusals are listed only where they exist.

## Reach

| | Forms drilled | Fraction denominators | Ordinals | Decimal mark | Price tag |
|---|---|---|---|---|---|
| de | all seven | 2–12 | 1–100 | `,` | `3,50 €` |
| en | all seven | 2–12 | 1–100 | `.` | `$3.50` |
| eo | all seven | 2–12 | 1–100 | `,` | `3,50 €` |
| es | all seven | 2–12 | **1–12** | `,` | `3,50 €` |
| fr | all seven | 2–12 | 1–100 | `,` | `3,50 €` |
| it | all seven | 2–12 | 1–100 | `,` | `3,50 €` |
| sw | six — **no ordinal** | **2–4** | — | `.` | `TSh 3500`, **whole sums** |
| uk | all seven | 2–12 | 1–100 | `,` | `45,50 грн` |

The ladder intersects its Sprosse with the pack's reach,
so a form a language cannot read is never drawn.
An exclusion costs the learner nothing but the Sprosse
they would otherwise have spent on an invention.

**A price is drawn in the currency the language's speakers price in**, and its tag is part of the prompt:
the euro for de, eo, es, fr and it,
the US dollar for en (the project's English is American),
the hryvnia for uk, written `грн` as DSTU 3582:2013 abbreviates it and price tags print it,
and the Tanzanian shilling for sw, the variety the Swahili sources here are Tanzanian for.
A shilling price is a round sum in steps of 50 with no cents:
*senti* exist on paper and nowhere in speech or on a tag.
The same rule runs through every language's minor unit —
a single-digit cent count is named (`drei Euro fünf Cent`),
and the bare "units cents" reading a till uses (`drei fünfzig`) grades only
where the cents run to two digits and the units stay below a hundred,
where it can be heard as nothing but a price.

The same reach feeds `NumberReadingIndex`, the drill's value check
(`../kern/docs/grading.md`): every reading a pack can be asked is indexed
back to its value through `NumberFormsAnswerSpace`, so a pack owes the index
nothing beyond honest `FormLimits` — a form read outside them is invisible
to the check and a slip there stays a forgiven typo.

## German

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `minus sieben` | — |
| Decimal | `drei Komma vier fünf` | the run-together `drei Komma fünfundvierzig` |
| Percent | `ein Prozent`, `einhundert Prozent` | `hundert Prozent` |
| Multiplicative | `dreimal` | `drei Mal`, `hundertmal` |
| Fraction | `ein Drittel`, `ein halb` | `einhalb`, `die Hälfte`, `ein Siebentel` |
| Ordinal | `zwanzigste` | `-er` / `-en` / `-es`, `siebente`, `hundertste` |
| Price | `drei Euro fünfzig`, `ein Euro`, `fünfzig Cent` | `drei Euro (und) fünfzig Cent`, `drei fünfzig`, `eins fünfzig` |

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

- **Euro and Cent take no plural after a numeral** (`zwei Euro`), and both are nouns,
  so the count before them is the attributive `ein` — while the cents AFTER the Euro stand alone
  and keep `eins` (`ein Euro eins`), as does the bare till reading `eins fünfzig`.

Sources: [Duden, Zahlwörter und ihre Schreibung](https://www.duden.de/sprachwissen/sprachratgeber/Zahlw%C3%B6rter-und-ihre-Schreibung)
· [Duden, mal/Mal](https://www.duden.de/sprachwissen/sprachratgeber/malMal)
· [Duden, Schreibung der Ordnungszahlen](https://www.duden.de/sprachwissen/sprachratgeber/Schreibung-der-Ordnungszahlen)
· [Duden „siebte"](https://www.duden.de/rechtschreibung/siebte) vs [DWDS „siebente"](https://www.dwds.de/wb/siebente)
· [Wikipedia, Zahlwort](https://de.wikipedia.org/wiki/Zahlwort) (fraction noun = ordinal stem + -el)
· [Wiktionary, Zweitel](https://de.wiktionary.org/wiki/Zweitel)
· [Duden, Euro](https://www.duden.de/rechtschreibung/Euro) (no plural after a numeral).

## English

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `minus seven` | `negative seven` |
| Decimal | `three point four five` | `oh` for a zero digit, `nought` and a dropped whole-part zero |
| Percent | `forty-five percent` | `per cent`, `a hundred percent`, bare `hundred percent` |
| Multiplicative | `once`, `twice`, `three times` | `one time`, `two times`, `thrice` |
| Fraction | `one half`, `one quarter`, `two thirds` | `a third`, `one fourth`, bare `half`/`quarter`, the hyphenated `two-thirds` |
| Ordinal | `twenty-first` | `twenty first`, `a hundredth` |
| Price | `three dollars and fifty cents`, `one dollar` | `three dollars fifty cents`, `three fifty`, `a dollar` |

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

- **A price is the full American reading**, `three dollars and fifty cents`.
  The elliptical `three dollars fifty` is British and Australian rather than American,
  so it does not grade; the till's `three fifty` does.

Sources: [Wikipedia, English numerals](https://en.wikipedia.org/wiki/English_numerals)
(the point-then-digits rule, the ordinal/partitive identity, `thrice`)
· [Wiktionary, per cent](https://en.wiktionary.org/wiki/per_cent)
· [Names for the number 0 in English](https://en.wikipedia.org/wiki/Names_for_the_number_0_in_English)
· [Cambridge Dictionary blog, talking about money](https://dictionaryblog.cambridge.org/2015/03/11/three-for-a-quid-talking-about-money) (the British ellipsis).

## Esperanto

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `minus sep` | — | `malplus sep`, `negativa sep` |
| Decimal | `tri komo kvar kvin` | the run-together `tri komo kvardek kvin` | — |
| Percent | `dudek kvin procentoj` | `elcento` / `elcentoj` | `dudek kvin procento` |
| Multiplicative | `tri fojojn` | `trifoje` | `tri fojoj`, `trioble` |
| Fraction | `kvarono`, `du trionoj`, `dek-duono` | an explicit `unu kvarono` | `dek duono` |
| Ordinal | `unua`, `dudek-unua` | the x-system `nauxa` | `dudek unua` |
| Price | `tri eŭroj kaj kvindek cendoj`, `unu eŭro` | `tri eŭroj kvindek (cendoj)` | — |

Esperanto is the one pack that names no reach of its own,
and the empty `FormLimits` is the claim rather than an omission:
`-ono` and `-a` are productive over every numeral,
so nothing runs out at a denominator or a hundredth rank the way it does in de, es and sw.

- **Two spelling rules decide every table cell**, and both are PMEG's.
  Composing: *"Dekoj kaj centoj kunmetiĝas al unu vorto: dudek, tridek, ducent, tricent k.t.p.
  Ĉio alia estu skribata kiel apartaj vortoj, ankaŭ miloj"* —
  so `sepdek` and `naŭcent` close up while `dek unu`, `cent unu` and `du mil` stay apart.
  Deriving: a numeral that takes an ending closes up again,
  *"oni tamen povas aŭ devas kunskribi, eventuale kun dividostrekoj"* —
  hence `dudek-unua` and `dek-duono`.
- The two rules point opposite ways, and the comparison pipeline is why that costs nothing:
  it DELETES the hyphen, so the fully closed-up `dudekunua` a writer may prefer grades exact
  without being emitted, while the spaced `dudek unua` — the spelling the deriving rule rules out —
  is one space from the reading and books amber with the correction shown.
  A welded `dumil` is corrected the same way, which is why it is not accepted:
  *"Tion, kion oni elparolas kiel apartajn vortojn, oni ne skribu kiel unu vorton."*
- **The x-system twin is emitted, not left to the typo budget.**
  `naux` sits two edits from `naŭ` (one substitution and one insertion) and would grade wrong,
  and a keyboard without `ŭ` is the ordinary case rather than an edge one.
  It rides behind every form word, so `minus naux` and `nauxa` grade like the bare cardinal does.
- `miliono` and `miliardo` are NOUNS: `unu miliono`, `du milionoj`,
  and before what they count they take `da` (*unu miliono da homoj*) —
  which is what keeps a counted-noun frame away from the numbers slot (`../catalog/phrases/README.md`).
  `cent` and `mil` are numerals and take no `unu`.
- `nul` is the numeral and `nulo` the noun; a bare 0 is written both ways, so both grade.
- **What the app names as the word each form adds is an AFFIX here** — `-ono` for the fraction,
  `-a` for the ordinal — because the derivation strips the cardinal out of the reading
  and Esperanto's whole answer is the ending. That is the honest hint and also the teaching point.
- `-obl-` is a factor (*trioble* = threefold), not a count of occasions,
  so it is refused for the same reason Ukrainian refuses `утричі`.
  `elcento` is the other way round: PIV registers it beside `procento` as the better-built word,
  and usage prefers `procento` — so it grades and is never shown.
- **`ses` and `sep` are the language's one minimal pair**, and Esperanto's regularity multiplies it:
  the pair comes back inside every ten, ordinal, `-ono` noun and `-foje` adverb built on six or seven.
  The value check catches the family by that one substitution rather than by a list:
  every reading built on six or seven is indexed to its value,
  so one typed for the other is refused as another number.

- **`eŭro` and `cendo` are counted nouns and pluralize** (`tri eŭroj`, `kvindek cendoj`).
  `cendo` is the generic minor unit of any currency; the specific `eŭrocendo` exists
  but is not needed where the tag already says euro.

Sources: [PMEG, Nombraj vortetoj — Formoj](https://bertilow.com/pmeg/gramatiko/nombroj/vortetoj/formoj.html)
(both spelling rules, verbatim)
· [PMEG, Miksitaj nombroj](https://bertilow.com/pmeg/gramatiko/nombroj/miksitaj.html)
(`unu komo kvin milionoj da homoj`, and `miliono` as a noun taking `da`)
· [PMEG, Matematikaj esprimoj](https://bertilow.com/pmeg/gramatiko/nombroj/matematiko.html)
(`minus` as the sign, and `-oble` as the factor family)
· [Wiktionary, elcento](https://en.wiktionary.org/wiki/elcento) (PIV registers it beside `procento`).

## Spanish

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `menos siete` | — | — |
| Decimal | `tres coma cuatro cinco` | `punto` for the mark, the run-together `cuarenta y cinco` | — |
| Percent | `veintiuno por ciento` | `cien por cien`, `ciento por ciento` (100 % only) | `veintiún por ciento`, `porciento` |
| Multiplicative | `una vez`, `veintiuna veces` | — | `un vez`, `veintiún veces` |
| Fraction | `un tercio`, `dos tercios` | `una tercera parte`, `la mitad`, `medio`, `un undécimo` for `un onceavo` | — |
| Ordinal | `undécimo` | feminine `-a`, `decimoprimero`, `décimo primero` | `onceavo` as an ordinal, bare `primer`/`tercer` |
| Price | `tres euros con cincuenta`, `veintiún euros`, `un céntimo` | `tres euros cincuenta`, `… con/y cincuenta céntimos`, `tres con cincuenta` | `veintiuno euros` |

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

- **`euro` and `céntimo` are masculine nouns, so a count before either apocopates** —
  `un euro`, `veintiún euros` — and `veintiuno euros` is the error that keeps it from grading.
  The cents after `con` stand alone and keep the full cardinal (`tres euros con veintiuno`).

Sources: [RAE, veintiuna personas / veintiuno por ciento](https://www.rae.es/espanol-al-dia/veintiuna-personas-veintiuno-por-ciento)
· [DPD, ordinales](https://www.rae.es/dpd/ordinales)
· [DPD, fraccionarios](https://www.rae.es/dpd/fraccionarios)
· [RAE, los números decimales y el separador decimal](https://www.rae.es/ortograf%C3%ADa/los-n%C3%BAmeros-decimales-y-el-separador-decimal)
· [RAE, la expresión de los porcentajes](https://www.rae.es/ortograf%C3%ADa/la-expresi%C3%B3n-de-los-porcentajes)
· [Nueva gramática, numerales ordinales](https://www.rae.es/gram%C3%A1tica/sintaxis/numerales-ordinales-i-aspectos-l%C3%A9xicos-y-morfol%C3%B3gicos).

## French

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `moins sept` | — | — |
| Decimal | `trois virgule quatre cinq` | the run-together `trois virgule quarante-cinq` | `trois point sept` |
| Percent | `quarante-cinq pour cent` | the regional decades (`septante pour cent`) | `pourcent` |
| Multiplicative | `une fois`, `vingt et une fois` | — | `un fois`, `vingt et un fois` |
| Fraction | `un tiers`, `trois quarts`, `cinq douzièmes` | `demi`, `la moitié`, `une demie` | `un troisième`, `un quatrième` |
| Ordinal | `premier`, `vingt et unième`, `quatre-vingt-dixième` | `première`, `second`/`seconde` | bare `unième` |
| Price | `trois euros cinquante`, `quatre-vingts euros`, `cinquante centimes` | `trois euros (et) cinquante centimes`, `trois cinquante` | — |

Every reading above also grades in the two spellings the cardinal has —
see the spelling rule below — so `moins quarante cinq` and `vingt-et-un pour cent` are correct answers.

- **70, 80 and 90 are counted on twenty**, and standard French is what the reference teaches:
  `soixante-dix` (60+10), `quatre-vingts` (4×20), `quatre-vingt-dix` (4×20+10).
  The Belgian and Swiss `septante` and `nonante` and the Swiss `huitante` grade beside them
  with their regular compounds (`septante-deux`, `nonante-neuf`, `huitante et un`).
  **`octante` is left out**: regionally near-extinct, and an accepted-but-dead form
  teaches a register nobody uses — the `thrice` rule, inverted.
- **The canonical spelling is the traditional orthography**,
  because that is what dictionaries, schoolbooks and published French still print
  and the reference table teaches what a learner will meet:
  a hyphen inside a compound below a hundred, spaces around `cent` and `mille`,
  and `et` instead of a hyphen where `et` appears (`vingt et un`, `mille neuf cent quatre-vingts`).
  The 1990-rectified all-hyphen spelling (`vingt-et-un`, `mille-neuf-cent-quatre-vingts`) grades beside it,
  and so does the **fully spaced twin** of both.
  That last one is not cosmetic: the comparison pipeline DELETES hyphens rather than spacing them,
  so `quatre-vingt-dix` is one word and `quatre vingt dix` is three,
  and a learner who spaces a compound would otherwise book a right answer amber.
  The 1990 hyphens tie a run of numerals together and stop at `million`/`milliard`,
  which are nouns rather than numeral adjectives.
- **`et` reaches 21, 31, 41, 51, 61 and 71 and stops there** —
  `quatre-vingt-un` and `quatre-vingt-onze` take none.
- **The plural -s of a multiplied `vingt` or `cent` falls before another NUMERAL and stands before a NOUN:**
  `quatre-vingts` but `quatre-vingt-deux` and `quatre-vingt mille`,
  `deux cents` but `deux cent un` and `deux cent mille` — while `quatre-vingts millions`
  and `deux cents millions` keep it, because `million` is a noun and not a numeral.
  `mille` never inflects; `million` and `milliard` are nouns and pluralize, and take `un` where `mille` does not.
- **Years** read as the plain cardinal (`mille neuf cent soixante-dix-huit`),
  with the hundred-counting `dix-neuf cent soixante-dix-huit` and the date spelling `mil neuf cent …` accepted.
  `mil` belongs to dates of the Christian era, so it grades for 1001–1999 and nowhere else.
- **`fois` is feminine**, so a count ending in one agrees with it:
  `une fois`, `vingt et une fois`, `quatre-vingt-une fois`.
  The masculine forms are the error the drill exists to catch and never grade.
  The same agreement is what makes the clock's minute count `deux heures vingt et une`.
- **A fraction is the ordinal noun**, with thirds and quarters suppletive (`un tiers`, `trois quarts`)
  and `-ième` from a fifth on (`cinq douzièmes`); `tiers` already ends in -s and takes no plural mark.
  Reading 1/3 as `un troisième` is the mistake the suppletion exists to teach, so it grades wrong.
  A half is `un demi`, with the feminine noun `la moitié` beside it;
  since 1 ≤ n < d, d == 2 forces n == 1, so no plural of `demi` is reachable.
- **Ordinals reach the drill's own 100** rather than stopping early:
  `-ième` is fully productive and lands on the LAST segment of the cardinal,
  so every value derives — `premier` is the only suppletive form,
  the plural mark of a multiplied `vingt` goes with it (`quatre-vingts` → `quatre-vingtième`)
  while the -s of `trois` is part of the word (`troisième`),
  and `cinq`/`neuf` shift for the sound (`cinquième`, `neuvième`).
  `unième` never stands alone — it exists only inside a compound — so a bare `unième` is refused,
  and `second`/`seconde` belong to a series of exactly two and grade without being taught.
- **The decimal mark is named `virgule`.**
  Digit-by-digit leads and the run-together reading of the fractional part grades beside it,
  as it does in German and Spanish; it is suppressed on a leading zero, where it would name a different number.

- **`euro` and `centime` take the plural -s**, and a multiplied `vingt`/`cent` keeps its own
  before them because a noun follows: `quatre-vingts euros`, `deux cents euros`.

Sources: [Académie française, Questions de langue](https://www.academie-francaise.fr/questions-de-langue)
(the `vingt`/`cent` agreement, the invariable `mille`, `mil` in dates, the 1990 hyphen rule)
· [Vitrine linguistique de l'OQLF](https://vitrinelinguistique.oqlf.gouv.qc.ca/)
(writing numbers out, the two hyphen systems)
· Grevisse & Goosse, *Le bon usage*, «Les numéraux» (the vigesimal decades, `et` at 21–71, the fraction nouns)
· [fr.wikipedia, Noms des nombres en français](https://fr.wikipedia.org/wiki/Noms_des_nombres_en_fran%C3%A7ais)
· [fr.wiktionary, septante](https://fr.wiktionary.org/wiki/septante)
· [nonante](https://fr.wiktionary.org/wiki/nonante)
· [huitante](https://fr.wiktionary.org/wiki/huitante)
· [octante](https://fr.wiktionary.org/wiki/octante) (marked regional and dated — the reason it is not accepted).

## Italian

| Form | Canonical | Also graded | Refused |
|---|---|---|---|
| Negative | `meno sette` | — | — |
| Decimal | `tre virgola quattro cinque` | the run-together `tre virgola quarantacinque` | `punto` for the mark |
| Percent | `ventuno per cento` | — | `ventun per cento`, `percento` |
| Multiplicative | `una volta`, `ventun volte` | `ventuno volte` | `uno volta`, `doppio` |
| Fraction | `un terzo`, `due terzi`, `un mezzo` | `mezzo`, `la metà`, `metà` | — |
| Ordinal | `ventunesimo`, `ventitreesimo` | the feminine `-a` | `ventitresimo`, `ventisesimo` |
| Price | `tre euro e cinquanta`, `ventun euro`, `un centesimo` | `tre euro e cinquanta centesimi`, `tre euro cinquanta`, `tre e cinquanta`, `ventuno euro` | — |

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
  the drill's value check refuses it (`../kern/docs/grading.md`),
  as it does `ventesimo` ↔ `centesimo` in the forms space.
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

- **`euro` is invariable** (`due euro`) and `centesimo` is not;
  before either a count ending in `uno` apocopates as it does before `volte`
  (`ventun euro`, with `ventuno euro` beside it).

Sources: [Treccani, *La grammatica italiana*, «numerali»](https://www.treccani.it/enciclopedia/numerali_(La-grammatica-italiana)/)
· [Treccani, *La grammatica italiana*, «aggettivi numerali»](https://www.treccani.it/enciclopedia/aggettivi-numerali_(La-grammatica-italiana)/)
· [Treccani, Vocabolario, «volta»](https://www.treccani.it/vocabolario/volta/)
· [Accademia della Crusca, consulenza linguistica](https://accademiadellacrusca.it/it/consulenza)
· [Crusca, elisione e troncamento](https://accademiadellacrusca.it/it/consulenza/elisione-e-troncamento-nellitaliano-contemporaneo/174) (`ventun euro`).

## Swahili

| Form | Canonical | Also graded |
|---|---|---|
| Negative | `hasi saba` | `saba hasi`, `minus saba` |
| Decimal | `tatu nukta saba` | `pointi` for the mark |
| Percent | `asilimia arobaini na tano` | `arobaini na tano kwa mia` |
| Multiplicative | `mara tatu` | `maradufu` (2 only) |
| Fraction | `nusu`, `theluthi`, `robo tatu` | `thuluthi`, an explicit `moja`, `sehemu moja ya tatu` |
| Ordinal | *excluded* | — |
| Price | `shilingi elfu tatu na mia tano` | the `na`-less `shilingi elfu tatu mia tano` |

Every reading composes over `SwahiliNumbers.acceptedVariants`, never over `cardinal` alone,
so the `na`-less spelling speakers routinely use
grades behind `hasi`/`asilimia`/`mara` exactly as it does in the plain drill.
Decimal digits are read through `cardinal` so a zero comes out `sifuri`
rather than the empty string the digit table holds.

**Cardinals 1–8 concord by noun class.**
1, 2, 3, 4, 5 and 8 are Bantu stems that behave like adjectives
and take the counted noun's class prefix; 6, 7 and 9 are Arabic loans and never agree.
`SwahiliConcord` carries the two classes a shipped frame counts —
KI-VI (`ki-`/`vi-`: *kiti kimoja*, *viti viwili*)
and JI-MA (bare/`ma-`: *jicho moja*, *macho mawili*).
The N-class needs no entry: its own concord is what `SwahiliNumbers` already spells,
which is why *sahani mbili* has been right all along —
`mbili` is not a citation form but N-class's output,
the nasal prefix mutating the bare stem `-wili`,
so every other class builds from `-wili` and never from `mbili`.
That is the contrast the frames teach: *viti viwili* against *sahani mbili*.
**Only the trailing ones-word agrees**, whatever the magnitude:
*vitabu mia moja na kimoja* (101) takes the SINGULAR prefix because its final digit is 1,
while a numeral multiplying *mia*/*elfu* stays bare — it agrees with those, not with the noun.
Readings past 9999 are left unconcorded on purpose; the sources part ways there.
A frame opts in with `swahiliNounClass` (`catalog/phrases/README.md`);
the fuller noun-class table and the source excerpts live outside the repo
in `../../data/reference/grammar-sw.md`.

**Ordinals are excluded, structurally.**
A Swahili ordinal is `-a kwanza`,
and that leading dash is a required associative concord slot the counted noun fills —
*mwanafunzi **wa** kwanza*, *kitabu **cha** pili*, *duka **la** tatu*.
A bare `20.` supplies no noun, so any prefix would be an invention shown to the learner as fact;
that every published source cites ordinals with the slot still empty
is the lexicographers saying the same thing.
Ordinals arrive with a noun-bearing frame or not at all.
The cardinals' `swahiliNounClass` does not reach them —
it prefixes a numeral stem, where an ordinal needs the frame itself to carry the concord —
so the ordinal frame stays open in `../catalog/backlog.md`.

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

**Prices are `shilingi` + the sum.** The head noun leads the noun phrase,
and *shilingi* is N-class, whose concord is the bare numeral — `shilingi elfu moja`.

Sources: Almasi et al., *Swahili Grammar for Introductory and Intermediate Levels* (UPA 2014),
[ch. 19](https://hist.hse.ru/data/2019/06/14/1486230008/19.%20More%20About%20Swahili%20Numbers.pdf)
— ordinal concord, fractions, the reversed percentage word order, `nukta`/`pointi`
· [ch. 18](https://hist.hse.ru/data/2019/06/14/1486229742/18.%20Numbers.pdf)
— the class prefixes (Table 18.1, p. 188), the bare-stem rule
and the single-digit-column rule (p. 187);
corroborated for the compound shapes by *Swahili Lehrbuch*
(Partnerschaft Tansania München, p. 13) and Wiktionary's declension tables
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
| Price | `сорок п'ять гривень п'ятдесят копійок`, `одна гривня`, `дві копійки` | `сорок п'ять гривень п'ятдесят` | `двадцять один гривня` |

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

- **`гривня` and `копійка` are both feminine**, so both counts go through
  `UkrainianNumbers.feminine` and `agree`: `одна гривня`, `дві гривні`, `п'ять гривень`,
  and `двадцять один гривня` is the masculine error that must not grade.

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
· [Правопис 2019 §36 on пів](https://webpen.com.ua/pages/Morphology_and_spelling/orthography_words_with_piv-poly.html)
· [ZIB, the abbreviation грн](https://zib.com.ua/ua/130239-skorochennya_slova_grivnya_vid_leninskoi_spadschini_do_ameri.html) (DSTU 3582:2013).

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
