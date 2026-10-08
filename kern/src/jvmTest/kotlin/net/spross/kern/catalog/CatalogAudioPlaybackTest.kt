package net.spross.kern.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The playback half of [CatalogAudioLintTest] over the REAL `catalog/audio/`:
 * the analysis index every pack carries, how clean the packs are,
 * and which languages the catalog admits to having a sound of their own.
 */
class CatalogAudioPlaybackTest {
    private val catalog get() = RealCatalog.catalog
    private val audioRoot = File(RealCatalog.root, "audio")

    /**
     * The index's RANGE needs no rule here: `AudioManifestParser` refuses a gain past ±20 dB
     * or a lead past 5 s outright, so a manifest carrying one never loads at all. What is
     * left to check is whether the numbers say what they were measured to say.
     *
     * The index exists BECAUSE the uk letters are quiet and start late (user ruling
     * 2026-08-01) — but its SIGN is a property of the pack it measured, never a rule: uk's
     * letters take a boost and a long lead skip, while de's letter names come out of the
     * same ordinary word recordings as its vocabulary and are ATTENUATED instead. What no
     * real pack produces is a whole alphabet measuring exactly 0 dB / 0 ms; that is the
     * analysis stage having been skipped, and the drill goes back to whispering a second
     * too late — which is exactly what nobody notices in a diff.
     */
    @Test
    fun everyLettersPackCarriesItsPlaybackIndex() {
        assertTrue(catalog.audio["uk"]?.letters?.isNotEmpty() == true, "uk ships no letter recordings")
        for ((lang, manifest) in catalog.audio) {
            if (manifest.letters.isEmpty()) continue
            assertTrue(
                manifest.letters.values.any { it.gain != 0.0 || it.leadMs > 0 },
                "audio/$lang: every letter measures 0 dB / 0 ms — the analysis stage did not run",
            )
        }
    }

    /**
     * How clean a pack is, as a DISTRIBUTION rather than a floor per file.
     *
     * A per-file minimum cannot be written honestly: some rows sit low because Commons has
     * nothing cleaner for those words, and a rule that fails the build over an unimprovable
     * file is a rule that gets suppressed. What a rebuild must not do is quietly undo the
     * sweep that removed the hiss — a whole pack sliding down, or the bad tail growing. Both
     * are visible in the shape and neither goes stale as content grows. Today: medians uk
     * 2.75 to de 3.08; worst tail under 1.8 es at 3.0%.
     *
     * `mos` changes no playback. It is carried purely so this can be asserted.
     */
    @Test
    fun noPackLosesItsRecordingQuality() {
        for ((lang, manifest) in catalog.audio) {
            val measured = (manifest.words.values + manifest.letters.values +
                manifest.texts.values + manifest.articles.values + manifest.calendar.values +
                manifest.countries.values)
                .map { it.mos }.filter { it != 0.0 }
            assertTrue(measured.size > 10, "audio/$lang: only ${measured.size} entries carry a mos")
            val median = measured.sorted()[measured.size / 2]
            assertTrue(median >= 2.6, "audio/$lang: median mos $median has fallen below 2.6")
            val poor = measured.count { it < 1.8 }
            assertTrue(
                poor * 100 <= measured.size * 5,
                "audio/$lang: $poor of ${measured.size} entries score under 1.8 — over 5%",
            )
        }
    }

    /**
     * The target IS the word packs' own median loudness, so half the word entries sit above
     * it and half below and the median gain is zero. A median that has drifted means the
     * manifests were generated against some other target than the one `ANALYSIS` records —
     * every pack would then be corrected toward a level no one chose.
     */
    @Test
    fun theWordPacksStayCenteredOnTheAnalysisTarget() {
        val gains = catalog.audio.values.flatMap { manifest -> manifest.words.values.map { it.gain } }.sorted()
        assertTrue(gains.isNotEmpty(), "no word recordings ship")
        val median = gains[gains.size / 2]
        assertTrue(median in -1.0..1.0, "word gains center on $median dB, not on the analysis target")
    }

    /**
     * `Catalog.hasRecordings` answers for exactly the packs that ship.
     *
     * That predicate decides what the audio setting may offer and whether a language has any
     * sound of its own, and it is a map lookup rather than a walk — which is only safe while
     * the map and the folders agree. Dropping a pack in registers a language, and this is
     * what says so out loud rather than leaving a segment that promises a sound nothing can
     * make (`docs/read-aloud.md`).
     */
    @Test
    fun everyShippedPackIsOneTheCatalogAdmitsTo() {
        val shipped = audioRoot.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "manifest.json").isFile }
            .map { it.name }
            .toSet()
        assertTrue(shipped.isNotEmpty(), "no packs ship at all — the scan is broken")
        for (lang in catalog.languages.keys) {
            assertEquals(
                lang in shipped,
                catalog.hasRecordings(lang),
                "hasRecordings disagrees with catalog/audio/ for $lang",
            )
        }
        for (lang in shipped) {
            assertTrue(lang in catalog.languages, "audio/$lang ships for a language nothing declares")
        }
    }
}
