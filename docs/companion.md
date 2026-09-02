# The companion — an AI beside the box (plan)

**A plan, not a contract.** Nothing here ships yet.
When it does, the engine half moves to `kern/README.md`,
the screen half to `design.md`, and this file is deleted.

The box teaches words one at a time and never asks the learner to use one in a sentence.
That is the deliberate shape of it — breadth of exposure over depth on any one word —
and it leaves a gap the app should not try to fill itself:
somewhere the learner has to actually SAY the thing.
An AI chat is good at exactly that and bad at everything the box does well,
so the two belong beside each other rather than inside one another.

## The app ships no AI

No key, no account, no network call, no bundled model, no monthly bill.
The app's whole contribution is a TEXT: what this learner knows, what they are learning,
and how a conversation partner should behave toward that.
The learner pastes it into whichever assistant they already pay for, or none.

That is not a compromise on the way to a real integration — it is the design.
An in-app chat would put a running cost on a free offline app,
tie the feature to one vendor's terms and one vendor's outage,
and land the app in the same undifferentiated middle
as every other language app that bolted a chat window on.
The brief is the differentiated part: nobody else knows these 214 words.

## What the box hands over

One text, four blocks. The tiers come straight off `GrowthStage` — no new taxonomy:

| Block | Sprossen | Form | Why |
|---|---|---|---|
| **May use freely** | `Consolidated`, `Matured` | bare target forms, grouped by area | the floor the conversation stands on; the assistant already knows what they mean, so glosses would only cost room |
| **Work these in** | `Learning`, `Fresh`, `Relearning` | `target = source` pairs | the words the box is currently spending its effort on; the conversation is where they get used |
| **New words** | `Growth.newCandidates` | `target (source)` pairs, ~15 | what the box will introduce NEXT — see below |
| **Held back** | `Unscheduled`, `Queued` | not listed at all | listing 800 words to forbid them is the whole catalog with a "no" on it |

`Suspended` is absent from every block:
a word taken out of rotation is one the learner does not want to meet.
Own words carry both halves in the free block, since they are the one entry an assistant may not know.

**The new-word allowance is drawn from the box's own next-up list.**
`Growth.newCandidates(state, limit)` already answers "what comes in next round",
so the handful of new words the conversation is allowed to spend
are the ones the box is about to teach anyway.
The learner then meets them a second time, in the app, two days later — with a hook already set.
Letting the assistant pick freely was the alternative:
it reads more natural and it teaches into a void, since nothing follows it up.
That is the one decision here that makes the feature a LOOP instead of a side quest.

### The brief, worked

```
Spross — vocabulary brief · 2026-09-02
Anna knows German, is learning Swahili.
214 words consolidated, 31 in play.

You are a patient conversation partner. Speak Swahili.
Explain in German, and only when the learner stalls or asks.
This app teaches WORDS, not grammar — assume no instruction in tense, case or agreement.
Short concrete sentences, one question per turn.
Correct at most one mistake per turn, in German, after answering what was actually said.
Never list vocabulary back at the learner. Talk.

MAY USE FREELY (214)
Im Bad: bafu, kuoga, sabuni, taulo, mswaki, kunawa …
In der Küche: jiko, sufuria, kisu, sahani …

WORK THESE IN — aim for eight, prefer them to synonyms (31)
kusubiri = warten · ratiba = Fahrplan · kuchelewa = sich verspäten …

NEW — at most three per turn, from this list only, glossed in German on first use (15)
abiria (Fahrgast) · tikiti (Fahrkarte) · kituo (Haltestelle) …

When the learner writes "genug", close with the new words they actually used:

    ```spross
    abiria = Fahrgast
    ```
```

**English, and in kern.** Same ruling as `Feedback`, for the same reason:
its reader is a machine, not the learner's device, so it is an interchange format
rather than chrome, one dialect rather than two, and it lives where both apps read it from.
The learner's own two languages are named inside it; the instructions around them are not translated.

**Size.** The catalog holds 1,033 concepts,
so the largest brief this can ever produce — every word consolidated — is about 7 KB.
That is the ceiling, not the typical case, and it is nothing.

## The way back in

The closing block is the point of the exercise.
A conversation surfaces words the catalog does not have — the learner's job, their street, their kid's school —
and those are exactly what `OwnWord` exists to hold.

So the brief asks for them in a fenced `spross` block, `target = source` per line,
and the Box grows a **Paste** action beside "own words" that reads them back.
Kern parses (interchange format, same argument as above), the app never imports silently:
the paste opens the parsed lines as a checklist, the learner keeps what they mean to keep,
and what they keep enters the box as own words, already carrying both halves.

Round trip: the box says what it is teaching → the conversation spends it and finds more →
the finds come home as tomorrow's cards.
Neither half of that needs the other to work, which is what makes it safe to ship in two commits.

## Flavors

One data spine, three protocol blocks — the tiers do not change, the instructions do:

- **Talk** — the conversation above. The default and the reason for the feature.
- **Text** — a graded reader instead of a dialogue: a short story over the same word lists,
  for a learner who does not want to type back. Cheapest possible addition once Talk stands.
- **Word** — one card, not the box: an example sentence, a mnemonic, or "why is my answer wrong",
  copied from the row menu the Box already has. It carries no lists and no state,
  so it is independent of everything above and could land first.

## Where it lives

The Box tab, in the own-content block beside the feedback export it is a sibling of.
NOT Home: Home answers "what do I do right now", and the answer to that is the round.

The sheet shows what the brief carries — the three counts, the flavor picker —
and never the raw text: 7 KB of prompt scrolling past is a wall, not a preview.
Two actions, both already precedented in `FeedbackExportActions`:
**Copy** and, on iOS, a `ShareLink` — which reaches every assistant on the phone
without the app knowing a single one of their names or URL schemes.

The offer after a finished round ("you're done — take these twelve words for a walk")
is the placement with the most pull, and it is deliberately NOT in this plan:
the completion screen is the app's quietest moment
and it earns its own ruling rather than riding in on this one.

## Layering

- **kern** — `box/Briefing.kt`, beside `Feedback.kt`: tier selection, caps, the protocol text,
  the return parser. A pure function of `BoxState`, so `jvmTest` covers all of it:
  no suspended word leaks, no held-back word is named, the parser survives an assistant
  that adds prose around the block, the empty box produces nothing rather than a brief with three zeros.
- **app** — the sheet, the clipboard, the share, and the chrome strings for the two buttons.
  Both platforms in the same sweep, per `../CLAUDE.md`.

Nothing else moves. No new dependency, no new document field except a `lastBriefingAt`
if "only what is new" turns out to be wanted — and it probably is not, since a brief is
a snapshot of a whole box rather than an outbox that empties.

## The picture idea, and why it is not one

The suggestion was to render the brief as an IMAGE, on the reasoning that
a chat model reads a page of pixels more cheaply than the same page of text.
The underlying result is real — optical compression, roughly 10× fewer tokens for rendered
text than for its characters, in systems built around a vision encoder for exactly that —
but none of it reaches this feature:

- **The saving is nothing here.** The brief tops out around 7 KB, call it 2,000 tokens,
  a fraction of a percent of any current context window. A pasted screenshot is billed
  at a four-figure token count of its own, so at this size the image is not even cheaper.
- **It puts an OCR pass between the box and the learner's practice.** What OCR drops first is
  diacritics and unfamiliar orthography — Ukrainian, Swahili's borrowed spellings, German umlauts —
  which is to say it drops precisely the part of a vocabulary list that carries the learning.
  A brief that teaches `kuoga` as `kuога` is worse than no brief.
- **Text is the format the whole loop is already in.** The learner can read it, edit it,
  reuse it in a second conversation, and the assistant can quote it back. An image can do none of that.

So: text. The one image worth building is a different thing entirely —
a share card of the box's state, for people rather than for models — and that is
marketing, not this (`backlog.md`).

Phone-to-desktop stays the honest friction: Handoff carries the clipboard between Apple devices for free,
the share sheet reaches any messenger on either platform, and a QR cannot hold 7 KB.
Nothing to build; worth knowing before someone tries.
