package net.spross.kern.catalog

import net.spross.kern.model.nfcNormalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The prompt-collision half of [CatalogLintTest] — two words that reach the learner as the
 * same string. A sibling file because the pinned cross-area list is content, and content
 * grows: kept here it never pushes the rest of the catalog rules past the file budget.
 *
 * The three rules differ only in what they do about a collision: inside one area it is
 * unanswerable and forbidden, one concept pair colliding in two languages is an authoring
 * duplicate, and a cross-area single-language merge is legitimate and therefore pinned.
 */
class CatalogCollisionLintTest {
    private val catalog get() = RealCatalog.catalog

    /**
     * Prompt forms as the learner SEES them — text plus synonyms (both rotate as prompts),
     * NFC-folded. Case-SENSITIVE on purpose: `Husten`/`husten` and `jua`/`kujua` are real
     * visual distinctions that keep noun/verb homographs unambiguous.
     */
    private fun promptForms(raw: RawRealization): List<String> =
        (listOf(raw.text) + raw.synonyms).map { nfcNormalized(it).trim() }

    /** (lang, form) → concept ids sharing it, keeping only the genuine collisions. */
    private fun collisionClusters(): Map<Pair<String, String>, List<String>> {
        val byForm = mutableMapOf<Pair<String, String>, MutableList<String>>()
        for (area in catalog.areas) {
            for ((lang, words) in area.realizations) {
                for ((slug, raw) in words) {
                    for (form in promptForms(raw)) {
                        byForm.getOrPut(lang to form) { mutableListOf() } += "${area.name}/$slug"
                    }
                }
            }
        }
        return byForm.filterValues { it.size > 1 }
    }

    /**
     * A display-identical prompt INSIDE one area is unfixable at runtime: the engine's
     * disambiguator is the area label, which would be identical. Repick the word.
     */
    @Test
    fun noPromptCollisionWithinAnArea() {
        for ((key, ids) in collisionClusters()) {
            val (lang, form) = key
            val perArea = ids.groupBy { it.substringBefore('/') }.filterValues { it.size > 1 }
            assertTrue(perArea.isEmpty(), "$lang \"$form\": same-area collision ${perArea.values}")
        }
    }

    /**
     * One concept PAIR colliding in two languages is one meaning authored twice — unify it
     * (the `variantOf` ruling). Exception, when this fires: if de/en genuinely distinguish
     * the two and only both targets merge them, fix the imprecise realization instead
     * (uk relax/rest was `відпочивати` twice while de keeps entspannen/ausruhen apart).
     */
    @Test
    fun noConceptPairCollidesInTwoLanguages() {
        val langsByPair = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        for ((key, ids) in collisionClusters()) {
            val sorted = ids.sorted()
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    langsByPair.getOrPut(sorted[i] to sorted[j]) { mutableSetOf() } += key.first
                }
            }
        }
        // Reviewed 2026-08-15: weather/time is the Romance family merge (es tiempo,
        // fr temps, it tempo) — one linguistic fact per language, each pinned in
        // [crossAreaPromptCollisionsAreKnown], not the same meaning authored twice.
        // Re-realizing weather as meteo/météo would teach the forecast, not the weather.
        // Reviewed 2026-09-05: that-conj/what is fr `que` and uk `що` — in each language the
        // relative-conjunction and the object interrogative share one word, and de/en/eo/es/it/sw
        // keep them apart, so these are two linguistic facts, not one meaning authored twice.
        val reviewedPairs = mapOf(
            ("time/time" to "weather/weather") to setOf("es", "fr", "it"),
            ("connectors/that-conj" to "questions/what") to setOf("fr", "uk"),
        )
        val duplicated = langsByPair
            .mapValues { (pair, langs) -> langs - reviewedPairs[pair].orEmpty() }
            .filterValues { it.size > 1 }
        assertTrue(duplicated.isEmpty(), "same meaning authored twice: $duplicated")
    }

    /**
     * Cross-area, single-language collisions are legitimate target-language merges (Swahili
     * has one word where German has two) — tolerated at runtime, where the join sets
     * `promptAmbiguous` and the UI adds the area label to the produce prompt. Pinned so a
     * NEW one (adding `nature/river` beside `bedroom/pillow`, both sw `mto`) fails here
     * instead of silently shipping an unanswerable prompt.
     */
    @Test
    fun crossAreaPromptCollisionsAreKnown() {
        val actual = collisionClusters()
            .map { (key, ids) -> "${key.first} ${key.second}: ${ids.sorted().joinToString(", ")}" }
            .toSortedSet()
        assertEquals(
            sortedSetOf(
                // Reviewed 2026-08-29: de `Schiene` is the train rail AND the medical splint —
                // en/es/fr/it/uk all split the pair (rail/splint, riel/férula, rail/attelle,
                // rotaia/stecca, рейка/шина); sw has no splint card yet. There is no second
                // German word for either sense of "rigid guiding strip," so both stay.
                "de Schiene: illness/splint, transport/rail",
                // Reviewed 2026-08-29: de `Stunde` is the clock hour AND the school period —
                // en/sw both split it (hour/period, saa/kipindi); es/fr/it/uk fold `period`
                // into their `lesson` card instead (clase/classe/lezione/урок already cover
                // it), so `school/period` was deliberately authored de/en/sw-only and this
                // stays one-language.
                "de Stunde: school/period, time/hour",
                // Reviewed 2026-08-29: de `Stock` is the walking stick AND the building floor
                // — en/es/fr/it/uk all split the pair (stick/story, palo/piso, bâton/étage,
                // bastone/piano, палиця/поверх); sw has no shared word either (fimbo/ghorofa).
                // `Etage`/`Stockwerk` name only the floor sense and `Stab`/`Rute` skew formal
                // or archaic for the stick sense, so `Stock` stays the plain word for both.
                "de Stock: living/story, nature/stick",
                // Reviewed 2026-08-29: es `piso` is the apartment/flat AND the building floor
                // it sits on — de/en/eo/fr/it/sw/uk all split the pair (Wohnung/Stock,
                // apartment/story, apartamento/etaĝo, appartement/étage, appartamento/piano,
                // ghorofa ya kuishi/ghorofa, квартира/поверх). `apartamento` already carries
                // its own second sense (the small/vacation flat, see hall/apartment's own
                // note) and is not the everyday word either, so there is no honest repick.
                "es piso: hall/apartment, living/story",
                // Reviewed 2026-08-29: de `sich vorstellen` is "to introduce oneself" AND "to
                // imagine" — the infinitive citation doesn't distinguish the accusative
                // reflexive (Ich stelle mich vor) from the dative one (Ich stelle mir das
                // vor), so one phrase genuinely covers both German senses. en/es/fr/it/sw/uk
                // all split the pair (introduce oneself/imagine, presentarse/imaginar,
                // se présenter/imaginer, presentarsi/immaginare, kujitambulisha/kuwazia,
                // представитися/уявляти); `sich einbilden` skews toward a delusion and
                // `sich ausmalen` toward picturing something vividly, so neither is the
                // plain word a learner reaches for.
                "de sich vorstellen: verbs/to-imagine, work/to-introduce-oneself",
                // Reviewed 2026-08-23: `cold` is the illness AND the adjective — English has
                // one word where de/eo/es/fr/it/sw/uk all split it (Erkältung/kalt,
                // malvarmumo/malvarma, resfriado/frío, rhume/froid, raffreddore/freddo,
                // mafua/baridi, застуда/холодний). qualities/cold-adj exists because `hot`
                // needs its polar partner, and illness/cold is the illness a learner asks the
                // doctor about; the adjective carries the de note naming the second sense,
                // the ndege treatment.
                "en cold: illness/cold, qualities/cold-adj",
                // Reviewed 2026-09-05: en `that` is the demonstrative (das dort) AND the
                // conjunction (dass) — de/eo/es/fr/it/sw/uk all split the pair (das dort/dass,
                // tio/ke, eso/que, cela/que, quello/che, hiyo/kwamba, те/що). English has no
                // second word for either, so both stay.
                "en that: connectors/that-conj, questions/that",
                // Reviewed 2026-07-31: `el tiempo` is both Zeit and Wetter. de/en/sw/uk
                // all split it; `clima` is das Klima in Spain, so there is no alternative.
                // Reviewed 2026-08-23: `la dirección` is the direction AND the postal
                // address — de/en/eo/fr/it/sw/uk all split the pair (Richtung/Anschrift,
                // direkto/adreso, direction/adresse, direzione/indirizzo). `el sentido`
                // names only the direction of travel and `el rumbo` a heading, so there is
                // no honest alternative; directions/direction carries the de note naming
                // the second sense, the ndege treatment.
                "es dirección: admin/address, directions/direction",
                // Reviewed 2026-09-05: es `esperar` is to wait AND to hope — de/en/eo/fr/it/sw/uk
                // all split the pair (warten/hoffen, attendre/espérer, kusubiri/kutumaini).
                // `aguardar` for waiting is literary, so both stay one-language.
                "es esperar: emotions/to-hope, verbs/to-wait",
                "es tiempo: time/time, weather/weather",
                // Reviewed 2026-08-15: `le tableau` is the picture on the wall AND the
                // classroom board — genuine French polysemy, one word both areas need
                // as their first pick. Repicking picture as `cadre` would teach the
                // frame; the living/picture card carries a de note naming the second
                // sense, the ndege treatment. Every other language splits the pair,
                // so it stays one-language.
                // Reviewed 2026-08-23: `l'entrée` is the way into a building AND the
                // hallway you step into (and the starter on a menu) — de and en split it
                // (Eingang/Flur, entrance/hallway) and Italian does too, once
                // directions/entrance is `entrata` against hall/hallway `ingresso`, so this
                // stays one-language. `le hall` is a lobby and `le vestibule` is dated, so
                // repicking would teach the wrong register; the card carries the de note.
                "fr entrée: directions/entrance, hall/hallway",
                // Reviewed 2026-08-23: `frais` is fresh AND, in the plural, the fees —
                // de/en/eo/es/it/sw/uk all split the pair (frisch/Gebühr, fresco/tasa,
                // fresco/spesa). There is no second word for fresh in French, and `les
                // frais` is what an office actually charges, so both stay; market/fresh
                // carries the de note naming the second sense, the ndege treatment.
                "fr frais: admin/fee, market/fresh",
                // Reviewed 2026-09-05: fr `même` is gleich (le même) AND sogar — de/en/eo/es/it/sw/uk
                // all split the pair (gleich/sogar, same/even, sama/eĉ, igual/incluso, sawa/hata).
                // `pareil` for same skews colloquial and `voire` for even is written French, so both stay.
                "fr même: connectors/even, qualities/same",
                // Reviewed 2026-09-05: fr `que` is the conjunction dass AND, as a synonym on
                // questions/what, the object what — with uk `що` it is the one pair two languages
                // merge, and both merges are real polysemy, not one meaning authored twice
                // (reviewed in [noConceptPairCollidesInTwoLanguages]).
                "fr que: connectors/that-conj, questions/what",
                // Reviewed 2026-09-05: `liquide` is the adjective flüssig AND, as `argent
                // liquide`, the everyday word for cash — de/en/eo/es/it/uk all split the pair
                // (flüssig/Bargeld, liquid/cash, likva/kontanta mono). money/cash keeps it as a
                // synonym because it is what a French speaker says at the till; the adjective
                // has no other word at all.
                "fr liquide: money/cash, qualities/liquid",
                "fr tableau: living/picture, school/board",
                // Reviewed 2026-08-15: `le temps` is Zeit and Wetter alike — the same
                // Romance merge `es tiempo` and `it tempo` pin here; `la météo` names
                // the forecast and `le climat` the climate, so there is no honest
                // alternative. The concept pair is allowlisted in
                // [noConceptPairCollidesInTwoLanguages] as the reviewed family-wide merge.
                "fr temps: time/time, weather/weather",
                // Reviewed 2026-08-23: `molto` is both viel and sehr — Italian has one word
                // where de/en/eo/es/fr/sw/uk all split the quantity from the intensifier
                // (viel/sehr, mucho/muy, beaucoup/très, -ingi/sana). `assai` is the only
                // alternative for sehr and is literary, so repicking would teach a register
                // nobody speaks. degree/very and qualities/much stay two concepts because
                // every other language needs them to be; qualities/much's own it note
                // already names the second sense.
                "it molto: degree/very, qualities/much",
                // Reviewed 2026-08-29: `il piano` is the building floor AND a plan — de/en/eo/
                // es/fr/sw/uk all split the pair (Stock/Plan, story/plan, ghorofa/mpango,
                // поверх/план). `piano` is genuinely the everyday word for both ("al primo
                // piano" and "fare un piano"); `livello` for the floor sense reads like a
                // video-game level, not a building one.
                "it piano: living/story, organization/plan",
                // Reviewed 2026-09-05: it `portare` is mitbringen AND tragen — de/en/eo/es/fr/sw/uk
                // all split the pair (mitbringen/tragen, bring/carry, kuleta/kubeba); `trasportare`
                // is freight and `recare` is literary, so both stay.
                "it portare: admin/to-bring, verbs/to-carry",
                // Reviewed 2026-08-15: `perché` is warum and weil in one word — the
                // interrogative and the causal conjunction genuinely merge in Italian
                // (Perché non vieni? — Perché piove.). Every other language splits them,
                // so the pair stays one-language; repicking poiché/siccome for `because`
                // would teach a formal register no one answers a question in.
                "it perché: connectors/because, questions/why",
                // Reviewed 2026-08-15: `il tempo` is Zeit and Wetter alike — the same
                // Romance merge `es tiempo` pins above; `meteo` names the forecast and
                // `clima` the climate, so there is no honest alternative. The concept
                // pair is allowlisted in [noConceptPairCollidesInTwoLanguages] as the
                // reviewed family-wide merge.
                "it tempo: time/time, weather/weather",
                // Reviewed 2026-07-25: the textbook homonym, and the only entry here that
                // is NOT a merge — sw `mto` is two unrelated senses (river, pillow),
                // not one word covering two German ones. Same treatment either way.
                // Re-pathed 2026-08-04: `outside` split into transport/city/nature and
                // `river` landed in `nature` — the same pair, renamed, not a new one.
                // Reviewed 2026-08-23: `rahisi` is cheap AND easy — de/en/eo/es/fr/it/uk all
                // split the pair (billig/einfach, barato/fácil, economico/facile). `nafuu`
                // is the relief of a better price rather than a low one, so repicking would
                // teach the wrong word; money/cheap carries the de note naming the second
                // sense, the ndege treatment.
                "sw rahisi: money/cheap, qualities/easy",
                // Reviewed 2026-09-02: sw `mpaka` is the border AND the preposition "until" —
                // de/en/eo/es/fr/it/uk all split the pair (Grenze/bis, border/until,
                // limo/ĝis, frontera/hasta, frontière/jusqu'à, confine/fino a,
                // кордон/до). `hadi` is already `until`'s synonym rather than a second
                // word for the border, and `mpakani` is the locative, so there is no
                // honest repick on either side.
                // Reviewed 2026-09-05: sw `kulia` is the right-hand side AND to cry (ku-lia) —
                // a homonym, not a merge: de/en/eo/es/fr/it/uk all split it (rechts/weinen). The
                // area label tells them apart on produce, and the verb has no other everyday word.
                "sw kulia: emotions/to-cry, place/right",
                "sw mpaka: connectors/until, politics/border",
                "sw mto: bedroom/pillow, nature/river",
                // Reviewed 2026-08-04: sw `mwezi` is moon and month, exactly as uk `місяць`
                // is — so the moon is authored without uk, which keeps this to one language
                // and pinnable instead of the unfixable two-language pair.
                "sw mwezi: nature/moon, time/month",
                // Reviewed 2026-08-04: `ndege` is the only Swahili word for both bird and
                // aeroplane; de/en/es/uk all split them. The plane carries a de note so the
                // learner meets the second sense as a fact, not as a surprise.
                "sw ndege: animals/bird, transport/plane",
                // Reviewed 2026-08-04: `nyanya` is the ordinary word for grandmother and for
                // tomato alike, both of them the first word a learner needs in their area.
                // Repicking either would teach the rarer word for no gain.
                "sw nyanya: food/tomato, people/grandmother",
                // Reviewed 2026-09-05: uk `з` is with AND since (з учора) — de/en/eo/es/fr/it/sw
                // all split the pair (mit/seit, with/since, kun/ekde, con/desde, avec/depuis,
                // con/da, na/tangu). `від учора` is understood but not what a speaker says, so
                // since sits in time, where the label disambiguates, and both stay.
                "uk з: connectors/with, time/since",
                // Reviewed 2026-09-05: uk `що` is what AND the conjunction that — every other
                // language splits the pair (was/dass, what/that, kio/ke, qué/que, quoi/que,
                // che cosa/che, nini/kwamba). No second Ukrainian word for either, so both stay.
                "uk що: connectors/that-conj, questions/what",
            ),
            actual,
        )
    }
}
