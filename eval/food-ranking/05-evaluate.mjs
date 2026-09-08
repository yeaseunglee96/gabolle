/**
 * 5단계 — 채점.
 *
 *   node 05-evaluate.mjs --variant v0            (조정용. 기본값)
 *   node 05-evaluate.mjs --variant v2 --split holdout   🔴 판정용. 딱 한 번만
 *   node 05-evaluate.mjs --random                무작위 기준선
 *
 * 지표 둘 (0단계에서 못 박은 것):
 *   · Recall@K  — 상위 K 위 안에 정답이 몇 % 들어왔나
 *   · 순위 백분위 중앙값 — 정답들이 평균적으로 위에서 몇 % 지점에 있나
 *
 * 동점 처리: 제품 로직은 후보 대부분에 **같은 점수**를 준다. 파일 순서로 줄을 세우면
 * OSM id 순서(= 먼저 그려진 동네 = 원도심)가 몰래 신호가 된다. 그래서 동점은
 * **id 해시로 섞어** 깨뜨린다 — 재현되면서도 지역 편향이 없다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VARIANTS } from "./lib/variants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const get = (k, d) => {
  const i = args.indexOf(k);
  return i >= 0 ? args[i + 1] : d;
};
const variantKey = get("--variant", "v0");
const split = get("--split", "tune");
const randomMode = args.includes("--random");
const quiet = args.includes("--quiet");

const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "features.json"), "utf8"));
const rows = feat.rows;

// ── 정답 집합 읽기 ────────────────────────────────────────────────────────────
let truthIds;
if (split === "holdout") {
  const log = path.join(HERE, "data", "holdout-access.log");
  fs.appendFileSync(
    log,
    `${new Date().toISOString()}\t변형=${variantKey}\t${randomMode ? "random" : "scored"}\n`,
  );
  const opened = fs.readFileSync(log, "utf8").trim().split("\n").filter(Boolean).length;
  console.error(`🔴 판정용 열람 ${opened}번째 (기록: data/holdout-access.log)`);
  const h = JSON.parse(fs.readFileSync(path.join(HERE, "data", "split-holdout.json"), "utf8"));
  truthIds = new Set(h.items.map((i) => i.poiId));
} else if (split === "all") {
  const m = JSON.parse(fs.readFileSync(path.join(HERE, "data", "matched.json"), "utf8"));
  truthIds = new Set(m.items.map((i) => i.poiId));
} else {
  const t = JSON.parse(fs.readFileSync(path.join(HERE, "data", "split-tune.json"), "utf8"));
  truthIds = new Set(t.items.map((i) => i.poiId));
}

// ── 동점을 깨는 결정론적 해시 ─────────────────────────────────────────────────
function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967296;
}

const N = rows.length;
const KS = [50, 100, 200, 500];

function metrics(ranked) {
  const positives = [];
  ranked.forEach((r, i) => {
    if (truthIds.has(r.id)) positives.push(i + 1);
  });
  positives.sort((a, b) => a - b);
  const nPos = positives.length;
  const recall = {};
  for (const K of KS) recall[K] = positives.filter((p) => p <= K).length;
  const medRank = nPos ? positives[Math.floor(nPos / 2)] : NaN;
  return { nPos, recall, medRank, medPct: (medRank / N) * 100, positives };
}

function rankBy(scoreFn) {
  return rows
    .map((r) => ({ id: r.id, s: scoreFn(r), tb: hash01(r.id) }))
    .sort((a, b) => b.s - a.s || a.tb - b.tb);
}

let m;
let label;
if (randomMode) {
  // 무작위 기준선: 씨앗 100개의 평균. 해석적 기대값과 맞는지도 같이 본다.
  label = "무작위 기준선 (씨앗 100개 평균)";
  const acc = { 50: 0, 100: 0, 200: 0, 500: 0 };
  let medSum = 0;
  const TRIALS = 100;
  for (let t = 0; t < TRIALS; t++) {
    const mm = metrics(rankBy((r) => hash01(`${t}:${r.id}`)));
    for (const K of KS) acc[K] += mm.recall[K];
    medSum += mm.medRank;
  }
  const nPos = metrics(rankBy(() => 0)).nPos;
  m = {
    nPos,
    recall: Object.fromEntries(KS.map((K) => [K, acc[K] / TRIALS])),
    medRank: medSum / TRIALS,
    medPct: (medSum / TRIALS / N) * 100,
  };
} else {
  const v = VARIANTS[variantKey];
  if (!v) throw new Error(`모르는 변형: ${variantKey}. 있는 것: ${Object.keys(VARIANTS).join(", ")}`);
  label = `${variantKey} — ${v.label}`;
  m = metrics(rankBy(v.fn));
}

const pct = (n) => ((n / m.nPos) * 100).toFixed(1);
if (quiet) {
  console.log(
    [
      variantKey + (randomMode ? "(random)" : ""),
      split,
      m.nPos,
      ...KS.map((K) => `${typeof m.recall[K] === "number" ? m.recall[K].toFixed(2) : m.recall[K]}(${pct(m.recall[K])}%)`),
      m.medRank.toFixed(0),
      m.medPct.toFixed(1) + "%",
    ].join("\t"),
  );
} else {
  console.log(`\n${label}`);
  console.log(`후보 ${N}곳 · ${split === "holdout" ? "판정용" : split === "all" ? "전체" : "조정용"} 정답 ${m.nPos}곳`);
  for (const K of KS) {
    const hit = m.recall[K];
    console.log(
      `  Recall@${String(K).padStart(3)} : ${typeof hit === "number" ? hit.toFixed(2) : hit} / ${m.nPos} = ${pct(hit)}%   (무작위 기대 ${((K / N) * 100).toFixed(1)}%)`,
    );
  }
  console.log(`  정답 순위 중앙값 : ${m.medRank.toFixed(0)}위 = 상위 ${m.medPct.toFixed(1)}%   (무작위 기대 50%)`);
}
