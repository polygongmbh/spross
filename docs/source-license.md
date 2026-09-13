# Licensing the source

What the repo has to grant, and what constrains the choice.
The audio and catalog terms themselves are `audio-licensing.md` and are not restated here.

## Where it stands

The repo carries no `LICENSE` file, so it grants nobody anything —
the code is readable on GitHub and reserved in full.
"Free and open" is a plan until that file exists.
The rights holder is Polygon GmbH, the same entity the Impressum names
(`App/Sources/Resources/Localizable.xcstrings`, `legal.*`).

## What the choice has to survive

- **The App Store.** A GPL-family license and a store binary conflict —
  the repo already rejected GPL-3.0 dependencies for exactly that reason
  (`audio-licensing.md` § 5). Permissive (MIT, Apache-2.0) and weak copyleft (MPL-2.0)
  are unaffected.
- **Hosted sync as the paid tier** (`sync.md`). A server under AGPL-3.0 obliges anyone
  running a modified copy to publish their changes, and it is never linked into the app,
  so it cannot reach the app's license. One owner may license the two differently.
- **What ships inside keeps its own terms** whatever the repo picks: Nunito under
  OFL-1.1 (`android/licenses/Nunito-OFL.txt`, credited on the About screen), the audio
  packs under CC BY-SA / CC BY / CC0, and the dependencies — kotlinx, AndroidX, Compose,
  Glance, SKIE — under their own, which a `NOTICE` has to list. Confirm each before publishing.
- **The FSRS golden vectors are copied verbatim** from ts-fsrs and py-fsrs with provenance
  (`kern/docs/fsrs.md`). A test corpus is still someone else's file: check those repos'
  licenses and carry whatever notice they ask for.
- **The catalog is content, not code.** Either the license file says code only and the
  catalog stays reserved, or the catalog gets its own grant — CC BY-SA 4.0 matches the
  share-alike audio it already ships beside.

## The shape worth taking

- **App and kern: Apache-2.0** — patent grant included, no conflict with the store binary,
  and no obstacle to anyone building the app themselves.
- **The sync server, when it exists: AGPL-3.0** — hosting is the thing being sold,
  and this is the license that keeps a hosted fork honest without touching the app.
- **Catalog: CC BY-SA 4.0**, the audio unchanged under its per-file terms.
- One `LICENSE`, one `NOTICE`, and a README line saying which covers what.

## Questions only the owner can answer

- Is a permissive app license actually wanted, or is MPL-2.0 — copyleft per file,
  still store-safe — the better trade while sync is the thing being sold?
- May a fork ship to the stores under the name Spross? That is trademark, not license,
  and it is the lever the license does not give.
- Is the catalog being lifted wholesale by a competitor acceptable? BY-SA permits it,
  with attribution and share-alike; reserving the catalog is the alternative.
