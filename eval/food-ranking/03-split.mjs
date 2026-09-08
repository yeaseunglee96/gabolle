/**
 * 3단계 — 정답표를 조정용 70% / 판정용 30% 로 가른다.
 *
 * 왜 가르나: **과적합**(overfitting — *정답을 외워서 맞히는 것*) 을 막기 위해서다.
 * 같은 정답표를 보면서 로직을 고치면 점수는 반드시 오르지만, 그 오름은 실력이
 * 아니라 외운 것이다. 그래서 **한 번도 안 본 몫**을 따로 떼어 둔다.
 *
 * 가르는 방식: 고정 씨앗(seed — *같은 씨앗이면 언제 돌려도 같은 순서가 나오게 하는 수*)
 * 20260907 로 결정론적 셔플. contentid 로 정렬한 뒤 섞으므로 파일 순서가 바뀌어도 결과가 같다.
 *
 * 산출: data/split-tune.json (조정용) · data/split-holdout.json (판정용)
 * 🔴 split-holdout.json 은 마지막 판정 때 딱 한 번만 연다. 열 때마다
 *    data/holdout-access.log 에 줄이 하나 늘어난다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SEED = 20260907;
const HOLDOUT_RATIO = 0.3;

const matched = JSON.parse(
  fs.readFileSync(path.join(HERE, "data", "matched.json"), "utf8"),
);

/** mulberry32 — 씨앗 하나로 같은 난수열을 다시 만들어 내는 아주 작은 난수기 */
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

const meta = { seed: SEED, holdoutRatio: HOLDOUT_RATIO, total: items.length };
fs.writeFileSync(
  path.join(HERE, "data", "split-tune.json"),
  JSON.stringify({ ...meta, role: "조정용", count: tune.length, items: tune }, null, 1),
);
fs.writeFileSync(
  path.join(HERE, "data", "split-holdout.json"),
  JSON.stringify({ ...meta, role: "판정용", count: holdout.length, items: holdout }, null, 1),
);
const accessLog = path.join(HERE, "data", "holdout-access.log");
if (!fs.existsSync(accessLog)) fs.writeFileSync(accessLog, "");

console.log(`씨앗 ${SEED} · 정답표 ${items.length}곳 → 조정용 ${tune.length} · 판정용 ${holdout.length}`);
console.log("🔴 판정용은 마지막에 딱 한 번 연다. 열람 횟수는 data/holdout-access.log 에 쌓인다.");
