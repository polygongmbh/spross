"use strict";

// Mailing-list endpoint — empty until an account exists; wiring notes in web/README.md.
// Buttondown-shaped: "https://buttondown.com/api/emails/embed-subscribe/<slug>"
const SIGNUP_ENDPOINT = "";

// Language rows mirror catalog/languages.json (name, flag, articles); speech is a web-only fact.
const LANGS = [
  { code: "de", name: "Deutsch", flag: "🇩🇪", speech: "de-DE", articles: ["der", "die", "das", "ein", "eine"] },
  { code: "en", name: "English", flag: "🇬🇧", speech: "en-GB", articles: ["the", "a", "an"] },
  { code: "es", name: "Español", flag: "🇪🇸", speech: "es-ES", articles: ["el", "la", "los", "las", "un", "una"] },
  { code: "sw", name: "Kiswahili", flag: "🇹🇿", speech: "sw", articles: [] },
  { code: "uk", name: "Українська", flag: "🇺🇦", speech: "uk-UA", articles: [] },
];

const web = globalThis.kern.net.spross.kern.web;
const WebTrainer = web.WebTrainer;

const MAX_LEVEL = 4; // the web taste stops at thousands; the app climbs to 10 digits
const NUDGE_AFTER = 10;
const ADVANCE_GRACE_MS = 350;

const $ = (id) => document.getElementById(id);
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;

// The app's own review-loop chimes (App/Resources/Sounds, scripts/sounds.py):
// correct = ascending major third, wrong = descending minor third,
// reveal = one neutral note, cheer = the correct interval up to the octave.
const CHIMES = Object.fromEntries(
  ["correct", "wrong", "reveal", "cheer"].map((n) => [n, new Audio(`assets/sounds/${n}.wav`)])
);

function chime(name) {
  if (run.muted) return;
  const a = CHIMES[name];
  a.currentTime = 0;
  a.play().catch(() => {}); // an unready or blocked player stays silent, never throws
}

// The drill card and the signup form only work with JS — reveal them now that it runs
// (no-JS visitors keep the noscript notes instead of dead controls).
document.querySelector(".drill-card").hidden = false;
$("signup-form").hidden = false;

// ---------- the ladder climbs with you ----------

// why: the rails' notches drift at a fraction of the scroll, so the page reads
// as something you are climbing rather than scrolling past.
const climb = document.querySelector(".climb");
if (climb && !reducedMotion) {
  let ticking = false;
  const drift = () => {
    ticking = false;
    const seen = window.scrollY + innerHeight - climb.offsetTop;
    climb.style.setProperty("--climb-shift", `${Math.max(-90, Math.min(0, -seen * 0.05))}px`);
  };
  addEventListener("scroll", () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(drift);
  }, { passive: true });
  drift();
}

// ---------- language pick ----------

let lang = null;
let drill = null;

const chipsHost = document.querySelector(".lang-chips");
for (const l of LANGS) {
  const chip = document.createElement("button");
  chip.className = "chip";
  chip.type = "button";
  chip.setAttribute("aria-pressed", "false");
  const name = document.createElement("span");
  name.lang = l.code;
  name.textContent = l.name;
  chip.append(`${l.flag} `, name);
  chip.addEventListener("click", () => pickLanguage(l, chip));
  chipsHost.appendChild(chip);
}

function pickLanguage(l, chip) {
  lang = l;
  showcaseStep(l);
  for (const c of chipsHost.children) c.setAttribute("aria-pressed", String(c === chip));
  $("primer-toggle").hidden = false;
  $("start-drill").hidden = false;
  $("primer").hidden = true;
  $("primer-toggle").textContent = "Peek at the numbers first";
  $("run").hidden = true;
  $("summary").hidden = true;
  renderPrimer();
}

// ---------- entry showcase (the drill introducing itself) ----------

const SHOWCASE_NUMBERS = [7, 12, 21, 33, 47, 64, 88, 99, 101, 111];
let showcaseIdx = Math.floor(Math.random() * LANGS.length);
let showcaseTimer = null;

function showcaseStep(l) {
  const n = SHOWCASE_NUMBERS[Math.floor(Math.random() * SHOWCASE_NUMBERS.length)];
  $("showcase-flag").textContent = l.flag;
  $("showcase-numeral").textContent = String(n);
  const word = $("showcase-word");
  word.lang = l.code;
  word.textContent = WebTrainer.spellNumber(n, l.code);
  const el = $("showcase");
  el.classList.remove("swap");
  void el.offsetWidth; // restart the fade
  el.classList.add("swap");
}

function startShowcase() {
  showcaseStep(lang ?? LANGS[showcaseIdx]);
  if (reducedMotion || showcaseTimer) return; // static under reduced motion
  showcaseTimer = setInterval(() => {
    if (!lang) showcaseIdx = (showcaseIdx + 1) % LANGS.length;
    showcaseStep(lang ?? LANGS[showcaseIdx]);
  }, 2600);
}

function stopShowcase() {
  clearInterval(showcaseTimer);
  showcaseTimer = null;
}

startShowcase();

// ---------- primer (the engine's own numbers page, so it cannot drift) ----------

// The kern names each band; the page gives it a heading in the site's voice.
const BAND_TITLES = {
  ones: "The seeds",
  teens: "Ten to nineteen",
  tens: "The tens",
  twenties: "The twenties",
  compounds: "Put together",
  hundreds: "Hundreds",
  places: "The big places",
  forms: "Other ways to write it",
};

function bandTable(band) {
  const rows = Array.from(band.entries)
    .map((e) => `<tr><td class="n">${e.value}</td><td class="w" lang="${lang.code}">${e.reading}</td></tr>`)
    .join("");
  return `<table><caption>${BAND_TITLES[band.key] ?? band.key}</caption>${rows}</table>`;
}

function renderPrimer() {
  const bands = Array.from(WebTrainer.reference(lang.code));
  $("primer").innerHTML = `<div class="cols">${bands.map(bandTable).join("")}</div>`;
}

$("primer-toggle").addEventListener("click", () => {
  const p = $("primer");
  p.hidden = !p.hidden;
  $("primer-toggle").textContent = p.hidden ? "Peek at the numbers first" : "Tuck the numbers away";
});

// ---------- run state (page-owned policy, mirroring the iOS session) ----------

const run = {
  level: 1,
  streak: 0,
  best: 0,
  cleanAtLevel: 0,
  outcomes: [], // "ok" | "near" | "miss"
  seenDigits: new Set(),
  task: null,
  prevPrompt: null,
  locked: false,
  hintUsed: false,
  nudged: false,
  muted: false,
  advancedAt: 0,
};

$("start-drill").addEventListener("click", startRun);
$("again").addEventListener("click", startRun);

function startRun() {
  drill = new web.NumbersDrill(lang.code, Date.now() % 0x7fffffff, lang.articles);
  Object.assign(run, {
    level: 1, streak: 0, best: 0, cleanAtLevel: 0, outcomes: [],
    seenDigits: new Set(), task: null, prevPrompt: null,
    locked: false, hintUsed: false, nudged: false,
  });
  stopShowcase();
  $("lang-pick").hidden = true;
  $("summary").hidden = true;
  $("nudge").hidden = true;
  $("run").hidden = false;
  $("progress").innerHTML = "";
  $("tens-link").hidden = !tensBand();
  nextTask();
}

function nextTask() {
  let task = drill.sample(run.level);
  if (task.prompt === run.prevPrompt) task = drill.sample(run.level); // no back-to-back repeat
  run.task = task;
  run.prevPrompt = task.prompt;
  run.locked = false;
  run.hintUsed = false;
  run.advancedAt = performance.now();

  $("numeral").textContent = task.promptDisplay;
  $("reveal").hidden = true;
  $("correction").hidden = true;
  $("tens-card").hidden = true;
  $("answer").dataset.state = "idle";
  // why: the previous verdict stays in the live region across the swap —
  // clearing it here raced screen readers out of ever announcing it.
  const input = $("answer-input");
  input.value = "";
  input.readOnly = false;
  input.lang = lang.code;
  input.placeholder = `… in ${lang.name}`;
  input.focus({ preventScroll: true });
  setAction("Reveal");

  const hint = WebTrainer.placeValueHint(task.digits, lang.code);
  const fresh = hint && !run.seenDigits.has(task.digits);
  $("hint").hidden = !fresh;
  if (fresh) $("hint").textContent = `New place: ${hint}`;
  run.seenDigits.add(task.digits);
  updateHead();
}

function updateHead() {
  const bits = [`Sprosse ${run.level}`]; // the level is a rung, and the pun is the point
  if (run.streak > 0) bits.push(`streak ${run.streak}`);
  if (run.best > run.streak) bits.push(`best ${run.best}`);
  const line = $("streak-line");
  line.textContent = bits.join(" · ");
  line.dataset.live = String(run.streak > 0);
  renderRungs();
}

/** The ladder fills from the bottom up — top rung is the last one earned. */
function renderRungs() {
  const host = $("rungs");
  host.innerHTML = "";
  for (let i = MAX_LEVEL; i >= 1; i--) {
    const rung = document.createElement("span");
    if (i <= run.level) rung.className = "on";
    host.appendChild(rung);
  }
}

function setAction(label) {
  $("main-action").textContent = label;
}

/** English frame around a target-language word, tagged so screen readers switch voice. */
function announce(prefix, word) {
  const region = $("feedback");
  region.textContent = prefix;
  if (word) {
    const span = document.createElement("span");
    span.lang = lang.code;
    span.textContent = word;
    region.append(" ", span);
  }
}

// ---------- answering ----------

const input = $("answer-input");

input.addEventListener("input", () => {
  if (run.locked) return;
  setAction(input.value.trim() ? "Check" : "Reveal");
  // Finishing the word IS the answer — but only once no accepted form still extends it.
  const typed = input.value.trim();
  if (!typed) return;
  const verdict = drill.grade(typed, run.task);
  if (verdict.verdict === "exact" && !stillGrowing(typed)) settle("exact", null, 450);
});

input.addEventListener("keydown", (e) => {
  if (e.key === "Enter") { e.preventDefault(); $("main-action").click(); }
});

function normLite(s) {
  return s.normalize("NFC").toLowerCase().replace(/ß/g, "ss").replace(/[-'’]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ").trim().replace(/\s+/g, " ");
}

function stillGrowing(typed) {
  const t = normLite(typed);
  return Array.from(run.task.accepted).some((a) => {
    const n = normLite(a);
    return n !== t && n.startsWith(t);
  });
}

$("main-action").addEventListener("click", () => {
  if (run.locked) { advance(); return; }
  // why: a bounced Enter right after an advance would reveal-and-miss a task
  // the visitor never saw — inside the grace window the press is the echo of
  // the one that advanced, not a decision about the new number.
  if (performance.now() - run.advancedAt < ADVANCE_GRACE_MS) return;
  const typed = input.value.trim();
  if (!typed) { settle("wrong", null, null, true); return; } // Reveal = a miss
  const verdict = drill.grade(typed, run.task);
  if (verdict.verdict === "exact") settle("exact", null, 1200);
  else if (verdict.verdict === "typo") settle("typo", verdict.corrected, null);
  else settle("wrong", null, null);
});

function settle(kind, corrected, autoAdvanceMs, emptyReveal) {
  run.locked = true;
  // why: readOnly, never disabled — disabling blurs the field and takes the
  // mobile keyboard with it, and a programmatic refocus cannot bring it back.
  input.readOnly = true;
  $("hint").hidden = true;
  const accepted = kind === "exact" || kind === "typo";
  const clean = kind === "exact" && !run.hintUsed;

  if (accepted) {
    // Any accepted answer extends the streak (as in the app); only a CLEAN
    // exact counts toward ramping the level.
    run.streak += 1;
    run.best = Math.max(run.best, run.streak);
  }

  if (kind === "exact") {
    run.outcomes.push(run.hintUsed ? "near" : "ok");
    $("answer").dataset.state = "exact";
    chime("correct");
    announce("Right!", null);
    if (clean) {
      run.cleanAtLevel += 1;
      if (run.cleanAtLevel >= 2 && run.level < MAX_LEVEL) { run.level += 1; run.cleanAtLevel = 0; }
    }
  } else if (kind === "typo") {
    run.outcomes.push("near");
    $("answer").dataset.state = "typo";
    chime("correct"); // a typo is an accepted answer — the app chimes it right
    $("correction").hidden = false;
    $("correction").innerHTML = "";
    $("correction").lang = lang.code;
    $("correction").append(speakable(corrected));
    announce("Almost —", corrected);
    speak(corrected);
  } else {
    run.outcomes.push("miss");
    run.streak = 0;
    run.cleanAtLevel = 0;
    run.level = Math.max(1, run.level - 1);
    $("answer").dataset.state = "wrong";
    chime(emptyReveal ? "reveal" : "wrong"); // revealing is not a verdict
    $("reveal").hidden = false;
    $("reveal").innerHTML = "";
    $("reveal").lang = lang.code;
    $("reveal").append(speakable(run.task.display));
    announce("It reads:", run.task.display);
    if (!$("tens-link").hidden) showTens(false);
    speak(run.task.display);
  }

  paintProgress();
  updateHead();

  // why: under reduced motion a timed advance would snatch the feedback away —
  // the explicit Next button replaces the timer, like the app under VoiceOver.
  if (autoAdvanceMs != null && !reducedMotion) {
    setTimeout(advance, autoAdvanceMs);
  } else {
    setAction("Next");
    $("main-action").focus();
  }
  maybeNudge();
}

function advance() {
  if (!run.locked) return;
  nextTask();
}

function paintProgress() {
  const seg = document.createElement("span");
  seg.className = run.outcomes[run.outcomes.length - 1];
  $("progress").appendChild(seg);
}

function maybeNudge() {
  if (run.nudged || run.outcomes.length < NUDGE_AFTER) return;
  run.nudged = true;
  $("nudge").hidden = false;
}

$("nudge-close").addEventListener("click", () => {
  $("nudge").hidden = true;
  input.focus({ preventScroll: true });
});

// ---------- tens reference (Swahili) ----------

$("tens-link").addEventListener("click", () => showTens(true));

function tensBand() {
  return Array.from(WebTrainer.reference(lang.code)).find((b) => b.key === "tens");
}

function showTens(costsTheRamp) {
  const band = tensBand();
  if (!band) return;
  $("tens-card").hidden = false;
  $("tens-card").lang = lang.code;
  $("tens-card").textContent = Array.from(band.entries)
    .map((e) => `${e.value} ${e.reading}`)
    .join(" · ");
  if (costsTheRamp && !run.locked) run.hintUsed = true; // a looked-up answer never ramps
}

// ---------- finish / summary ----------

$("finish").addEventListener("click", () => {
  const n = run.outcomes.length;
  if (n === 0) { backToPick(); return; }
  $("run").hidden = true;
  const tier = run.best >= 10 ? "🏆" : run.best >= 5 ? "🎉" : run.best >= 2 ? "💪" : "🌱";
  $("summary-emoji").textContent = tier;
  $("summary-count").textContent = `${n} ${n === 1 ? "number" : "numbers"}`;
  $("summary-best").textContent = run.best > 0 ? `Best streak: ${run.best}` : "Every seed starts somewhere.";
  $("summary-lang").textContent = `Numbers · ${lang.name}`;
  $("summary-record").hidden = !beatsRecord();
  $("summary").hidden = false;
  $("summary-count").focus({ preventScroll: true });
});

// The cheer is the record's alone, like the app: no per-close celebration.
function beatsRecord() {
  if (run.best === 0) return false;
  const key = `spross.record.numbers.${lang.code}`;
  try {
    if (run.best <= Number(localStorage.getItem(key) || 0)) return false;
    localStorage.setItem(key, String(run.best));
  } catch {
    return false; // storage denied — nothing to beat, nothing to celebrate
  }
  chime("cheer");
  return true;
}

function backToPick() {
  $("run").hidden = true;
  $("summary").hidden = true;
  $("lang-pick").hidden = false;
  startShowcase();
  chipsHost.querySelector('[aria-pressed="true"]')?.focus({ preventScroll: true });
}

$("switch-lang").addEventListener("click", backToPick);

// ---------- speech (answer-side only, like the app) ----------

let voice;

function findVoice() {
  if (!("speechSynthesis" in window) || !lang) return null;
  return speechSynthesis.getVoices().find((v) => v.lang.replace("_", "-").startsWith(lang.code)) || null;
}

if ("speechSynthesis" in window) {
  speechSynthesis.addEventListener?.("voiceschanged", () => { voice = findVoice(); });
}

function speak(text) {
  if (run.muted || !("speechSynthesis" in window)) return;
  voice = voice && voice.lang.startsWith(lang.code) ? voice : findVoice();
  if (!voice) return;
  const u = new SpeechSynthesisUtterance(text);
  u.voice = voice;
  u.lang = lang.speech;
  setTimeout(() => speechSynthesis.speak(u), 300); // let the moment land first
}

function speakable(text) {
  const frag = document.createDocumentFragment();
  frag.append(text);
  if ("speechSynthesis" in window && findVoice()) {
    const btn = document.createElement("button");
    btn.className = "icon-btn";
    btn.type = "button";
    btn.textContent = "🔈";
    btn.setAttribute("aria-label", `Hear “${text}”`);
    btn.addEventListener("click", () => { const m = run.muted; run.muted = false; speak(text); run.muted = m; });
    frag.append(" ", btn);
  }
  return frag;
}

$("mute").addEventListener("click", () => {
  run.muted = !run.muted;
  $("mute").textContent = run.muted ? "🔇" : "🔊";
  $("mute").setAttribute("aria-pressed", String(run.muted));
});

// ---------- mailing list ----------

$("signup-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const note = $("signup-note");
  const email = new FormData(e.target).get("email");
  if (!SIGNUP_ENDPOINT) {
    note.textContent = "The list isn't open quite yet — write to feedback@spross.net and we'll plant you in by hand.";
    return;
  }
  try {
    await fetch(SIGNUP_ENDPOINT, {
      method: "POST",
      mode: "no-cors",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ email }),
    });
    note.textContent = "Welcome, Sprössling. 🌱 Check your inbox to confirm.";
    note.className = "note thanks";
    e.target.hidden = true;
  } catch {
    note.textContent = "That didn't take root — please try again, or write to feedback@spross.net.";
  }
});
