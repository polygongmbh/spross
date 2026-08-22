# Catalog layout restructure

> Delete this file once the restructure has landed.

The complaint this answers, in the owner's words:
"mixed in with the vocab including an area called `language` is a separate folder `languages`,
the countries for the atlas drill, and the audio as well as the alphabet."

`catalog/` root holds 36 directories today: 30 card areas and six that are not areas
(`alphabet/`, `audio/`, `countries/`, `docs/`, `drills/`, `languages/`),
plus `areas.json`, `languages.json` and `README.md`.
Nothing in a listing says which is which,
and `language/` (the area), `languages/` (learner-facing language names)
and `languages.json` (per-language app metadata) collide on one word —
a collision `catalog/docs/languages.md` already carries a "not to be confused with" paragraph to survive.

## 1. Recommended layout

```
catalog/
  README.md
  docs/                 # one file per topic below, unchanged
  areas.json            # ordered groups → ordered areas: the catalog's table of contents
  languages.json        # which languages exist + their app metadata
  areas/                # the 30 card areas, incl. the area `language/`
    <area>/{concepts.json, <lang>.json}
  alphabet/<lang>.json
  countries/{atlas.json, <lang>.json}
  drills/{frames.json, <lang>.json}
  language-names/<lang>.json     # was languages/
  audio/<lang>/…        # generated, stays put
```

Root goes from 39 entries to ten: two manifests, a README, `docs/`, and six content directories —
each of the six a distinct topic that already owns a `catalog/docs/*.md` file.

### Why `areas/` and not a nested `drills/`

The set that grows without bound is the areas — 30 today, more with every content series.
The non-vocab set is four registries and has been stable.
Nesting the unbounded side is what keeps the root readable a year from now;
nesting the bounded side leaves adding an area still adding a root entry.

Rejected: folding `alphabet/` and `countries/` under `drills/`.
Three reasons, each checkable.
`catalog/docs/alphabet.md` opens by calling the alphabet "the letter sheets **the reference screen renders**
and the letter drill samples from" — it is reference content with a drill attached, and `drills/alphabet/` would misname it.
`drills/` today means specifically sentence frames (`catalog/docs/drills.md`), so admitting siblings
would force `frames.json` and `drills/<lang>.json` down into `drills/frames/` to stay coherent — more churn, one more renamed topic.
And `docs/dates-drill-plan.md:256` already commits the next drill to
"a new top-level folder, a sibling of `alphabet/`, `countries/` and `drills/`";
nesting would invalidate an in-flight plan for no gain the `areas/` move does not already deliver.

### Why the directory is called `areas/`

The domain word for what is in it is *area*, everywhere: `areas.json`, `CatalogArea`,
`catalog.areaNames`, `README.md`'s "one folder per area", the Box screen's area rows.
`vocab/` or `words/` would mint a fourth word for one concept,
and `words/` is additionally false — areas hold phrases and idioms too.

The one objection: a listing then shows `areas.json` beside `areas/`,
the shape of the very clash being fixed.
It is not the same clash. `languages.json` and `languages/` name **different subjects**
(app metadata vs. learner-visible content), which is why the doc needs a disambiguating paragraph.
`areas.json` and `areas/` name **one subject**: the manifest and the thing it indexes,
the same pattern `countries/atlas.json` and `drills/frames.json` already use one level down.

Designated fallback, if the adjacency is still unwanted: rename the directory `vocab/`
and change nothing else in this plan — the move table, the code sites and the commit split are identical.

### Why `areas.json` stays at root

It is not a per-area file. It carries the ordered **groups** with their own per-language titles —
cross-area structure the areas themselves never see (`kern/docs/reports.md`, `BoxView.swift:41`).
Burying cross-area structure inside `areas/` would file it wrong.

It is also the catalog's entry point in three places:
`Catalog.load` reads it first, and `RealCatalogTestSupport.kt:15` locates the repo's catalog
by walking up for `catalog/areas.json`.
Keeping it at root means that locator needs **no** change, which removes the single most
fragile edit the restructure would otherwise carry.

Rejected: `areas/index.json`. Consistent with the atlas/frames pattern, but it buries the group titles
and costs the locator, `catalog-move.py:233` and `audio-catalog.py:125` for a cosmetic gain.

### Why `languages/` becomes `language-names/`

The content is what each language calls the languages, in inflected forms,
and the kern names it exactly that: `LanguageName`, `languageNames`, `LanguageNames.resolve`,
`CatalogParser.parseLanguageNames`. The directory should say the same word the type does.

Rejected: `names/` — `countries/<lang>.json` holds names too, so `names/` would be a lie about scope.
Rejected: `exonyms/` — precise for what German calls Swahili, wrong for what German calls German (an endonym),
and jargon besides.
Rejected: merging it with `countries/` under one `names/` parent.
They read through different sources on purpose: language names are TRACKED
(they land inside joined card text, so an edit must restamp — `Catalog.kt:506-511`)
while country names go through the RAW source (drill-only, must never restamp — `Catalog.kt:493-503`).
A shared folder would hide a distinction the loader comments spell out.

After the move, `catalog/areas/language/` and `catalog/languages.json` never appear in one listing.

### Why `audio/` stays at root

It is generated, it is 3,603 files, and five separate places key on the literal string `catalog/audio/`
to mean "not hand-authored":
`scripts/catalog-format.py:41` (the GENERATED sentinel), `kern/build.gradle.kts:72`
(`exclude("audio/**")` from the jvmTest input tree), `scripts/hooks/pre-commit:41`
(a commit of only mp3s skips the format gate), `CatalogAudioLintTest.kt:21`, and `audio-catalog.py:316,384,400`.
Moving it costs 3,603 renames and five edits to buy nothing: it is already the one unambiguous folder.

`catalog-format.py` needs **no** change under this plan — it globs `catalog/**/*.json` recursively
and excludes only `catalog/audio/`, so it is layout-agnostic as long as audio stays put.

## 2. Move table

Directory-level; content bytes are untouched, so `catalog-format.py --check` holds throughout by construction.

| source | destination | files |
|---|---|---|
| `catalog/languages/<lang>.json` (8) | `catalog/language-names/<lang>.json` | 8 |
| `catalog/<area>/` × 30 (admin, basics, bath, bedroom, body, city, colors, connectors, conversation, degree, desk, doctor, food, greetings, hall, health, idioms, kitchen, language, living, nature, organization, people, qualities, questions, school, time, transport, verbs, work) | `catalog/areas/<area>/` | 270 |
| everything else | unchanged | — |

278 files, 31 `git mv` invocations.

**Generated by a one-shot shell sequence, not a codemod.**
The repo's rule is that a codemod worth writing is committed and carries `--check`;
this migration has no repeat sites and would be dead code the day after it runs,
so it fails the first half of the rule and should not be written.
The durable `--check` already exists and gets **strengthened instead**:
`CatalogLintTest.everyAreaFolderIsRegisteredInTheManifest` (`CatalogLintTest.kt:55-62`)
changes from listing the catalog root to listing `areas/`, and gains the second half of the rule —
that no directory **outside** `areas/` carries a `concepts.json`.
That is the permanent gate that the layout holds, in the home this repo keeps layout rules.

`catalog-move.py` is not the vehicle either: it moves **concepts between areas**, a different operation.
It only needs its area root retargeted (§3).

```sh
git mv catalog/languages catalog/language-names
mkdir catalog/areas
for a in $(python3 -c "import json;print(' '.join(a['area'] for g in json.load(open('catalog/areas.json')) for a in g['areas']))"); do
  git mv "catalog/$a" "catalog/areas/$a"
done
```

Deriving the area list from `areas.json` rather than from `ls` is what proves no non-area folder is swept in;
the strengthened lint then proves the reverse from the disk side.

**Provable no-op**: dump the joined concept set before and after and diff.
Same concepts, same card ids, same joins — this is the check the recent area splits used, and the same one applies.
No proposed move touches a slug: every rename is a **directory** rename,
and area slugs are directory names one level deeper, unchanged.

## 3. Code sites

Kern (`kern/src/commonMain/kotlin/net/spross/kern/catalog/Catalog.kt`):
- `:471` `"$name/concepts.json"` → `"areas/$name/concepts.json"`
- `:484` `"$name/$lang.json"` → `"areas/$name/$lang.json"`
- `:509` `"languages/$lang.json"` → `"language-names/$lang.json"`
- `:467, :495-499, :516, :522, :528-532` unchanged.

The brief's premise that `Catalog.load` is the only path-composing site is **false**:
`AudioManifest.kt:83` composes `"audio/$language/${recording.file}"`,
the catalog-relative path both platforms resolve against the bundle
(`AppModel+Audio.swift:19-25`, `Pronouncer.kt:261-266`).
Audio not moving is what keeps that second site out of the diff.

`CatalogSource` itself (`CatalogSource.kt:4-8`) and both implementations need **no** change —
they take an arbitrary relative path:
`BundleCatalogSource` (`AppModel.swift:395-407`) appends to the folder-reference URL,
`AssetCatalogSource.kt:9-14` prefixes `catalog/`.

Kern tests:
- `CatalogLintTest.kt:56-57` — list `areas/`, plus the new "no `concepts.json` outside `areas/`" assertion; `:303` message string.
- `CatalogLanguageNamesLintTest.kt:32` (`File(RealCatalog.root, "languages")`) and its nine `"languages/…"` message strings (`:33,35,37,38,55,59,78,87,102,111`).
- `RealCatalogTestSupport.kt:15,19` — **unchanged**, because `areas.json` stays at root.
- `AlphabetLintTest.kt:31`, `CountryAtlasLintTest.kt:32`, `CatalogAudioLintTest.kt:21` — **unchanged**.
- In-memory fixture keys (`MapCatalogSource` is keyed by the exact path `load` asks for),
  41 literals over six files — 32 area paths and 9 `languages/` paths:
  `Fixture.kt` (13 + 3, the `names` map at `:72-81`), `CatalogFixtureTest.kt` (5 + 6),
  `LanguageChoicesTest.kt` (5), `BoxBrowserTest.kt` (4), `GreetingTests.kt` (4), `CoveredSourcesTest.kt` (1).
  `AlphabetFixture.kt`, `AlphabetFixtureTest.kt` and `LetterDrillAvailabilityTest.kt` carry only
  `alphabet/` and `drills/` keys and are unchanged.
  Mechanical: area-prefixed keys gain `areas/`, `"languages/` becomes `"language-names/`.

Scripts:
- `scripts/catalog-move.py` — add an areas root beside `CATALOG` and use it at `:100, :107, :108, :109, :148, :150, :203`; `:233` (`areas.json`) unchanged.
- `scripts/audio-catalog.py` — `:129, :130, :134` gain the `areas` segment; `:125` (`areas.json`) unchanged.
- `scripts/catalog-format.py` — no change.
- `scripts/hooks/pre-commit:41` — no change (`^catalog/` still matches; the `^catalog/audio/` carve-out still holds).

Build:
- `project.yml:35-37` — no change; `catalog` is a folder reference, so the tree shape is invisible to it.
- `android/build.gradle.kts:130-134` — no change; the sync copies the whole tree.
- `kern/build.gradle.kts:72` — no change; `fileTree("catalog") { exclude("audio/**") }` is shape-independent.

App code: none. No Swift or Kotlin file outside the kern names a catalog sub-path other than `audio/…`.

## 4. Docs

- `catalog/README.md` — the Layout block (§ Layout) and the pointer list (§ end).
  The Layout block is **already stale**: it lists neither `alphabet/` nor `countries/`. Fix that here.
- `catalog/docs/languages.md` → renamed `catalog/docs/language-names.md`;
  title, the `languages/<lang>.json` path lines, and the "not to be confused with `languages.json`" paragraph,
  which shrinks to a single pointer once the names no longer collide.
- `catalog/docs/authoring.md` — one path (`catalog/idioms/`).
- `catalog/docs/alphabet.md`, `audio.md`, `countries.md`, `drills.md` — unchanged.
- `kern/docs/catalog.md` — the `catalog/languages/` bullets and the area-path lines (`:7, :47, :59, :69, :74, :100`).
- `docs/backlog.md:110` — `catalog/languages/*.json` (`:116`'s `catalog/drills/*.json` is unchanged).
- `docs/dates-drill-plan.md:10, :627, :632` — `catalog/time/concepts.json` → `catalog/areas/time/…`;
  `:256`'s "top-level sibling" claim stays true and needs no edit.
- `docs/website.md:49`, `web/site.js:7`, `kern/docs/audio.md`, `docs/audio-licensing.md` — unchanged (`languages.json` and `audio/` do not move).
- `CHANGELOG.md` — no entry. Nothing user-facing changes; `docs/distribution.md` governs.

## 5. Commits

Two independent commits, either order, plus the doc sweep folded into each.
Neither depends on the other.

**Commit A** — `refactor(catalog): the language-name tables leave the "languages" collision behind`
8 moves, `Catalog.kt:509`, `CatalogLanguageNamesLintTest`, `CatalogLintTest:303`,
the 9 `Fixture`/`CatalogFixtureTest` name keys, `catalog/docs/languages.md` → `language-names.md`,
`catalog/README.md`, `kern/docs/catalog.md`, `docs/backlog.md`.

**Commit B** — `refactor(catalog): the 30 card areas move under areas/`
270 moves, `Catalog.kt:471,484`, the strengthened `CatalogLintTest`, 32 fixture literals,
`catalog-move.py`, `audio-catalog.py`, `catalog/README.md`, `catalog/docs/authoring.md`,
`kern/docs/catalog.md`, `docs/dates-drill-plan.md`.

**Commit B cannot be split further.** `Catalog.load` composes one path shape for every area,
so a half-moved tree does not load — every test red, both apps at `.catalogMissing`.
Splitting would require a transitional loader that tries both paths,
which is more code and a temporary rule to remember and remove: worse than one large-but-mechanical commit.
For the same reason, neither commit may be split into "move the files" and "update the paths".

Gate per commit: `./gradlew :kern:jvmTest`.
`kern/build.gradle.kts:72` declares `catalog/` (minus audio) as a jvmTest input,
so a content move **is** tracked and `--rerun-tasks` is not needed here —
note that `../CLAUDE.md`'s "Gradle does not track `app/catalog/`" caveat looks stale against that line;
worth confirming and correcting separately, not in this series.
Run the iOS build and one launch once, after the last commit (§6 risk 3).

## 6. Risks

1. **A concurrent catalog edit.** A 278-file rename rebases badly against content edits to the same files
   (they surface as add/delete pairs, not conflicts, and a resolution can silently drop the edit).
   Land this on a quiet tree, from a clean `git status`, and never combine a move with a content edit.
   This is the top risk, not a theoretical one — the catalog is under active editing.
2. **The fingerprint changes.** `FingerprintingSource.read` folds the **path** as well as the content
   (`CatalogParser.kt:16`, and `:28`'s comment says so outright: "separates path/content segments so moves can't alias").
   So a pure move changes `catalog.fingerprint`, every installed box rejoins once on launch,
   and any running session recomposes.
   Benign — `BoxEngine.rejoin` keeps every schedule, queue entry and stat, and card ids are unchanged —
   but it means the no-op claim is provable at the **concept-set and card-id** level, not at the fingerprint level. Say so in the commit.
3. **The iOS folder reference.** `project.yml:35-37` emits one reference, `*.xcodeproj` is gitignored and
   regenerated by xcodegen, so there is nothing to update — but the resource copy phase has a habit of
   leaving deleted paths behind in an incrementally built bundle, which would present as a catalog that
   still loads from stale files. Run `scripts/run-sim.sh --clean` once after commit B and confirm no `.catalogMissing`.
4. **The Android sync's README exclusion.** `android/build.gradle.kts:120` is `from(sourceDir) { exclude("README.md") }`.
   The restructure must therefore introduce **no nested README** — keep every catalog doc in `catalog/docs/`,
   which is where they already are. Whether that Ant-style pattern is root-anchored or matches at any depth
   was not verified by running Gradle; if a nested README is ever added, check with
   `./gradlew :android:syncDebugCatalogAssets` and `find` over the task output before trusting either reading.
   (Note in passing: `catalog/docs/` and `catalog/README.md` ship inside both apps today.
   Excluding them would shrink the APK and the bundle, but it changes shipped bytes and belongs in its own commit.)
5. **Lint tests that hardcode a directory.** Five do:
   `CatalogLintTest.kt:56`, `CatalogLanguageNamesLintTest.kt:32`, `AlphabetLintTest.kt:31`,
   `CountryAtlasLintTest.kt:32`, `CatalogAudioLintTest.kt:21`.
   All four registry lints guard with `assertTrue(files.isNotEmpty(), …)`,
   so a missed rename fails loudly rather than passing vacuously — the failure mode here is safe.
6. **Fixture drift.** The 41 fixture literals are the largest code surface and the easiest to half-finish.
   A missed key makes the fixture file simply absent, which several tests treat as legal coverage
   (an absent `drills/`, an unauthored language) — so a miss can look green.
   Mitigate by rewriting them with one `sed` per pattern over the whole test tree, not by hand.

## 7. What this makes easier

- **The dates drill** (`docs/dates-drill-plan.md`): its `catalog/dates/` lands among four
  drill and reference registries instead of 31st in an alphabetical list of areas.
  Its later step (`:627-635`), which moves weekday and month names between `time/` and `dates/`,
  becomes a visible move between two different **kinds** of directory rather than between two lookalike siblings.
- **Adding an area** stops adding a root entry. `catalog-move.py --create`, the lint,
  and any future per-area contribution flow (`catalog/README.md` anticipates crowdsourcing) all anchor on one root.
- **Path-scoped tooling becomes expressible.** `^catalog/areas/` in the pre-commit hook,
  in a CODEOWNERS-style split, or in a review checklist currently has to be spelled as
  "everything except these six folders" — the reason the hook carves out audio by name today.
- **Trimming what ships**: excluding non-parsed files from the APK and the bundle becomes one pattern
  over a two-level tree instead of an enumeration that goes stale with every new area.
- **The next reader.** The root listing becomes a ten-line answer to "what is this catalog made of",
  and every content directory in it has a `catalog/docs/*.md` behind it.
