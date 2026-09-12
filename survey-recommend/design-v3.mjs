#!/usr/bin/env node
/**
 * 짝 비교 v3 — 여덟 취향축을 덩어리 셋으로 잰다. (S15P21E201-842)
 *
 * 🔴 2026-09-12 정정 (S15P21E201-864). 아래 다섯 줄이 이 자리에 **사실과
 *    반대로** 적혀 있었다 — *"이 파일은 아직 설문에 안 붙었다. design.mjs(v2)가
 *    지금 살아 있는 설문을 만들고 있다"*. 안 붙은 것이 아니라 **붙어 있다.**
 *    S15P21E201-851(커밋 f4bab973)이 네 가지를 같이 넣으면서 붙였고, 그때
 *    이 머리말만 안 고쳤다.
 *
 *    문서가 **비관 쪽으로 틀린 것**이 버그보다 나빴다. 이 파일을 읽은 사람이
 *    "안 붙은 초안" 으로 알고 넘어갔다. 낡은 기록은 지우지 말고 정정한 날짜와
 *    함께 남긴다 — 다음 사람이 같은 것을 다시 확인하지 않게.
 *
 * 🔴 **이 파일이 지금 살아 있는 설문(/survey/)을 만든다.** 고치고 나면 인수가
 *    없는 채로 한 번 돌려서 index.html 의 문항 블록을 다시 써야 한다.
 *    (`--print` 는 찍기만 하고 `--check` 는 균형만 본다.)
 *
 *    붙일 때 함께 갔던 넷 — 다음에 속성을 바꿀 때도 같이 가야 한다.
 *      1) migrations/0007 — pairwise_choice 를 JSONB 로. 그 전에는 alt0_price
 *         처럼 칸이 박혀 있어 덩어리마다 다른 속성을 담을 수 없었다
 *      2) server.mjs      — 속성 검사와 INSERT 를 새 모양으로
 *      3) index.html      — 문항 블록 재생성 + 카드 문구 (덩어리마다 다르다)
 *      4) form_version    — 7 로 올린다
 *
 *    하나라도 빠지면 사람이 화면에서 통과하고 DB 에서 거절당한다.
 *    🔴 이번 변경(S15P21E201-864)은 **속성을 안 건드렸다.** 문항 순서와 고르는
 *       규칙만 바뀌었고 저장되는 모양이 그대로라 마이그레이션이 필요 없다.
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

/* 🔴 2026-09-12 (S15P21E201-864) 씨앗을 하루 올렸다. 덩어리·속성·수준은 그대로라
      구조는 여전히 v3 인데, 뻔한 쌍을 거르는 규칙이 세 개 늘어 **문항이 달라졌다.**
      같은 이름을 두면 같은 setId 아래 다른 질문이 생긴다. 응답이 0건인 지금이
      이름을 바꿀 수 있는 마지막 시점이다 — 한 건이라도 들어오면 못 바꾼다. */
const SEED = 20260912;
const DESIGN_ID = "recommend-v3-seed-20260912";

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

/* ── 🔴 한쪽이 아는-속성을 다 이길 때, 얼마나 기울어도 되나 ───────────
   아는-속성(작을수록 좋다) 전부에서 한쪽이 나은 쌍을 통째로 버리지는 않는다.
   그런 쌍이 **모르는 속성의 값을 매기는 유일한 자리**이기 때문이다 —
   「SNS 에서 유명한 곳을 위해 몇 분까지 더 걷나」는 그렇게밖에 못 묻는다.

   문제는 기울기다. 22분 더 걷고 1만원 더 내고 40분 줄까지 서야 먹자골목에
   갈 수 있는 쌍은 거의 전원이 같은 쪽을 고르고, 그러면 아무것도 안 알려주면서
   사람만 지치게 한다.

   그래서 **벌어진 칸 수를 양쪽에서 세어 견준다.** 잃는 쪽(아는 속성)이
   얻는 쪽(모르는 속성)보다 많이 벌어져 있으면 버린다.

   🔴 칸 수는 단위가 다른 것을 더한 값이다 — 걷기 한 칸과 유명세 한 칸은
      같은 크기가 아니다. 정확한 저울이 아니라 **기울기를 보는 눈금**으로만
      쓴다. 진짜 크기는 설문이 끝난 뒤 계수가 알려준다. 지금은 그걸 모르니까
      이 설문을 하는 것이고, 그래서 여기서는 거친 눈금이 맞다.

   ── 이 규칙이 실제로 걸러낸 것 (첫 판 recommend-v3-seed-20260911) ──
     · see02  「8분 · 평지」 대 「25분 · 완만」, 차이는 「한산 ↔ 적당」 한 칸
     · eat01  「3분 · 1만원 · 줄 없음」 대 「25분 · 2만원 · 줄 40분」,
              뒤쪽이 얻는 것은 먹자골목 한복판이라는 것 하나 */

/**
 * 🔴 지배되는 쌍(한쪽이 사실상 모든 조건에서 나은 쌍)을 뺀다.
 *    방향이 합의된 속성(작을수록 좋다) 전부에서 나쁘지 않고 하나라도 나은데
 *    방향을 모르는 속성이 거의 안 벌어졌다면, 아무도 나쁜 쪽을 안 고른다.
 *    그런 쌍은 자리를 먹고 아무것도 안 알려준다.
 *
 * 🔴 2026-09-12 정정 (S15P21E201-864). 원래 조건은 **"모르는 속성이 하나라도
 *    다르면 뻔하지 않다"** 였다. 너무 헐거웠다 — 한 칸만 달라도 통과라서
 *    위 주석의 see02 · eat01 이 둘 다 빠져나갔다. 이제 양쪽 칸 수를 견준다.
 */
function obviouslyOneSided(a, b, attrs) {
  const keys = Object.keys(attrs);
  const known   = keys.filter(k => attrs[k].lowerIsBetter === true);
  const unknown = keys.filter(k => attrs[k].lowerIsBetter === null);
  const rank = (k, v) => attrs[k].levels.indexOf(v);   /* 수준 배열이 곧 순서다 */
  const aWins = known.every(k => rank(k, a[k]) <= rank(k, b[k])) && known.some(k => rank(k, a[k]) < rank(k, b[k]));
  const bWins = known.every(k => rank(k, b[k]) <= rank(k, a[k])) && known.some(k => rank(k, b[k]) < rank(k, a[k]));
  /* 아는 속성이 서로 엇갈린다 = 무엇을 포기할지 고르는 진짜 문항이다 */
  if (!aWins && !bWins) return false;
  const spread = ks => ks.reduce((acc, k) => acc + Math.abs(rank(k, a[k]) - rank(k, b[k])), 0);
  return spread(unknown) < spread(known);
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
      /* 🔴 공통 자가 양쪽 같으면 그 문항은 자 맞추기에 아무것도 안 보탠다.
            걷는 시간은 덩어리끼리 배율을 맞추는 유일한 기준인데, 첫 판의
            play02 는 양쪽 다 「걸어서 3분」이라 여덟 중 하나를 그렇게 버렸다.
            섞인 문항에만 걸려 있던 조건을 전 문항으로 올린다. */
      if (a.walkMin === b.walkMin) continue;
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
/* ── 🔴 섞인 문항의 뻔한 쌍 (2026-09-12 추가, S15P21E201-864) ─────────
   여기에는 검사가 아예 없었다. 걷는 시간이 다른지만 보고 넘어갔다. 그래서
   첫 판의 mx01 이 이렇게 나왔다 — 「하는 곳: 걸어서 3분 · 1만원」 대
   「먹는 곳: 걸어서 25분 · 2만원 · 줄 40분」. 가까운 쪽이 싸고 줄도 없다.
   배가 고픈 사람 말고는 나를 고를 이유가 없다.

   덩어리가 다르면 속성 이름이 달라 대부분 견줄 수가 없다. 견줄 수 있는 것은 둘뿐이다.
     · walkMin — 양쪽에 같은 뜻·같은 수준으로 있다 (공통 자)
     · 돈 — 먹는 곳의 price 와 하는 곳의 cost 는 둘 다 원이라 그대로 견준다.
            보는 곳에는 돈 칸이 아예 없다.

   그래서 둘로 나눈다.
     ① 양쪽에 돈이 있으면: 싼 쪽이 가깝기까지 하면 버린다. 돈과 거리가
        반대로 가야 사람이 실제로 무엇을 포기할지 고른다
     ② 한쪽에 돈이 없으면: 가까운 쪽이 자기 덩어리의 나머지 아는-속성에서도
        가장 좋은 등급이면 버린다 — 가깝고 평지인 카드는 그냥 좋은 카드다

   🔴 돈이 양쪽 같은 액수인 것은 안 버린다. 그때 답이 알려주는 것이
      「그 갈래를 위해 몇 분까지 걷나」라서, 섞인 문항이 원래 맡은 일이다. */
const MONEY_OF = { eat: "price", play: "cost", see: null };

function mixedOneSided(b0, b1, a, b) {
  const near = a.walkMin < b.walkMin ? 0 : 1;           /* 가까운 쪽 */
  const money = [MONEY_OF[b0], MONEY_OF[b1]];

  if (money[0] && money[1]) {                           /* ① 양쪽에 돈이 있다 */
    const m0 = a[money[0]], m1 = b[money[1]];
    if (m0 === m1) return false;
    return (m0 < m1 ? 0 : 1) === near;                  /* 싼 쪽 == 가까운 쪽 */
  }

  /* ② 한쪽에 돈이 없다 — 가까운 카드가 자기 쪽 아는-속성에서도 최고 등급인가 */
  const block = near === 0 ? b0 : b1;
  const card  = near === 0 ? a  : b;
  const attrs = attrsOf(block);
  return Object.keys(attrs)
    .filter(k => k !== "walkMin" && attrs[k].lowerIsBetter === true)
    .every(k => attrs[k].levels.indexOf(card[k]) === 0);
}

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
    if (mixedOneSided(b0, b1, a, b)) continue;
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

/* ── 🔴 화면에 나가는 순서 — 여행의 시간 순서다 (2026-09-12, S15P21E201-864) ──
   전에는 덩어리별로 몰아서 냈다: 먹 · 먹 · 보 · 함정 · 보 · 하 · 하 · 섞.
   같은 덩어리가 연달아 오면 사람이 "아까 그 질문을 또 받는다" 고 느끼고,
   덩어리가 바뀌는 자리에서는 상황이 통째로 갈아엎어져 처음부터 다시 읽어야
   한다. 여덟 번을 그렇게 한다. 짝 비교는 그 자체로 지치는 형식이라
   상황까지 매번 새로 주면 대충 찍게 된다.

   부산 1박 2일의 시간 순서로 흩어 놓으면 장면이 바뀌는 것 자체가 쉬는 자리가
   되고, 다음 문항이 무엇을 물을지 짐작이 된다.

   🔴 장면은 **때와 곳까지만** 말한다. "오늘은 특별한 날이니까" · "큰맘 먹고"
      같은 말을 넣지 않는다. 추정은 한 사람의 답 여덟 개를 **같은 저울에서
      나온 것**으로 보는데, 저울을 문항마다 바꾸면 그 차이가 잡음으로 처리되고
      계수만 흐려진다. 장면은 배경이지 판돈이 아니다.

   🔴 카드에 적히는 조건은 한 글자도 안 바뀐다. 장면은 머리말일 뿐이고 DB 에
      들어가는 것은 여전히 문항 번호(set_id)다. **마이그레이션이 필요 없다.**

   🔴 함정은 TRAP_AT 자리에 그대로 둔다. 그 자리가 「점심」이라 먹는 곳 쌍인
      함정이 장면과 어긋나지 않는다. 아래 buildSets 가 그것을 검사한다. */
const SCENES = [
  { setId: "eat01",  when: "첫날 저녁",    lead: "부산에 도착해 첫 끼를 먹습니다" },
  { setId: "see01",  when: "첫날 밤",      lead: "밥을 먹고 숙소 근처를 한 바퀴 돕니다" },
  { setId: "see02",  when: "이튿날 아침",  lead: "아침에 나와 어디부터 볼지 고릅니다" },
  { setId: "tr01",   when: "이튿날 점심",  lead: "걷다 보니 점심때가 됐습니다" },
  { setId: "play01", when: "이튿날 오후",  lead: "오후에 한 가지 해 봅니다" },
  { setId: "play02", when: "해 기울 무렵", lead: "돌아가기 전에 하나만 더 해 볼 참입니다" },
  { setId: "eat02",  when: "마지막 저녁",  lead: "부산에서의 마지막 저녁입니다" },
  { setId: "mx01",   when: "떠나기 전",    lead: "기차 시간까지 한 시간 남았습니다" }
];

/* ── 조립 ─────────────────────────────────────────────────────────── */
function buildSets() {
  const byId = new Map();
  for (const b of Object.keys(BLOCKS)) {
    setsForBlock(b, PER_BLOCK).forEach((alts, i) => {
      const setId = b + String(i + 1).padStart(2, "0");
      byId.set(setId, { setId, block: b, trap: false, alternatives: alts });
    });
  }
  mixedSets(MIXED).forEach((m, i) => {
    const setId = "mx" + String(i + 1).padStart(2, "0");
    byId.set(setId, { setId, blocks: m.blocks, trap: false, alternatives: m.alternatives });
  });
  byId.set(TRAP.setId, TRAP);

  /* 🔴 장면 목록과 실제로 만들어진 문항이 어긋나면 여기서 멈춘다. 조용히
        빠지면 사람이 일곱 문항만 받고 함정 자리도 한 칸 밀리는데, 그건
        화면을 끝까지 넘겨 보기 전에는 안 보인다. */
  const sets = SCENES.map(sc => {
    const one = byId.get(sc.setId);
    if (!one) throw new Error("장면 목록에 있는 문항이 안 만들어졌다: " + sc.setId);
    return { ...one, when: sc.when, lead: sc.lead };
  });
  if (sets.length !== byId.size) {
    throw new Error("만든 문항 " + byId.size + "개 가운데 " + sets.length + "개만 장면이 있다 — SCENES 를 확인해라");
  }
  if (sets[TRAP_AT].setId !== TRAP.setId) {
    throw new Error("함정이 " + (TRAP_AT + 1) + "번째 자리에 없다 — SCENES 순서와 TRAP_AT 을 맞춰라");
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
  for (const [i, s] of sets.entries()) {
    const where = s.trap ? "함정"
      : (s.blocks ? s.blocks.map(b => BLOCKS[b].label).join(" ↔ ") : BLOCKS[s.block].label);
    console.log("  " + String(i + 1) + ". " + s.setId.padEnd(6) + " " + (s.when || "").padEnd(12) + " " + where);
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
