#!/usr/bin/env node
/**
 * 짝 비교 v4 — 문항지를 **두 벌**로 나눈다. (S15P21E201-884)
 *
 * 🔴 **이 파일이 지금 살아 있는 설문(/survey/)을 만든다.** 고치고 나면 인수 없이
 *    한 번 돌려서 index.html 의 문항 블록을 다시 써야 한다.
 *    (`--check` 는 검사만, `--print` 는 찍기만 한다.)
 *
 * ── 왜 v3 을 버리나 ───────────────────────────────────────────────────
 * v3 은 **모든 사람에게 똑같은 8문항**을 줬다. 문항 하나는 방정식 하나이므로
 * 함정을 뺀 **방정식 7개**인데, 풀어야 할 미지수(속성별 계수)는 11개였다.
 *
 *   🔴 **사람을 늘려도 안 풀린다.** 400명이 와도 다들 같은 7개를 풀고 가므로
 *      방정식은 여전히 7개다. 사람이 느는 것은 그 7개의 **답이 정확해지는 것**
 *      이지 새 방정식이 생기는 것이 아니다.
 *
 * v3 은 `coefficientCount()` 로 **"계수 15개 × 관측 20 = 300 선택 필요"** 까지
 * 계산하고 있었다. **몇 명이 필요한가는 세면서, 풀 수 있는가는 아무도 안 봤다.**
 * 그 계산을 이 파일이 직접 한다 — 못 풀면 파일을 안 쓰고 멈춘다.
 *
 * ── 무엇이 바뀌나 ─────────────────────────────────────────────────────
 *   ① **두 벌.** 사람마다 한 벌(8문항)만 받는다 — 설문 길이는 그대로다.
 *      화면이 열릴 때 반반으로 고른다. 합치면 방정식이 14개가 되어 풀린다.
 *   ② **「비슷한 가게」를 뺐다.** 아래 BLOCKS 주석 참고.
 *   ③ **검사 셋이 코드로 들어왔다.** 사람이 눈으로 지키는 규칙은 안 지켜진다.
 *
 * ── 🔴 코드가 못 재는 것 하나 ─────────────────────────────────────────
 * **"이 문항이 뻔해 보이는가" 는 코드가 못 잰다.** 그걸 재려면 속성마다의 무게를
 * 이미 알아야 하는데, 그 무게가 바로 이 설문이 재려는 값이다. 그래서 코드는
 * **객관적으로 판정되는 것**만 막는다 (한쪽이 다 이기는 쌍 · 늘 붙어 다니는 속성
 * 짝 · 못 푸는 설계). 나머지는 `--check` 가 문항마다 **어느 편이 무엇을 가져가나**
 * 를 찍어 주므로 사람이 보고 정한다.
 *
 * ── 이 설문이 맡는 것과 안 맡는 것 (v3 과 같다) ───────────────────────
 *   앱 온보딩 → 이 사람이 무엇을 원하나 (개인 값)
 *   이 설문   → 그 원함이 선택을 얼마나 움직이나 (모두에게 공통인 환율)
 *   장소 자료 → 그 장소가 어떤가 (특성값)
 *
 * 쓰는 법:
 *    node survey-recommend/design-v4.mjs --check    # 검사 결과와 문항 전부를 본다
 *    node survey-recommend/design-v4.mjs --print    # JSON 만 찍는다
 *    node survey-recommend/design-v4.mjs            # index.html 을 다시 쓴다
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

/* ── 씨앗 ─────────────────────────────────────────────────────────────
   같은 씨앗이면 같은 문항이 나온다. 검사에 걸리면 씨앗을 한 칸씩 옮겨 가며
   다시 뽑는데, **몇 번째에 통과했는지도 설계 이름에 안 넣는다** — 통과한
   설계는 하나뿐이고 그 이름이 DB 에 남아야 하기 때문이다. */
const SEED    = 20260913;
const FAMILY  = "recommend-v4-seed-20260913";
const LETTERS = ["a", "b"];
const MAX_TRIES = 4000;

/* ── 공통 칸 — 모든 카드에 같은 수준으로 들어간다 ────────────────────
   🔴 수준을 덩어리마다 바꾸지 않는다. 바꾸는 순간 자가 아니게 된다. */
const COMMON = {
  walkMin: { levels: [3, 8, 15, 25], lowerIsBetter: true }
};

/* ── 덩어리 셋 ────────────────────────────────────────────────────────
   🔴 카드 한 장에 **네 칸까지**로 줄였다 (v3 은 다섯이었다). 「비슷한 가게」를
      빼면서 먹는 곳이 네 칸이 됐고, 나머지 둘도 원래 네 칸이다. 코드의 옛
      주석이 *"더 넣으면 아래 칸을 안 읽고 위 두 줄로만 고른다"* 고 적어
      두었는데, 한 칸을 덜면 남은 칸의 신호가 그만큼 또렷해진다. */
const BLOCKS = {
  eat: {
    label: "먹는 곳",
    types: ["FOOD", "CAFE", "BAR"],
    attrs: {
      price:    { levels: [10000, 20000, 40000, 70000], lowerIsBetter: true, axis: "SPEND_PROFILE" },
      queueMin: { levels: [0, 20, 40],                  lowerIsBetter: true, axis: "QUIETNESS" },
      /* 🔴 **sameStreet(비슷한 가게 몇 곳)을 뺐다** (S15P21E201-884).
            v3 에서는 「먹자골목이냐 단독이냐」를 재려고 넣었고, 이유는 둘이었다 —
            좌표만으로 53,716곳 전부에 대해 공짜로 계산되고, 방향을 모른다.
            뺀 이유는 셋이다.
            ① **점수식에 그 값을 받을 자리가 없다.** axis 가 ATMOSPHERE 인데
               지금 점수식의 분위기 몫은 **태그 매칭**이 맡고 있다. 밀도 숫자를
               쓰려면 기능을 새로 만들어야 한다. 나머지 셋은 받을 자리가 있다
            ② **밀도의 진짜 쓸모는 취향이 아니다.** 먹자골목이 좋은 진짜 이유는
               "내 취향이 골목형" 이 아니라 **"닫혔거나 만석이면 옆으로 갈 수
               있어서"** 다. 그건 순위를 정하는 축이 아니라 **동점일 때 덜 실패할
               쪽을 고르는 기준**이고, 물어볼 것이 아니라 그냥 넣으면 된다
            ③ **자리가 없다.** 먹는 곳 문항은 벌당 2개인데 속성이 다섯이었다
            🔴 v3 때 이 축이 살아 있다는 근거로 들었던 것 — *"응답 12번이
               「조용한 쪽」이라 답하고 먹자골목 12곳을 2/2 골랐다"* — 은 **근거가
               아니었다.** 그 두 문항에서 「가게 많은 쪽」이 두 번 다 「줄 짧은
               쪽」이었다. 줄을 보고 고른 것이라면 「조용한 쪽」과 모순이 아니라
               일치한다. 그 자체가 아래 「늘 붙어 다니는 속성 짝」의 실례였다. */
      fame:     { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  },
  see: {
    label: "보는 곳",
    types: ["SIGHT", "NATURE", "CULTURE"],
    attrs: {
      slope: { levels: ["FLAT", "GENTLE", "STEEP"],   lowerIsBetter: true, axis: "SLOPE_PREFERENCE" },
      /* 🔴 daylightOnly — 밤 장면에서는 뜻이 없는 칸이다. buildSets 가 막는다. */
      shade: { levels: ["SHADED", "SUNNY"],           lowerIsBetter: null, axis: "SHADE_PREFERENCE", daylightOnly: true },
      crowd: { levels: ["QUIET", "SOME", "PACKED"],   lowerIsBetter: null, axis: "QUIETNESS" },
      fame:  { levels: ["LOCAL_ONLY", "SNS_FAMOUS"],  lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  },
  play: {
    label: "노는 곳",
    types: ["MARKET", "ACTIVITY"],
    attrs: {
      cost:   { levels: [0, 10000, 30000],            lowerIsBetter: true, axis: "SPEND_PROFILE" },
      indoor: { levels: ["INDOOR", "OUTDOOR"],        lowerIsBetter: null, axis: "SHADE_PREFERENCE" },
      /* 🔴 v3 은 노는 곳의 crowd 만 axis 를 ATMOSPHERE 로 적었다. 보는 곳의
            crowd 는 QUIETNESS 였다. **같은 칸이 덩어리마다 다른 축을 가리키면**
            계수를 하나로 합칠 수도, 둘로 나눌 수도 없다 (나누면 14개 방정식으로
            못 푼다). 붐빔은 어느 덩어리에서든 붐빔이므로 QUIETNESS 하나로 맞춘다.
            ATMOSPHERE(활기)는 점수식에서 이미 **분위기 태그 0.15** 가 맡고 있다. */
      crowd:  { levels: ["QUIET", "SOME", "PACKED"],  lowerIsBetter: null, axis: "QUIETNESS" },
      fame:   { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" }
    }
  }
};

const PER_BLOCK = 2;
const MIXED     = 1;
const PER_PAGE  = 2;
const PAGES     = 4;
const TRAP_AT   = 3;
const OBS_PER_COEF = 20;
const CANDIDATES   = 200;

/* ── 난수 — 씨앗을 주면 매번 같은 수열 ─────────────────────────────── */
let state = SEED;
const rand = () => ((state = (state * 1664525 + 1013904223) >>> 0) / 4294967296);
const pick = arr => arr[Math.floor(rand() * arr.length)];

const attrsOf = block => ({ ...COMMON, ...BLOCKS[block].attrs });

function alternativesOf(block) {
  const attrs = attrsOf(block);
  const keys = Object.keys(attrs);
  let out = [{}];
  for (const k of keys) {
    const next = [];
    for (const partial of out) for (const v of attrs[k].levels) next.push({ ...partial, [k]: v });
    out = next;
  }
  return out;
}

const differCount = (a, b, keys) => keys.filter(k => a[k] !== b[k]).length;

/* ── 🔴 검사 ① 한쪽이 「방향을 아는 칸」을 싹 쓸면 버린다 ──────────────
   방향을 아는 칸 = 낮을수록 좋다고 우리가 아는 칸 (걷기 · 가격 · 줄 · 경사).
   그 칸들이 **전부 한 편**이면 재 볼 것이 없는 문항이다.

   🔴 v3 은 여기에 예외가 있었다 — *"쓸었더라도 모르는 칸의 차이가 더 크면
      통과"*. 그 예외를 없앤다. 모르는 칸은 사람마다 방향이 갈리는 칸이라,
      아는 칸이 한쪽으로 쏠린 것을 상쇄한다고 볼 근거가 없다.

   🔴 **아는 칸이 하나만 다를 때는 안 막는다.** 그때 "싹 쓸었다" 는 말은
      "그 칸이 다르다" 와 같은 뜻이고, 그런 문항은 오히려 그 칸을 깨끗하게
      재는 문항이다. */
function knownSweep(a, b, attrs) {
  const known = Object.keys(attrs).filter(k => attrs[k].lowerIsBetter === true);
  const rank = (k, v) => attrs[k].levels.indexOf(v);
  const diff = known.filter(k => a[k] !== b[k]);
  if (diff.length < 2) return false;
  return diff.every(k => rank(k, a[k]) < rank(k, b[k]))
      || diff.every(k => rank(k, b[k]) < rank(k, a[k]));
}

/* ── 수준이 골고루 나오게 ─────────────────────────────────────────── */
const levelKey = (k, v) => k + "=" + v;
const costOf  = (pair, counts, keys) =>
  pair.reduce((acc, alt) => acc + keys.reduce((s, k) => s + (counts.get(levelKey(k, alt[k])) ?? 0), 0), 0);
function freshCount(pair, counts, keys) {
  const fresh = new Set();
  for (const alt of pair) for (const k of keys) {
    const key = levelKey(k, alt[k]);
    if ((counts.get(key) ?? 0) === 0) fresh.add(key);
  }
  return fresh.size;
}
const bump = (pair, counts, keys) => {
  for (const alt of pair) for (const k of keys) {
    const key = levelKey(k, alt[k]);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
};

/* ── 한 덩어리에서 문항 n개 ───────────────────────────────────────── */
function setsForBlock(block, n, counts) {
  const attrs = attrsOf(block);
  const keys  = Object.keys(attrs);
  const pool  = alternativesOf(block);
  const chosen = [], seen = new Set();
  while (chosen.length < n) {
    const cands = [];
    let guard = 0;
    while (cands.length < CANDIDATES && guard++ < 400000) {
      const a = pick(pool), b = pick(pool);
      if (differCount(a, b, keys) < 3) continue;
      /* 공통 자가 양쪽 같으면 그 문항은 자 맞추기에 아무것도 안 보탠다 */
      if (a.walkMin === b.walkMin) continue;
      if (knownSweep(a, b, attrs)) continue;
      const sig = JSON.stringify([a, b]), rev = JSON.stringify([b, a]);
      if (seen.has(sig) || seen.has(rev)) continue;
      cands.push([a, b]);
    }
    if (cands.length === 0) throw new Error(block + ": 쌍을 못 만들었다 — 제약이 너무 빡빡하다");
    cands.sort((x, y) => freshCount(y, counts, keys) - freshCount(x, counts, keys)
                      || costOf(x, counts, keys) - costOf(y, counts, keys));
    const best = cands[0];
    seen.add(JSON.stringify(best));
    bump(best, counts, keys);
    chosen.push(best);
  }
  return chosen;
}

/* ── 덩어리가 섞인 문항 ──────────────────────────────────────────────
   🔴 가까운 쪽이 제 덩어리의 「방향을 아는 칸」을 전부 가장 좋은 수준으로
      갖고 있으면 버린다 — 가깝고 싸고 완만하면 고를 것이 없다. */
const MONEY_OF = { eat: "price", play: "cost", see: null };

function mixedOneSided(b0, b1, a, b) {
  const near = a.walkMin < b.walkMin ? 0 : 1;
  const money = [MONEY_OF[b0], MONEY_OF[b1]];
  if (money[0] && money[1]) {
    const m0 = a[money[0]], m1 = b[money[1]];
    if (m0 === m1) return false;
    return (m0 < m1 ? 0 : 1) === near;
  }
  const block = near === 0 ? b0 : b1;
  const card  = near === 0 ? a  : b;
  const attrs = attrsOf(block);
  return Object.keys(attrs)
    .filter(k => k !== "walkMin" && attrs[k].lowerIsBetter === true)
    .every(k => attrs[k].levels.indexOf(card[k]) === 0);
}

function mixedSets(n, usedPairs) {
  const names = Object.keys(BLOCKS);
  const out = [];
  let guard = 0;
  while (out.length < n && guard++ < 400000) {
    const b0 = pick(names);
    const b1 = pick(names.filter(x => x !== b0));
    /* 🔴 두 벌의 섞인 문항이 같은 덩어리 짝이면 덩어리 자체의 계수를 못 푼다 */
    const key = [b0, b1].sort().join("|");
    if (usedPairs.has(key)) continue;
    const a = pick(alternativesOf(b0));
    const b = pick(alternativesOf(b1));
    if (a.walkMin === b.walkMin) continue;
    if (mixedOneSided(b0, b1, a, b)) continue;
    usedPairs.add(key);
    out.push({ blocks: [b0, b1], alternatives: [a, b] });
  }
  if (out.length < n) throw new Error("섞인 문항을 못 만들었다");
  return out;
}

/* ── 함정 ─────────────────────────────────────────────────────────────
   🔴 한쪽이 모든 면에서 낫다. 읽고 고르면 반드시 맞힌다. 두 벌 모두 같은
      함정을 쓴다 — 성의를 재는 문항이라 벌마다 다를 이유가 없고, 같아야
      통과율을 두 벌 사이에 비교할 수 있다. */
const TRAP = {
  setId: "tr01", block: "eat", trap: true, trapCorrect: 0,
  alternatives: [
    { walkMin: 3,  price: 20000, queueMin: 0,  fame: "LOCAL_ONLY" },
    { walkMin: 25, price: 20000, queueMin: 40, fame: "LOCAL_ONLY" }
  ]
};

/* ── 장면 — 부산 1박 2일의 시간 순서. 두 벌이 같은 장면을 쓴다 ────────
   🔴 장면은 **때와 곳까지만** 말한다. "큰맘 먹고" 같은 말을 넣지 않는다 —
      저울을 문항마다 바꾸면 그 차이가 잡음으로 처리되고 계수만 흐려진다.
   🔴 밤 장면에 「볕/그늘」이 있는 덩어리를 두지 않는다. buildSets 가 막는다. */
const SCENES = [
  { setId: "eat01",  when: "첫날 저녁",      lead: "도착한 날 저녁, 첫 끼를 먹습니다" },
  { setId: "play01", when: "첫날 밤", night: true, lead: "밥을 먹고 밤에 한 군데 더 들릅니다" },
  { setId: "see01",  when: "이튿날 아침",    lead: "아침에 나와 어디부터 볼지 고릅니다" },
  { setId: "tr01",   when: "이튿날 점심",    lead: "걷다 보니 점심때가 됐습니다" },
  { setId: "see02",  when: "이튿날 오후",    lead: "점심을 먹고 한 곳 더 봅니다" },
  { setId: "play02", when: "해 기울 무렵",   lead: "돌아가기 전에 하나만 더 해 볼 참입니다" },
  { setId: "eat02",  when: "마지막 저녁",    lead: "이 여행의 마지막 저녁입니다" },
  { setId: "mx01",   when: "떠나기 전",      lead: "돌아가는 시간까지 한 시간 남았습니다" }
];

/* ── 한 벌 조립 ───────────────────────────────────────────────────── */
function buildSets(counts, usedPairs) {
  const byId = new Map();
  for (const b of Object.keys(BLOCKS)) {
    setsForBlock(b, PER_BLOCK, counts).forEach((alts, i) => {
      const setId = b + String(i + 1).padStart(2, "0");
      byId.set(setId, { setId, block: b, trap: false, alternatives: alts });
    });
  }
  mixedSets(MIXED, usedPairs).forEach((m, i) => {
    const setId = "mx" + String(i + 1).padStart(2, "0");
    byId.set(setId, { setId, blocks: m.blocks, trap: false, alternatives: m.alternatives });
  });
  byId.set(TRAP.setId, TRAP);

  const sets = SCENES.map(sc => {
    const one = byId.get(sc.setId);
    if (!one) throw new Error("장면 목록에 있는 문항이 안 만들어졌다: " + sc.setId);
    return { ...one, when: sc.when, lead: sc.lead };
  });
  if (sets.length !== byId.size) throw new Error("문항과 장면 수가 안 맞는다");
  if (sets[TRAP_AT].setId !== TRAP.setId) throw new Error("함정이 " + (TRAP_AT + 1) + "번째 자리에 없다");

  for (const [i, one] of sets.entries()) {
    if (!SCENES[i].night) continue;
    for (const b of one.blocks || [one.block]) {
      const bad = Object.entries(BLOCKS[b].attrs).filter(([, s]) => s.daylightOnly).map(([k]) => k);
      if (bad.length) throw new Error("밤 장면 「" + SCENES[i].when + "」 에 낮에만 뜻이 있는 칸: " + bad.join(", "));
    }
  }
  return sets;
}

/* ══════════════════════════════════════════════════════════════════════
   검사 — 여기가 v4 의 본체다
   ══════════════════════════════════════════════════════════════════════ */

/* ── 계수 한 줄 = 열 하나 ─────────────────────────────────────────────
   🔴 이름이 같은 칸은 덩어리가 달라도 **한 계수**로 본다. 「SNS 에서 유명」은
      식당이든 전망대든 같은 뜻이다. 따로 세면 계수가 늘어 못 푼다.
   🔴 순서가 있는 값(평지<완만<가파름, 한산<적당<붐빔)은 **직선 하나**로 본다.
      단계마다 계수를 따로 두면 계수가 둘씩 늘어 14개 방정식으로 못 푼다.
      직선 가정은 conjoint 에서 흔한 단순화이고, 응답이 더 모이면 풀 수 있다. */
function columns() {
  const cols = ["walkMin"];
  for (const b of Object.keys(BLOCKS))
    for (const k of Object.keys(BLOCKS[b].attrs)) if (!cols.includes(k)) cols.push(k);
  const bs = Object.keys(BLOCKS);
  for (let i = 1; i < bs.length; i++) cols.push("block:" + bs[i]);
  return cols;
}

const valueOf = (v, spec) => typeof v === "number" ? v / 10000 : spec.levels.indexOf(v);

function contrastRow(one, cols) {
  const blocks = one.blocks || [one.block, one.block];
  const side = [new Array(cols.length).fill(0), new Array(cols.length).fill(0)];
  one.alternatives.forEach((alt, s) => {
    const attrs = attrsOf(blocks[s]);
    for (const k of Object.keys(alt)) {
      const c = cols.indexOf(k);
      if (c >= 0) side[s][c] = valueOf(alt[k], attrs[k]);
    }
    const bc = cols.indexOf("block:" + blocks[s]);
    if (bc >= 0) side[s][bc] = 1;
  });
  return side[0].map((x, i) => x - side[1][i]);
}

/* 가우스 소거로 「실제로 풀 수 있는 방향의 수」를 센다 */
function rank(rows) {
  if (!rows.length) return 0;
  const A = rows.map(r => r.slice());
  const m = A.length, n = A[0].length;
  let r = 0;
  for (let c = 0; c < n && r < m; c++) {
    let p = -1, best = 1e-9;
    for (let i = r; i < m; i++) if (Math.abs(A[i][c]) > best) { best = Math.abs(A[i][c]); p = i; }
    if (p < 0) continue;
    [A[r], A[p]] = [A[p], A[r]];
    for (let i = 0; i < m; i++) if (i !== r && Math.abs(A[i][c]) > 1e-9) {
      const f = A[i][c] / A[r][c];
      for (let k = c; k < n; k++) A[i][k] -= f * A[r][k];
    }
    r++;
  }
  return r;
}

/* ── 🔴 검사 ② 늘 붙어 다니는 속성 짝 ────────────────────────────────
   두 속성이 같이 나온 문항에서 **한 번도 편이 갈리지 않으면** 그 둘을 영영
   못 가른다. v3 에서 「싼 쪽이 두 번 다 유명한 쪽」이었던 것이 이것이다.
   사람을 늘려도 안 고쳐진다 — 문항이 그렇게 생겼기 때문이다. */
function stuckPairs(allSets) {
  const sides = [];
  for (const one of allSets) {
    if (one.trap) continue;
    const blocks = one.blocks || [one.block, one.block];
    if (blocks[0] !== blocks[1]) continue;          /* 섞인 문항은 칸이 서로 달라 비교 못 한다 */
    const attrs = attrsOf(blocks[0]);
    const [a, b] = one.alternatives;
    const cell = {};
    for (const k of Object.keys(attrs)) {
      if (a[k] === b[k]) continue;
      const ra = attrs[k].levels.indexOf(a[k]), rb = attrs[k].levels.indexOf(b[k]);
      cell[k] = attrs[k].lowerIsBetter === true ? (ra < rb ? 1 : -1) : (ra > rb ? 1 : -1);
    }
    sides.push(cell);
  }
  const attrs = [...new Set(sides.flatMap(c => Object.keys(c)))];
  const stuck = [];
  for (let i = 0; i < attrs.length; i++) for (let j = i + 1; j < attrs.length; j++) {
    const A = attrs[i], B = attrs[j];
    const both = sides.filter(c => c[A] !== undefined && c[B] !== undefined);
    if (both.length < 2) continue;
    const same = both.filter(c => c[A] === c[B]).length;
    if (same === both.length || same === 0) stuck.push({ A, B, n: both.length, same });
  }
  return stuck;
}

/* ── 빠진 수준 ───────────────────────────────────────────────────── */
function missingLevels(allSets) {
  const seen = new Set();
  for (const one of allSets) {
    const blocks = one.blocks || [one.block, one.block];
    one.alternatives.forEach((alt, i) => {
      for (const k of Object.keys(alt)) seen.add((k === "walkMin" ? "공통" : blocks[i]) + "." + k + "=" + alt[k]);
    });
  }
  const miss = [];
  for (const v of COMMON.walkMin.levels) if (!seen.has("공통.walkMin=" + v)) miss.push("공통 걷기 " + v + "분");
  for (const b of Object.keys(BLOCKS))
    for (const [k, spec] of Object.entries(BLOCKS[b].attrs))
      for (const v of spec.levels) if (!seen.has(b + "." + k + "=" + v)) miss.push(BLOCKS[b].label + " " + k + " " + v);
  return miss;
}

/* ── 한 벌씩 뽑아 놓고 셋을 다 본다 ──────────────────────────────── */
const COLS = columns();

function audit(variants) {
  const all = variants.flat();
  const rows = all.filter(s => !s.trap).map(s => contrastRow(s, COLS));
  const r = rank(rows);
  const stuck = stuckPairs(all);
  const miss  = missingLevels(all);
  return {
    coefs: COLS.length, contrasts: rows.length, rank: r, stuck, miss,
    ok: r >= COLS.length && stuck.length === 0 && miss.length === 0
  };
}

function generate() {
  for (let attempt = 0; attempt < MAX_TRIES; attempt++) {
    state = (SEED + attempt * 7919) >>> 0;
    let variants;
    try {
      const counts = new Map();
      for (const b of Object.keys(BLOCKS)) {
        const attrs = attrsOf(b);
        for (const k of Object.keys(attrs)) for (const v of attrs[k].levels) counts.set(levelKey(k, v), 0);
      }
      const usedPairs = new Set();
      variants = LETTERS.map(() => buildSets(counts, usedPairs));
    } catch { continue; }
    const report = audit(variants);
    if (report.ok) return { variants, report, attempt };
  }
  console.error("🔴 " + MAX_TRIES + "번 뽑아도 검사를 통과하는 설계가 안 나왔다.");
  console.error("   계수 " + COLS.length + "개 · 대비 " + ((SCENES.length - 1) * LETTERS.length) + "개.");
  console.error("   LETTERS 를 늘리거나(벌을 하나 더) 속성을 줄여라.");
  process.exit(1);
}

const { variants, report, attempt } = generate();

const realCount  = variants[0].filter(s => !s.trap).length;
const needObs    = COLS.length * OBS_PER_COEF;
const needPeople = Math.ceil(needObs / realCount);

const design = {
  family: FAMILY,
  seed: SEED,
  perPage: PER_PAGE,
  msBuckets: [1500, 3000, 5000, 8000, 12000, 20000, 40000],
  common: Object.fromEntries(Object.keys(COMMON).map(k => [k, COMMON[k].levels])),
  blocks: Object.fromEntries(Object.keys(BLOCKS).map(b => [b, {
    label: BLOCKS[b].label,
    types: BLOCKS[b].types,
    attributes: Object.fromEntries(Object.keys(BLOCKS[b].attrs).map(k => [k, BLOCKS[b].attrs[k].levels])),
    axes: Object.fromEntries(Object.keys(BLOCKS[b].attrs).map(k => [k, BLOCKS[b].attrs[k].axis]))
  }])),
  variants: LETTERS.map((letter, i) => ({ designId: FAMILY + "-" + letter, sets: variants[i] }))
};

for (const v of design.variants) {
  if (v.sets.length !== PER_PAGE * PAGES) {
    console.error(v.designId + " 의 문항이 " + v.sets.length + "개다 — " + (PER_PAGE * PAGES) + "개여야 한다.");
    process.exit(1);
  }
}

/* ── 사람이 보고 판정하는 자리 ───────────────────────────────────── */
if (process.argv.includes("--check")) {
  const L = (k, v, spec) => {
    if (k === "walkMin")  return v + "분";
    if (k === "price")    return (v / 10000) + "만원";
    if (k === "cost")     return v === 0 ? "무료" : (v / 10000) + "만원";
    if (k === "queueMin") return v === 0 ? "줄 없음" : "줄 " + v + "분";
    return { FLAT: "평지", GENTLE: "완만", STEEP: "가파름", SHADED: "그늘", SUNNY: "볕",
             QUIET: "한산", SOME: "적당", PACKED: "붐빔", INDOOR: "실내", OUTDOOR: "실외",
             LOCAL_ONLY: "동네만", SNS_FAMOUS: "SNS유명" }[v] ?? String(v);
  };
  console.log("설계 묶음 : " + FAMILY + "   (" + (attempt + 1) + "번째 뽑기에서 통과)");
  console.log("");
  console.log("■ 풀 수 있는 설계인가");
  console.log("   추정할 계수   " + report.coefs + "개   " + COLS.join(" · "));
  console.log("   쓸 수 있는 대비 " + report.contrasts + "개   (" + LETTERS.length + "벌 × 진짜 " + realCount + "문항)");
  console.log("   대비 행렬 계수  " + report.rank + "   " + (report.rank >= report.coefs ? "✅ 푼다" : "🔴 못 푼다"));
  console.log("   늘 붙어 다니는 속성 짝  " + (report.stuck.length === 0 ? "없음 ✅"
    : "🔴 " + report.stuck.map(s => s.A + "×" + s.B).join(" · ")));
  console.log("   빠진 수준  " + (report.miss.length === 0 ? "없음 ✅" : "🔴 " + report.miss.join(" · ")));
  console.log("   표본  관측 " + needObs + "개 필요 → 1인당 " + realCount + "문항이면 최소 " + needPeople + "명");
  console.log("");
  console.log("■ 문항 — 어느 편이 무엇을 가져가나 (◀ 는 방향을 아는 칸에서 이긴 쪽)");
  for (const v of design.variants) {
    console.log("");
    console.log("  ── " + v.designId + " " + "─".repeat(46));
    for (const s of v.sets) {
      const blocks = s.blocks || [s.block, s.block];
      const where = s.trap ? "함정" : blocks[0] === blocks[1] ? BLOCKS[blocks[0]].label
        : blocks.map(b => BLOCKS[b].label).join(" ↔ ");
      console.log("  " + s.setId.padEnd(7) + (s.when || "").padEnd(11) + where);
      const keys = [...new Set(s.alternatives.flatMap(a => Object.keys(a)))];
      for (const k of keys) {
        const spec = attrsOf(blocks[0])[k] || attrsOf(blocks[1])[k];
        const a = s.alternatives[0][k], b = s.alternatives[1][k];
        let mark = "  ";
        if (spec && spec.lowerIsBetter === true && a !== b && blocks[0] === blocks[1]) {
          mark = spec.levels.indexOf(a) < spec.levels.indexOf(b) ? "◀ " : " ▶";
        }
        const sa = a === undefined ? "—" : L(k, a, spec);
        const sb = b === undefined ? "—" : L(k, b, spec);
        console.log("      " + k.padEnd(10) + sa.padStart(8) + "  " + mark + "  " + sb.padEnd(8) +
          (a === b && a !== undefined ? "   (같음)" : ""));
      }
    }
  }
  process.exit(0);
}

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
  const LF = String.fromCharCode(10);
  writeFileSync(target, html.slice(0, i + open.length) + LF + json + LF + html.slice(j), "utf8");
  console.log("index.html 의 문항을 다시 썼다 — " + design.variants.length + "벌 × "
    + design.variants[0].sets.length + "문항 (" + FAMILY + ")");
  console.log("계수 " + report.coefs + " · 대비 " + report.contrasts + " · 행렬 계수 " + report.rank + " ✅");
}
