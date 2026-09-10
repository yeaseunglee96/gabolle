/**
 * 라운드4 5단계 — **배포될 채점기로 후보 풀을 줄 세운다.** 예행(조정용)이다.
 *
 * 🔴 판정용은 열지 않는다. 여기서 나온 숫자를 보고 `r4-preregistration.md` 를 쓰고,
 *    그 다음에 `r4-08-final.mjs` 로 판정용을 **딱 한 번** 연다.
 *
 *   node --max-old-space-size=8192 r4-05-score.mjs
 *
 * ── 무엇을 재나 ──────────────────────────────────────────────────────────────
 *
 * 두 가지를 따로 잰다. 섞으면 무엇이 나쁜지 알 수 없다.
 *
 * **① 전체 줄 세우기** — 후보 55,392곳 전부를 점수순으로 놓고 정답이 몇 위인가.
 *    라운드1~3 과 같은 눈금이라 견줄 수 있다. 성공 기준도 이 눈금으로 정해져 있다.
 *
 * **② 실제 배포 흐름** — 🔴 서버는 후보 전부를 채점하지 않는다.
 *    `PlaceCandidateQueryService` 가 **거리순으로 가까운 200곳만** 남기고
 *    (`candidateLimit` 기본 200), **그 다음에** 채점기가 그 200곳을 줄 세운다.
 *    그래서 200곳 안에 못 든 정답은 **점수와 무관하게 영원히 안 보인다.**
 *    사용자가 실제로 보는 것은 그중 상위 10~20곳이다
 *    (`RecommendationProperties.defaultTopK` 기본 10 · 앱은 20 을 보낸다).
 *
 * ── 출발지 ──────────────────────────────────────────────────────────────────
 * 거리 항이 있으니 **출발지가 있어야 점수가 나온다.** 그런데 사용자가 0명이라
 * 실제 출발지 분포를 모른다. 그래서 후보 풀의 좌표에서 **씨앗 고정으로 100곳**을
 * 뽑아 출발지로 쓴다 — "부산 어딘가 음식점이 있는 동네에 묵는 사람" 이라는 뜻이다.
 * 🔴 내가 관광지 몇 곳을 손으로 고르면 그 선택이 곧 결론이 된다. 그래서 안 고른다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { scoreCandidate, DEFAULT_RADIUS_M, DEFAULT_CANDIDATE_LIMIT } from "./lib/baseline-scorer.mjs";
import { emptyFeatures, derivedFeatures, cuisineOf } from "./lib/place-features.mjs";
import { haversine } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const D = (f) => JSON.parse(fs.readFileSync(path.join(HERE, "data", f), "utf8"));

const ORIGIN_SEED = 7054026;
const N_ORIGINS = 100;
const TOP_K_SHOWN = 20; // 앱이 실제로 요청하는 값

const pool = D("r4-pool.json");
const rows = pool.rows;
const N = rows.length;
const tune = D("r4-split-tune.json");
console.log(`후보 ${N}곳 (상가정보 ${pool.sbizCount} + OSM 단독 ${pool.osmOnlyCandidates})`);
console.log(`조정용 정답 ${tune.count}곳 (씨앗 ${tune.seed})\n`);

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}

// ── 출발지 100곳 ─────────────────────────────────────────────────────────────
const rndO = mulberry32(ORIGIN_SEED);
const origins = [];
for (let k = 0; k < N_ORIGINS; k++) {
  const r = rows[Math.floor(rndO() * N)];
  origins.push({ lat: r.lat, lon: r.lon });
}

// ── 미리 구워 두는 것 ────────────────────────────────────────────────────────
const TB = new Float64Array(N);
for (let i = 0; i < N; i++) TB[i] = hash01(rows[i].id);
const LAT = new Float64Array(N), LON = new Float64Array(N);
for (let i = 0; i < N; i++) { LAT[i] = rows[i].lat; LON[i] = rows[i].lon; }
const FEAT_EMPTY = rows.map(() => emptyFeatures());
const FEAT_DERIVED = rows.map((r) => derivedFeatures(r));

const truthIdx = new Map(rows.map((r, i) => [r.id, i]));
const tuneIdx = tune.items.map((t) => truthIdx.get(t.storeId)).filter((i) => i != null);
const isTruth = new Uint8Array(N);
for (const i of tuneIdx) isTruth[i] = 1;
console.log(`조정용 정답 ${tuneIdx.length}곳을 후보에서 찾았다\n`);

/**
 * 한 출발지에서 한 시나리오로 줄 세우고 지표를 낸다.
 * @returns 전체 줄 세우기 지표 + 실제 배포 흐름 지표
 */
function runOne(origin, feats, user) {
  const dist = new Float64Array(N);
  for (let i = 0; i < N; i++) dist[i] = haversine(origin.lat, origin.lon, LAT[i], LON[i]);

  const score = new Float64Array(N);
  for (let i = 0; i < N; i++) {
    score[i] = scoreCandidate({ distanceM: dist[i], features: feats[i] }, user).total;
  }

  // ── ① 전체 줄 세우기 ──────────────────────────────────────────────────────
  const order = new Int32Array(N);
  for (let i = 0; i < N; i++) order[i] = i;
  const arr = Array.from(order);
  arr.sort((a, b) => score[b] - score[a] || TB[a] - TB[b]);
  const ranks = [];
  for (let p = 0; p < N; p++) if (isTruth[arr[p]]) ranks.push(p + 1);
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const cnt = (k) => { let c = 0; for (const r of ranks) if (r <= k) c++; return c; };

  // ── ② 실제 배포 흐름 ──────────────────────────────────────────────────────
  // 반경 5km 안 → 거리순 200곳 → 그 200곳을 채점 → 상위 20곳을 사용자가 본다
  const inRadius = [];
  for (let i = 0; i < N; i++) if (dist[i] <= DEFAULT_RADIUS_M) inRadius.push(i);
  inRadius.sort((a, b) => dist[a] - dist[b] || (rows[a].id < rows[b].id ? -1 : 1));
  const candSet = inRadius.slice(0, DEFAULT_CANDIDATE_LIMIT);
  const effectiveRadiusM = candSet.length ? dist[candSet[candSet.length - 1]] : 0;
  let reach = 0;
  for (const i of candSet) if (isTruth[i]) reach++;
  const scored = [...candSet].sort((a, b) => score[b] - score[a] || TB[a] - TB[b]);
  let shown = 0;
  for (let p = 0; p < Math.min(TOP_K_SHOWN, scored.length); p++) if (isTruth[scored[p]]) shown++;

  return {
    n,
    r50: (cnt(50) / n) * 100, r100: (cnt(100) / n) * 100, r200: (cnt(200) / n) * 100,
    r500: (cnt(500) / n) * 100, r1000: (cnt(1000) / n) * 100,
    medPct: (ranks[Math.floor(n / 2)] / N) * 100,
    inRadius: inRadius.length,
    effectiveRadiusM,
    reachPct: (reach / n) * 100,
    shownPct: (shown / n) * 100,
  };
}

function summarize(list, key) {
  const v = list.map((x) => x[key]).sort((a, b) => a - b);
  const mean = v.reduce((a, b) => a + b, 0) / v.length;
  return { mean, p10: v[Math.floor(v.length * 0.1)], med: v[Math.floor(v.length / 2)], p90: v[Math.floor(v.length * 0.9)] };
}

// ── 시나리오 ────────────────────────────────────────────────────────────────
const scenarios = [
  {
    k: "무작위",
    label: "기준선 — 아무 순서나",
    feats: FEAT_EMPTY,
    user: { codes: {}, scores: {}, mobilityWarnings: 0 },
    zero: true,
  },
  {
    k: "asis",
    label: "🔴 **지금 배포하면 이렇게 된다** — place_feature 가 비어 있다",
    feats: FEAT_EMPTY,
    user: { codes: {}, scores: {}, mobilityWarnings: 0 },
  },
  {
    k: "cuisine-한식",
    label: "피처를 채웠다면 — 음식 태그만 (사용자가 '한식' 을 골랐을 때)",
    feats: FEAT_DERIVED,
    user: { codes: { CATEGORY: ["FOOD"], FOOD_PREFERENCE: ["한식"] }, scores: {}, mobilityWarnings: 0 },
  },
  {
    k: "cuisine-일식",
    label: "피처를 채웠다면 — 음식 태그만 (사용자가 '일식' 을 골랐을 때)",
    feats: FEAT_DERIVED,
    user: { codes: { CATEGORY: ["FOOD"], FOOD_PREFERENCE: ["일식"] }, scores: {}, mobilityWarnings: 0 },
  },
];

const results = [];
for (const s of scenarios) {
  // 무작위는 점수가 아니라 동점 깨기만으로 줄 세우므로 아래에서 따로 만든다
  const per = s.zero ? [] : origins.map((o) => runOne(o, s.feats, s.user));
  results.push({ ...s, per });
  process.stdout.write(`  ${s.k} 완료\n`);
}

// 🔴 무작위는 위 루프에서 asis 와 같은 점수가 나온다 (둘 다 거리항만). 따로 만든다
{
  const per = [];
  for (const o of origins) {
    const dist = new Float64Array(N);
    for (let i = 0; i < N; i++) dist[i] = haversine(o.lat, o.lon, LAT[i], LON[i]);
    const arr = Array.from({ length: N }, (_, i) => i).sort((a, b) => TB[a] - TB[b]);
    const ranks = [];
    for (let p = 0; p < N; p++) if (isTruth[arr[p]]) ranks.push(p + 1);
    ranks.sort((a, b) => a - b);
    const n = ranks.length;
    const cnt = (k) => ranks.filter((x) => x <= k).length;
    const inRadius = [];
    for (let i = 0; i < N; i++) if (dist[i] <= DEFAULT_RADIUS_M) inRadius.push(i);
    inRadius.sort((a, b) => dist[a] - dist[b]);
    const candSet = inRadius.slice(0, DEFAULT_CANDIDATE_LIMIT);
    let reach = 0;
    for (const i of candSet) if (isTruth[i]) reach++;
    const shuffled = [...candSet].sort((a, b) => TB[a] - TB[b]);
    let shown = 0;
    for (let p = 0; p < Math.min(TOP_K_SHOWN, shuffled.length); p++) if (isTruth[shuffled[p]]) shown++;
    per.push({
      n, r50: (cnt(50) / n) * 100, r100: (cnt(100) / n) * 100, r200: (cnt(200) / n) * 100,
      r500: (cnt(500) / n) * 100, r1000: (cnt(1000) / n) * 100,
      medPct: (ranks[Math.floor(n / 2)] / N) * 100,
      inRadius: inRadius.length, effectiveRadiusM: candSet.length ? dist[candSet[candSet.length - 1]] : 0,
      reachPct: (reach / n) * 100, shownPct: (shown / n) * 100,
    });
  }
  results[0].per = per;
}

// ── 보고 ────────────────────────────────────────────────────────────────────
console.log(`\n${"═".repeat(78)}`);
console.log(`① 전체 줄 세우기 — 후보 ${N}곳을 점수순으로. 출발지 ${N_ORIGINS}곳의 평균`);
console.log(`${"═".repeat(78)}`);
console.log("시나리오\tR@50\tR@100\tR@200\tR@500\tR@1000\t중앙백분위");
for (const r of results) {
  const g = (k) => summarize(r.per, k).mean.toFixed(1);
  console.log(`${r.k}\t${g("r50")}%\t${g("r100")}%\t${g("r200")}%\t${g("r500")}%\t${g("r1000")}%\t${g("medPct")}%`);
}
console.log(`\n무작위 기대값: R@200 = 200/${N} = ${((200 / N) * 100).toFixed(2)}% · 중앙 백분위 50.0%`);

console.log(`\n${"═".repeat(78)}`);
console.log(`② 🔴 실제 배포 흐름 — 반경 ${DEFAULT_RADIUS_M}m 안에서 **거리순 ${DEFAULT_CANDIDATE_LIMIT}곳만** 채점한다`);
console.log(`${"═".repeat(78)}`);
const base = results[1].per; // asis — 후보 집합은 시나리오와 무관하게 같다
const ir = summarize(base, "inRadius");
const er = summarize(base, "effectiveRadiusM");
console.log(`반경 5km 안 후보 수:      평균 ${ir.mean.toFixed(0)}곳 (10% 지점 ${ir.p10} · 중앙 ${ir.med} · 90% 지점 ${ir.p90})`);
console.log(`🔴 실효 반경(200번째 후보까지의 거리): 평균 ${er.mean.toFixed(0)}m (10% ${er.p10.toFixed(0)}m · 중앙 ${er.med.toFixed(0)}m · 90% ${er.p90.toFixed(0)}m)`);
console.log(`   → 반경을 5km 로 잡아 놓고 실제로는 이만큼만 본다. 상한 200 이 반경보다 먼저 걸린다\n`);
console.log("시나리오\t후보 200 안에 든 정답\t사용자가 보는 상위 20 안에 든 정답");
for (const r of results) {
  const reach = summarize(r.per, "reachPct").mean;
  const shown = summarize(r.per, "shownPct").mean;
  console.log(`${r.k}\t${reach.toFixed(2)}%\t${shown.toFixed(2)}%`);
}
console.log(`\n🔴 "후보 200 안에 든 정답" 은 **채점기와 무관하다** — 거리순으로만 자르기 때문이다.`);
console.log(`   시나리오가 달라도 값이 같으면, 그것이 바로 채점기가 그 지표를 못 건드린다는 증거다.`);

fs.writeFileSync(path.join(HERE, "data", "r4-tune-score.json"), JSON.stringify({
  ranAt: new Date().toISOString(),
  role: "조정용 예행 — 판정용은 열지 않았다",
  poolSize: N, nOrigins: N_ORIGINS, originSeed: ORIGIN_SEED,
  radiusM: DEFAULT_RADIUS_M, candidateLimit: DEFAULT_CANDIDATE_LIMIT, topKShown: TOP_K_SHOWN,
  truthCount: tuneIdx.length,
  scenarios: results.map((r) => ({
    k: r.k, label: r.label,
    r200: summarize(r.per, "r200"), medPct: summarize(r.per, "medPct"),
    reachPct: summarize(r.per, "reachPct"), shownPct: summarize(r.per, "shownPct"),
  })),
  inRadius: ir, effectiveRadiusM: er,
}, null, 1));
console.log("\n→ data/r4-tune-score.json");
