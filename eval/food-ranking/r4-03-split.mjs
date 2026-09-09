/**
 * 라운드4 3단계 — 새 정답 집합을 조정용(tune) / 판정용(holdout) 으로 **새로** 가른다.
 *
 * 🔴 라운드3 의 분할(씨앗 7053026, 맛 정답 157곳)을 그대로 쓰면 안 된다. 후보 풀이
 *    넓어져 붙은 정답이 158곳으로 바뀌었기 때문이다. 씨앗도 새로 쓴다.
 *
 * 🔴 **이번 라운드는 맞추는 것이 없다.** 재는 대상이 배포되는 채점기
 *    (`BaselineCandidateScorer`) 인데, 그건 가중치가 설정값으로 **고정**돼 있고
 *    정답을 보고 배우는 부분이 하나도 없다. 그래도 조정용/판정용을 가르는 이유는
 *    ① 앞 라운드들과 같은 눈금으로 견주기 위해서 ② 앞으로 이 채점기의 가중치를
 *    만지게 될 때 쓸 자리를 지금 만들어 두기 위해서다.
 *    맞추는 것이 없으므로 **판정용 성적과 전체 158곳 성적을 둘 다 적는다** —
 *    둘이 크게 다르면 그건 표본이 작다는 뜻이지 과적합이 아니다.
 *
 * 씨앗: **7054026** — 라운드3 의 7053026 에서 라운드 번호 자리만 3→4 로 바꿨다.
 * 비율: 조정용 70% / 판정용 30% — 라운드1·2 와 같다. 바꾸면 비교가 안 된다.
 *
 * 🔴 무엇을 정답으로 삼는가 — **맛 정답지 셋만** (미쉐린·블루리본·블로그100).
 *    백년가게는 맛이 아니라 **존속기간**을 재는 표라 여기 안 넣는다. 대신
 *    r3-05-diag.mjs 에서 **대조군**으로 쓴다 (TourAPI 와 같은 자리다).
 *    근거는 docs/FOOD-RANKING-EVAL.md 15.2 에 적었다.
 *
 * 산출: data/r4-split-tune.json · data/r4-split-holdout.json
 * 🔴 판정용은 마지막에 딱 한 번 연다. 열람은 data/r4-holdout-access.log 에 쌓인다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SEED = 7054026;
const HOLDOUT_RATIO = 0.3;

const matched = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r4-matched.json"), "utf8"));
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

const meta = { round: 4, seed: SEED, holdoutRatio: HOLDOUT_RATIO, total: items.length, basis: "맛 정답지 셋 (미쉐린·블루리본·블로그100). 백년가게 제외. 후보 풀 = 상가정보 ∪ OSM" };
fs.writeFileSync(path.join(HERE, "data", "r4-split-tune.json"),
  JSON.stringify({ ...meta, role: "조정용", count: tune.length, items: tune }, null, 1));
fs.writeFileSync(path.join(HERE, "data", "r4-split-holdout.json"),
  JSON.stringify({ ...meta, role: "판정용", count: holdout.length, items: holdout }, null, 1));
fs.writeFileSync(path.join(HERE, "data", "r4-control-baengnyeon.json"),
  JSON.stringify({ role: "대조군 — 백년가게만 (맛 정답지에 없는 것)", count: baeng.length, items: baeng }, null, 1));

const accessLog = path.join(HERE, "data", "r4-holdout-access.log");
if (!fs.existsSync(accessLog)) fs.writeFileSync(accessLog, "");

const cnt = (arr, k) => arr.filter((x) => x.nTaste === k).length;
console.log(`씨앗 ${SEED} · 맛 정답 ${items.length}곳 → 조정용 ${tune.length} · 판정용 ${holdout.length}`);
console.log(`  조정용 겹침: 1출처 ${cnt(tune, 1)} · 2출처 ${cnt(tune, 2)} · 3출처 ${cnt(tune, 3)}`);
console.log(`  판정용 겹침: 1출처 ${cnt(holdout, 1)} · 2출처 ${cnt(holdout, 2)} · 3출처 ${cnt(holdout, 3)}`);
console.log(`대조군(백년가게 전용) ${baeng.length}곳 — 학습·판정 어디에도 안 쓴다`);
