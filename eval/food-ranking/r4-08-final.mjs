/**
 * 라운드4 8단계 — 🔴 **판정용을 연다. 딱 한 번.**
 *
 * 열기 전에 `data/r4-preregistration.md` 에 채점 대상·예상 성적·기준을 적어 두었다.
 * 이 파일을 돌리면 `data/r4-holdout-access.log` 에 한 줄이 쌓인다.
 * **판정용을 본 뒤에는 채점기를 고치지 않는다.** 고칠 것이 생기면 문서에 적기만 한다.
 *
 *   node --max-old-space-size=8192 r4-08-final.mjs --dry   # 예행. 조정용. 기록 안 남김
 *   node --max-old-space-size=8192 r4-08-final.mjs         # 🔴 진짜. 판정용을 연다
 *
 * 성공 기준 (라운드1 에서 못 박았고 이후 바꾸지 않았다):
 *   Recall@200 ≥ 20%  **그리고**  순위 백분위 중앙값 ≤ 33%
 *   🔴 ① 전체 줄 세우기에 적용한다 (선등록 6절)
 *
 * 🔴 재는 대상: **`BaselineCandidateScorer` — 실제로 서버에 배포되는 채점기.**
 *    라운드1~3 은 전부 참고 구현(`ref/local-route/recommend.ts`)을 잰 것이었다.
 *    이번이 배포될 코드의 **첫 채점**이다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { scoreCandidate, DEFAULT_RADIUS_M, DEFAULT_CANDIDATE_LIMIT } from "./lib/baseline-scorer.mjs";
import { emptyFeatures, derivedFeatures } from "./lib/place-features.mjs";
import { haversine } from "./lib/pool.mjs";

const DRY = process.argv.includes("--dry");
const HERE = path.dirname(fileURLToPath(import.meta.url));
const D = (f) => JSON.parse(fs.readFileSync(path.join(HERE, "data", f), "utf8"));

const ORIGIN_SEED = 7054026;
const N_ORIGINS = 100;
const TOP_K_SHOWN = 20; // 앱이 실제로 요청하는 값 (서버 기본은 10)
const CRITERIA = { r200: 20, medPct: 33 };

const pool = D("r4-pool.json");
const rows = pool.rows;
const N = rows.length;
const tune = D("r4-split-tune.json");
console.log(`후보 ${N}곳 (상가정보 ${pool.sbizCount} + OSM 단독 ${pool.osmOnlyCandidates})`);

let evalSet;
if (DRY) {
  console.log("── 예행(--dry): **조정용**으로 돌린다. 판정용은 열지 않고 기록도 안 남긴다 ──\n");
  evalSet = tune;
} else {
  const logPath = path.join(HERE, "data", "r4-holdout-access.log");
  const prev = fs.existsSync(logPath)
    ? fs.readFileSync(logPath, "utf8").split("\n").filter((l) => l.trim()).length
    : 0;
  console.log(`🔴 판정용을 연다. 이번이 ${prev + 1}번째 열람이다.\n`);
  evalSet = D("r4-split-holdout.json");
  fs.appendFileSync(
    logPath,
    `${new Date().toISOString()}\tr4-08-final.mjs\t판정용 ${evalSet.count}곳\t배포 채점기 BaselineCandidateScorer\n`,
  );
}
const holdoutItems = D("r4-split-holdout.json").items;
const allItems = [...tune.items, ...holdoutItems];

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

const rndO = mulberry32(ORIGIN_SEED);
const origins = [];
for (let k = 0; k < N_ORIGINS; k++) {
  const r = rows[Math.floor(rndO() * N)];
  origins.push({ lat: r.lat, lon: r.lon });
}

const TB = new Float64Array(N);
const LAT = new Float64Array(N), LON = new Float64Array(N);
for (let i = 0; i < N; i++) { TB[i] = hash01(rows[i].id); LAT[i] = rows[i].lat; LON[i] = rows[i].lon; }
const FEAT_EMPTY = rows.map(() => emptyFeatures());
const FEAT_DERIVED = rows.map((r) => derivedFeatures(r));

const truthIdx = new Map(rows.map((r, i) => [r.id, i]));
function markSet(items) {
  const mask = new Uint8Array(N);
  let n = 0;
  for (const t of items) {
    const i = truthIdx.get(t.storeId);
    if (i != null && !mask[i]) { mask[i] = 1; n++; }
  }
  return { mask, n };
}
const MAIN = markSet(evalSet.items);
const ALL = markSet(allItems);
console.log(`${evalSet.role} 정답 ${MAIN.n}곳 · 전체 정답 ${ALL.n}곳을 후보에서 찾았다\n`);

/** 한 출발지·한 시나리오. `byScore=false` 면 점수를 무시하고 해시로만 줄 세운다(무작위) */
function runOne(origin, feats, user, mask, byScore) {
  const dist = new Float64Array(N);
  for (let i = 0; i < N; i++) dist[i] = haversine(origin.lat, origin.lon, LAT[i], LON[i]);
  const score = new Float64Array(N);
  if (byScore) {
    for (let i = 0; i < N; i++) {
      score[i] = scoreCandidate({ distanceM: dist[i], features: feats[i] }, user).total;
    }
  }

  // ① 전체 줄 세우기
  const arr = Array.from({ length: N }, (_, i) => i);
  arr.sort((a, b) => score[b] - score[a] || TB[a] - TB[b]);
  const ranks = [];
  for (let p = 0; p < N; p++) if (mask[arr[p]]) ranks.push(p + 1);
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const cnt = (k) => { let c = 0; for (const r of ranks) if (r <= k) c++; return c; };

  // ② 실제 배포 흐름 — 반경 5km → 거리순 200곳 → 채점 → 상위 20곳
  const inRadius = [];
  for (let i = 0; i < N; i++) if (dist[i] <= DEFAULT_RADIUS_M) inRadius.push(i);
  inRadius.sort((a, b) => dist[a] - dist[b] || TB[a] - TB[b]);
  const candSet = inRadius.slice(0, DEFAULT_CANDIDATE_LIMIT);
  const effectiveRadiusM = candSet.length ? dist[candSet[candSet.length - 1]] : 0;
  let reach = 0;
  for (const i of candSet) if (mask[i]) reach++;
  const scored = [...candSet].sort((a, b) => score[b] - score[a] || TB[a] - TB[b]);
  let shown = 0;
  for (let p = 0; p < Math.min(TOP_K_SHOWN, scored.length); p++) if (mask[scored[p]]) shown++;

  return {
    n, c200: cnt(200),
    r50: (cnt(50) / n) * 100, r100: (cnt(100) / n) * 100, r200: (cnt(200) / n) * 100,
    r500: (cnt(500) / n) * 100, r1000: (cnt(1000) / n) * 100,
    medPct: (ranks[Math.floor(n / 2)] / N) * 100,
    inRadius: inRadius.length, effectiveRadiusM,
    reachPct: (reach / n) * 100, shownPct: (shown / n) * 100,
  };
}

function summarize(list, key) {
  const v = list.map((x) => x[key]).sort((a, b) => a - b);
  const mean = v.reduce((a, b) => a + b, 0) / v.length;
  return { mean, p10: v[Math.floor(v.length * 0.1)], med: v[Math.floor(v.length / 2)], p90: v[Math.floor(v.length * 0.9)] };
}

const SCENARIOS = [
  { k: "무작위", label: "기준선 — 아무 순서나", feats: FEAT_EMPTY, user: { codes: {}, scores: {} }, byScore: false },
  { k: "asis", label: "🔴 지금 배포하면 이렇게 된다 — place_feature 가 비어 있다", feats: FEAT_EMPTY, user: { codes: {}, scores: {} }, byScore: true },
  { k: "cuisine-한식", label: "피처를 채웠다면 — 음식 태그, 사용자가 '한식' 을 고른 경우", feats: FEAT_DERIVED, user: { codes: { CATEGORY: ["FOOD"], FOOD_PREFERENCE: ["한식"] }, scores: {} }, byScore: true },
  { k: "cuisine-일식", label: "피처를 채웠다면 — 음식 태그, 사용자가 '일식' 을 고른 경우", feats: FEAT_DERIVED, user: { codes: { CATEGORY: ["FOOD"], FOOD_PREFERENCE: ["일식"] }, scores: {} }, byScore: true },
];

function runAll(mask) {
  return SCENARIOS.map((s) => {
    const per = origins.map((o) => runOne(o, s.feats, s.user, mask, s.byScore));
    process.stdout.write(`  ${s.k} 완료\n`);
    return { k: s.k, label: s.label, per };
  });
}

console.log(`── ${evalSet.role} ${MAIN.n}곳으로 채점 ──`);
const main = runAll(MAIN.mask);
console.log(`\n── 참고: 전체 정답 ${ALL.n}곳으로도 채점 (맞추는 것이 없어 과적합이 없다) ──`);
const allRes = runAll(ALL.mask);

function table(res, title) {
  console.log(`\n${"═".repeat(80)}`);
  console.log(`① 전체 줄 세우기 — 후보 ${N}곳. 출발지 ${N_ORIGINS}곳의 평균 · ${title}`);
  console.log(`${"═".repeat(80)}`);
  console.log("시나리오\tR@50\tR@100\t**R@200**\tR@500\tR@1000\t**중앙백분위**");
  for (const r of res) {
    const g = (k) => summarize(r.per, k).mean.toFixed(1);
    console.log(`${r.k}\t${g("r50")}%\t${g("r100")}%\t${g("r200")}%\t${g("r500")}%\t${g("r1000")}%\t${g("medPct")}%`);
  }
  console.log(`무작위 기대값: R@200 = 200/${N} = ${((200 / N) * 100).toFixed(2)}% · 중앙 백분위 50.0%`);
}
table(main, `${evalSet.role} ${MAIN.n}곳`);
table(allRes, `전체 ${ALL.n}곳`);

// ── ② 실제 배포 흐름 ────────────────────────────────────────────────────────
console.log(`\n${"═".repeat(80)}`);
console.log(`② 🔴 실제 배포 흐름 — 반경 ${DEFAULT_RADIUS_M}m 안에서 **거리순 ${DEFAULT_CANDIDATE_LIMIT}곳만** 채점한다`);
console.log(`${"═".repeat(80)}`);
const base = main[1].per;
const ir = summarize(base, "inRadius");
const er = summarize(base, "effectiveRadiusM");
console.log(`반경 5km 안 후보 수: 평균 ${ir.mean.toFixed(0)}곳 (10% ${ir.p10} · 중앙 ${ir.med} · 90% ${ir.p90})`);
console.log(`🔴 실효 반경(200번째까지의 거리): 평균 ${er.mean.toFixed(0)}m (10% ${er.p10.toFixed(0)}m · 중앙 ${er.med.toFixed(0)}m · 90% ${er.p90.toFixed(0)}m)`);
console.log(`   → 반경을 5km 로 잡아 놓고 실제로는 이만큼만 본다. 상한 200 이 반경보다 먼저 걸린다\n`);
console.log("시나리오\t후보 200 안에 든 정답\t사용자가 보는 상위 20 안에 든 정답");
for (const r of main) {
  console.log(`${r.k}\t${summarize(r.per, "reachPct").mean.toFixed(2)}%\t${summarize(r.per, "shownPct").mean.toFixed(2)}%`);
}
console.log(`\n🔴 "후보 200 안에 든 정답" 이 시나리오마다 같으면, 그것이 곧 **채점기가 이 지표를`);
console.log(`   못 건드린다**는 증거다 — 자르는 기준이 점수가 아니라 거리이기 때문이다.`);

// ── 판정 ────────────────────────────────────────────────────────────────────
const fin = main.find((r) => r.k === "asis");
const r200 = summarize(fin.per, "r200").mean;
const medPct = summarize(fin.per, "medPct").mean;
const passMain = r200 >= CRITERIA.r200;
const passSub = medPct <= CRITERIA.medPct;
console.log(`\n${"═".repeat(80)}`);
console.log(`═══ 판정 — 채점 대상은 \`asis\`(실제로 배포되는 상태) 다 ═══`);
console.log(`주  기준: Recall@200 ≥ ${CRITERIA.r200}%  →  실측 ${r200.toFixed(1)}%  ${passMain ? "통과" : "🔴 못 넘었다"}`);
console.log(`부  기준: 중앙 백분위 ≤ ${CRITERIA.medPct}% →  실측 ${medPct.toFixed(1)}%  ${passSub ? "통과" : "🔴 못 넘었다"}`);
console.log(`둘 다 넘어야 성공 → ${passMain && passSub ? "🟢 성공" : "🔴 실패"}`);
console.log(`(기준은 라운드1 에서 못 박은 값 그대로다. 결과를 보고 낮추지 않았다)`);

// 우연일 확률 — 출발지 100곳을 합쳐 이항검정
{
  const hits = fin.per.reduce((a, x) => a + x.c200, 0);
  const trials = fin.per.reduce((a, x) => a + x.n, 0);
  const p = 200 / N;
  let logFact = [0];
  for (let i = 1; i <= trials; i++) logFact[i] = logFact[i - 1] + Math.log(i);
  let tail = 0;
  for (let x = hits; x <= trials; x++) {
    tail += Math.exp(logFact[trials] - logFact[x] - logFact[trials - x] + x * Math.log(p) + (trials - x) * Math.log(1 - p));
  }
  console.log(`\n── 우연일 확률 (asis · 출발지 100곳 합산) ──`);
  console.log(`상위 200 안 적중 ${hits} / ${trials}회 · 무작위 기대 ${(p * trials).toFixed(1)}회 · 우연일 확률 ${tail.toExponential(1)}`);
}

if (!DRY) {
  fs.writeFileSync(path.join(HERE, "data", "r4-final.json"), JSON.stringify({
    ranAt: new Date().toISOString(),
    scorer: "BaselineCandidateScorer (backend/.../recommendation/adapter) 를 lib/baseline-scorer.mjs 로 옮긴 것",
    poolSize: N, poolSbiz: pool.sbizCount, poolOsmOnly: pool.osmOnlyCandidates,
    holdoutCount: MAIN.n, allTruthCount: ALL.n, seed: tune.seed,
    nOrigins: N_ORIGINS, originSeed: ORIGIN_SEED,
    radiusM: DEFAULT_RADIUS_M, candidateLimit: DEFAULT_CANDIDATE_LIMIT, topKShown: TOP_K_SHOWN,
    criteria: CRITERIA, judged: "asis", pass: passMain && passSub,
    holdout: main.map((r) => ({
      k: r.k, label: r.label,
      r200: summarize(r.per, "r200"), medPct: summarize(r.per, "medPct"),
      reachPct: summarize(r.per, "reachPct"), shownPct: summarize(r.per, "shownPct"),
    })),
    allTruth: allRes.map((r) => ({
      k: r.k, r200: summarize(r.per, "r200"), medPct: summarize(r.per, "medPct"),
    })),
    inRadius: ir, effectiveRadiusM: er,
  }, null, 1));
  console.log("\n→ data/r4-final.json");
}
