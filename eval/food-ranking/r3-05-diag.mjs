/**
 * 라운드3 5단계 — 🔴 **이번 라운드의 핵심 실험.**
 *
 * 가설: *"TourAPI 정답과 미쉐린/블루리본 정답은 서로 다른 것을 가리킨다."*
 *
 * 라운드2 는 정답 매칭을 37 → 285건으로 늘리고도 판정용 Recall@200 이 9.3% 에
 * 그쳤다. 그리고 그 실패보다 중요한 것을 발견했다 — 힘 있는 신호가 전부
 * *"번화가에서 멀고 · 식당이 드물고 · 지하철이 없고 · 독립 건물"* 을 가리켰다.
 * 정답표가 한국관광공사 TourAPI 관광 안내 목록이라, 모델이 배운 것은 "맛" 이
 * 아니라 **"관광 안내에 실릴 자리"** 였다는 뜻이다.
 *
 * 여기서 재는 것 셋:
 *   ① 두 정답 집합이 **얼마나 겹치는가** (같은 가게를 가리키는가)
 *   ② 라운드2 의 최강 신호들이 **새 정답에서도 같은 방향인가** (뒤집히면 가설이 맞다)
 *   ③ 업종 분포가 얼마나 다른가
 *
 * 🔴 **판정용은 열지 않는다.** 새 정답은 조정용 115곳만 쓴다.
 *    TourAPI 285곳과 백년가게 26곳은 라운드3 의 학습·판정 어디에도 안 쓰는
 *    **대조군**이라 전량을 쓴다. 이 비대칭은 의도한 것이고 여기 적어 둔다.
 *
 * 산출: 표준출력 (docs/FOOD-RANKING-EVAL.md 로 옮겨 적는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const D = (f) => JSON.parse(fs.readFileSync(path.join(HERE, "data", f), "utf8"));

const feat = D("r3-features.json");
const rows = feat.rows;
const N = rows.length;

const r2 = D("r2-matched.json"); // TourAPI 285건 — 대조군
const tune = D("r3-split-tune.json"); // 새 정답 조정용 115곳
const baeng = D("r3-control-baengnyeon.json"); // 백년가게 전용 26곳 — 대조군
const r3all = D("r3-matched.json");

const setTour = new Set(r2.items.map((i) => i.storeId));
const setTaste = new Set(r3all.items.filter((i) => i.isTaste).map((i) => i.storeId));
const setTuneOnly = new Set(tune.items.map((i) => i.storeId));
const setBaeng = new Set(baeng.items.map((i) => i.storeId));

// ── ① 겹침 ──────────────────────────────────────────────────────────────────
console.log("═══ ① TourAPI 정답과 새 정답(맛)이 같은 가게를 가리키는가 ═══\n");
const inter = [...setTaste].filter((id) => setTour.has(id));
const jac = inter.length / (setTour.size + setTaste.size - inter.length);
console.log(`TourAPI(관광 안내) 정답  ${setTour.size}곳`);
console.log(`새 정답(맛 셋)          ${setTaste.size}곳`);
console.log(`🔴 겹치는 가게            ${inter.length}곳`);
console.log(`   TourAPI 중 새 정답이기도 한 비율  ${((inter.length / setTour.size) * 100).toFixed(1)}%`);
console.log(`   새 정답 중 TourAPI 이기도 한 비율 ${((inter.length / setTaste.size) * 100).toFixed(1)}%`);
console.log(`   자카드 지수(교집합÷합집합. 1이면 같은 표, 0이면 남남) ${jac.toFixed(3)}`);
const byId = new Map(rows.map((r) => [r.id, r]));
console.log(`   겹치는 가게: ${inter.slice(0, 20).map((id) => byId.get(id)?.name).join(" · ")}`);
const interB = [...setBaeng].filter((id) => setTour.has(id));
console.log(`\n백년가게 전용 ${setBaeng.size}곳 중 TourAPI 이기도 한 것 ${interB.length}곳`);

// 우연히 이만큼 겹칠 확률의 눈금 — 두 표가 서로 무관하다면 기대 겹침은?
const expected = (setTour.size * setTaste.size) / N;
console.log(`\n두 표가 서로 무관했다면 기대 겹침 ${expected.toFixed(1)}곳 · 실제 ${inter.length}곳 (${(inter.length / expected).toFixed(1)}배)`);

// ── ② 신호 방향 ─────────────────────────────────────────────────────────────
/** AUC = 정답 하나와 오답 하나를 뽑았을 때 정답의 값이 더 큰 확률. 0.5 = 동전던지기 */
function auc(values, posSet) {
  const arr = rows.map((r, i) => ({ v: values[i], p: posSet.has(r.id) ? 1 : 0 }));
  arr.sort((a, b) => a.v - b.v);
  // 동점은 평균 순위로 (Mann-Whitney U)
  let i = 0;
  let sumRankPos = 0;
  let nPos = 0;
  while (i < arr.length) {
    let j = i;
    while (j + 1 < arr.length && arr[j + 1].v === arr[i].v) j++;
    const avgRank = (i + j) / 2 + 1;
    for (let k = i; k <= j; k++) if (arr[k].p) { sumRankPos += avgRank; nPos++; }
    i = j + 1;
  }
  const nNeg = arr.length - nPos;
  if (!nPos || !nNeg) return NaN;
  return (sumRankPos - (nPos * (nPos + 1)) / 2) / (nPos * nNeg);
}

const B = (b) => (b ? 1 : 0);
const SIGNALS = [
  ["식당 밀집 100m", (r) => r.foodDens100],
  ["식당 밀집 300m", (r) => r.foodDens300],
  ["같은 업종 밀집 300m", (r) => r.soDens300],
  ["예술·스포츠 상가 밀집 500m", (r) => r.leisureDens500],
  ["숙박 상가 밀집 500m", (r) => r.lodgeDens500],
  ["지하철까지 거리", (r) => r.subwayM],
  ["버스정류장까지 거리", (r) => r.transitM],
  ["관광 기준점까지 거리", (r) => r.touristM],
  ["🌊 해안선까지 거리 (라운드3 신규)", (r) => r.coastM],
  ["같은 건물 음식점 수", (r) => r.bldgFoodCount],
  ["같은 법정동 음식점 수", (r) => r.bdongFoodCount],
  ["같은 상호가 부산에 몇 개", (r) => r.nameCount],
  ["1층인가", (r) => B(r.isFloor1)],
  ["층정보 없음(독립 건물로 본다)", (r) => B(r.floorMissing)],
  ["건물명 있음", (r) => B(r.hasBldgName)],
  ["OSM 에 그려져 있음", (r) => B(r.osmListed)],
  ["OSM 태그 촘촘함", (r) => r.osmTagRichness],
  ["OSM 전화번호", (r) => B(r.osmHasPhone)],
];

console.log("\n\n═══ ② 라운드2 의 최강 신호들이 새 정답에서도 같은 방향인가 ═══\n");
console.log("AUC 는 **신호 값이 클수록 정답일 확률**이다. 0.5 = 동전던지기.");
console.log("0.5 를 사이에 두고 갈리면 **방향이 뒤집힌 것**이고, 그러면 두 정답표는");
console.log("서로 다른 것을 가리킨다는 뜻이다.\n");
console.log("신호\tTourAPI(285)\t맛-조정용(115)\t백년가게(26)\t방향");
const table = [];
for (const [label, f] of SIGNALS) {
  const v = rows.map(f);
  const aT = auc(v, setTour);
  const aM = auc(v, setTuneOnly);
  const aB = auc(v, setBaeng);
  const flip = (aT - 0.5) * (aM - 0.5) < 0;
  const mark = flip ? "🔴 뒤집힘" : Math.abs(aM - 0.5) < 0.03 ? "· 무력" : "같음";
  table.push({ label, tour: aT, taste: aM, baeng: aB, flip });
  console.log(`${label}\t${aT.toFixed(3)}\t${aM.toFixed(3)}\t${aB.toFixed(3)}\t${mark}`);
}
const flips = table.filter((t) => t.flip);
console.log(`\n🔴 방향이 뒤집힌 신호 ${flips.length} / ${table.length}개: ${flips.map((t) => t.label).join(" · ")}`);

// 라운드2 가 최강이라고 적어 둔 신호 넷만 따로
console.log("\n── 라운드2 가 '최강' 으로 적어 둔 신호 넷 (문서 10절) ──");
const KEY = ["예술·스포츠 상가 밀집 500m", "식당 밀집 100m", "지하철까지 거리", "같은 건물 음식점 수"];
console.log("신호\t라운드2 가 적은 것\tTourAPI 실측\t맛 정답 실측\t판정");
const R2SAID = {
  "예술·스포츠 상가 밀집 500m": "드물수록 정답 (AUC 0.698)",
  "식당 밀집 100m": "낮을수록 정답 (AUC 0.691)",
  "지하철까지 거리": "멀수록 정답 (AUC 0.669)",
  "같은 건물 음식점 수": "적을수록 정답 (AUC 0.665)",
};
for (const k of KEY) {
  const t = table.find((x) => x.label === k);
  const dirT = t.tour < 0.5 ? "낮을수록 정답" : "높을수록 정답";
  const dirM = t.taste < 0.5 ? "낮을수록 정답" : "높을수록 정답";
  console.log(`${k}\t${R2SAID[k]}\t${t.tour.toFixed(3)} (${dirT})\t${t.taste.toFixed(3)} (${dirM})\t${t.flip ? "🔴 뒤집힘" : "같음"}`);
}

// ── ③ 업종 분포 ─────────────────────────────────────────────────────────────
console.log("\n\n═══ ③ 업종 분포 — 두 정답표가 고르는 업종이 다른가 ═══\n");
function soDist(posSet) {
  const m = new Map();
  for (const r of rows) if (posSet.has(r.id)) m.set(r.so, (m.get(r.so) ?? 0) + 1);
  return m;
}
const baseAll = new Map();
for (const r of rows) baseAll.set(r.so, (baseAll.get(r.so) ?? 0) + 1);
const dT = soDist(setTour);
const dM = soDist(setTuneOnly);
const allSo = [...new Set([...dT.keys(), ...dM.keys()])];
const lift = (d, so, n) => (d.get(so) ?? 0) / n / ((baseAll.get(so) ?? 1) / N);
console.log("업종\t전체후보\tTourAPI건수\t배수\t맛건수\t배수");
const soRows = allSo
  .map((so) => ({ so, t: dT.get(so) ?? 0, m: dM.get(so) ?? 0, lt: lift(dT, so, setTour.size), lm: lift(dM, so, setTuneOnly.size) }))
  .sort((a, b) => b.m + b.t - (a.m + a.t));
for (const s of soRows.slice(0, 18)) {
  console.log(`${s.so}\t${baseAll.get(s.so) ?? 0}\t${s.t}\t×${s.lt.toFixed(2)}\t${s.m}\t×${s.lm.toFixed(2)}`);
}

// ── ④ 구군 분포 ─────────────────────────────────────────────────────────────
console.log("\n═══ ④ 구군 분포 ═══\n");
function guDist(posSet) {
  const m = new Map();
  for (const r of rows) if (posSet.has(r.id)) m.set(r.sigungu, (m.get(r.sigungu) ?? 0) + 1);
  return m;
}
const gAll = new Map();
for (const r of rows) gAll.set(r.sigungu, (gAll.get(r.sigungu) ?? 0) + 1);
const gT = guDist(setTour);
const gM = guDist(setTuneOnly);
console.log("구군\t전체후보\tTourAPI\t배수\t맛\t배수");
for (const gu of [...gAll.keys()].sort((a, b) => (gM.get(b) ?? 0) - (gM.get(a) ?? 0))) {
  const lt = ((gT.get(gu) ?? 0) / setTour.size) / ((gAll.get(gu) ?? 1) / N);
  const lm = ((gM.get(gu) ?? 0) / setTuneOnly.size) / ((gAll.get(gu) ?? 1) / N);
  console.log(`${gu}\t${gAll.get(gu)}\t${gT.get(gu) ?? 0}\t×${lt.toFixed(2)}\t${gM.get(gu) ?? 0}\t×${lm.toFixed(2)}`);
}

fs.writeFileSync(path.join(HERE, "data", "r3-diag.json"), JSON.stringify({
  ranAt: new Date().toISOString(),
  overlap: { tour: setTour.size, taste: setTaste.size, inter: inter.length, jaccard: jac, expected },
  auc: table,
  flips: flips.map((t) => t.label),
}, null, 1));
console.log("\n→ data/r3-diag.json");
