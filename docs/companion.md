# The companion — an AI beside the box

The box teaches words one at a time and never asks the learner to use one in a sentence.
That is the deliberate shape of it — breadth of exposure over depth on any one word —
and it leaves a gap the app does not try to fill itself:
somewhere the learner has to actually SAY the thing.
A chat assistant is good at exactly that and bad at everything the box does well,
so the two stand beside each other rather than inside one another.

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

`Briefings.of` builds it, `Briefing.text` writes it, and the tiers are `GrowthStage`'s —
no taxonomy of its own:

| Block | Sprossen | Form | Why |
|---|---|---|---|
| **May use freely** | `Consolidated`, `Matured` | bare target forms, grouped by area | the floor the conversation stands on; the assistant knows what they mean, so glosses would only cost room |
| **Work these in** | `Learning`, `Fresh`, `Relearning` | `target = source` pairs | the words the box is spending its effort on; the conversation is where they get used |
| **New** | `Growth.newCandidates` | `target (source)` pairs | what the box will introduce NEXT — see below |
| **Held back** | `Unscheduled`, `Queued` | not named at all | listing 800 words to forbid them would be the whole catalog with a "no" on it |

`Suspended` is in none of them:
a word taken out of rotation is one the learner has said they do not want to meet.
Own words carry both halves in the free block, since they are the one entry an assistant may not know.
Target nouns are written with their article (`articledForm`), as they are everywhere spoken.

**The new-word allowance is the box's own next-up list.**
The handful of new words the conversation may spend
are the ones the box is about to teach anyway,
so the learner meets them again, in the app, two days later — with a hook already set.
Letting the assistant pick freely was the alternative:
it reads more natural and it teaches into a void, since nothing follows it up.
That is what makes this a LOOP rather than a side quest.

### The brief, worked

```
Spross — vocabulary brief
Anna knows German and is learning Swahili.
214 words consolidated, 31 in play.

You are a patient conversation partner for a vocabulary learner. Speak Swahili.
Explain in German, and only when the learner stalls or asks.
This app teaches WORDS, not grammar: assume no instruction in tense, case or
agreement, and keep sentences short and concrete.
Ask one question per turn and wait for the answer.
Correct at most one mistake per turn, in German, after answering what was said.
Never list vocabulary back at the learner. Talk.

MAY USE FREELY — 214 words the learner has consolidated
In the bathroom: bafu, kuoga, sabuni, taulo, mswaki …
In the kitchen: jiko, sufuria, kisu, sahani …

WORK THESE IN — 31 words being learned right now; aim for 8 of them, and prefer them to synonyms
kusubiri = warten
ratiba = Fahrplan

NEW — at most 3 per turn, from this list only, glossed in German the first time
abiria (Fahrgast)
tikiti (Fahrkarte)

When the learner says they are done, list the words they met that were new to
them, one per line as `Swahili = German`, in a block fenced ```spross:

```spross
abiria = Fahrgast
```
```

The grammar line is the load-bearing one:
the box teaches words and conjugates nothing,
so an assistant left to assume otherwise
writes perfect subordinate clauses at someone who has met nouns.

**English, and in kern**, on `Feedback`'s ruling and for its reasons:
the reader is a machine rather than the learner's device,
so it is an interchange format rather than chrome, one dialect rather than one per platform.
The learner's two languages are NAMED inside it; the instructions around them are not translated.
There is no stop word for the same reason — one would have to be written
in one of those two languages, and the text is in neither, so the closing ask is prose.

**Size.** The catalog holds about a thousand concepts,
so the largest brief that can ever exist — every word consolidated — is around 7 KB.
That is the ceiling, not the typical case.

## The way back in

The closing block is the point of the exercise.
A conversation surfaces words the catalog does not have — the learner's job, their street, their kid's school —
and those are exactly what `OwnWord` exists to hold.

`Harvest.read` parses an ASSISTANT, so it forgives one:
fences it did not ask for, bullets, numbering, backticks, an arrow where an `=` was asked for,
prose wrapped around the whole thing.
It drops what the box already holds — the articled form included,
since "das Amt" handed back for a card keyed `Amt` has found nothing —
and it caps one paste, because an assistant answering with a dictionary
is a misunderstanding rather than a windfall.
What it never does is import: it returns a list, and the sheet asks.

Round trip: the box says what it is teaching → the conversation spends it and finds more →
the finds come home as own words, already carrying both halves.

## Where it lives

The Box tab, leading the own-content panel it is a sibling of —
the one entry there that goes out and comes back, with the words it writes home under it.
NOT Home: Home answers "what do I do right now", and the answer to that is the round.

The finished round offers it once more, under the celebration rather than in it:
the words are warm, and a conversation about them costs nothing to propose.
It asks rather than instructs, and the screen's own answer to "what now" is still Fertig.

The sheet shows the three counts and never the text:
7 KB of prompt scrolling past is a wall, not a preview.
Copy takes it; Share hands it to any chat app on the phone,
which is how the app reaches every assistant while knowing the name of none.
Paste reads an answer back, ticked — the learner asked for that list — to keep or drop.

## The picture idea, and why it is not one

Rendering the brief as an IMAGE was proposed,
on the reasoning that a chat model reads a page of pixels more cheaply than the same page of text.
The underlying result is real — optical compression, roughly 10× fewer tokens for rendered text
than for its characters, in systems built around a vision encoder for exactly that —
but none of it reaches this feature:

- **The saving is nothing here.** The brief tops out around 7 KB, call it 2,000 tokens,
  a fraction of a percent of any current context window. A pasted screenshot is billed at a
  four-figure token count of its own, so at this size the image is not even cheaper.
- **It puts an OCR pass between the box and the learner's practice.** What OCR drops first is
  diacritics and unfamiliar orthography — Ukrainian, Swahili's borrowed spellings, German umlauts —
  which is to say precisely the part of a vocabulary list that carries the learning.
  A brief that teaches `kuога` for `kuoga` is worse than no brief.
- **Text is the format the whole loop is already in.** The learner can read it, edit it, and
  reuse it in a second conversation, and the assistant can quote it back. An image can do none of that.

The one image worth building is a different thing entirely —
a share card of the box's state, for people rather than for models — and that is marketing, not this.

Phone-to-desktop stays the honest friction: Handoff carries the clipboard between Apple devices for free,
the share sheet reaches any messenger on either platform, and a QR code cannot hold 7 KB.
