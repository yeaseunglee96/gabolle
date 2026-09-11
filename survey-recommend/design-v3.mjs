#!/usr/bin/env node
/**
 * 짝 비교 v3 — 여덟 취향축을 덩어리 셋으로 잰다. (S15P21E201-842)
 *
 * 🔴 이 파일은 아직 **설문에 안 붙었다.** `--print` 로 검토만 한다.
 *    `design.mjs`(v2)가 지금 살아 있는 설문(/survey/)을 만들고 있고, 이 파일을
 *    그 자리에 넣으려면 아래 넷이 **같은 MR 에서** 함께 가야 한다.
 *
 *      1) migrations/0007 — pairwise_choice 를 JSONB 로. 지금은 alt0_price 처럼
 *         칸이 박혀 있어 덩어리마다 다른 속성을 담을 수 없다
 *      2) server.mjs      — 속성 검사와 INSERT 를 새 모양으로
 *      3) index.html      — 문항 블록 재생성 + 카드 문구 (덩어리마다 다르다)
 *      4) form_version    — 7 로 올린다
 *
 *    하나라도 빠지면 사람이 화면에서 통과하고 DB 에서 거절당한다.
 *
 * ── 왜 다시 짜나 ──────────────────────────────────────────────────────
 * v2 는 식당 하나에서 속성 다섯만 잰다. 그런데 추천은 갈래를 섞어서 낸다.
 * 식당에서 잰 「줄 40분」의 무게를 전망대에 그대로 쓰면, 원래 줄을 서는 전망대가
 * 전부 밀린다. 그리고 「한 끼 가격」은 자연·산책에서 말 자체가 안 된다.
 *
 * ── 이 설문이 맡는 것과 안 맡는 것 ────────────────────────────────────
 *   앱 온보딩 → 이 사람이 무엇을 원하나 (개인 값)
 *   이 설문   → 그 원함이 선택을 얼마나 움직이나 (모두에게 공통인 환율)
 *   장소 자료 → 그 장소가 어떤가 (특성값)
 *
 * 🔴 그래서 이 설문은 개인 프로필을 안 받는다. 환율은 원래 모두 공통이라
 *    익명이어도 된다. 설문 DB 에 사람을 가리키는 칸이 없는 것과 충돌하지 않는다.
 *
 * ── 🔴 공통 칸 하나가 반드시 있어야 한다 ──────────────────────────────
 * 짝 비교 계산은 계수의 크기를 못 정하고 비율만 정한다. 덩어리마다 따로 계산하면
 * 각 덩어리의 숫자에 보이지 않는 배율이 곱해지고, 그 배율은 덩어리마다 다르다.
 * 덩어리를 섞어 한 목록으로 낼 때 이걸 안 맞추면 결론이 뒤집힌다.
 *
 * 그 공통 칸이 walkMin(역에서 걸어서)이다. 가격이 아닌 이유는 셋이다.
 *   ① 자연·산책에서 「한 끼 가격」이 말이 안 된다
 *   ② 우리 DB 는 원이 아니라 레벨(그나마 ESTIMATED)로 갖고 있다
 *   ③ 팀이 이미 "가격대는 비싼 정도이지 맛이 아니다" 라며 무게로 안 쓰기로 정했다
 *
 * 쓰는 법:
 *    node survey-recommend/design-v3.mjs --check    # 균형·배분·표본을 본다
 *    node survey-recommend/design-v3.mjs            # 문항 JSON 을 찍는다
 */

/* ── 씨앗 ─────────────────────────────────────────────────────────────
   🔴 v2 와 다른 씨앗·다른 이름이다. 속성이 바뀌었으므로 v2 응답과 합칠 수 없다.
      같은 setId 아래 서로 다른 질문이 생기는 것을 막으려고 이름부터 가른다.
      (survey-place 는 2026-09-11 에 지웠다 — 합칠 대상이 애초에 없었다.) */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const SEED = 20260911;
const DESIGN_ID = "recommend-v3-seed-20260911";

/* ── 공통 칸 — 모든 카드에 같은 수준으로 들어간다 ────────────────────
   🔴 수준을 덩어리마다 바꾸지 않는다. 바꾸는 순간 자가 아니게 된다. */
const COMMON = {
  walkMin: { levels: [3, 8, 15, 25], lowerIsBetter: true }
};

/* ── 덩어리 셋 ────────────────────────────────────────────────────────
   갈래 여덟을 셋으로 묶는다. 여덟로 다 쪼개면 계수가 33개가 되어 100명으로도 모자란다.
   묶는 기준은 그 자리에서 무엇을 저울질하나다.

   🔴 카드 한 장에 다섯 칸까지만 둔다 (공통 1 + 덩어리 4). 사람이 한 번에
      비교할 수 있는 한계다. 더 넣으면 아래 칸을 안 읽고 위 두 줄로만 고른다. */
const BLOCKS = {
  eat: {
    label: "먹는 곳",
    types: ["FOOD", "CAFE", "BAR"],
    lead: "저녁 먹을 곳을 고른다면",
    attrs: {
      price:      { levels: [10000, 20000, 40000, 70000], lowerIsBetter: true, axis: "SPEND_PROFILE" },
      queueMin:   { levels: [0, 20, 40],                  lowerIsBetter: true, axis: "QUIETNESS" },
      sameStreet: { levels: [0, 3, 12],                   lowerIsBetter: null, axis: "ATMOSPHERE" },
      fame:       { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  },
  see: {
    label: "보는 곳",
    types: ["SIGHT", "NATURE", "CULTURE"],
    lead: "구경할 곳을 고른다면",
    attrs: {
      /* 🔴 경사·그늘이 여기 있다. v2 주석은 그건 빅데이터의 경로 비용 몫이라고
            적었는데, 그쪽이 재는 것은 길의 비용이고 여기서 재는 것은
            그 장소를 그래도 갈 만한가다. 다른 질문이다. */
      slope: { levels: ["FLAT", "GENTLE", "STEEP"],   lowerIsBetter: true, axis: "SLOPE_PREFERENCE" },
      shade: { levels: ["SHADED", "SUNNY"],           lowerIsBetter: null, axis: "SHADE_PREFERENCE" },
      crowd: { levels: ["QUIET", "SOME", "PACKED"],   lowerIsBetter: null, axis: "QUIETNESS" },
      fame:  { levels: ["LOCAL_ONLY", "SNS_FAMOUS"],  lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  },
  play: {
    label: "하는 곳",
    types: ["MARKET", "ACTIVITY"],
    lead: "한 가지 해 본다면",
    attrs: {
      cost:   { levels: [0, 10000, 30000],           lowerIsBetter: true, axis: "SPEND_PROFILE" },
      indoor: { levels: ["INDOOR", "OUTDOOR"],       lowerIsBetter: null, axis: "SHADE_PREFERENCE" },
      crowd:  { levels: ["QUIET", "SOME", "PACKED"], lowerIsBetter: null, axis: "ATMOSPHERE" },
      fame:   { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  }
};

/* ── 문항 배분 ────────────────────────────────────────────────────────
   덩어리마다 2 + 덩어리가 섞인 문항 1 + 함정 1 = 8. 화면 4장 × 2문항.
   🔴 지금(v2)과 같은 길이다. 8축을 다 재면서 설문이 안 길어진다.

   🔴 섞인 문항이 두 가지 일을 한다.
      ① 갈래 선호(CATEGORY 축) — 한 문항 안에서 덩어리가 바뀌어야 계수가 생긴다.
         두 카드가 둘 다 먹는 곳이면 덩어리는 빼면 0 이라 식에서 사라진다.
      ② 자 맞추기 — 두 덩어리가 같은 문항 안에 놓이므로 배율이 저절로 맞는다. */
const PER_BLOCK = 2;
const MIXED     = 1;
const PER_PAGE  = 2;
const PAGES     = 4;
const TRAP_AT   = 3;          /* 0부터 세서 네 번째 자리 */

/* ── 🔴 표본이 얼마나 필요한가 (계수 × 20) ────────────────────────────
   공통 걷기 1 + 덩어리 셋 × 속성 4 = 13, + 덩어리 자체 2(셋 중 기준 하나 제외) = 15.
   필요 관측 300. 1인당 진짜 7문항이면 약 43명. 100명이면 2배 여유다.
   🔴 ×20 은 경험칙이지 보장이 아니다. 답이 한쪽으로 몰리면 더 필요하다. */
const OBS_PER_COEF = 20;

/* ── 난수 — 씨앗을 주면 매번 같은 수열 ─────────────────────────────── */
let state = SEED >>> 0;
const rand = () => ((state = (state * 1664525 + 1013904223) >>> 0) / 4294967296);
const pick = arr => arr[Math.floor(rand() * arr.length)];

/* ── 한 덩어리의 카드 전체 ─────────────────────────────────────────── */
const attrsOf = block => ({ ...COMMON, ...BLOCKS[block].attrs });

function alternativesOf(block) {
  const attrs = attrsOf(block);
  let out = [{}];
  for (const k of Object.keys(attrs)) {
    const next = [];
    for (const partial of out) for (const v of attrs[k].levels) next.push({ ...partial, [k]: v });
    out = next;
  }
  return out;
}

function differCount(a, b, keys) { return keys.filter(k => a[k] !== b[k]).length; }

/**
 * 🔴 지배되는 쌍(한쪽이 사실상 모든 조건에서 나은 쌍)을 뺀다.
 *    방향이 합의된 속성(작을수록 좋다) 전부에서 나쁘지 않고 하나라도 나은데
 *    방향을 모르는 속성이 전부 같다면, 아무도 나쁜 쪽을 안 고른다.
 *    그런 쌍은 자리를 먹고 아무것도 안 알려준다.
 *    반대로 모르는 쪽이 다르면 그건 뻔하지 않다 — 그게 우리가 묻는 것이다.
 */
function obviouslyOneSided(a, b, attrs) {
  const keys = Object.keys(attrs);
  const known   = keys.filter(k => attrs[k].lowerIsBetter === true);
  const unknown = keys.filter(k => attrs[k].lowerIsBetter === null);
  if (unknown.some(k => a[k] !== b[k])) return false;
  const rank = (k, v) => attrs[k].levels.indexOf(v);   /* 수준 배열이 곧 순서다 */
  const aWins = known.every(k => rank(k, a[k]) <= rank(k, b[k])) && known.some(k => rank(k, a[k]) < rank(k, b[k]));
  const bWins = known.every(k => rank(k, b[k]) <= rank(k, a[k])) && known.some(k => rank(k, b[k]) < rank(k, a[k]));
  return aWins || bWins;
}

/* ── 수준이 골고루 나오게 ────────────────────────────────────────────
   한 수준이 한 번도 안 나오면 그 수준의 무게는 못 잰다. 아직 적게 나온 수준을
   쓰는 쌍을 먼저 고른다. */
const levelKey = (k, v) => k + "=" + v;

function costOf(pair, counts, keys) {
  let c = 0;
  for (const alt of pair) for (const k of keys) c += counts.get(levelKey(k, alt[k])) ?? 0;
  return c;
}

function bump(pair, counts, keys) {
  for (const alt of pair) for (const k of keys) {
    const key = levelKey(k, alt[k]);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
}

/* ── 한 덩어리에서 문항 n개 ───────────────────────────────────────── */
function setsForBlock(block, n) {
  const attrs = attrsOf(block);
  const keys  = Object.keys(attrs);
  const pool  = alternativesOf(block);
  const counts = new Map();
  for (const k of keys) for (const v of attrs[k].levels) counts.set(levelKey(k, v), 0);

  const chosen = [], seen = new Set();
  while (chosen.length < n) {
    const cands = [];
    let guard = 0;
    while (cands.length < 40 && guard++ < 40000) {
      const a = pick(pool), b = pick(pool);
      if (differCount(a, b, keys) < 3) continue;        /* 너무 비슷하면 답이 흐려진다 */
      if (obviouslyOneSided(a, b, attrs)) continue;
      const sig = JSON.stringify([a, b]), rev = JSON.stringify([b, a]);
      if (seen.has(sig) || seen.has(rev)) continue;
      cands.push([a, b]);
    }
    if (cands.length === 0) throw new Error(block + ": 쌍을 못 만들었다 — 제약이 너무 빡빡하다");
    cands.sort((x, y) => costOf(x, counts, keys) - costOf(y, counts, keys));
    const best = cands[0];
    seen.add(JSON.stringify(best));
    bump(best, counts, keys);
    chosen.push(best);
  }
  return chosen;
}

/* ── 덩어리가 섞인 문항 ───────────────────────────────────────────────
   🔴 두 카드의 덩어리가 다르다. 공통 칸(걷기)만 양쪽에 같은 뜻으로 있고,
      나머지는 각자 덩어리의 속성이다. 화면에서도 그렇게 보인다 —
      먹는 곳: 2만원 · 줄 없음  vs  보는 곳: 평지 · 그늘 */
function mixedSets(n) {
  const names = Object.keys(BLOCKS);
  const out = [], seen = new Set();
  let guard = 0;
  while (out.length < n && guard++ < 40000) {
    const b0 = pick(names);
    const b1 = pick(names.filter(x => x !== b0));
    const a = pick(alternativesOf(b0));
    const b = pick(alternativesOf(b1));
    if (a.walkMin === b.walkMin) continue;              /* 공통 칸이 안 변하면 자를 못 맞춘다 */
    const sig = b0 + "|" + b1 + "|" + JSON.stringify([a, b]);
    if (seen.has(sig)) continue;
    seen.add(sig);
    out.push({ blocks: [b0, b1], alternatives: [a, b] });
  }
  if (out.length < n) throw new Error("섞인 문항을 못 만들었다");
  return out;
}

/* ── 함정 ─────────────────────────────────────────────────────────────
   🔴 걷기·줄서기만 나쁘고 나머지가 같은 쌍. "비싼 게 좋겠지" 같은 진짜 취향이
      끼어들 자리가 없다. 나쁜 쪽(1번)을 고르면 읽지 않고 찍은 것으로 본다.
      함정은 추정에서 빠지므로 계수 셈에도 안 들어간다. */
const TRAP = {
  setId: "tr01", block: "eat", trap: true, trapCorrect: 0,
  alternatives: [
    { walkMin: 3,  price: 20000, queueMin: 0,  sameStreet: 3, fame: "LOCAL_ONLY" },
    { walkMin: 25, price: 20000, queueMin: 40, sameStreet: 3, fame: "LOCAL_ONLY" }
  ]
};

/* ── 조립 ─────────────────────────────────────────────────────────── */
function buildSets() {
  const real = [];
  for (const b of Object.keys(BLOCKS)) {
    setsForBlock(b, PER_BLOCK).forEach((alts, i) => {
      real.push({ setId: b + String(i + 1).padStart(2, "0"), block: b, trap: false, alternatives: alts });
    });
  }
  mixedSets(MIXED).forEach((m, i) => {
    real.push({ setId: "mx" + String(i + 1).padStart(2, "0"), blocks: m.blocks, trap: false, alternatives: m.alternatives });
  });

  const sets = [];
  let k = 0;
  for (let i = 0; i < real.length + 1; i++) {
    if (i === TRAP_AT) sets.push(TRAP);
    else { sets.push(real[k]); k += 1; }
  }
  return sets;
}

/* ── 응답 시간 눈금 — 초가 아니라 구간으로만 (v2 와 같다) ──────────── */
const MS_BUCKETS = [1500, 3000, 5000, 8000, 12000, 20000, 40000];

/* ── 계수와 표본 ─────────────────────────────────────────────────── */
function coefficientCount() {
  let n = Object.keys(COMMON).length;                         /* 공통 칸 */
  for (const b of Object.keys(BLOCKS)) n += Object.keys(BLOCKS[b].attrs).length;
  n += Object.keys(BLOCKS).length - 1;                        /* 덩어리 자체 (기준 하나 제외) */
  return n;
}

const sets = buildSets();
const realCount = sets.filter(s => !s.trap).length;
const coefs = coefficientCount();
const needObs = coefs * OBS_PER_COEF;
const needPeople = Math.ceil(needObs / realCount);

const design = {
  designId: DESIGN_ID,
  seed: SEED,
  perPage: PER_PAGE,
  msBuckets: MS_BUCKETS,
  common: Object.fromEntries(Object.keys(COMMON).map(k => [k, COMMON[k].levels])),
  blocks: Object.fromEntries(Object.keys(BLOCKS).map(b => [b, {
    label: BLOCKS[b].label,
    lead: BLOCKS[b].lead,
    types: BLOCKS[b].types,
    attributes: Object.fromEntries(Object.keys(BLOCKS[b].attrs).map(k => [k, BLOCKS[b].attrs[k].levels])),
    axes: Object.fromEntries(Object.keys(BLOCKS[b].attrs).map(k => [k, BLOCKS[b].attrs[k].axis]))
  }])),
  sets
};

/* 🔴 화면에 딱 안 떨어지면 여기서 멈춘다. 숫자를 고친 사람이 화면을 열어 보기 전에 알아야 한다. */
if (sets.length !== PER_PAGE * PAGES) {
  console.error("문항이 " + sets.length + "개다 — 화면 " + PAGES + "장 × " + PER_PAGE + "문항 = " + (PER_PAGE * PAGES) + "개여야 한다.");
  process.exit(1);
}

if (process.argv.includes("--check")) {
  console.log("설계  : " + DESIGN_ID);
  console.log("문항  : " + sets.length + " (진짜 " + realCount + " + 함정 " + (sets.length - realCount) + ") · 화면 " + PAGES + "장 × " + PER_PAGE);
  console.log("계수  : " + coefs + "  (공통 " + Object.keys(COMMON).length + " + 덩어리별 속성 + 덩어리 " + (Object.keys(BLOCKS).length - 1) + ")");
  console.log("표본  : 관측 " + needObs + "개 필요 → 1인당 " + realCount + "문항이면 최소 " + needPeople + "명");
  for (const s of sets) {
    const where = s.trap ? "함정"
      : (s.blocks ? s.blocks.map(b => BLOCKS[b].label).join(" ↔ ") : BLOCKS[s.block].label);
    console.log("  " + s.setId.padEnd(6) + " " + where);
  }
  process.exit(0);
}

const json = JSON.stringify(design, null, 2);

if (process.argv.includes("--print")) {
  console.log(json);
} else {
  /* index.html 안의 문항 블록을 통째로 다시 쓴다 (v2 design.mjs 와 같은 방식) */
  const here = dirname(fileURLToPath(import.meta.url));
  const target = join(here, "index.html");
  const html = readFileSync(target, "utf8");
  const open = '<script id="design" type="application/json">';
  const close = "<" + "/script>";
  const i = html.indexOf(open);
  if (i < 0) { console.error("index.html 에 문항 블록이 없다"); process.exit(1); }
  const j = html.indexOf(close, i);
  const LF = String.fromCharCode(10);
  writeFileSync(target, html.slice(0, i + open.length) + LF + json + LF + html.slice(j), "utf8");
  console.log("index.html 의 문항 " + design.sets.length + "개를 다시 썼다 (" + DESIGN_ID + ")");
}
