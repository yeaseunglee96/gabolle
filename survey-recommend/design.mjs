#!/usr/bin/env node
/**
 * 짝 비교 문항을 만든다 — 손으로 적지 않는다. (S15P21E201-754)
 *
 * 🔴 이 파일은 ../survey-place/design.mjs 와 **같은 씨앗 · 같은 속성 · 같은
 *    알고리즘**을 쓴다. 일부러 그렇게 했다.
 *
 *    난수는 씨앗 하나로 굴러가고 쌍을 앞에서부터 차례로 뽑으므로,
 *    N_REAL 을 11 에서 5 로 줄여도 **앞의 다섯 쌍은 글자 하나까지 같다.**
 *    그래서 이 설문의 s01~s05 와 survey-place 의 s01~s05 는 **같은 문항**이고,
 *    두 설문의 응답을 setId 로 그냥 합칠 수 있다. 씨앗을 바꾸면 그게 끊긴다.
 *    (같은지 확인하는 한 줄 명령은 README 3.4 에 있다.)
 *
 * 🔴 왜 손으로 안 적나.
 *    사람이 쌍을 지어내면 자기도 모르게 "가격이 싼 쪽이 대체로 좋아 보이는"
 *    쌍만 만든다. 그러면 나중에 계산이 내는 것은 사람들의 취향이 아니라
 *    **문항을 만든 사람의 취향**이다. 그리고 그건 결과만 봐서는 안 보인다.
 *
 * 🔴 씨앗(seed)을 바꾸지 않는다.
 *    씨앗이 같으면 언제 돌려도 같은 쌍이 나온다. 회차를 나눠 받은 응답을
 *    나중에 합치려면 **모두 같은 문항에 답했어야** 한다. 씨앗을 바꾸면
 *    그 순간 앞의 응답과 뒤의 응답이 서로 다른 설문이 된다.
 *    그리고 데이터베이스에는 문항의 값이 아니라 setId 만 들어간다 —
 *    값을 되찾는 유일한 길이 이 파일이다.
 *
 * 쓰는 법:
 *    node survey-recommend/design.mjs          # index.html 안의 문항 블록을 다시 쓴다
 *    node survey-recommend/design.mjs --print  # 화면에만 찍는다 (파일 안 건드림)
 */

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/* ── 씨앗 ─────────────────────────────────────────────────────────────── */
const SEED = 20260907;                              /* survey-place 와 같은 값. 바꾸지 않는다 */
/* 🔴 판 번호가 v1 → v2 다 (2026-09-10). 씨앗은 그대로인데 **어느 문항을
      쓰는지**가 바뀌었다. 옛 판(v1)의 응답과 섞이면 안 되므로 이름을 가른다.
      (지금 응답이 0건이라 옮길 값은 없다. 그래도 이름은 가른다 — 나중에
       옛 판 응답이 어딘가에서 나왔을 때 구별할 유일한 표시다.) */
const DESIGN_ID = "recommend-v2-seed-20260907";

/* ── 🔴 왜 문항을 다시 골랐나 (2026-09-10, S15P21E201-754) ─────────────
   v1 은 앞의 다섯(s01~s05)을 그대로 썼다. 그런데 그 다섯 안에서
   **가격과 골목 밀집이 거의 같이 움직였다.** 두 값이 같이 움직이면
   "가격 때문인지 골목 때문인지" 를 계산이 못 가른다.

   그 정도를 재는 숫자가 **분산 팽창**(VIF — 다른 속성과 겹쳐서 그 속성
   계수의 불확실함이 몇 배로 부풀었나. 1 이면 안 겹침, 10 이 넘으면
   보통 못 쓴다고 본다)이다. v1 은 **가격 27.65 · 골목 24.53** 이었다.
   27배면 사람을 아무리 모아도 그 둘을 못 가른다 — 표본을 늘려 해결되는
   문제가 아니라 **문항 조합의 문제**다.

   전수 탐색으로 고른 조합(A안)이 아래 PICK 이다. 최대 팽창 **2.06**
   (가격과 골목 밀집이 나란히 2.06 으로 가장 높다). 27.65 → 2.06.

   🔴 이 숫자는 이 저장소에서 다시 재 본 값이다. 재는 법: 문항마다
      (위 카드 − 아래 카드)로 다섯 속성의 차를 만들어 행렬을 만들고,
      속성 하나를 나머지 넷으로 회귀해 1/(1−R²) 을 낸다 (**절편을 두지
      않는다** — 차이값은 이미 0 을 중심으로 놓인 값이라 절편을 두면
      자유도가 모자라 무한대가 나온다). 함정 문항(tr01)은 추정에서 빠지므로
      셈에서도 뺀다.

   🔴 s01 · s02 · s05 는 v1 과 **같은 문항**이다. 뺀 것은 s03 · s04 이고
      새로 넣은 것이 s07 · s08 · s10 · s11 이다.
   ─────────────────────────────────────────────────────────────────── */
const N_POOL = 11;     /* 🔴 survey-place 와 **같은 수**를 만든다. 아래 참고 */
const PICK = [1, 2, 5, 7, 8, 10, 11];   /* 그중 실제로 물을 일곱. setId 번호 그대로다 */
const TRAP_AT = 3;     /* 함정이 들어갈 자리 (0부터 세서 네 번째) */

/* 🔴 **11개를 만든 뒤 골라낸다.** 앞의 N 개를 쓰는 것이 아니다.
      난수가 씨앗 하나로 굴러가므로 s07 · s10 같은 뒤쪽 문항은 11개를 다
      만들어야 나온다. 5개만 만들고 색인 7을 찾으면 없다.
      🔴 그리고 고른 문항의 **이름을 다시 매기지 않는다.** s07 은 계속 s07 이다 —
         setId 가 survey-place 응답과 합치는 유일한 열쇠라, 여기서 s01~s07 로
         다시 번호를 매기면 s04 라는 이름 아래 서로 다른 두 질문이 생긴다. */

/* 🔴 화면은 **한 장에 두 문항**이다. 진짜 일곱에 함정 하나라 여덟 칸이고,
      나눠서 정확히 4장이 된다. 문항 수를 바꾸면 이 나눗셈이 안 떨어지므로
      아래에서 막는다 — 안 막으면 마지막 장에 한 문항만 남고, 그 장만
      길이가 달라서 사람이 "덜 나왔나" 하고 새로고침한다. */
const PER_PAGE = 2;
const PAGES = 4;

/* ── 속성 다섯 ─────────────────────────────────────────────────────────
   가격 · 걷는 거리 · 줄서기 · 골목 밀집 · 알려진 정도.
   🔴 bigData/process/choice-design.mjs 의 속성(걷는 시간 · 경사 · 계단 · 환승)이
      아니다. 그쪽은 **경로 비용**을 재는 것이고 여기는 **장소 추천 순위**다.

   방향이 합의된 셋(price·walkMin·queueMin — 작을수록 좋다)과
   방향을 우리가 **모르는** 둘(sameStreet·fame)로 나뉜다.
   모르는 둘이 이 설문의 진짜 질문이다.
   ─────────────────────────────────────────────────────────────────── */
const ATTRS = {
  price:      { levels: [10000, 20000, 40000, 70000], lowerIsBetter: true  },
  walkMin:    { levels: [3, 8, 15, 25],               lowerIsBetter: true  },
  queueMin:   { levels: [0, 20, 40],                  lowerIsBetter: true  },
  sameStreet: { levels: [0, 3, 12],                   lowerIsBetter: null  }, /* 같은 골목의 동종 가게 수 */
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
 * 🔴 지배되는 쌍(한쪽이 사실상 모든 조건에서 나은 쌍)을 뺀다.
 *    한쪽이 가격·거리·줄서기 셋 모두에서 나쁘지 않고 하나라도 낫고,
 *    나머지 둘(골목·알려진 정도)이 **같다면** 아무도 나쁜 쪽을 안 고른다.
 *    그런 쌍은 한 자리를 먹고 아무것도 안 알려준다.
 *    (문항이 여섯뿐이라 survey-place 때보다 자리 한 칸이 훨씬 비싸다.)
 *
 *    반대로 셋에서 나은데 나머지 둘이 **다르면** 그건 뻔하지 않다 —
 *    "얼마를 더 걸어야 유명한 집에 갈 만한가" 가 정확히 우리가 묻는 것이다.
 *
 *    🔴 이 검사를 통과하지 않는 쌍이 딱 하나 있다: 함정 문항이다. 일부러다.
 */
function obviouslyOneSided(a, b) {
  if (UNKNOWN.some(k => a[k] !== b[k])) return false;
  const aWins = KNOWN.every(k => a[k] <= b[k]) && KNOWN.some(k => a[k] < b[k]);
  const bWins = KNOWN.every(k => b[k] <= a[k]) && KNOWN.some(k => b[k] < a[k]);
  return aWins || bWins;
}

/* ── 수준이 골고루 나오게 ────────────────────────────────────────────
   한 수준이 한 번도 안 나오면 그 수준의 무게는 못 잰다.
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

  while (chosen.length < N_POOL) {
    /* 후보 쌍을 여러 개 뽑아 두고 그중 가장 골고루인 것을 고른다 */
    const cands = [];
    let guard = 0;
    while (cands.length < 40 && guard++ < 20000) {
      const a = pick(pool), b = pick(pool);
      if (differCount(a, b) < 3) continue;            /* 너무 비슷하면 답이 흐려진다 */
      if (obviouslyOneSided(a, b)) continue;          /* 답이 뻔하면 자리를 낭비한다 */
      const sig = JSON.stringify([a, b]);
      const rev = JSON.stringify([b, a]);
      if (seen.has(sig) || seen.has(rev)) continue;   /* 같은 쌍을 두 번 묻지 않는다 */
      cands.push([a, b]);
    }
    if (cands.length === 0) throw new Error("쌍을 못 만들었다 — 제약이 너무 빡빡하다");
    cands.sort((x, y) => costOf(x, counts) - costOf(y, counts));
    const best = cands[0];
    seen.add(JSON.stringify(best));
    bump(best, counts);
    chosen.push(best);
  }

  /* 🔴 함정 문항 — 가격 · 골목 · 알려진 정도가 같고 거리 · 줄서기만 나쁜 쪽.
        "비싼 게 맛있겠지" 같은 진짜 취향이 끼어들 자리를 없앤다.
        여기서 나쁜 쪽(1번 색인)을 고르면 읽지 않고 찍은 것으로 본다.
        🔴 survey-place 의 tr01 과 값이 같다. 두 설문의 함정 결과를 같이 볼 수 있다. */
  const trap = [
    { price: 20000, walkMin: 3,  queueMin: 0,  sameStreet: 3, fame: "LOCAL_ONLY" },
    { price: 20000, walkMin: 25, queueMin: 40, sameStreet: 3, fame: "LOCAL_ONLY" }
  ];

  /* 🔴 11개 중 PICK 에 적힌 것만 쓴다. **이름은 원래 번호 그대로** 둔다 —
        다시 번호를 매기면 s04 라는 이름 아래 서로 다른 두 질문이 생기고,
        survey-place 응답과 setId 로 합칠 수 없게 된다. */
  const picked = PICK.map(n => {
    if (!chosen[n - 1]) throw new Error(`s${n} 를 못 만들었다 — N_POOL(${N_POOL})보다 큰 번호다`);
    return { setId: "s" + String(n).padStart(2, "0"), trap: false, alternatives: chosen[n - 1] };
  });

  const sets = [];
  let k = 0;
  for (let i = 0; i < picked.length + 1; i++) {
    if (i === TRAP_AT) {
      sets.push({ setId: "tr01", trap: true, trapCorrect: 0, alternatives: trap });
    } else {
      sets.push(picked[k]);
      k += 1;
    }
  }
  return sets;
}

/* ── 응답 시간을 남기는 눈금 ─────────────────────────────────────────
   🔴 몇 초 걸렸는지를 **초가 아니라 구간**으로만 남긴다. 안 읽고 찍은 응답을
      거르는 데는 구간이면 충분하고, 초 단위는 그 이상(재식별)에 쓸모가 있다.
   🔴 이 눈금이 여기 있는 이유는 **한 벌만 두려고**다. 화면(index.html)도
      서버(server.mjs)도 이 값을 읽고, 표의 ms_bucket 제약(0~7)도 이 길이에서 나온다.
      세 곳에 따로 적으면 언젠가 한 곳만 바뀐다.
   ─────────────────────────────────────────────────────────────────── */
const MS_BUCKETS = [1500, 3000, 5000, 8000, 12000, 20000, 40000];

/* ── 내보내기 ──────────────────────────────────────────────────────── */
const design = {
  designId: DESIGN_ID,
  seed: SEED,
  perPage: PER_PAGE,
  msBuckets: MS_BUCKETS,
  attributes: Object.fromEntries(KEYS.map(k => [k, ATTRS[k].levels])),
  sets: buildSets()
};

/* 🔴 화면 3장에 딱 떨어지지 않으면 여기서 멈춘다. 숫자를 고친 사람이
      화면을 열어 보기 전에 알아야 한다. */
if (design.sets.length !== PER_PAGE * PAGES) {
  console.error(`문항이 ${design.sets.length}개다 — 화면 ${PAGES}장 × ${PER_PAGE}문항 = ${PER_PAGE * PAGES}개여야 한다.`);
  process.exit(1);
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
  const next = html.slice(0, i + open.length) + "\n" + json + "\n" + html.slice(j);
  writeFileSync(target, next, "utf8");
  console.log("index.html 의 문항 " + design.sets.length + "개를 다시 썼다 (" + DESIGN_ID + ")");
}
