#!/usr/bin/env node
/**
 * 짝 비교 v5 — 문항지를 **네 벌**로. (S15P21E201-884 의 다음 걸음)
 *
 * 🔴 **이 파일이 지금 살아 있는 설문(/survey/)의 문항을 만든다.** 고치고 나면
 *    인수 없이 한 번 돌려서 index.html 의 문항 블록을 다시 써야 한다.
 *    (`--check` 는 검사만, `--print` 는 찍기만 한다. 둘 다 안 쓴다.)
 *
 * ── 왜 v4 에서 더 가나 ──────────────────────────────────────────────
 * v4 로 계수 11개가 **풀리기는** 했다. 그런데 응답 113건을 실제로 풀어 보니
 * 칸마다 **얼마나 아는지**가 딴판이었다. 95% 구간을 걷기 분으로 옮기면:
 *
 *     줄         0.5 ~  1.0분   🟢
 *     밥값       1.9 ~  4.6분   🟢
 *     붐빔       4.1 ~ 15.2분   🟡
 *     그늘      15.5 ~ 33.5분   🟡
 *     경사       8.2 ~ 27.7분   🔴
 *     놀이비용    0.7 ~ 14.2분   🔴
 *     덩어리 쏠림  폭 46~55분     🔴 사실상 아무 말도 못 한다
 *
 * 원인은 표본이 아니라 **설계**다. 그 칸이 실제로 갈린 문항을 세면 이렇다:
 *
 *     걷기 14문항 · 현지인도 11 · 붐빔 5 · 밥값 4 · 줄 4 · 실내 4 · 그늘 3
 *     🔴 경사 1 · 놀이비용 1 · 섞인 문항 2 (먹기↔보기 짝이 **아예 없다**)
 *
 * 🔴 **문항이 하나면 방정식이 하나다. 사람을 천 명 받아도 안 좁아진다.**
 *    v4 주석이 같은 말을 했는데(*"사람을 늘려도 안 풀린다"*), 그때는 **벌 단위**
 *    로만 세었다. 칸 단위로 세면 아직 하나짜리가 둘 남아 있었다.
 *
 * ── 무엇이 바뀌나 ───────────────────────────────────────────────────
 *   ① **a·b 를 얼린다.** 이미 109명이 답했다. 다시 뽑으면 그 답이 무효가 된다.
 *      index.html 에서 읽어 그대로 싣는다 — 이 파일은 a·b 를 만들지 않는다.
 *   ② **c·d 를 더한다.** 노리는 칸은 **경사 · 그늘 · 놀이비용 · 먹기↔보기** 넷.
 *   ③ **c·d 의 노는 곳 카드에서 실내/실외를 뺀다.** 문항 4개·관측 218개로
 *      물었는데 95% 구간이 0 을 품었다 — 사람들이 안 따진다는 **답이 나온 것**
 *      이다. a·b 는 그대로라 계수는 계속 추정된다. 새로 묻지 않을 뿐이다.
 *   ④ **뻔한 문항을 「예상 확률」로 거른다.** v4 는 *"한쪽이 방향 아는 칸을 싹
 *      쓸면 버린다"* 는 어림으로 막았다. 이제 계수를 아니까 **그 문항에서 한쪽이
 *      몇 %로 이길지 계산해서** 8:2 보다 기울면 버린다.
 *   ⑤ **검사에 「예상 폭」이 들어왔다.** 응답을 받기 전에 계산할 수 있다 —
 *      정밀도는 **설계**가 정하지 응답 내용이 정하지 않는다.
 *
 * 🔴 사람이 푸는 문항 수는 **8개 그대로**다. 한 사람은 한 벌만 받는다.
 *
 * ── 문항 하나가 주는 정보 ───────────────────────────────────────────
 *
 *        정보 = 사람 수 × p(1−p) × (칸 차이)²
 *                          ↑
 *        p 는 그 문항에서 한쪽을 고를 확률. 0.5 에서 멀수록 0 으로 죽는다.
 *        **차이를 키우면 (칸 차이)² 는 커지지만 p 가 쏠려 p(1−p) 가 죽는다.**
 *        그래서 노리는 칸을 벌리되 **걷기를 반대편에 두어** 팽팽하게 만든다.
 *
 * 쓰는 법:
 *    node survey-recommend/design-v5.mjs --check    # 검사 결과와 문항 전부
 *    node survey-recommend/design-v5.mjs --print    # JSON 만
 *    node survey-recommend/design-v5.mjs            # index.html 을 다시 쓴다
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE   = dirname(fileURLToPath(import.meta.url));
const TARGET = join(HERE, "index.html");
const OPEN   = '<script id="design" type="application/json">';
const CLOSE  = "<" + "/script>";

/* ── 씨앗 ───────────────────────────────────────────────────────────── */
const SEED       = 20260914;
const NEW_FAMILY = "recommend-v5-seed-20260914";
const NEW_LETTERS = ["c", "d"];
const FROZEN_PREFIX = "recommend-v4-";   /* 이 접두사를 가진 벌은 건드리지 않는다 */
const MAX_TRIES  = 6000;

/* ── 지금까지 추정된 계수 ────────────────────────────────────────────
   응답 113명 중 함정을 통과한 102명으로 푼 값 (조건부 로짓).
   🔴 이것을 **참값으로 가정**해서 새 문항이 팽팽한지, 폭이 얼마나 좁아질지를
      미리 계산한다. 참값이 아니라 **지금까지의 최선의 짐작**이다 — 설계를
      고르는 데만 쓰고, 결과 보고에는 절대 쓰지 않는다. */
const PRIOR = {
  walkMin: -0.060, price: -0.195, cost: -0.447, queueMin: -0.044,
  fame: 0.290, crowd: -0.579, slope: -1.075, shade: 1.469, indoor: -0.176,
  "block:see": -0.209, "block:play": 0.365,
};

/* ── 이번에 좁히려는 칸과, 지금 폭(걷기 분) ─────────────────────────── */
const TARGETS = {
  slope:        { need: 3, nowWidth: 19.5, label: "경사" },
  shade:        { need: 3, nowWidth: 18.0, label: "그늘" },
  cost:         { need: 3, nowWidth: 13.5, label: "놀이비용" },
  "block:see":  { need: 0, nowWidth: 54.5, label: "덩어리:보기" },
  "block:play": { need: 0, nowWidth: 46.4, label: "덩어리:놀기" },
};
/* 🔴 아직 한 번도 안 나온 섞인 짝. 덩어리 쏠림 두 칸이 서로 얽힌(상관 0.71)
      까닭이 이것이다 — 짝이 둘뿐이라 셋째 방향이 없다. */
const NEEDED_MIX = "eat|see";

/* 한쪽이 이 밖으로 이기면 뻔한 문항이라 버린다 */
const P_MIN = 0.2, P_MAX = 0.8;

/* ── 공통 칸 ────────────────────────────────────────────────────────── */
const COMMON = { walkMin: { levels: [3, 8, 15, 25], lowerIsBetter: true } };

/* ── 덩어리 셋 — c·d 가 쓰는 칸 ──────────────────────────────────────
   🔴 노는 곳에서 indoor 가 빠졌다 (위 ③). a·b 의 카드에는 그대로 있다. */
const BLOCKS = {
  eat: {
    label: "먹는 곳", types: ["FOOD", "CAFE", "BAR"],
    attrs: {
      price:    { levels: [10000, 20000, 40000, 70000], lowerIsBetter: true, axis: "SPEND_PROFILE" },
      queueMin: { levels: [0, 20, 40],                  lowerIsBetter: true, axis: "QUIETNESS" },
      fame:     { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" },
    },
  },
  see: {
    label: "보는 곳", types: ["SIGHT", "NATURE", "CULTURE"],
    attrs: {
      slope: { levels: ["FLAT", "GENTLE", "STEEP"],  lowerIsBetter: true, axis: "SLOPE_PREFERENCE" },
      shade: { levels: ["SHADED", "SUNNY"],          lowerIsBetter: null, axis: "SHADE_PREFERENCE", daylightOnly: true },
      crowd: { levels: ["QUIET", "SOME", "PACKED"],  lowerIsBetter: null, axis: "QUIETNESS" },
      fame:  { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" },
    },
  },
  play: {
    label: "노는 곳", types: ["MARKET", "ACTIVITY"],
    attrs: {
      cost:  { levels: [0, 10000, 30000],            lowerIsBetter: true, axis: "SPEND_PROFILE" },
      crowd: { levels: ["QUIET", "SOME", "PACKED"],  lowerIsBetter: null, axis: "QUIETNESS" },
      fame:  { levels: ["LOCAL_ONLY", "SNS_FAMOUS"], lowerIsBetter: null, axis: "TOURIST_PREFERENCE" },
    },
  },
};

const PER_BLOCK = 2, MIXED = 1, PER_PAGE = 2, PAGES = 4, TRAP_AT = 3;

/* ── 난수 ───────────────────────────────────────────────────────────── */
let state = SEED;
const rand = () => ((state = (state * 1664525 + 1013904223) >>> 0) / 4294967296);
const pick = a => a[Math.floor(rand() * a.length)];

const attrsOf = b => ({ ...COMMON, ...BLOCKS[b].attrs });

function alternativesOf(block) {
  const attrs = attrsOf(block);
  let out = [{}];
  for (const k of Object.keys(attrs)) {
    const next = [];
    for (const p of out) for (const v of attrs[k].levels) next.push({ ...p, [k]: v });
    out = next;
  }
  return out;
}

/* ── 칸 → 숫자 ──────────────────────────────────────────────────────── */
const ORD = {
  crowd: { QUIET: 0, SOME: 1, PACKED: 2 }, slope: { FLAT: 0, GENTLE: 1, STEEP: 2 },
  shade: { SUNNY: 0, SHADED: 1 }, indoor: { OUTDOOR: 0, INDOOR: 1 },
  fame:  { SNS_FAMOUS: 0, LOCAL_ONLY: 1 },
};
const numOf = (k, v) =>
  ORD[k] ? ORD[k][v] : (k === "price" || k === "cost") ? v / 10000 : v;

/* 모든 벌(언 것 + 새 것)에 걸친 계수 목록 — indoor 는 a·b 가 아직 먹여 준다 */
const COLS = ["walkMin", "price", "queueMin", "cost", "fame", "crowd",
              "slope", "shade", "indoor", "block:see", "block:play"];

/** 카드 한 장 → 칸 벡터 */
function vecOf(card, block) {
  const x = new Array(COLS.length).fill(0);
  for (const [k, v] of Object.entries(card)) {
    const i = COLS.indexOf(k);
    if (i >= 0 && numOf(k, v) != null) x[i] = numOf(k, v);
  }
  const bi = COLS.indexOf("block:" + block);
  if (bi >= 0) x[bi] = 1;
  return x;
}
/** 문항 → 두 카드의 차이 벡터 */
function contrastOf(one) {
  const blocks = one.blocks || [one.block, one.block];
  const a = vecOf(one.alternatives[0], blocks[0]);
  const b = vecOf(one.alternatives[1], blocks[1]);
  return a.map((v, i) => v - b[i]);
}
/** 그 문항에서 첫째 카드를 고를 확률 (PRIOR 를 참값으로 놓고) */
function pOf(one) {
  const d = contrastOf(one);
  const u = d.reduce((s, v, i) => s + v * (PRIOR[COLS[i]] ?? 0), 0);
  return 1 / (1 + Math.exp(-u));
}

/* ── 선형대수 ───────────────────────────────────────────────────────── */
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
function inverse(M0) {
  const n = M0.length;
  const M = M0.map((r, i) => [...r, ...M0.map((_, j) => (i === j ? 1 : 0))]);
  for (let c = 0; c < n; c++) {
    let p = c;
    for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
    if (Math.abs(M[p][c]) < 1e-12) return null;
    [M[c], M[p]] = [M[p], M[c]];
    const d = M[c][c];
    for (let k = 0; k < 2 * n; k++) M[c][k] /= d;
    for (let r = 0; r < n; r++) {
      if (r === c) continue;
      const f = M[r][c];
      for (let k = 0; k < 2 * n; k++) M[r][k] -= f * M[c][k];
    }
  }
  return M.map(r => r.slice(n));
}
/**
 * 🔴 응답을 받기 전에 각 계수가 얼마나 또렷해질지 계산한다.
 *    sets 는 [{문항, n(그 문항에 답할 사람 수)}].
 */
function predictedSE(weighted) {
  const K = COLS.length;
  const I = Array.from({ length: K }, () => new Array(K).fill(0));
  for (const { one, n } of weighted) {
    const d = contrastOf(one);
    const p = pOf(one);
    const w = n * p * (1 - p);
    for (let i = 0; i < K; i++) for (let j = 0; j < K; j++) I[i][j] += w * d[i] * d[j];
  }
  const inv = inverse(I);
  return inv ? COLS.map((_, i) => Math.sqrt(Math.max(inv[i][i], 0))) : null;
}
/** 95% 구간의 폭을 걷기 분으로 */
const widthInMinutes = (se, i) => 2 * 1.96 * se[i] / Math.abs(PRIOR.walkMin);

/* ── 문항 고르기 ────────────────────────────────────────────────────── */
const differCount = (a, b, keys) => keys.filter(k => a[k] !== b[k]).length;

/**
 * 🔴 늘 붙어 다니는 속성 짝 (v4 에서 그대로 가져왔다).
 *
 * 두 칸이 같이 나온 문항에서 **한 번도 편이 갈리지 않으면** 그 둘을 영영 못
 * 가른다. 「가파른 쪽이 늘 그늘인 쪽」이면 사람이 경사를 보고 골랐는지 그늘을
 * 보고 골랐는지 알 길이 없다. **사람을 늘려도 안 고쳐진다** — 문항이 그렇게
 * 생긴 탓이다.
 *
 * 🔴 언 벌까지 **다 넣어서** 본다. 계수는 네 벌을 합쳐 풀기 때문에, a·b 가
 *    이미 갈라 놓은 짝이라면 c·d 가 또 갈라 줄 필요가 없다.
 *
 * 🔴 **걷기는 이 검사에서 뺀다.** 걷기는 다른 칸을 팽팽하게 맞추려고 **일부러
 *    반대편에 놓는 균형추**다. 「방향을 아는 칸을 한쪽이 싹 쓸면 버린다」는 바로
 *    아래 검사가 그렇게 되도록 강제하기까지 한다. 그래서 걷기는 무엇과 견주든
 *    늘 반대편으로 나오고, 이 검사에 넣으면 **멀쩡한 설계가 전부 걸린다**
 *    (실제로 6000번 뽑아도 하나도 안 나왔다).
 *
 *    빼도 되는 까닭은 이 검사가 **부호만** 보기 때문이다. 걷기는 4수준이라
 *    같은 부호 안에서도 크기가 크게 달라지고(3·8·15·25분), 크기가 달라지면
 *    계수는 갈린다. 진짜로 못 가르는 경우는 아래 「예상 폭」 검사가 잡는다 —
 *    그쪽은 부호가 아니라 **정보량**을 직접 센다.
 */
const STUCK_EXEMPT = new Set(["walkMin"]);

function stuckPairs(sets) {
  const sides = [];
  for (const one of sets) {
    if (one.trap) continue;
    const blocks = one.blocks || [one.block, one.block];
    if (blocks[0] !== blocks[1]) continue;   /* 섞인 문항은 칸이 서로 달라 비교 못 한다 */
    const attrs = attrsOf(blocks[0]);
    const [a, b] = one.alternatives;
    const cell = {};
    for (const k of Object.keys(attrs)) {
      if (STUCK_EXEMPT.has(k)) continue;
      if (a[k] === undefined || b[k] === undefined || a[k] === b[k]) continue;
      const ra = attrs[k].levels.indexOf(a[k]), rb = attrs[k].levels.indexOf(b[k]);
      cell[k] = attrs[k].lowerIsBetter === true ? (ra < rb ? 1 : -1) : (ra > rb ? 1 : -1);
    }
    sides.push(cell);
  }
  const keys = [...new Set(sides.flatMap(c => Object.keys(c)))];
  const stuck = [];
  for (let i = 0; i < keys.length; i++) for (let j = i + 1; j < keys.length; j++) {
    const A = keys[i], B = keys[j];
    const both = sides.filter(c => c[A] !== undefined && c[B] !== undefined);
    if (both.length < 2) continue;
    const same = both.filter(c => c[A] === c[B]).length;
    if (same === both.length || same === 0) stuck.push(A + "×" + B);
  }
  return stuck;
}

/** v4 에서 가져온 검사 — 방향을 아는 칸을 한쪽이 싹 쓸면 버린다 */
function knownSweep(a, b, attrs) {
  const known = Object.keys(attrs).filter(k => attrs[k].lowerIsBetter === true);
  const rk = (k, v) => attrs[k].levels.indexOf(v);
  const diff = known.filter(k => a[k] !== b[k]);
  if (diff.length < 2) return false;
  return diff.every(k => rk(k, a[k]) < rk(k, b[k])) || diff.every(k => rk(k, b[k]) < rk(k, a[k]));
}

/**
 * 한 덩어리에서 문항 n개.
 * 🔴 `must` 에 적힌 칸은 **반드시 갈리게** 한다. 이번에 좁히려는 칸이다.
 */
function setsForBlock(block, n, must, seen) {
  const attrs = attrsOf(block), keys = Object.keys(attrs), pool = alternativesOf(block);
  const out = [];
  let guard = 0;
  while (out.length < n && guard++ < 600000) {
    const a = pick(pool), b = pick(pool);
    if (differCount(a, b, keys) < 2) continue;
    if (a.walkMin === b.walkMin) continue;              /* 자를 맞추는 칸은 늘 갈린다 */
    if (knownSweep(a, b, attrs)) continue;
    /* 이번 문항이 맡기로 한 칸이 안 갈리면 버린다 */
    const need = must[out.length] ?? [];
    if (need.some(k => a[k] === b[k])) continue;
    /* 🔴 뻔한 문항 거르기 — 예상 확률이 8:2 밖이면 배울 것이 없다 */
    const p = pOf({ block, alternatives: [a, b] });
    if (p < P_MIN || p > P_MAX) continue;
    const sig = JSON.stringify([a, b]), rev = JSON.stringify([b, a]);
    if (seen.has(sig) || seen.has(rev)) continue;
    seen.add(sig);
    out.push([a, b]);
  }
  if (out.length < n) throw new Error(block + ": 조건을 만족하는 쌍을 못 만들었다");
  return out;
}

/** 덩어리가 섞인 문항 — `wantPair` 가 주어지면 그 짝으로 */
function mixedSet(wantPair, seen) {
  const [b0, b1] = wantPair.split("|");
  let guard = 0;
  while (guard++ < 600000) {
    const a = pick(alternativesOf(b0)), b = pick(alternativesOf(b1));
    if (a.walkMin === b.walkMin) continue;
    const p = pOf({ blocks: [b0, b1], alternatives: [a, b] });
    if (p < P_MIN || p > P_MAX) continue;
    const sig = JSON.stringify([b0, b1, a, b]);
    if (seen.has(sig)) continue;
    seen.add(sig);
    return { blocks: [b0, b1], alternatives: [a, b] };
  }
  throw new Error("섞인 문항(" + wantPair + ")을 못 만들었다");
}

/* ── 함정 · 장면 (a·b 와 같게 둔다 — 통과율을 벌 사이에 견주려면 같아야 한다) */
const TRAP = {
  setId: "tr01", block: "eat", trap: true, trapCorrect: 0,
  alternatives: [
    { walkMin: 3,  price: 20000, queueMin: 0,  fame: "LOCAL_ONLY" },
    { walkMin: 25, price: 20000, queueMin: 40, fame: "LOCAL_ONLY" },
  ],
};
const SCENES = [
  { setId: "eat01",  when: "첫날 저녁",    lead: "도착한 날 저녁, 첫 끼를 먹습니다" },
  { setId: "play01", when: "첫날 밤", night: true, lead: "밥을 먹고 밤에 한 군데 더 들릅니다" },
  { setId: "see01",  when: "이튿날 아침",  lead: "아침에 나와 어디부터 볼지 고릅니다" },
  { setId: "tr01",   when: "이튿날 점심",  lead: "걷다 보니 점심때가 됐습니다" },
  { setId: "see02",  when: "이튿날 오후",  lead: "점심을 먹고 한 곳 더 봅니다" },
  { setId: "play02", when: "해 기울 무렵", lead: "돌아가기 전에 하나만 더 해 볼 참입니다" },
  { setId: "eat02",  when: "마지막 저녁",  lead: "이 여행의 마지막 저녁입니다" },
  { setId: "mx01",   when: "떠나기 전",    lead: "돌아가는 시간까지 한 시간 남았습니다" },
];

/**
 * 벌 하나를 조립한다.
 * 🔴 **밤 장면(play01)에 볕/그늘이 있는 덩어리를 두지 않는다.** 노는 곳에는
 *    그 칸이 없으므로 지금 구조에서는 저절로 지켜지지만, 확인은 남긴다.
 */
function buildVariant(letter, mixPair, seen) {
  const must = {
    /* 이 벌에서 어느 문항이 어느 칸을 맡는가 */
    see:  letter === "c" ? [["slope"], ["shade"]] : [["shade"], ["slope"]],
    play: [["cost"], ["cost"]],
    eat:  [[], []],
  };
  const byId = new Map();
  for (const b of ["eat", "see", "play"]) {
    setsForBlock(b, PER_BLOCK, must[b], seen).forEach((alts, i) => {
      const setId = b + String(i + 1).padStart(2, "0");
      byId.set(setId, { setId, block: b, trap: false, alternatives: alts });
    });
  }
  const m = mixedSet(mixPair, seen);
  byId.set("mx01", { setId: "mx01", blocks: m.blocks, trap: false, alternatives: m.alternatives });
  byId.set(TRAP.setId, TRAP);

  const sets = SCENES.map(sc => {
    const one = byId.get(sc.setId);
    if (!one) throw new Error("장면에 있는 문항이 안 만들어졌다: " + sc.setId);
    return { ...one, when: sc.when, lead: sc.lead };
  });
  if (sets[TRAP_AT].setId !== TRAP.setId) throw new Error("함정이 제자리에 없다");
  for (const [i, one] of sets.entries()) {
    if (!SCENES[i].night) continue;
    for (const b of one.blocks || [one.block]) {
      const bad = Object.entries(BLOCKS[b].attrs).filter(([, s]) => s.daylightOnly).map(([k]) => k);
      if (bad.length) throw new Error("밤 장면에 낮에만 뜻이 있는 칸: " + bad.join(", "));
    }
  }
  return sets;
}

/* ── 언 벌 읽기 ─────────────────────────────────────────────────────── */
const html = readFileSync(TARGET, "utf8");
const iOpen = html.indexOf(OPEN);
if (iOpen < 0) { console.error("🔴 index.html 에 문항 블록이 없다"); process.exit(1); }
const iClose = html.indexOf(CLOSE, iOpen);
const live = JSON.parse(html.slice(iOpen + OPEN.length, iClose));
const frozen = live.variants.filter(v => v.designId.startsWith(FROZEN_PREFIX));
if (frozen.length === 0) {
  console.error("🔴 얼려 둘 " + FROZEN_PREFIX + "* 벌이 index.html 에 없다. 109명이 답한 문항이다 — 확인 없이 진행하지 않는다.");
  process.exit(1);
}

/* ── 뽑기 ───────────────────────────────────────────────────────────── */
function generate() {
  for (let attempt = 0; attempt < MAX_TRIES; attempt++) {
    state = (SEED + attempt * 7919) >>> 0;
    try {
      const seen = new Set();
      /* 🔴 새 벌 하나는 아직 없는 짝(먹기↔보기)을 맡고, 다른 하나는 그 반대편 */
      const pairs = [NEEDED_MIX, "see|play"];
      const made = NEW_LETTERS.map((L, i) => buildVariant(L, pairs[i], seen));
      const all = [...frozen.map(v => v.sets), ...made];
      const rows = all.flat().filter(s => !s.trap).map(contrastOf);
      if (rank(rows) < COLS.length) continue;
      if (stuckPairs(all.flat()).length) continue;
      /* 노리는 칸이 새 벌에서 충분히 갈렸나 */
      const newOnly = made.flat().filter(s => !s.trap);
      const varies = k => newOnly.filter(s => {
        const a = s.alternatives[0][k], b = s.alternatives[1][k];
        return a !== undefined && b !== undefined && a !== b;
      }).length;
      if (Object.entries(TARGETS).some(([k, t]) => t.need > 0 && varies(k) < t.need)) continue;
      return { made, attempt };
    } catch { continue; }
  }
  console.error("🔴 " + MAX_TRIES + "번 뽑아도 조건을 만족하는 설계가 안 나왔다.");
  console.error("   노리는 칸을 줄이거나(TARGETS) 팽팽함 기준(P_MIN·P_MAX)을 넓혀라.");
  process.exit(1);
}
const { made, attempt } = generate();

/* ── 설계 묶기 ──────────────────────────────────────────────────────── */
const design = {
  ...live,
  /* 🔴 `family` 는 언 벌의 이름 그대로 둔다. 이제 한 판에 두 묶음(v4·v5)이
        섞여 있어 대표 이름이 없다 — 진짜 이름은 **벌마다의 designId** 이고,
        표에 남는 것도 그쪽이다. 시작 로그도 designId 를 찍는다. */
  /* 🔴 blocks(칸 정의)도 덮어쓰지 않는다. a·b 의 노는 곳 카드에는 indoor 가
        남아 있어야 하기 때문이다. 화면은 **카드에 실제로 값이 있는 줄만**
        그리므로(index.html 의 `lv === undefined && rv === undefined` 검사),
        칸 정의에 indoor 가 남아 있어도 c·d 카드에는 그 줄이 안 뜬다. */
  variants: [
    ...frozen,
    ...NEW_LETTERS.map((L, i) => ({ designId: NEW_FAMILY + "-" + L, sets: made[i] })),
  ],
};
for (const v of design.variants) {
  if (v.sets.length !== PER_PAGE * PAGES) {
    console.error("🔴 " + v.designId + " 의 문항이 " + v.sets.length + "개다 — " + PER_PAGE * PAGES + "개여야 한다.");
    process.exit(1);
  }
}

/* ── 검사 — 예상 폭이 지금보다 좁아지는가 ───────────────────────────── */
/* 앞으로 200명을 더 받는다고 놓고, 네 벌에 고르게 나뉜다고 본다.
   언 벌에는 이미 답한 사람이 있다 (a 58 · b 51). */
const HAVE = { a: 58, b: 51 };
const MORE = 200 / 4;
const weighted = [];
for (const v of design.variants) {
  const L = v.designId.slice(-1);
  const n = (HAVE[L] ?? 0) + MORE;
  for (const s of v.sets) if (!s.trap) weighted.push({ one: s, n });
}
const nowOnly = frozen.flatMap(v => {
  const L = v.designId.slice(-1);
  return v.sets.filter(s => !s.trap).map(s => ({ one: s, n: HAVE[L] ?? 0 }));
});
const seNow  = predictedSE(nowOnly);
const seNext = predictedSE(weighted);

const checks = [];
const push = (ok, id, msg) => checks.push({ ok, id, msg });
push(!!seNext, "solvable", "네 벌을 합쳐 계수 " + COLS.length + "개가 풀려야 한다");
if (seNow && seNext) {
  for (const [k, t] of Object.entries(TARGETS)) {
    const i = COLS.indexOf(k);
    const a = widthInMinutes(seNow, i), b = widthInMinutes(seNext, i);
    push(b < a * 0.75, "narrower:" + k,
      t.label + " 의 폭이 뚜렷하게 좁아져야 한다 — " + a.toFixed(1) + "분 → " + b.toFixed(1) + "분");
  }
  const wi = COLS.indexOf("walkMin");
  push(seNext[wi] < seNow[wi], "anchor-tighter",
    "걷기 자가 더 정확해져야 한다 — 모든 환산이 이걸로 나눈다");
}
const newMix = made.flat().filter(s => s.blocks).map(s => s.blocks.join("|"));
push(newMix.includes(NEEDED_MIX), "mixed-pair",
  "아직 없던 섞인 짝(" + NEEDED_MIX + ")이 들어가야 한다 — 지금 " + newMix.join(", "));
const tooEasy = made.flat().filter(s => !s.trap).filter(s => { const p = pOf(s); return p < P_MIN || p > P_MAX; });
push(tooEasy.length === 0, "no-obvious",
  "뻔한 문항이 없어야 한다 (" + tooEasy.length + "개 발견)");
const stuck = stuckPairs(design.variants.flatMap(v => v.sets));
push(stuck.length === 0, "no-stuck-pairs",
  "늘 같은 편에 서는 칸 짝이 없어야 한다" + (stuck.length ? " — 🔴 " + stuck.join(" · ") : ""));

/* ── 사람이 보는 자리 ───────────────────────────────────────────────── */
const LB = (k, v) => {
  if (k === "walkMin")  return v + "분";
  if (k === "price")    return v / 10000 + "만원";
  if (k === "cost")     return v === 0 ? "무료" : v / 10000 + "만원";
  if (k === "queueMin") return v === 0 ? "줄 없음" : "줄 " + v + "분";
  return { FLAT: "평지", GENTLE: "완만", STEEP: "가파름", SHADED: "그늘", SUNNY: "볕",
           QUIET: "한산", SOME: "적당", PACKED: "붐빔", INDOOR: "실내", OUTDOOR: "실외",
           LOCAL_ONLY: "동네만", SNS_FAMOUS: "SNS유명" }[v] ?? String(v);
};
function report() {
  console.log("언 벌   : " + frozen.map(v => v.designId).join(", ") + "   (건드리지 않음)");
  console.log("새 벌   : " + NEW_LETTERS.map(L => NEW_FAMILY + "-" + L).join(", ")
    + "   (" + (attempt + 1) + "번째 뽑기에서 통과)");
  console.log("");
  console.log("■ 응답을 받기 전에 계산한 95% 구간 폭 (걷기 분)");
  console.log("   " + "칸".padEnd(14) + "지금(109명)".padStart(12) + "네 벌 +200명".padStart(14));
  for (let i = 0; i < COLS.length; i++) {
    if (!seNow || !seNext) break;
    const a = widthInMinutes(seNow, i), b = widthInMinutes(seNext, i);
    const t = TARGETS[COLS[i]];
    console.log("   " + (t ? t.label : COLS[i]).padEnd(14)
      + (a.toFixed(1) + "분").padStart(12) + (b.toFixed(1) + "분").padStart(14)
      + (t ? "   ← 노린 칸" : ""));
  }
  console.log("");
  console.log("■ 새 문항 — 예상 확률이 5:5 에 가까울수록 배우는 것이 많다");
  for (const [i, L] of NEW_LETTERS.entries()) {
    console.log("");
    console.log("  ── " + NEW_FAMILY + "-" + L + " " + "─".repeat(40));
    for (const s of made[i]) {
      const blocks = s.blocks || [s.block, s.block];
      const where = s.trap ? "함정" : blocks[0] === blocks[1] ? BLOCKS[blocks[0]].label
        : blocks.map(b => BLOCKS[b].label).join(" ↔ ");
      const p = s.trap ? null : pOf(s);
      console.log("  " + s.setId.padEnd(7) + (s.when || "").padEnd(11) + where.padEnd(14)
        + (p == null ? "" : "예상 " + (100 * p).toFixed(0) + " : " + (100 - 100 * p).toFixed(0)));
      const keys = [...new Set(s.alternatives.flatMap(a => Object.keys(a)))];
      for (const k of keys) {
        const spec = attrsOf(blocks[0])[k] || attrsOf(blocks[1])[k];
        const a = s.alternatives[0][k], b = s.alternatives[1][k];
        console.log("      " + k.padEnd(10)
          + (a === undefined ? "—" : LB(k, a)).padStart(8) + "   "
          + (b === undefined ? "—" : LB(k, b)).padEnd(8)
          + (a === b && a !== undefined ? "   (같음)" : ""));
      }
    }
  }
  console.log("");
  for (const c of checks) console.log("  " + (c.ok ? "ok  " : "🔴  ") + c.id + " — " + c.msg);
}

if (process.argv.includes("--check")) { report(); process.exit(checks.every(c => c.ok) ? 0 : 1); }

const bad = checks.filter(c => !c.ok);
if (bad.length) {
  report();
  console.error("\n🔴 검사 " + bad.length + "건 실패. index.html 을 고치지 않았다.");
  process.exit(1);
}

const json = JSON.stringify(design, null, 2);
if (process.argv.includes("--print")) {
  console.log(json);
} else {
  const LF = String.fromCharCode(10);
  writeFileSync(TARGET, html.slice(0, iOpen + OPEN.length) + LF + json + LF + html.slice(iClose), "utf8");
  console.log("index.html 의 문항을 다시 썼다 — " + design.variants.length + "벌 × "
    + design.variants[0].sets.length + "문항");
  console.log("  얼린 벌 " + frozen.length + " · 새 벌 " + NEW_LETTERS.length + " · 검사 전부 통과 ✅");
}
