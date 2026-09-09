/**
 * 7단계 — 🔴 판정. 판정용 정답표를 **딱 한 번** 연다.
 *
 * 이 파일 하나가 판정용 파일을 한 번 읽고, 그 한 번으로 필요한 것을 전부 계산한다.
 * (기준선 v0 · 최종 후보 v13 · 무작위 기대값). 변형마다 따로 돌리면 열람이 늘어난다.
 *
 * 열람 기록은 data/holdout-access.log 에 남는다. 이 파일을 다시 돌리면 줄이 하나 는다.
 *
 * 0단계에서 못 박은 성공 기준:
 *   주: Recall@200 ≥ 20%      (무작위 6.6%)
 *   부: 순위 백분위 중앙값 ≤ 33%  (무작위 50%)
 *   둘 다 넘어야 성공.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VARIANTS } from "./lib/variants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FINAL = "v13";
const BASELINE = "v0";
const CRITERION = { r200: 20, medPct: 33 };

const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "features.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;

// ── 🔴 판정용 열람 ────────────────────────────────────────────────────────────
const log = path.join(HERE, "data", "holdout-access.log");
fs.appendFileSync(
  log,
  `${new Date().toISOString()}\t07-final-judgement.mjs\t변형=${BASELINE},${FINAL}\n`,
);
const opened = fs.readFileSync(log, "utf8").trim().split("\n").filter(Boolean).length;
const hold = JSON.parse(fs.readFileSync(path.join(HERE, "data", "split-holdout.json"), "utf8"));
const posIds = new Set(hold.items.map((i) => i.poiId));
console.log(`🔴 판정용 열람 ${opened}번째. 이 실행 하나로 ${BASELINE} 와 ${FINAL} 을 함께 잰다.\n`);

function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}

function measure(key) {
  const fn = VARIANTS[key].fn;
  const ranked = rows
    .map((r) => ({ id: r.id, s: fn(r), tb: hash01(r.id) }))
    .sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (posIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  return {
    key,
    n,
    ranks,
    r50: (ranks.filter((x) => x <= 50).length / n) * 100,
    r100: (ranks.filter((x) => x <= 100).length / n) * 100,
    r200: (ranks.filter((x) => x <= 200).length / n) * 100,
    r500: (ranks.filter((x) => x <= 500).length / n) * 100,
    medPct: (ranks[Math.floor(n / 2)] / N) * 100,
  };
}

const results = [BASELINE, FINAL].map(measure);
console.log(`후보 ${N}곳 · 판정용 정답 ${results[0].n}곳 (씨앗 ${hold.seed})\n`);
console.log("변형\tR@50\tR@100\tR@200\tR@500\t중앙 백분위");
console.log(`무작위\t${((50 / N) * 100).toFixed(1)}%\t${((100 / N) * 100).toFixed(1)}%\t${((200 / N) * 100).toFixed(1)}%\t${((500 / N) * 100).toFixed(1)}%\t50.0%`);
for (const r of results) {
  console.log(
    `${r.key}\t${r.r50.toFixed(1)}%\t${r.r100.toFixed(1)}%\t${r.r200.toFixed(1)}%\t${r.r500.toFixed(1)}%\t${r.medPct.toFixed(1)}%`,
  );
}

const f = results.find((r) => r.key === FINAL);
console.log(`\n${FINAL} 정답들의 실제 순위: ${f.ranks.join(", ")} (전체 ${N} 중)`);
const passR = f.r200 >= CRITERION.r200;
const passM = f.medPct <= CRITERION.medPct;
console.log(`\n성공 기준 대조 (0단계에서 못 박은 것, 바꾸지 않았다)`);
console.log(`  주 Recall@200 ≥ ${CRITERION.r200}%  →  ${f.r200.toFixed(1)}%  ${passR ? "통과" : "🔴 못 넘었다"}`);
console.log(`  부 중앙 백분위 ≤ ${CRITERION.medPct}%  →  ${f.medPct.toFixed(1)}%  ${passM ? "통과" : "🔴 못 넘었다"}`);
console.log(`\n판정: ${passR && passM ? "성공" : "🔴 실패"}`);
console.log(`🔴 판정용은 ${results[0].n}곳뿐이다. 이 숫자 하나로 결론을 굳히지 않는다.`);
