# Phone readings — how each language says a phone number
How the `phone` frame slot draws a number per answer language, groups it, and which readings grade, with the source that decided each.
Neighbors: the slot and its frames `../catalog/phrases/README.md`, numerals `number-forms.md`.

The code is `PhonePlans` in `../kern/src/commonMain/kotlin/net/spross/kern/trainer/PhoneNumbers.kt`,
one `PhonePlan` per language, pinned by `PhoneReadingsTests`.
A number is a mobile of the country whose speakers the pack teaches,
opening on a real network or area code with the rest drawn at random,
and written in that country's grouping — the grouping is part of what is taught.

A number is read in ONE style throughout:
a learner who says `oh` for one zero says it for all of them,
so the accepted set is one reading per style rather than every mix of them.
The digits themselves always grade, with and without the grouping.

| | Written | Canonical | Also graded |
|---|---|---|---|
| de | `0176 12345678` | `null eins sieben sechs, eins zwei drei vier fünf sechs sieben acht` | `zwo` for every `zwei`; the subscriber block in pairs (`…, zwölf, vierunddreißig, …`) |
| en | `212 555 0198` | `two one two, five five five, zero one nine eight` | `oh` for every zero |
| eo | `0612 345 678` | `nul ses unu du, tri kvar kvin, ses sep ok` | the noun `nulo`; the x-system `naux` |
| es | `612 345 678` | `seiscientos doce, trescientos cuarenta y cinco, seiscientos setenta y ocho` | every digit on its own |
| fr | `06 12 34 56 78` | `zéro six, douze, trente-quatre, cinquante-six, soixante-dix-huit` | the regional decades, the cardinal's other spellings |
| it | `347 123 4567` | `tre quattro sette, uno due tre, quattro cinque sei sette` | — |
| sw | `0712 345 678` | `sifuri saba moja mbili, tatu nne tano, sita saba nane` | — |
| uk | `067 123 45 67` | `нуль шістдесят сім, сто двадцять три, сорок п'ять, шістдесят сім` | every digit on its own |

- **A group read as its number speaks its leading zeros first**:
  French `05` is `zéro cinq`, Ukrainian `045` `нуль сорок п'ять`, Spanish `005` `cero cero cinco`.
- **German writes DIN 5008's unbroken subscriber block**, so the grouping never decides the pairs;
  digit by digit is canonical because the block can be any length a network gives out,
  and `zwo` is the telephone's own word for two, made to keep it apart from `drei`.
- **Spain groups a mobile in threes** and reads each group as the number it spells;
  the older `612 34 56 78` layout is less common for mobiles.
- **Italian reads every digit**, since its mobile numbers vary too much in length to pair reliably.
- **Esperanto has no country's plan**, so its number keeps the plain four-three-three shape and is read digit by digit,
  zero as the numeral `nul` with the noun `nulo` beside it (`number-forms.md` § Esperanto).

Sources: [workingoffice.de, DIN 5008 Telefonnummer](https://www.workingoffice.de/korrespondenz/din-5008/telefonnummer-richtig-schreiben/)
· [saberespractico.com, cómo escribir teléfonos](https://www.saberespractico.com/ortografia/como-escribir-telefonos/)
· [fr.wikipedia, Numéro de téléphone en France](https://fr.wikipedia.org/wiki/Plan_de_num%C3%A9rotation_t%C3%A9l%C3%A9phonique_en_France)
· [Wikipedia, North American Numbering Plan](https://en.wikipedia.org/wiki/North_American_Numbering_Plan)
· [Wikipedia, Telephone numbers in Ukraine](https://en.wikipedia.org/wiki/Telephone_numbers_in_Ukraine)
· [Wikipedia, Telephone numbers in Tanzania](https://en.wikipedia.org/wiki/Telephone_numbers_in_Tanzania)
· [Wikipedia, Telephone numbers in Italy](https://en.wikipedia.org/wiki/Telephone_numbers_in_Italy).
