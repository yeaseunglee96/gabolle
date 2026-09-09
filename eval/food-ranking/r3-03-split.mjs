/**
 * 라운드3 3단계 — 새 정답 집합을 조정용(tune) / 판정용(holdout) 으로 **새로** 가른다.
 *
 * 🔴 라운드2 의 분할(씨앗 7052026, TourAPI 정답 285곳)을 쓰면 안 된다. 정답표가
 *    통째로 바뀌었기 때문이다. 씨앗도 새로 쓴다.
 *
 * 씨앗: **7053026** — 라운드2 의 7052026 에서 라운드 번호 자리만 2→3 으로 바꿨다.
 * 비율: 조정용 70% / 판정용 30% — 라운드1·2 와 같다. 바꾸면 비교가 안 된다.
 *
 * 🔴 무엇을 정답으로 삼는가 — **맛 정답지 셋만** (미쉐린·블루리본·블로그100).
 *    백년가게는 맛이 아니라 **존속기간**을 재는 표라 여기 안 넣는다. 대신
 *    r3-05-diag.mjs 에서 **대조군**으로 쓴다 (TourAPI 와 같은 자리다).
 *    근거는 docs/FOOD-RANKING-EVAL.md 15.2 에 적었다.
 *
 * 산출: data/r3-split-tune.json · data/r3-split-holdout.json
 * 🔴 판정용은 마지막에 딱 한 번 연다. 열람은 data/r3-holdout-access.log 에 쌓인다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SEED = 7053026;
const HOLDOUT_RATIO = 0.3;

const matched = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-matched.json"), "utf8"));
const taste = matched.items.filter((m) => m.isTaste);
const baeng = matched.items.filter((m) => !m.isTaste && m.isBaengnyeon);

function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const items = [...taste].sort((a, b) => a.tid.localeCompare(b.tid));
const rnd = mulberry32(SEED);
for (let i = items.length - 1; i > 0; i--) {
  const j = Math.floor(rnd() * (i + 1));
  [items[i], items[j]] = [items[j], items[i]];
}

const nHold = Math.round(items.length * HOLDOUT_RATIO);
const holdout = items.slice(0, nHold);
const tune = items.slice(nHold);

const meta = { round: 3, seed: SEED, holdoutRatio: HOLDOUT_RATIO, total: items.length, basis: "맛 정답지 셋 (미쉐린·블루리본·블로그100). 백년가게 제외" };
fs.writeFileSync(path.join(HERE, "data", "r3-split-tune.json"),
  JSON.stringify({ ...meta, role: "조정용", count: tune.length, items: tune }, null, 1));
fs.writeFileSync(path.join(HERE, "data", "r3-split-holdout.json"),
  JSON.stringify({ ...meta, role: "판정용", count: holdout.length, items: holdout }, null, 1));
fs.writeFileSync(path.join(HERE, "data", "r3-control-baengnyeon.json"),
  JSON.stringify({ role: "대조군 — 백년가게만 (맛 정답지에 없는 것)", count: baeng.length, items: baeng }, null, 1));

const accessLog = path.join(HERE, "data", "r3-holdout-access.log");
if (!fs.existsSync(accessLog)) fs.writeFileSync(accessLog, "");

const cnt = (arr, k) => arr.filter((x) => x.nTaste === k).length;
console.log(`씨앗 ${SEED} · 맛 정답 ${items.length}곳 → 조정용 ${tune.length} · 판정용 ${holdout.length}`);
console.log(`  조정용 겹침: 1출처 ${cnt(tune, 1)} · 2출처 ${cnt(tune, 2)} · 3출처 ${cnt(tune, 3)}`);
console.log(`  판정용 겹침: 1출처 ${cnt(holdout, 1)} · 2출처 ${cnt(holdout, 2)} · 3출처 ${cnt(holdout, 3)}`);
console.log(`대조군(백년가게 전용) ${baeng.length}곳 — 학습·판정 어디에도 안 쓴다`);
