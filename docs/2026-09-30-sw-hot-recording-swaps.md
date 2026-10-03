# Swahili hot recordings: swap candidates
Which shipped Swahili recordings are limited hot at the source, and which other Commons take of the same form could replace each.
Neighbors: the fields and the sweep `../catalog/audio/README.md`, the score `2026-09-28-audio-quality-tools.md`, what may ship `audio-licensing.md`.

## Summary

251 sw recordings ship with `gain` ≤ -8: 229 words, 3 calendar names, 19 country names.
Every one is Waithera Were's (`Sw-ke-*.flac`, CC BY-SA 4.0);
the hot takes are the loudest 28% of her own pack (251 of 908), not a second voice.
They measure -10.0 to -5.5 LUFS at peaks of -2.4 to -4.8 dBFS,
only 1.6 to 7.4 dB of peak over loudness, where her own cleaner duplicates keep 8 and the other voices 9 to 16.
None has clipped samples, so the fill's clipping refusal passes them all; the damage is limiting, not clipping.

Commons holds little else in Swahili.
`Category:Swahili pronunciation` is 16,775 files, 16,662 of them `Sw-ke-*`;
the rest is 192 Lingua Libre files, six `Sw-*.oga` and a handful of Tanzanian place names.
Only 15 of the 251 have another take of the exact form the card shows.

**12 have a clean swap**, 3 candidates fail, 236 have no alternative at all.
Shipped 2026-10-03 after a listening pass: 10 of the 12.
cow and hour stay — the same speaker's other upload, heard no different;
government's new take is a bit echoey, still preferred over the screamy one.
Then every other catalog form the three voices record moved onto them too (2026-10-03), 42 of 44:
Goethe-Institut Cameroon takes the whole calendar and eight words, Ismail Ibn Ahmed thirteen, Byera04 three.
Ismail's hiyo and katika were heard as bad and stay with Waithera Were.

- **Waithera Were herself** has capitalized duplicates, `Sw-ke-Ng'ombe.flac` and `Sw-ke-Saa.flac`:
  cleaner than the shipped take, better MOS, and no voice change or new credit (cow, hour).
- **Ismail Ibn Ahmed** (Lingua Libre, CC0), four swaps: for, government, if, time.
  Quiet (-23 to -26 LUFS), 12 to 16 dB of peak range, MOS within 0.25 of the incumbent.
- **Goethe-Institut Cameroon** (the `Mndetatsin` Lingua Libre uploads, CC BY-SA 4.0, already credited for `nakupenda`), four swaps: egg, neighborhood, potato, Julai.
  Normalized close to full scale but not limited; two of its takes fail (Agosti, Jumatatu).
- **Byera04** (Lingua Libre, CC0 and CC BY-SA 4.0), two swaps: airport, coast.
  Two takes is under the sweep's "≤3 takes" line, but `consolidate-pack.py` refuses a hot take, so they stay.
- Rejected: scales (`Sw-ke-Mizani.flac` is hot too, -9.0 LUFS),
  Agosti (MOS 0.54 under the incumbent, peak -0.3 dBFS),
  Jumatatu (peak +0.2 dBFS, two clipped samples).
- No alternative: the other 217 words, all 19 country names, and the rest are Waithera Were's only take.
  They stay as they are until someone records them; a re-recording is the only fix.

A swap here meets all of:
integrated loudness ≤ -12 LUFS (the fill would ship its `gain` at -6 or above, clear of the hot band),
no decoded sample at full scale,
`mos` no more than 0.3 under the incumbent (`requalify-pack.py`'s `--margin`, the score's per-file noise),
a shippable license,
and a filename that says exactly the card's form (`says_its_word`, casing aside).
No listener verdict exists for any candidate; one hot incumbent (blood) is rated okay.

Measured with `audio_measure.measure` under ffmpeg 9.0.2 (ANALYSIS was taken on 9.0.1),
on Commons' mp3 transcodes, the bytes a swap would ship.

## Words (229)

| slug | form | current file | author | gain | LUFS | MOS | candidate | author | license | LUFS | peak | MOS | verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| to-hurt | kuuma | Sw-ke-kuuma.flac | Waithera Were | -12.5 | -5.5 | 2.76 | none found | | | | | | |
| at-school | shuleni | Sw-ke-shuleni.flac | Waithera Were | -12.4 | -5.6 | 2.63 | none found | | | | | | |
| bad | baya | Sw-ke-baya.flac | Waithera Were | -12.4 | -5.6 | 2.72 | none found | | | | | | |
| sea | bahari | Sw-ke-bahari.flac | Waithera Were | -12.1 | -5.9 | 2.23 | none found | | | | | | |
| coffee | kahawa | Sw-ke-kahawa.flac | Waithera Were | -12.0 | -6.0 | 2.84 | none found | | | | | | |
| purple | zambarau | Sw-ke-zambarau.flac | Waithera Were | -12.0 | -6.0 | 2.87 | none found | | | | | | |
| mother-tongue | lugha ya mama | Sw-ke-lugha ya mama.flac | Waithera Were | -11.7 | -6.3 | 3.24 | none found | | | | | | |
| later | baadaye | Sw-ke-baadaye.flac | Waithera Were | -11.5 | -6.5 | 2.49 | none found | | | | | | |
| to-marry | kuoa | Sw-ke-kuoa.flac | Waithera Were | -11.5 | -6.5 | 2.46 | none found | | | | | | |
| to-stir | kukoroga | Sw-ke-kukoroga.flac | Waithera Were | -11.5 | -6.5 | 2.97 | none found | | | | | | |
| fever | homa | Sw-ke-homa.flac | Waithera Were | -11.1 | -6.9 | 2.63 | none found | | | | | | |
| to-forget | kusahau | Sw-ke-kusahau.flac | Waithera Were | -11.1 | -6.9 | 2.79 | none found | | | | | | |
| they | wao | Sw-ke-wao.flac | Waithera Were | -11.0 | -7.0 | 2.79 | none found | | | | | | |
| authority | mamlaka | Sw-ke-mamlaka.flac | Waithera Were | -10.9 | -7.1 | 3.29 | none found | | | | | | |
| cable | kebo | Sw-ke-kebo.flac | Waithera Were | -10.8 | -7.2 | 2.88 | none found | | | | | | |
| carrot | karoti | Sw-ke-karoti.flac | Waithera Were | -10.8 | -7.2 | 3.00 | none found | | | | | | |
| first | kwanza | Sw-ke-kwanza.flac | Waithera Were | -10.8 | -7.2 | 2.76 | none found | | | | | | |
| to-improve | kuboresha | Sw-ke-kuboresha.flac | Waithera Were | -10.8 | -7.2 | 2.67 | none found | | | | | | |
| vision | maono | Sw-ke-maono.flac | Waithera Were | -10.8 | -7.2 | 2.83 | none found | | | | | | |
| i-have-no-money | sina pesa | Sw-ke-sina pesa.flac | Waithera Were | -10.6 | -7.4 | 3.10 | none found | | | | | | |
| mouse | kipanya | Sw-ke-kipanya.flac | Waithera Were | -10.6 | -7.4 | 2.91 | none found | | | | | | |
| ready | tayari | Sw-ke-tayari.flac | Waithera Were | -10.6 | -7.4 | 3.04 | none found | | | | | | |
| to-meet | kukutana | Sw-ke-kukutana.flac | Waithera Were | -10.6 | -7.4 | 2.67 | none found | | | | | | |
| potato | kiazi | Sw-ke-kiazi.flac | Waithera Were | -10.5 | -7.5 | 2.95 | LL-Q16587531(swa)-Mndetatsin-Kiazi(la pomme de terre).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -14.7 | -1.8 | 2.66 | swap (MOS −0.29, at the margin) |
| to-sleep | kulala | Sw-ke-kulala.flac | Waithera Were | -10.5 | -7.5 | 2.79 | none found | | | | | | |
| good-morning | habari za asubuhi | Sw-ke-habari za asubuhi.flac | Waithera Were | -10.4 | -7.6 | 2.63 | none found | | | | | | |
| hunger | njaa | Sw-ke-njaa.flac | Waithera Were | -10.4 | -7.6 | 2.94 | none found | | | | | | |
| pen | kalamu | Sw-ke-kalamu.flac | Waithera Were | -10.4 | -7.6 | 3.09 | none found | | | | | | |
| story | ghorofa | Sw-ke-ghorofa.flac | Waithera Were | -10.4 | -7.6 | 2.52 | none found | | | | | | |
| to-start | kuanza | Sw-ke-kuanza.flac | Waithera Were | -10.4 | -7.6 | 2.57 | none found | | | | | | |
| which | gani | Sw-ke-gani.flac | Waithera Were | -10.4 | -7.6 | 2.89 | none found | | | | | | |
| door | mlango | Sw-ke-mlango.flac | Waithera Were | -10.3 | -7.7 | 2.93 | none found | | | | | | |
| same | sawa | Sw-ke-sawa.flac | Waithera Were | -10.3 | -7.7 | 2.79 | none found | | | | | | |
| apple | tufaha | Sw-ke-tufaha.flac | Waithera Were | -10.2 | -7.8 | 2.86 | none found | | | | | | |
| heart | moyo | Sw-ke-moyo.flac | Waithera Were | -10.2 | -7.8 | 2.74 | none found | | | | | | |
| plate | sahani | Sw-ke-sahani.flac | Waithera Were | -10.2 | -7.8 | 2.52 | none found | | | | | | |
| please | tafadhali | Sw-ke-tafadhali.flac | Waithera Were | -10.2 | -7.8 | 2.76 | none found | | | | | | |
| to-know-something | kujua | Sw-ke-kujua.flac | Waithera Were | -10.2 | -7.8 | 2.73 | none found | | | | | | |
| training | mazoezi | Sw-ke-mazoezi.flac | Waithera Were | -10.2 | -7.8 | 2.74 | none found | | | | | | |
| verb | kitenzi | Sw-ke-kitenzi.flac | Waithera Were | -10.2 | -7.8 | 2.82 | none found | | | | | | |
| far | mbali | Sw-ke-mbali.flac | Waithera Were | -10.1 | -7.9 | 2.58 | none found | | | | | | |
| full | kujaa | Sw-ke-kujaa.flac | Waithera Were | -10.1 | -7.9 | 2.83 | none found | | | | | | |
| longing | hamu | Sw-ke-hamu.flac | Waithera Were | -10.1 | -7.9 | 2.67 | none found | | | | | | |
| overnight-stay | malazi | Sw-ke-malazi.flac | Waithera Were | -10.1 | -7.9 | 2.88 | none found | | | | | | |
| bladder | kibofu | Sw-ke-kibofu.flac | Waithera Were | -10.0 | -8.0 | 2.91 | none found | | | | | | |
| to-cough | kukohoa | Sw-ke-kukohoa.flac | Waithera Were | -10.0 | -8.0 | 2.86 | none found | | | | | | |
| to-drink | kunywa | Sw-ke-kunywa.flac | Waithera Were | -10.0 | -8.0 | 2.70 | none found | | | | | | |
| grandfather | babu | Sw-ke-babu.flac | Waithera Were | -9.9 | -8.1 | 2.02 | none found | | | | | | |
| date | tarehe | Sw-ke-tarehe.flac | Waithera Were | -9.8 | -8.2 | 2.92 | none found | | | | | | |
| finger | kidole | Sw-ke-kidole.flac | Waithera Were | -9.8 | -8.2 | 2.56 | none found | | | | | | |
| glass | glasi | Sw-ke-glasi.flac | Waithera Were | -9.8 | -8.2 | 2.89 | none found | | | | | | |
| toilet | choo | Sw-ke-choo.flac | Waithera Were | -9.8 | -8.2 | 3.17 | none found | | | | | | |
| yesterday | jana | Sw-ke-jana.flac | Waithera Were | -9.8 | -8.2 | 2.98 | none found | | | | | | |
| boat | mashua | Sw-ke-mashua.flac | Waithera Were | -9.7 | -8.3 | 2.43 | none found | | | | | | |
| gift | zawadi | Sw-ke-zawadi.flac | Waithera Were | -9.7 | -8.3 | 3.10 | none found | | | | | | |
| glove | glavu | Sw-ke-glavu.flac | Waithera Were | -9.7 | -8.3 | 2.59 | none found | | | | | | |
| sun | jua | Sw-ke-jua.flac | Waithera Were | -9.7 | -8.3 | 2.92 | none found | | | | | | |
| time | wakati | Sw-ke-wakati.flac | Waithera Were | -9.7 | -8.3 | 3.27 | LL-Q7838 (swa)-Ismail Ibn Ahmed-wakati.wav | Ismail Ibn Ahmed | CC0 | -25.9 | -13.4 | 3.02 | swap |
| to-reject | kukataa | Sw-ke-kukataa.flac | Waithera Were | -9.7 | -8.3 | 3.14 | none found | | | | | | |
| to-smile | kutabasamu | Sw-ke-kutabasamu.flac | Waithera Were | -9.7 | -8.3 | 2.94 | none found | | | | | | |
| to-try | kujaribu | Sw-ke-kujaribu.flac | Waithera Were | -9.7 | -8.3 | 2.59 | none found | | | | | | |
| today | leo | Sw-ke-leo.flac | Waithera Were | -9.7 | -8.3 | 2.97 | none found | | | | | | |
| car | gari | Sw-ke-gari.flac | Waithera Were | -9.6 | -8.4 | 2.79 | none found | | | | | | |
| grade | alama | Sw-ke-alama.flac | Waithera Were | -9.6 | -8.4 | 2.97 | none found | | | | | | |
| meaning | maana | Sw-ke-maana.flac | Waithera Were | -9.6 | -8.4 | 3.01 | none found | | | | | | |
| to-arrange | kupanga | Sw-ke-kupanga.flac | Waithera Were | -9.6 | -8.4 | 2.72 | none found | | | | | | |
| to-cost | kugharimu | Sw-ke-kugharimu.flac | Waithera Were | -9.6 | -8.4 | 2.78 | none found | | | | | | |
| to-explain | kueleza | Sw-ke-kueleza.flac | Waithera Were | -9.6 | -8.4 | 3.01 | none found | | | | | | |
| to-fill-in | kujaza | Sw-ke-kujaza.flac | Waithera Were | -9.6 | -8.4 | 2.83 | none found | | | | | | |
| to-sit | kukaa | Sw-ke-kukaa.flac | Waithera Were | -9.6 | -8.4 | 3.09 | none found | | | | | | |
| to-understand | kuelewa | Sw-ke-kuelewa.flac | Waithera Were | -9.6 | -8.4 | 2.88 | none found | | | | | | |
| skin | ngozi | Sw-ke-ngozi.flac | Waithera Were | -9.5 | -8.5 | 2.87 | none found | | | | | | |
| to-get-dressed | kuvaa | Sw-ke-kuvaa.flac | Waithera Were | -9.5 | -8.5 | 2.93 | none found | | | | | | |
| to-help | kusaidia | Sw-ke-kusaidia.flac | Waithera Were | -9.5 | -8.5 | 2.31 | none found | | | | | | |
| to-look | kuangalia | Sw-ke-kuangalia.flac | Waithera Were | -9.5 | -8.5 | 2.93 | none found | | | | | | |
| to-wish | kutamani | Sw-ke-kutamani.flac | Waithera Were | -9.5 | -8.5 | 2.62 | none found | | | | | | |
| firm | imara | Sw-ke-imara.flac | Waithera Were | -9.4 | -8.6 | 3.10 | none found | | | | | | |
| form | fomu | Sw-ke-fomu.flac | Waithera Were | -9.4 | -8.6 | 2.39 | none found | | | | | | |
| government | serikali | Sw-ke-serikali.flac | Waithera Were | -9.4 | -8.6 | 3.16 | LL-Q7838 (swa)-Ismail Ibn Ahmed-serikali.wav | Ismail Ibn Ahmed | CC0 | -23.5 | -11.6 | 3.02 | swap |
| medicine | dawa | Sw-ke-dawa.flac | Waithera Were | -9.4 | -8.6 | 2.89 | none found | | | | | | |
| place | mahali | Sw-ke-mahali.flac | Waithera Were | -9.4 | -8.6 | 2.80 | none found | | | | | | |
| to-pay | kulipa | Sw-ke-kulipa.flac | Waithera Were | -9.4 | -8.6 | 2.62 | none found | | | | | | |
| celebration | sherehe | Sw-ke-sherehe.flac | Waithera Were | -9.3 | -8.7 | 2.65 | none found | | | | | | |
| email | barua pepe | Sw-ke-barua pepe.flac | Waithera Were | -9.3 | -8.7 | 3.13 | none found | | | | | | |
| formerly | zamani | Sw-ke-zamani.flac | Waithera Were | -9.3 | -8.7 | 2.76 | none found | | | | | | |
| furniture | samani | Sw-ke-samani.flac | Waithera Were | -9.3 | -8.7 | 2.80 | none found | | | | | | |
| help | msaada | Sw-ke-msaada.flac | Waithera Were | -9.3 | -8.7 | 2.75 | none found | | | | | | |
| human | binadamu | Sw-ke-binadamu.flac | Waithera Were | -9.3 | -8.7 | 2.63 | none found | | | | | | |
| lamp | taa | Sw-ke-taa.flac | Waithera Were | -9.3 | -8.7 | 2.88 | none found | | | | | | |
| tasty | tamu | Sw-ke-tamu.flac | Waithera Were | -9.3 | -8.7 | 2.82 | none found | | | | | | |
| to-snore | kukoroma | Sw-ke-kukoroma.flac | Waithera Were | -9.3 | -8.7 | 2.98 | none found | | | | | | |
| appointment | miadi | Sw-ke-miadi.flac | Waithera Were | -9.2 | -8.8 | 2.93 | none found | | | | | | |
| day-before-yesterday | juzi | Sw-ke-juzi.flac | Waithera Were | -9.2 | -8.8 | 2.90 | none found | | | | | | |
| foot | mguu | Sw-ke-mguu.flac | Waithera Were | -9.2 | -8.8 | 2.80 | none found | | | | | | |
| may-i-come-in | hodi | Sw-ke-hodi.flac | Waithera Were | -9.2 | -8.8 | 2.92 | none found | | | | | | |
| once | mara moja | Sw-ke-mara_moja.flac | Waithera Were | -9.2 | -8.8 | 2.86 | none found | | | | | | |
| smile | tabasamu | Sw-ke-tabasamu.flac | Waithera Were | -9.2 | -8.8 | 3.42 | none found | | | | | | |
| to-follow | kufuata | Sw-ke-kufuata.flac | Waithera Were | -9.2 | -8.8 | 2.65 | none found | | | | | | |
| to-want | kutaka | Sw-ke-kutaka.flac | Waithera Were | -9.2 | -8.8 | 3.05 | none found | | | | | | |
| to-write | kuandika | Sw-ke-kuandika.flac | Waithera Were | -9.2 | -8.8 | 2.39 | none found | | | | | | |
| very | sana | Sw-ke-sana.flac | Waithera Were | -9.2 | -8.8 | 2.91 | none found | | | | | | |
| wound | jeraha | Sw-ke-jeraha.flac | Waithera Were | -9.2 | -8.8 | 2.87 | none found | | | | | | |
| bean | haragwe | Sw-ke-haragwe.flac | Waithera Were | -9.1 | -8.9 | 2.80 | none found | | | | | | |
| board | ubao | Sw-ke-ubao.flac | Waithera Were | -9.1 | -8.9 | 2.46 | none found | | | | | | |
| contract | mkataba | Sw-ke-mkataba.flac | Waithera Were | -9.1 | -8.9 | 2.57 | none found | | | | | | |
| father | baba | Sw-ke-baba.flac | Waithera Were | -9.1 | -8.9 | 2.99 | none found | | | | | | |
| profession | taaluma | Sw-ke-taaluma.flac | Waithera Were | -9.1 | -8.9 | 2.83 | none found | | | | | | |
| salary | mshahara | Sw-ke-mshahara.flac | Waithera Were | -9.1 | -8.9 | 3.19 | none found | | | | | | |
| similar | kufanana | Sw-ke-kufanana.flac | Waithera Were | -9.1 | -8.9 | 2.67 | none found | | | | | | |
| student | mwanachuo | Sw-ke-mwanachuo.flac | Waithera Were | -9.1 | -8.9 | 2.44 | none found | | | | | | |
| to-apply-for | kuomba | Sw-ke-kuomba.flac | Waithera Were | -9.1 | -8.9 | 2.75 | none found | | | | | | |
| west | magharibi | Sw-ke-magharibi.flac | Waithera Were | -9.1 | -8.9 | 2.63 | none found | | | | | | |
| although | ingawa | Sw-ke-ingawa.flac | Waithera Were | -9.0 | -9.0 | 2.98 | none found | | | | | | |
| responsible | husika | Sw-ke-husika.flac | Waithera Were | -9.0 | -9.0 | 2.74 | none found | | | | | | |
| safe | salama | Sw-ke-salama.flac | Waithera Were | -9.0 | -9.0 | 2.77 | none found | | | | | | |
| to-visit | kutembelea | Sw-ke-kutembelea.flac | Waithera Were | -9.0 | -9.0 | 3.14 | none found | | | | | | |
| address | anwani | Sw-ke-anwani.flac | Waithera Were | -8.9 | -9.1 | 2.78 | none found | | | | | | |
| animal | mnyama | Sw-ke-mnyama.flac | Waithera Were | -8.9 | -9.1 | 2.81 | none found | | | | | | |
| crossing | njia panda | Sw-ke-njia panda.flac | Waithera Were | -8.9 | -9.1 | 3.11 | none found | | | | | | |
| documents | nyaraka | Sw-ke-nyaraka.flac | Waithera Were | -8.9 | -9.1 | 3.25 | none found | | | | | | |
| dry | kavu | Sw-ke-kavu.flac | Waithera Were | -8.9 | -9.1 | 3.12 | none found | | | | | | |
| east | mashariki | Sw-ke-mashariki.flac | Waithera Were | -8.9 | -9.1 | 2.99 | none found | | | | | | |
| emergency | dharura | Sw-ke-dharura.flac | Waithera Were | -8.9 | -9.1 | 2.95 | none found | | | | | | |
| hour | saa | Sw-ke-saa.flac | Waithera Were | -8.9 | -9.1 | 2.94 | Sw-ke-Saa.flac | Waithera Were | CC BY-SA 4.0 | -12.3 | -4.2 | 3.03 | swap (same speaker) |
| if | kama | Sw-ke-kama.flac | Waithera Were | -8.9 | -9.1 | 2.63 | LL-Q7838 (swa)-Ismail Ibn Ahmed-kama.wav | Ismail Ibn Ahmed | CC0 | -24.1 | -8.3 | 2.65 | swap |
| neighborhood | mtaa | Sw-ke-mtaa.flac | Waithera Were | -8.9 | -9.1 | 2.84 | LL-Q123705(swa)-Mndetatsin-Mtaa(le quartier).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -16.0 | -0.8 | 2.77 | swap (peak −0.8) |
| to-eat | kula | Sw-ke-kula.flac | Waithera Were | -8.9 | -9.1 | 2.37 | none found | | | | | | |
| to-show | kuonyesha | Sw-ke-kuonyesha.flac | Waithera Were | -8.9 | -9.1 | 2.69 | none found | | | | | | |
| fear | woga | Sw-ke-woga.flac | Waithera Were | -8.8 | -9.2 | 2.71 | none found | | | | | | |
| gram | gramu | Sw-ke-gramu.flac | Waithera Were | -8.8 | -9.2 | 2.81 | none found | | | | | | |
| open | wazi | Sw-ke-wazi.flac | Waithera Were | -8.8 | -9.2 | 3.14 | none found | | | | | | |
| then | halafu | Sw-ke-halafu.flac | Waithera Were | -8.8 | -9.2 | 3.31 | none found | | | | | | |
| to-cancel | kughairi | Sw-ke-kughairi.flac | Waithera Were | -8.8 | -9.2 | 2.92 | none found | | | | | | |
| to-carry | kubeba | Sw-ke-kubeba.flac | Waithera Were | -8.8 | -9.2 | 2.80 | none found | | | | | | |
| to-deliver | kupeleka | Sw-ke-kupeleka.flac | Waithera Were | -8.8 | -9.2 | 3.15 | none found | | | | | | |
| to-have | kuwa na | Sw-ke-kuwa na.flac | Waithera Were | -8.8 | -9.2 | 2.92 | none found | | | | | | |
| century | karne | Sw-ke-karne.flac | Waithera Were | -8.7 | -9.3 | 2.36 | none found | | | | | | |
| face | uso | Sw-ke-uso.flac | Waithera Were | -8.7 | -9.3 | 2.43 | none found | | | | | | |
| for | kwa | Sw-ke-kwa.flac | Waithera Were | -8.7 | -9.3 | 2.83 | LL-Q7838 (swa)-Ismail Ibn Ahmed-kwa.wav | Ismail Ibn Ahmed | CC0 | -23.7 | -10.0 | 2.75 | swap |
| sauce | mchuzi | Sw-ke-mchuzi.flac | Waithera Were | -8.7 | -9.3 | 2.68 | none found | | | | | | |
| shop-assistant | muuzaji | Sw-ke-muuzaji.flac | Waithera Were | -8.7 | -9.3 | 2.89 | none found | | | | | | |
| to-bring | kuleta | Sw-ke-kuleta.flac | Waithera Were | -8.7 | -9.3 | 3.18 | none found | | | | | | |
| wallet | pochi | Sw-ke-pochi.flac | Waithera Were | -8.7 | -9.3 | 2.47 | none found | | | | | | |
| bridge | daraja | Sw-ke-daraja.flac | Waithera Were | -8.6 | -9.4 | 2.49 | none found | | | | | | |
| coin | sarafu | Sw-ke-sarafu.flac | Waithera Were | -8.6 | -9.4 | 3.06 | none found | | | | | | |
| cooked-rice | wali | Sw-ke-wali.flac | Waithera Were | -8.6 | -9.4 | 2.82 | none found | | | | | | |
| fast | haraka | Sw-ke-haraka.flac | Waithera Were | -8.6 | -9.4 | 3.23 | none found | | | | | | |
| good-day | habari | Sw-ke-habari.flac | Waithera Were | -8.6 | -9.4 | 3.06 | none found | | | | | | |
| list | orodha | Sw-ke-orodha.flac | Waithera Were | -8.6 | -9.4 | 3.07 | none found | | | | | | |
| old | zee | Sw-ke-zee.flac | Waithera Were | -8.6 | -9.4 | 2.83 | none found | | | | | | |
| scales | mizani | Sw-ke-mizani.flac | Waithera Were | -8.6 | -9.4 | 2.31 | Sw-ke-Mizani.flac | Waithera Were | CC BY-SA 4.0 | -9.0 | -4.0 | 2.70 | reject: hot too (−9.0 LUFS) |
| since | tangu | Sw-ke-tangu.flac | Waithera Were | -8.6 | -9.4 | 2.74 | none found | | | | | | |
| sofa | kochi | Sw-ke-kochi.flac | Waithera Were | -8.6 | -9.4 | 3.15 | none found | | | | | | |
| thirst | kiu | Sw-ke-kiu.flac | Waithera Were | -8.6 | -9.4 | 2.82 | none found | | | | | | |
| to-hire | kuajiri | Sw-ke-kuajiri.flac | Waithera Were | -8.6 | -9.4 | 2.67 | none found | | | | | | |
| to-like | kupenda | Sw-ke-kupenda.flac | Waithera Were | -8.6 | -9.4 | 2.89 | none found | | | | | | |
| to-repair | kutengeneza | Sw-ke-kutengeneza.flac | Waithera Were | -8.6 | -9.4 | 2.88 | none found | | | | | | |
| to-suggest | kupendekeza | Sw-ke-kupendekeza.flac | Waithera Were | -8.6 | -9.4 | 3.09 | none found | | | | | | |
| hotel | hoteli | Sw-ke-hoteli.flac | Waithera Were | -8.5 | -9.5 | 3.06 | none found | | | | | | |
| or | au | Sw-ke-au.flac | Waithera Were | -8.5 | -9.5 | 3.02 | none found | | | | | | |
| shelf | rafu | Sw-ke-rafu.flac | Waithera Were | -8.5 | -9.5 | 2.87 | none found | | | | | | |
| to-buy | kununua | Sw-ke-kununua.flac | Waithera Were | -8.5 | -9.5 | 2.39 | none found | | | | | | |
| to-recover | kupona | Sw-ke-kupona.flac | Waithera Were | -8.5 | -9.5 | 2.83 | none found | | | | | | |
| to-see | kuona | Sw-ke-kuona.flac | Waithera Were | -8.5 | -9.5 | 2.38 | none found | | | | | | |
| border | mpaka | Sw-ke-mpaka.flac | Waithera Were | -8.4 | -9.6 | 3.15 | none found | | | | | | |
| brother-in-law | shemeji | Sw-ke-shemeji.flac | Waithera Were | -8.4 | -9.6 | 2.65 | none found | | | | | | |
| classroom | darasa | Sw-ke-darasa.flac | Waithera Were | -8.4 | -9.6 | 2.80 | none found | | | | | | |
| evening | jioni | Sw-ke-jioni.flac | Waithera Were | -8.4 | -9.6 | 2.59 | none found | | | | | | |
| nationality | uraia | Sw-ke-uraia.flac | Waithera Were | -8.4 | -9.6 | 2.55 | none found | | | | | | |
| newspaper | gazeti | Sw-ke-gazeti.flac | Waithera Were | -8.4 | -9.6 | 3.14 | none found | | | | | | |
| t-shirt | fulana | Sw-ke-fulana.flac | Waithera Were | -8.4 | -9.6 | 3.12 | none found | | | | | | |
| to-stop | kuacha | Sw-ke-kuacha.flac | Waithera Were | -8.4 | -9.6 | 2.75 | none found | | | | | | |
| until | mpaka | Sw-ke-mpaka.flac | Waithera Were | -8.4 | -9.6 | 3.15 | none found | | | | | | |
| airport | uwanja wa ndege | Sw-ke-uwanja wa ndege.flac | Waithera Were | -8.3 | -9.7 | 3.05 | LL-Q7838 (swa)-Byera04-Uwanja wa ndege.wav | Byera04 | CC0 | -21.9 | -12.8 | 3.06 | swap |
| clean-adj | safi | Sw-ke-safi.flac | Waithera Were | -8.3 | -9.7 | 3.11 | none found | | | | | | |
| conjugation | mnyambuliko | Sw-ke-mnyambuliko.flac | Waithera Were | -8.3 | -9.7 | 2.59 | none found | | | | | | |
| plug | plagi | Sw-ke-plagi.flac | Waithera Were | -8.3 | -9.7 | 2.90 | none found | | | | | | |
| rail | reli | Sw-ke-reli.flac | Waithera Were | -8.3 | -9.7 | 2.76 | none found | | | | | | |
| rain | mvua | Sw-ke-mvua.flac | Waithera Were | -8.3 | -9.7 | 2.77 | none found | | | | | | |
| shirt | shati | Sw-ke-shati.flac | Waithera Were | -8.3 | -9.7 | 3.18 | none found | | | | | | |
| stall | kibanda | Sw-ke-kibanda.flac | Waithera Were | -8.3 | -9.7 | 2.78 | none found | | | | | | |
| this | hii | Sw-ke-hii.flac | Waithera Were | -8.3 | -9.7 | 2.55 | none found | | | | | | |
| to-come-back | kurudi | Sw-ke-kurudi.flac | Waithera Were | -8.3 | -9.7 | 2.49 | none found | | | | | | |
| to-fry | kukaanga | Sw-ke-kukaanga.flac | Waithera Were | -8.3 | -9.7 | 3.12 | none found | | | | | | |
| to-need | kuhitaji | Sw-ke-kuhitaji.flac | Waithera Were | -8.3 | -9.7 | 2.32 | none found | | | | | | |
| to-submit | kukabidhi | Sw-ke-kukabidhi.flac | Waithera Were | -8.3 | -9.7 | 2.97 | none found | | | | | | |
| to-taste | kuonja | Sw-ke-kuonja.flac | Waithera Were | -8.3 | -9.7 | 2.76 | none found | | | | | | |
| ugali | ugali | Sw-ke-ugali.flac | Waithera Were | -8.3 | -9.7 | 2.67 | none found | | | | | | |
| worry | wasiwasi | Sw-ke-wasiwasi.flac | Waithera Were | -8.3 | -9.7 | 3.18 | none found | | | | | | |
| back | mgongo | Sw-ke-mgongo.flac | Waithera Were | -8.2 | -9.8 | 2.95 | none found | | | | | | |
| cheap | rahisi | Sw-ke-rahisi.flac | Waithera Were | -8.2 | -9.8 | 2.24 | none found | | | | | | |
| cousin | binamu | Sw-ke-binamu.flac | Waithera Were | -8.2 | -9.8 | 2.77 | none found | | | | | | |
| cow | ng'ombe | Sw-ke-ng'ombe.flac | Waithera Were | -8.2 | -9.8 | 2.99 | Sw-ke-Ng'ombe.flac | Waithera Were | CC BY-SA 4.0 | -12.7 | -4.2 | 3.15 | swap (same speaker) |
| easy | rahisi | Sw-ke-rahisi.flac | Waithera Were | -8.2 | -9.8 | 2.24 | none found | | | | | | |
| egg | yai | Sw-ke-yai.flac | Waithera Were | -8.2 | -9.8 | 2.84 | LL-Q93189(swa)-Mndetatsin-Yai(l'oeuf).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -14.3 | -1.6 | 2.87 | swap |
| pain | maumivu | Sw-ke-maumivu.flac | Waithera Were | -8.2 | -9.8 | 2.78 | none found | | | | | | |
| teacher | mwalimu | Sw-ke-mwalimu.flac | Waithera Were | -8.2 | -9.8 | 2.77 | none found | | | | | | |
| tired | chovu | Sw-ke-chovu.flac | Waithera Were | -8.2 | -9.8 | 2.77 | none found | | | | | | |
| to-happen | kutokea | Sw-ke-kutokea.flac | Waithera Were | -8.2 | -9.8 | 3.02 | none found | | | | | | |
| to-open | kufungua | Sw-ke-kufungua.flac | Waithera Were | -8.2 | -9.8 | 2.27 | none found | | | | | | |
| to-save | kuhifadhi | Sw-ke-kuhifadhi.flac | Waithera Were | -8.2 | -9.8 | 3.08 | none found | | | | | | |
| to-say-goodbye | kuaga | Sw-ke-kuaga.flac | Waithera Were | -8.2 | -9.8 | 2.50 | none found | | | | | | |
| to-take-shower | kuoga | Sw-ke-kuoga.flac | Waithera Were | -8.2 | -9.8 | 2.67 | none found | | | | | | |
| trader | mfanyabiashara | Sw-ke-mfanyabiashara.flac | Waithera Were | -8.2 | -9.8 | 3.07 | none found | | | | | | |
| wood | mbao | Sw-ke-mbao.flac | Waithera Were | -8.2 | -9.8 | 2.35 | none found | | | | | | |
| belt | mkanda | Sw-ke-mkanda.flac | Waithera Were | -8.1 | -9.9 | 2.91 | none found | | | | | | |
| bill | bili | Sw-ke-bili.flac | Waithera Were | -8.1 | -9.9 | 2.68 | none found | | | | | | |
| coast | pwani | Sw-ke-pwani.flac | Waithera Were | -8.1 | -9.9 | 2.40 | LL-Q7838 (swa)-Byera04-Pwani.wav | Byera04 | CC BY-SA 4.0 | -16.5 | -7.2 | 2.29 | swap |
| doctor | daktari | Sw-ke-daktari.flac | Waithera Were | -8.1 | -9.9 | 2.84 | none found | | | | | | |
| here | hapa | Sw-ke-hapa.flac | Waithera Were | -8.1 | -9.9 | 2.78 | none found | | | | | | |
| keyboard | kibodi | Sw-ke-kibodi.flac | Waithera Were | -8.1 | -9.9 | 2.80 | none found | | | | | | |
| parents | wazazi | Sw-ke-wazazi.flac | Waithera Were | -8.1 | -9.9 | 2.58 | none found | | | | | | |
| to-be-late | kuchelewa | Sw-ke-kuchelewa.flac | Waithera Were | -8.1 | -9.9 | 2.85 | none found | | | | | | |
| to-confuse | kuchanganya | Sw-ke-kuchanganya.flac | Waithera Were | -8.1 | -9.9 | 2.96 | none found | | | | | | |
| to-congratulate | kupongeza | Sw-ke-kupongeza.flac | Waithera Were | -8.1 | -9.9 | 2.63 | none found | | | | | | |
| to-stand | kusimama | Sw-ke-kusimama.flac | Waithera Were | -8.1 | -9.9 | 3.11 | none found | | | | | | |
| umbrella | mwavuli | Sw-ke-mwavuli.flac | Waithera Were | -8.1 | -9.9 | 2.70 | none found | | | | | | |
| afternoon | mchana | Sw-ke-mchana.flac | Waithera Were | -8.0 | -10.0 | 3.17 | none found | | | | | | |
| alive | hai | Sw-ke-hai.flac | Waithera Were | -8.0 | -10.0 | 3.04 | none found | | | | | | |
| blood | damu | Sw-ke-damu.flac | Waithera Were | -8.0 | -10.0 | 2.71 | none found | | | | | | |
| broken | bovu | Sw-ke-bovu.flac | Waithera Were | -8.0 | -10.0 | 2.85 | none found | | | | | | |
| challenge | changamoto | Sw-ke-changamoto.flac | Waithera Were | -8.0 | -10.0 | 3.18 | none found | | | | | | |
| fee | ada | Sw-ke-ada.flac | Waithera Were | -8.0 | -10.0 | 2.89 | none found | | | | | | |
| hair | nywele | Sw-ke-nywele.flac | Waithera Were | -8.0 | -10.0 | 2.66 | none found | | | | | | |
| his | ake | Sw-ke-ake.flac | Waithera Were | -8.0 | -10.0 | 2.73 | none found | | | | | | |
| long | refu | Sw-ke-refu.flac | Waithera Were | -8.0 | -10.0 | 3.04 | none found | | | | | | |
| mother | mama | Sw-ke-mama.flac | Waithera Were | -8.0 | -10.0 | 2.66 | none found | | | | | | |
| no | hapana | Sw-ke-hapana.flac | Waithera Were | -8.0 | -10.0 | 3.22 | none found | | | | | | |
| to-pronounce | kutamka | Sw-ke-kutamka.flac | Waithera Were | -8.0 | -10.0 | 2.24 | none found | | | | | | |

## Calendar (3)

| slug | form | current file | author | gain | LUFS | MOS | candidate | author | license | LUFS | peak | MOS | verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Jumatatu | Jumatatu | Sw-ke-Jumatatu.flac | Waithera Were | -9.8 | -8.2 | 3.16 | LL-Q105(swa)-Mndetatsin-Jumatatu(Lundi).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -12.2 | 0.2 | 2.80 | reject: clipped (peak +0.2) |
| Julai | Julai | Sw-ke-Julai.flac | Waithera Were | -8.8 | -9.2 | 2.60 | LL-Q121(swa)-Mndetatsin-Julai(Juillet).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -12.9 | -2.7 | 2.55 | swap |
| Agosti | Agosti | Sw-ke-Agosti.flac | Waithera Were | -8.1 | -9.9 | 2.92 | LL-Q122(swa)-Mndetatsin-Agosti(Août).wav | Goethe-Institut Cameroon | CC BY-SA 4.0 | -12.6 | -0.3 | 2.38 | reject: MOS −0.54, peak −0.3 |

## Countries (19)

| slug | form | current file | author | gain | LUFS | MOS | candidate | author | license | LUFS | peak | MOS | verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Ghana | Ghana | Sw-ke-Ghana.flac | Waithera Were | -11.0 | -7.0 | 2.90 | none found | | | | | | |
| Kanada | Kanada | Sw-ke-Kanada.flac | Waithera Were | -10.7 | -7.3 | 3.14 | none found | | | | | | |
| Wafaransa | Wafaransa | Sw-ke-Wafaransa.flac | Waithera Were | -10.6 | -7.4 | 3.29 | none found | | | | | | |
| Wanyarwanda | Wanyarwanda | Sw-ke-Wanyarwanda.flac | Waithera Were | -10.4 | -7.6 | 2.77 | none found | | | | | | |
| Watanzania | Watanzania | Sw-ke-Watanzania.flac | Waithera Were | -10.1 | -7.9 | 2.73 | none found | | | | | | |
| Wajerumani | Wajerumani | Sw-ke-Wajerumani.flac | Waithera Were | -10.0 | -8.0 | 2.94 | none found | | | | | | |
| Wamarekani | Wamarekani | Sw-ke-Wamarekani.flac | Waithera Were | -9.3 | -8.7 | 2.93 | none found | | | | | | |
| Uganda | Uganda | Sw-ke-Uganda.flac | Waithera Were | -9.2 | -8.8 | 2.73 | none found | | | | | | |
| Waghana | Waghana | Sw-ke-Waghana.flac | Waithera Were | -9.2 | -8.8 | 2.84 | none found | | | | | | |
| Wamisri | Wamisri | Sw-ke-Wamisri.flac | Waithera Were | -9.0 | -9.0 | 2.05 | none found | | | | | | |
| Ureno | Ureno | Sw-ke-Ureno.flac | Waithera Were | -8.9 | -9.1 | 2.89 | none found | | | | | | |
| Waganda | Waganda | Sw-ke-Waganda.flac | Waithera Were | -8.9 | -9.1 | 2.79 | none found | | | | | | |
| Wajapani | Wajapani | Sw-ke-Wajapani.flac | Waithera Were | -8.9 | -9.1 | 2.97 | none found | | | | | | |
| Waholanzi | Waholanzi | Sw-ke-Waholanzi.flac | Waithera Were | -8.7 | -9.3 | 2.65 | none found | | | | | | |
| Wagiriki | Wagiriki | Sw-ke-Wagiriki.flac | Waithera Were | -8.6 | -9.4 | 2.79 | none found | | | | | | |
| Waingereza | Waingereza | Sw-ke-Waingereza.flac | Waithera Were | -8.6 | -9.4 | 2.74 | none found | | | | | | |
| Rwanda | Rwanda | Sw-ke-Rwanda.flac | Waithera Were | -8.4 | -9.6 | 2.85 | none found | | | | | | |
| Wareno | Wareno | Sw-ke-Wareno.flac | Waithera Were | -8.4 | -9.6 | 2.99 | none found | | | | | | |
| Wakorea | Wakorea | Sw-ke-Wakorea.flac | Waithera Were | -8.2 | -9.8 | 3.10 | none found | | | | | | |
