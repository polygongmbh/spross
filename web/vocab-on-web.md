# Vocabulary training on the web — feasibility

Assessment of 2026-08-09, against `duolernen/app` @ `atlas` and `duolernen/app-website` @ `website`.
Question asked: how tough would it be to put more of the app — the vocabulary training — on the site,
so a visitor can try it without downloading anything?

Everything below is measured from the two repos.
Where a number is an estimate, the arithmetic is shown.

---

## 1. The short answer

The engine is ready and the payload is small — 213 kB of catalog JSON for one language pair,
38.5 kB gzipped, and no file I/O anywhere in the kern to port.
What is *not* ready is everything around it:
the produce/recognize turn is ~180 lines of Swift that kern does not own yet
(`docs/portability.md` lists it as unshipped move #2),
the review-loop UI is 2,556 lines of SwiftUI on iOS and ~1,100 of Compose on Android,
and pulling the box and store layers into the JS bundle drags `kotlinx-serialization` and `@js-joda/core` along —
roughly 150–200 kB gzipped where the drill costs 46 kB today.
A credible, honestly-composed vocabulary session on the web is **20–30 engineer-days**, not a week.
But the deeper problem is not cost: a spaced-repetition box is unobservable in a single browser visit,
so the thing the demo would spend a month building is the one thing a stranger cannot feel.
**Recommendation: build the 7–11 day "taste with the schedule visible" instead** (§4) —
a dozen curated cards through the real grader, plus a fast-forward that shows the box breathing over thirty days,
which is the argument the numbers drill cannot make and the app screenshot cannot either.

---

## 2. What makes the numbers drill easy and vocabulary hard

The drill is easy for four structural reasons, and vocabulary fails all four.

**It has no content.**
`Trainer` generates its own material from hard-coded language packs
(`kern/src/commonMain/kotlin/net/spross/kern/trainer/`, ~2,500 lines, all five languages compiled in).
Vocabulary needs the catalog: 818 concepts across 27 areas,
loaded by `Catalog.load(source)` through ~184 synchronous `read(path)` calls
(`kern/src/commonMain/kotlin/net/spross/kern/catalog/Catalog.kt:433-511`).

**It has no state.**
`NumbersDrill` holds one seeded `Random` and nothing else
(`kern/src/jsMain/kotlin/net/spross/kern/web/WebDrill.kt:40-49`).
A vocabulary session runs against `BoxState` —
config, the joined card map, the join stamp, one `CardScheduling` per card with its full review log,
the enqueued list, three day-keyed counter maps, and the learner's own words
(`kern/src/commonMain/kotlin/net/spross/kern/box/BoxState.kt:15-40`).

**It has no clock.**
Nothing in the trainer path reads or takes a time.
Every session entry point takes `nowEpochMillis` + `tzId`:
`SessionRun.reduce(state, intent, nowEpochMillis, tzId)`,
`SessionComposer.composeSession/composeRound`,
`BoxEngine.answer/endSession/today/statistics/growth`.

**Its answer is its own grading.**
The drill grades one generated reading against one accepted set.
A produce turn grades through `CatalogAnswerGrader`, which indexes *every card in the join*
so a typed word that is really another concept's answer is named rather than forgiven
(`kern/.../session/CatalogAnswerGrader.kt:61-74`).

Two further asymmetries decide the cost.

**The turn is not in the kern.**
`docs/portability.md` move #2, still unshipped:
> `session/Turn` — the produce/recognize turn: feedback state × revealed × typo × heardInstead × otherWord × retry,
> and which rating each branch fires (`SessionView+Produce.swift:26-316`, ~180 lines).

`SessionView+Produce.swift` (297 lines) is ~85 % logic, and several of its ratings are Swift literals,
not kern decisions — the ear-mode amber override says so in its own comment.
A web client is the **third** implementation of that machine.
`docs/portability.md` documents four real behavioural drifts that already opened between two clients;
a third is not a linear cost.

**The UI is the bulk.**
Review-loop SwiftUI, counted:

| Tier | Files | Lines |
|---|---:|---:|
| A — card, field, grading, container, summary, run reducer | 12 | 2,556 |
| B — summary visuals (tree stack, confetti, streak flame) | 9 | 1,695 |
| C — design atoms the loop pulls | 6 | 685 |
| D — model / bridge / persistence | 8 | 1,235 |
| Audio | 5 | 612 |
| **Total on the review-loop path** | **40** | **6,783** (47 % of App/Sources' 14,327) |

The largest single items are `Screens/SessionView.swift` (410, mostly a 17-`@State` machine),
`Design/VocabCardView.swift` (352, the hero card with its fixed-height and nothing-moves-on-reveal invariants),
`Screens/SessionView+Produce.swift` (297, near-pure logic),
`Design/AnswerInputView.swift` (287),
`Design/SessionCompletionView.swift` (250, on top of 1,695 lines of procedural tree drawing).

The best available yardstick is Android:
`android/src/main/kotlin/net/spross/app/` shipped a contract-conformant vocab loop in about **1,100 lines** —
`ui/ProduceCard.kt` 309, `ui/SessionScreen.kt` 189, `ui/Components.kt` 177, `AppModel.kt` 375 —
by dropping the write-it-out step, the 450 ms live-typed tier, and the celebration screen entirely.
Vanilla DOM/CSS with no framework runs more verbose than Compose, not less.

---

## 3. The facts, domain by domain

### 3.1 What a session needs handed to it

Entry points a web client would call, in order:

```
Catalog.load(CatalogSource)                    → Catalog          (~184 sync reads)
catalog.join(source, target)                   → List<Card>       (≤ 809 cards for en→de)
BoxEngine.bootstrap(cards, BoxConfig.product(), JoinStamp)
                                               → BoxState
SessionRun.idle(box) / reduce(state, intent, nowEpochMillis, tzId)
                                               → SessionReduction (state + effects)
presentationRole(cardId, reviewCount)          → Produce | Recognize
producePrompt / recognitionPromptForm / emojiCue / pronunciationCue
CatalogAnswerGrader(normalizer, cards).grade(input, card)
                                               → Match (Exact | Typo | OtherWord | Wrong)
Match.producedRating()                         → Rating?
SelfGrading.rating(verdict, elapsedMs, promptChars)
                                               → Rating
BoxEngine.isConsolidated / today / statistics / growth
StoreCodec.encode(state) / decode(json)        (only if persisting)
```

The presentation rules are the cheap part:
`presentationRole`, `recognitionPromptForm`, `emojiCue`, `producePrompt` are pure functions of
`(cardId, reviewCount, consolidated, audible)` and need no box at all
(`kern/.../model/Presentation.kt`).
A scripted demo gets all of them for free.

What is *not* in the kern, and must be written a third time
(`docs/portability.md`, plus the SwiftUI audit): auto-advance timing (450 ms live / 1200 ms explicit —
Android has only the 1200 tier, so the two clients already differ), focus policy,
live exact-match approval and its revocation, the retry-prefix priming string surgery,
the write-it-out step in full (`SessionView+Copy.swift`, 128 lines; Android has none),
the recall-clock boundaries, and article/plural/alternates rendering
(`Model/DisplayText.swift:102-154`, already written twice).

### 3.2 State and persistence

The persisted shape is `BoxDocument` (`kern/.../store/BoxDocument.kt`, 253 lines), schema version 1,
one JSON document per target language, deterministic sorted-key encoding via `StoreCodec`.
Per card: `cardId`, `addedAt`, `phase`, `stepIndex`, `memory{stability,difficulty}`, `due`, `lapses`,
`suspended`, and the whole `log` of `{date, rating, elapsedDays}`.

Sizing from that shape: a card shell is ~200 B of JSON, a log entry ~65 B.

- a 30-card demo box, ~2 answers each → **~10 kB**
- a 300-card box, ~8 answers each → **~215 kB**
- a 1,000-card box, ~10 answers each → **~850 kB**

`localStorage` (5 MB, synchronous, string-only) holds all three;
IndexedDB is the right home past the first.
Neither is a real problem — the real problem is what they mean.

A visitor who clears site data loses the box, and there is no recovery:
schedules are the only thing the document holds that is not re-derived,
apart from the learner's own words (`kern/README.md` §6 — "the only CONTENT the box document holds").
A second browser is a second box; there is no account, no sync, no export path into the phone app.
The repo's pre-production stance ("no live user data", `CLAUDE.md` Invariants)
means nothing has to be *migrated* — but it does not mean a visitor's twenty minutes are cheap to lose.
Persisting a web box is therefore not a technical decision, it is a promise:
once the site keeps progress, losing it is the site's fault.

### 3.3 Content payload

Text catalog, measured (`find catalog -name '*.json' -not -path './audio/*'`):

| Set | Files | Raw | gzip -9 |
|---|---:|---:|---:|
| Whole catalog JSON (no audio) | 184 | 500,978 B | 98,664 B |
| **One pair, en→de, everything `Catalog.load` probes** | **91** | **213,475 B** | **38,552 B** |
| + `audio/de/manifest.json` | 92 | 434,369 B | ~80 kB |

38.5 kB gzipped for a working pair is a non-issue —
it is smaller than the kern bundle already on the page.
One caveat: `CatalogSource.read` is **synchronous** and `fetch` is not,
so the browser must prefetch into a map first and hand kern a `MapCatalogSource`-style adapter.
That shape already exists in `kern/src/commonTest/.../Fixture.kt`; no kern change is needed.
Ninety-one requests over HTTP/2 is fine, one pre-concatenated envelope is better.

Audio is the opposite story. `catalog/audio/`: **2,069 mp3, 41.87 MiB**, all 64 kbps MPEG-1 layer III.

| Lang | Files | MiB |
|---|---:|---:|
| de | 580 | 11.82 |
| es | 498 | 9.99 |
| sw | 467 | 7.44 |
| uk | 524 | 12.62 |

Plus 824 kB of manifests (larger than the entire rest of the catalog; pure sha256/author/source provenance).
A page that must load fast cannot ship 11.8 MiB of German.
A subset is viable and obvious — twelve cards is ~250 kB at the 21 kB average —
but **only as a hand-picked subset, never as a re-encode**:
`docs/audio-licensing.md` §3 requires the bytes stay byte-identical Commons transcodes,
so transcoding to Opus or packing a sprite is an adaptation, breaks the sha256 gate,
and falsifies the "shipped unmodified" credit line.
41.87 MiB is a hard floor for the full set.

Two obligations travel with any recording served:
attribution for 1,964 of the 2,069 files (1,426 BY-SA, 538 BY; only 105 are CC0/PD),
derived from the manifests via `Catalog.audioCredits()` and never hand-kept (`audio-licensing.md` §2),
and share-alike on the BY-SA half.
Worth knowing the other way round, though:
`audio-licensing.md` §6.1 names a public un-DRM'd URL as the *mitigation on record*
for the App Store's FairPlay-vs-BY-SA problem, in exactly the per-language shape `catalog/audio/<lang>/` already has.
A web audio mirror has standalone legal value independent of any demo.

### 3.4 The clock, and what spacing means to someone who will not come back

`nowEpochMillis` + `tzId` as parameters is the single best thing about this port.
Nothing in kern reads a clock, so the browser can hand it *any* time it likes — including a fake one.
`Date.now()` and `Intl.DateTimeFormat().resolvedOptions().timeZone` cover the honest case in two lines.

The hard question is what a schedule is *for* in a session that will never have a tomorrow.
Trace it with the product calibration (`kern/README.md` §5):
one learning step at 2 minutes, desired retention 0.8, S0(Good) = 2.3065, interval = 3.316 × stability.
A visitor answering seven first sights Good sends every one of them away for ~7.6 days
before the page has been open five minutes.
`composeSession` then reports the day done —
nothing due, nothing returning within 12 h, and `workedARound` satisfied at 7 answers —
and the honest next screen says "nothing more right now" (`SessionComposer.composeSession:73-87`).
Only a missed card comes back, at two minutes, as the typed production attempt.

So a real session on the web demonstrates the *turn* — the prompt, the typo forgiveness, the reveal, the emoji cue —
and demonstrates nothing whatsoever about the box, which is the product.
"Each one comes back just before you would have lost it" is the site's own first feature bullet,
and it is precisely the claim a live session cannot show.

The inverse is the opportunity.
Because the clock is a parameter, a "fast-forward" is nearly free:
drive the same twelve cards through thirty simulated days by advancing `nowEpochMillis`,
answering with a plausible mix, and drawing the resulting `GrowthStage` rungs
(`Unscheduled → Learning → Fresh → Consolidated → Matured`, `kern/.../box/GrowthStage.kt`).
That is the one demo a native app *cannot* put in a screenshot,
it is genuine kern output rather than an animation,
and it costs days rather than weeks.

### 3.5 JS-export friction and bundle growth

The existing facade states the constraint in its own doc comment:
"One sampled task, narrowed to JS-clean types (**no Long, no List**)" (`WebDrill.kt:16`).
It takes `Int` and calls `.toLong()` internally, and returns `Array<String>` everywhere a `List` would appear.
What does not cross `@JsExport` cleanly for a session:

- `Long` — `nowEpochMillis` on every entry point, `SelfGrading.instantBudgetMs`, `elapsedMs`
- `List<T>` / `Map<K,V>` — `SessionPlan.queue`, `BoxState.scheduling`, `Realization.synonyms/variants/grammar`, `CardGrowth[]`
- sealed classes — `Match` (`Typo(corrected)`, `OtherWord(word, meanings)`), `SessionStep`, `SessionIntent`, `SessionEffect`
- data classes — exportable, but `copy`/`componentN` are not, and value semantics are what SwiftUI wanted, not JS
- `kotlin.time.Instant` on `CardScheduling.due/addedAt`
- `BoxState` itself must stay an opaque handle held Kotlin-side; it cannot be a JS object

None of that is a blocker — it is a facade, and the drill's 123 lines are the pattern.
Sized against it: the drill exports 6 entry points and 4 value types.
A session needs roughly 18–22 entry points and 8–10 value types
(`WebCatalog`, `WebSession`, `WebCard`, `WebSide`, `WebTurn`, `WebVerdict`, `WebSummary`, `WebRung`),
plus a `CatalogSource` adapter over a JS map.
**Estimate 450–700 lines of Kotlin**, 4–6× the drill's facade.

Bundle. Today `web/dist/kern.js` is **146,913 B raw / 45,978 B gzipped**,
and it contains *no* datetime and *no* serialization — dead-code elimination removed both
(`grep -c "js-joda\|kotlinx.serialization" dist/kern.js` → 0).
That changes the moment the box comes in:

| Added | Raw | gzip |
|---|---:|---:|
| current bundle (trainer + normalizer + model + stdlib) | 147 kB | 46 kB |
| box + session + fsrs + store + catalog (~4,700 more Kotlin lines at the current ~30 B/line) | +120–160 kB | +35–45 kB |
| `kotlinx-serialization-json` runtime (`JsonSupport.kt` parses every catalog file through it) | +150–200 kB | +35–45 kB |
| `@js-joda/core` 3.2.0 — a declared dependency of the JS target already, pulled in by `TimeZone.of(tzId)` in `box/Time.kt` | +194 kB | +42 kB |
| **projected** | **550–750 kB** | **150–200 kB** |

A 3.5–4.5× bundle for a marketing page is real but not disqualifying —
it is a lazily-loaded second tab, not the hero.
The unknown worth measuring first is js-joda:
`@js-joda/core` is in `kotlin-js-store/yarn.lock`, but **`@js-joda/timezone` is not**,
so whether `TimeZone.of("Europe/Berlin")` actually resolves in a browser is unverified.
Only `box/Time.kt`, `BoxEngine.kt`, `Statistics.kt` and `TodayReport.kt` touch `kotlinx.datetime` —
the FSRS layer is clean (`fsrs/FsrsScheduler.kt` imports nothing but `kotlin.math` and kern models).
A schedule-preview facade that avoids day keys therefore avoids js-joda entirely.

### 3.6 Audio and speech

The kern decides *what* to say and hands out a path; the app decides *whether* and *with what*.
`Catalog.pronunciation(lang, visibleForm)` returns a total `Pronunciation`,
where `recordingPath == null` **is** the fallback signal.
Recordings are keyed by the **form on screen**, never by concept
(`AudioManifest.kt:64-74` — exact NFC form first, then a normalized speech key),
so a rotated synonym is never answered with the canonical word,
and a speech-key collision with differing sha256 indexes to `null` rather than guessing.
The app then branches (`App/Sources/Audio/Pronouncer.swift:93`):
recording → `PronunciationPlayer` (AVAudioEngine + EQ, because `gain` reaches +20 dB and `AVAudioPlayer` only attenuates),
else `Speaker` (AVSpeechSynthesizer), else silence.

On the web, most of that maps:

| App mechanism | Web | Verdict |
|---|---|---|
| `AVSpeechSynthesizer` | `speechSynthesis` (already used in `web/site.js:475`) | works |
| `AVAudioUnitEQ.globalGain` ±20 dB | Web Audio `GainNode` | clean map — a bare `<audio>` is insufficient for the same reason `AVAudioPlayer` was |
| `scheduleSegment(startingFrame:)` for `lead` | `AudioBufferSourceNode.start(0, offset)` | works |
| `voice.quality` tiers → `VoiceUpgradeHint` | none — `SpeechSynthesisVoice` exposes name/lang/localService only | dropped |
| Google-TTS pinning for offline Swahili | none | **Swahili is silent on the web**, and 41 % of sw words have no recording |
| `AVAudioSession` `.ambient` vs `.playback` | none | the three-state read-aloud switch collapses; "follows the phone" is unimplementable |
| autoplay on reveal | blocked without a prior gesture | needs a one-time unlock tap; iOS Safari is strictest |
| `UIAccessibility.isVoiceOverRunning` veto | none — no web API reports a screen reader | the "never talk over the reader" rule and the auto-advance kill-switch both need an explicit user toggle instead |

TTS licensing is unchanged and fine:
`audio-licensing.md` §4's rule is "live synthesis yes, pre-rendered asset no",
and the Web Speech API has no synthesis-to-file path at all, so it satisfies that by construction.

---

## 4. Four options, costed

Effort is one engineer, familiar with this codebase, including copy, mobile layout, a11y and cross-browser QA.
Estimates assume the existing `web/site.js` run shell (372 lines) and palette (`site.css`, 484 lines) are reused.

### Option A — a scripted taste

**Visitor sees:** picks a pair, gets 10–12 curated cards.
First sight is recognition (target form, reveal, three self-grade buttons);
the same word returns as a typed produce turn with its emoji.
Real typo forgiveness, real "that's another word" naming, real reveal alternates.
A small summary, then the signup.

**Built:** a ~60–90 line `WebVocab` facade over `AnswerNormalizer` (vocab defaults: `articleLeniency = true`,
`maxTyposPerWord = null`) plus the four pure presentation functions, which need no box;
a build-time extraction script pulling N concepts and their emoji out of `catalog/<area>/`;
~350 lines JS and ~220 CSS on top of the existing shell.

**Dropped:** the box, the composer, FSRS, phrase unlock, growth, own words, persistence, recordings.

**Payload:** bundle unchanged at ~46 kB gz (no serialization, no datetime);
+2 kB of card JSON per pair; TTS only.

**Effort: 5–8 days.**
0.5 facade · 1 content extraction and curation · 2 card UI for both roles including article/plural/alternates ·
1 grading states (green / amber typo / other-word / wrong+retry) · 1.5 a11y, TTS, mobile, copy · 1 QA.

**Honest weakness:** it is a flashcard toy. It does not say why Spross rather than Anki.

### Option A+ — a taste with the schedule visible  ← recommended

**Visitor sees:** everything in A, plus after each answer the real interval the model just chose
("back in 8 days" — that is `Fsrs.intervalRawDays`, not a mock),
and a fast-forward strip that runs those twelve cards through thirty simulated days
and draws them climbing the growth rungs, leaves to blossom to fruit, in the site's own tree language.

**Built:** A, plus the facade reaches `FsrsScheduler`/`Fsrs` and a hand-rolled simulation loop
that advances `nowEpochMillis` — deliberately *not* `BoxEngine`, to keep `dayKey`/`TimeZone.of` and js-joda out.
Plus ~150 lines of SVG/canvas for the strip.

**Payload:** ~+25–40 kB raw / +8–12 kB gz. Still one small bundle.

**Effort: 7–11 days** (A + 2–3).

**Why it is the strongest for the money:** it demonstrates the differentiator —
that material *grows while it sits* — which neither the numbers drill nor a screenshot can,
and it does so with real engine output rather than a marketing animation.
And it cannot substitute for the app, because there is no box to keep.

### Option B — a real session, session-scoped, nothing persisted

**Visitor sees:** picks a pair, gets a genuinely composed round —
`SessionComposer` rules and all: seven first sights, pull-aheads to the floor, the day-done answer,
real self-grading with the instant budget, a missed word genuinely returning after two minutes,
a summary with true tallies. Closing the tab discards it.

**Built:**
- kern facade, 450–700 lines Kotlin, ~18–22 entry points (§3.5) — **4–6 d**
- catalog prefetch + envelope build step + `MapCatalogSource` adapter — **1 d**
- the turn state machine in JS (`docs/portability.md` move #2, ~180 lines Swift, ~85 % logic) — **3–4 d**,
  *or* land the kern move first: **4–6 d** with tests, but it pays back on iOS and Android
- session UI: card, field, both roles, reveal, self-grade, retry priming, progress, summary.
  Android's equivalent is ~675 lines of Compose; vanilla DOM/CSS runs longer,
  call it 1,100–1,400 lines JS + ~350 CSS — **6–8 d**
- audio: manifest subset, Web Audio gain/lead, TTS fallback, autoplay unlock, a credits page off `Catalog.audioCredits()` — **3–4 d**
- language picker, copy, a11y, cross-browser QA — **3 d**

**Dropped:** persistence, multiple days, own words, box browser, forest, widgets,
the write-it-out step, the celebration tree.

**Payload:** ~150–200 kB gz bundle + 38.5 kB catalog + whatever audio subset ships.

**Effort: 20–30 days**, i.e. 4–6 weeks, and that is with a contingency already inside the range
for `@JsExport` friction and the js-joda unknown.

**Honest weakness:** four to six weeks buys a demo that still cannot show the spacing (§3.4),
and creates a third client for a turn machine that has already drifted between two.

### Option C — a full web app

Adds persistence (IndexedDB + `StoreCodec`), multiple days, own words, the box browser, the forest,
statistics, credits, internationalization (iOS carries 4,299 lines of `Localizable.xcstrings`),
and — as soon as progress is kept — an account and sync story, or an explicit "this box lives in this browser only" promise.

**Effort: 45–70 days** on top of nothing (B + 25–40), plus permanent maintenance:
every kern change acquires a third consumer, and `docs/portability.md` records four behavioural drifts
that opened between the two that exist now.

**This is a second product, not a marketing page.** Cost it as one, if it is ever wanted.

---

## 5. Recommendation

**Build Option A+. Do not build B for the website.**

The site's job is to make a stranger want the app and leave an email address.
Measured against that job:

- **A+ argues the differentiator.** The visible claim — "a few new words a day, spaced so they stay" —
  is invisible inside any single sitting (§3.4). The fast-forward is the only cheap way to show it,
  and the clock-as-a-parameter design makes it nearly free.
- **B risks arguing against the download.** A real session that works in the browser
  invites the conclusion "this is a web flashcard app" —
  at which point the visitor either bookmarks a page whose box they will lose (bad),
  or asks for accounts and sync (worse: that is Option C, arriving by accident).
  The download's whole case is that the box lives on the phone, keeps growing, and shows up in a widget.
  The web version should make that case, not compete with it.
- **B also demos the app at its weakest.** Seven cards, no forest, no recordings for most words,
  Swahili silent, no celebration screen, and a "nothing more right now" after four minutes.
  The app is better than that; a stranger judging Spross by a stripped web session judges it unfairly.
- **A+ leaves the door open.** Its facade is a strict subset of B's,
  its card UI is B's card UI, and its catalog extraction is B's build step.
  Nothing in A+ has to be thrown away if B is ever justified.

One thing worth doing regardless of which option is chosen:
publish the recordings per language at a plain HTTPS URL.
`audio-licensing.md` §6.1 already names that as the mitigation on record for the BY-SA/FairPlay conflict,
and `catalog/audio/<lang>/` is already the right shape.
That is a deployment task, not a demo, and it is worth about a day.

---

## 6. Risks and unknowns

**Needs measurement before B or C could be honestly costed:**

1. **`@js-joda/timezone` is absent from `kotlin-js-store/yarn.lock`.**
   Whether `TimeZone.of("Europe/Berlin")` resolves in a browser under kotlinx-datetime 0.8.0 is unverified.
   If it does not, every day-keyed path (`dayKey`, `endOfTomorrow`, `endSession`, `today`, `statistics`)
   needs either a tz-free variant in kern or a 42 kB-gzipped extra dependency.
2. **Actual bundle delta.** §3.5's 150–200 kB gz is arithmetic, not a build.
3. **`ULong` FNV over ~184 catalog files** in Kotlin/JS's emulated 64-bit arithmetic
   (`FingerprintingSource`) — correctness is fine, speed is unmeasured.

**Owner decisions:**

4. **Does the site want to keep anything?** The moment a web box persists, losing it is the site's fault,
   and "export to the app" is a feature that does not exist. Say no explicitly, or budget for accounts.
5. **Is the catalog an asset to protect?** A working web session ships 213 kB of en/de content in the clear.
6. **How much audio may the page cost?** A twelve-card subset is ~250 kB; a full German pack is 11.8 MiB;
   re-encoding to shrink it is a licence breach (§3.3).
7. **Swahili in a browser is mute** for the 41 % of words with no recording.
   Either restrict the web demo to de/es/uk, or accept a silent language on the site
   that speaks on Android.

**Standing costs, whichever is built:**

8. **Attribution ships with the audio** — 1,964 of 2,069 files, generated from the manifests, never hand-kept.
9. **A third client for the turn machine.** Land `session/Turn` in kern before, not after,
   if anything past A+ is ever built.
10. **No screen-reader detection on the web.** The VoiceOver autoplay veto and the auto-advance kill-switch
    have no equivalent and become user-visible toggles.

---

## 7. The first concrete step

**A one-day spike that measures the two numbers everything else hangs on.**
Throwaway branch in `duolernen/app`, nothing to keep:

1. Add ~40 lines to `kern/src/jsMain/kotlin/net/spross/kern/web/`
   exposing `AnswerNormalizer` with vocab defaults and a `FsrsScheduler` review preview.
   Run `:kern:jsBrowserDistribution`. Record raw and gzipped bytes against today's 146,913 / 45,978.
2. Add ~30 more lines that touch `BoxEngine.answer` — hence `dayKey` and `TimeZone.of` —
   rebuild, record again, and open the bundle in a browser to check
   `TimeZone.of("Europe/Berlin")` actually resolves rather than throwing.

Those two deltas decide the shape of everything above.
If (1) is +10 kB gz and (2) is +80 kB gz with a working timezone, B stays a real option at the stated cost.
If (2) throws, kern needs a tz-free day-key path before B can be costed at all —
and A+, which never calls it, is the only shape available this quarter.

**Then, if green: ship A+ for en→de only**, twelve curated cards,
as a second tab beside the existing "How far can you count?" section, and instrument it.
The kill criterion is not technical: **if a vocabulary taste does not lift signup conversion
above what the numbers drill already delivers, no amount of Option B is justified.**
That is a two-week answer to a question that would otherwise cost two months.
