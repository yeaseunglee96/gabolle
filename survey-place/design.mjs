#!/usr/bin/env node
/**
 * 짝 비교 문항을 만든다 — 손으로 적지 않는다.
 *
 * 🔴 왜 손으로 안 적나.
 *    사람이 12쌍을 지어내면 자기도 모르게 "가격이 싼 쪽이 대체로 좋아 보이는" 쌍만
 *    만든다. 그러면 나중에 계산이 내는 것은 사람들의 취향이 아니라 **문항을 만든
 *    사람의 취향**이다. 그리고 그건 결과만 봐서는 안 보인다.
 *
 * 🔴 씨앗(seed)을 바꾸지 않는다.
 *    씨앗이 같으면 언제 돌려도 같은 12쌍이 나온다. 회차를 나눠 받은 응답을 나중에
 *    합치려면 **모두 같은 문항에 답했어야** 한다. 씨앗을 바꾸면 그 순간 앞의 응답과
 *    뒤의 응답이 서로 다른 설문이 된다.
 *
 * 쓰는 법:
 *    node survey-place/design.mjs            # index.html 안의 문항 블록을 다시 쓴다
 *    node survey-place/design.mjs --print    # 화면에만 찍는다 (파일 안 건드림)
 */

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/* ── 씨앗 ─────────────────────────────────────────────────────────────── */
const SEED = 20260907;
const DESIGN_ID = "place-v1-seed-20260907";
const N_REAL = 11;      // 진짜 문항
const TRAP_AT = 5;      // 함정 문항이 들어갈 자리 (0부터 세서 6번째)

/* ── 속성 다섯 ─────────────────────────────────────────────────────────
   방향이 합의된 셋(price·walkMin·queueMin — 작을수록 좋다)과
   방향을 우리가 **모르는** 둘(sameStreet·fame)로 나뉜다.
   모르는 둘이 이 설문의 진짜 질문이다.
   ─────────────────────────────────────────────────────────────────── */
const ATTRS = {
  price:      { levels: [10000, 20000, 40000, 70000], lowerIsBetter: true  },
  walkMin:    { levels: [3, 8, 15, 25],               lowerIsBetter: true  },
  queueMin:   { levels: [0, 20, 40],                  lowerIsBetter: true  },
  sameStreet: { levels: [0, 3, 12],                   lowerIsBetter: null  }, // 같은 골목의 동종 가게 수
  fame:       { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null  }
};
const KEYS = Object.keys(ATTRS);
const KNOWN = KEYS.filter(k => ATTRS[k].lowerIsBetter === true);
const UNKNOWN = KEYS.filter(k => ATTRS[k].lowerIsBetter === null);

/* ── 난수 — 씨앗을 주면 매번 같은 수열 ─────────────────────────────── */
let state = SEED >>> 0;
const rand = () => ((state = (state * 1664525 + 1013904223) >>> 0) / 4294967296);
const pick = arr => arr[Math.floor(rand() * arr.length)];

/* ── 후보 전체 (4 × 4 × 3 × 3 × 2 = 288) ───────────────────────────── */
function allAlternatives() {
  let out = [{}];
  for (const k of KEYS) {
    const next = [];
    for (const partial of out) for (const v of ATTRS[k].levels) next.push({ ...partial, [k]: v });
    out = next;
  }
  return out;
}

/* ── 이 쌍이 물어볼 값이 있나 ───────────────────────────────────────── */
function differCount(a, b) { return KEYS.filter(k => a[k] !== b[k]).length; }

/**
 * 🔴 답이 뻔한 쌍을 뺀다.
 *    한쪽이 가격·거리·줄서기 셋 모두에서 나쁘지 않고 하나라도 낫고,
 *    나머지 둘(골목·알려진 정도)이 **같다면** 아무도 나쁜 쪽을 안 고른다.
 *    그런 쌍은 12개 중 한 자리를 먹고 아무것도 안 알려준다.
 *
 *    반대로 셋에서 나은데 나머지 둘이 **다르면** 그건 뻔하지 않다 —
 *    "얼마를 더 걸어야 유명한 집에 갈 만한가" 가 정확히 우리가 묻는 것이다.
 */
function obviouslyOneSided(a, b) {
  if (UNKNOWN.some(k => a[k] !== b[k])) return false;
  const aWins = KNOWN.every(k => a[k] <= b[k]) && KNOWN.some(k => a[k] < b[k]);
  const bWins = KNOWN.every(k => b[k] <= a[k]) && KNOWN.some(k => b[k] < a[k]);
  return aWins || bWins;
}

/* ── 수준이 골고루 나오게 ────────────────────────────────────────────
   한 수준이 12쌍에 한 번도 안 나오면 그 수준의 무게는 못 잰다.
   그래서 아직 적게 나온 수준을 쓰는 쌍을 먼저 고른다.
   ─────────────────────────────────────────────────────────────────── */
function levelKey(k, v) { return k + "=" + v; }
function costOf(pair, counts) {
  let c = 0;
  for (const alt of pair) for (const k of KEYS) c += counts.get(levelKey(k, alt[k])) ?? 0;
  return c;
}
function bump(pair, counts) {
  for (const alt of pair) for (const k of KEYS) {
    const key = levelKey(k, alt[k]);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
}

function buildSets() {
  const pool = allAlternatives();
  const counts = new Map();
  for (const k of KEYS) for (const v of ATTRS[k].levels) counts.set(levelKey(k, v), 0);

  const chosen = [];
  const seen = new Set();

  while (chosen.length < N_REAL) {
    /* 후보 쌍을 여러 개 뽑아 두고 그중 가장 골고루인 것을 고른다 */
    const cands = [];
    let guard = 0;
    while (cands.length < 40 && guard++ < 20000) {
      const a = pick(pool), b = pick(pool);
      if (differCount(a, b) < 3) continue;            // 너무 비슷하면 답이 흐려진다
      if (obviouslyOneSided(a, b)) continue;          // 답이 뻔하면 자리를 낭비한다
      const sig = JSON.stringify([a, b]);
      const rev = JSON.stringify([b, a]);
      if (seen.has(sig) || seen.has(rev)) continue;   // 같은 쌍을 두 번 묻지 않는다
      cands.push([a, b]);
    }
    if (cands.length === 0) throw new Error("쌍을 못 만들었다 — 제약이 너무 빡빡하다");
    cands.sort((x, y) => costOf(x, counts) - costOf(y, counts));
    const best = cands[0];
    seen.add(JSON.stringify(best));
    bump(best, counts);
    chosen.push(best);
  }

  /* 🔴 함정 문항 — 가격은 같고 거리·줄서기만 나쁜 쪽.
        "비싼 게 맛있겠지" 같은 진짜 취향이 끼어들 자리를 없앤다.
        여기서 나쁜 쪽을 고르면 읽지 않고 찍은 것으로 본다. */
  const trap = [
    { price: 20000, walkMin: 3,  queueMin: 0,  sameStreet: 3, fame: "LOCAL_ONLY" },
    { price: 20000, walkMin: 25, queueMin: 40, sameStreet: 3, fame: "LOCAL_ONLY" }
  ];

  const sets = [];
  let n = 0;
  for (let i = 0; i < N_REAL + 1; i++) {
    if (i === TRAP_AT) {
      sets.push({ setId: "tr01", trap: true, trapCorrect: 0, alternatives: trap });
    } else {
      n += 1;
      sets.push({ setId: "s" + String(n).padStart(2, "0"), trap: false, alternatives: chosen[n - 1] });
    }
  }
  return sets;
}

/* ── 내보내기 ──────────────────────────────────────────────────────── */
const design = {
  designId: DESIGN_ID,
  seed: SEED,
  attributes: Object.fromEntries(KEYS.map(k => [k, ATTRS[k].levels])),
  sets: buildSets()
};

const json = JSON.stringify(design, null, 2);

if (process.argv.includes("--print")) {
  console.log(json);
} else {
  const here = dirname(fileURLToPath(import.meta.url));
  const target = join(here, "index.html");
  const html = readFileSync(target, "utf8");
  const open = '<script id="design" type="application/json">';
  const close = "<" + "/script>";
  const i = html.indexOf(open);
  if (i < 0) { console.error("index.html 에 문항 블록이 없다"); process.exit(1); }
  const j = html.indexOf(close, i);
  const next = html.slice(0, i + open.length) + "\n" + json + "\n" + html.slice(j);
  writeFileSync(target, next, "utf8");
  console.log("index.html 의 문항 " + design.sets.length + "개를 다시 썼다 (" + DESIGN_ID + ")");
}
