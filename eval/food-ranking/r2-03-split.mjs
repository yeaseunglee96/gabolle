/**
 * 라운드2 3단계 — 늘어난 정답 285곳을 조정용 70% / 판정용 30% 로 **새로** 가른다.
 *
 * 🔴 라운드1 의 분할(씨앗 20260907, 정답 37곳)을 그대로 쓰면 안 된다. 후보 풀이
 *    통째로 바뀌어 정답 집합 자체가 달라졌기 때문이다. 그래서 씨앗도 새로 쓴다.
 *
 * 씨앗(seed — 같은 씨앗이면 언제 돌려도 같은 순서가 나오게 하는 수): 7052026
 *   (Jira 키 S15P21E201-**705** + 연도 2026. 라운드1 의 20260907 과 겹치지 않게 골랐다)
 *
 * 산출: data/r2-split-tune.json · data/r2-split-holdout.json
 * 🔴 판정용은 마지막에 딱 한 번 연다. 열람은 data/r2-holdout-access.log 에 쌓인다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SEED = 7052026;
const HOLDOUT_RATIO = 0.3;

const matched = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-matched.json"), "utf8"));

function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const items = [...matched.items].sort((a, b) => a.contentid.localeCompare(b.contentid));
const rnd = mulberry32(SEED);
for (let i = items.length - 1; i > 0; i--) {
  const j = Math.floor(rnd() * (i + 1));
  [items[i], items[j]] = [items[j], items[i]];
}

const nHold = Math.round(items.length * HOLDOUT_RATIO);
const holdout = items.slice(0, nHold);
const tune = items.slice(nHold);

const meta = { round: 2, seed: SEED, holdoutRatio: HOLDOUT_RATIO, total: items.length };
fs.writeFileSync(path.join(HERE, "data", "r2-split-tune.json"),
  JSON.stringify({ ...meta, role: "조정용", count: tune.length, items: tune }, null, 1));
fs.writeFileSync(path.join(HERE, "data", "r2-split-holdout.json"),
  JSON.stringify({ ...meta, role: "판정용", count: holdout.length, items: holdout }, null, 1));
const accessLog = path.join(HERE, "data", "r2-holdout-access.log");
if (!fs.existsSync(accessLog)) fs.writeFileSync(accessLog, "");

console.log(`씨앗 ${SEED} · 정답 ${items.length}곳 → 조정용 ${tune.length} · 판정용 ${holdout.length}`);
