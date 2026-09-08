/**
 * 3단계 — **인기 신호를 못 얻었으니, 얻을 수 있는 것으로 같은 질문을 던진다.**
 *
 *   node bigData/eval/popularity/03-signals.mjs --repo <루트> --truth-repo <루트>
 *
 * 원래 재려던 것은 네이버 플레이스의 평점·방문자 리뷰 수·블로그 리뷰 수였다.
 * 1단계에서 그 길이 닫혔다(`findings/01-source-verdict.json`). 그래서 **질문은 그대로 두고
 * 자료만 바꾼다.**
 *
 * 핵심 가설: **"사람들이 많이 가는 곳이 곧 현지인의 맛집인가."**
 *
 * 방문자 리뷰 수를 못 쓰니, 이미 우리 손에 있는 **부산 도시철도 승하차 인원**으로
 * 대신한다. 역마다 하루 몇 사람이 오르내리는지는 부산교통공사가 공개한 숫자이고,
 * 가게에서 가까운 역들의 승하차를 합치면 **"이 자리 앞을 사람이 얼마나 지나는가"**
 * 의 거친 대리 지표가 된다.
 *
 * 🔴 **대리 지표다. 방문자 리뷰 수가 아니다.** 역세권 유동인구는 "그 가게에 간 사람"이
 *    아니라 "그 동네를 지난 사람"이다. 결론을 쓸 때 이 둘을 섞지 않는다.
 *
 * 재는 것 셋.
 *  A. 유동인구·자리 신호의 판별력(AUC) — 조정용 111곳 vs 업종 맞춘 무작위 350곳
 *  B. 정답지 넷이 서로 얼마나 겹치는가 — 전문가의 맛집과 현지인의 맛집은 같은 집인가
 *  C. 무엇이 맛집을 만드는가 — 업종·구·가격대의 쏠림
 *
 * 🔴 **판정용 47곳은 열지 않는다.** 이 파일은 그 명단 경로를 아예 모른다.
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { OUT_DIR, RAW_DIR, inputs, repoRootFromArgs, truthRootFromArgs } from "./paths.mjs";

const IN = inputs(repoRootFromArgs(process.argv), truthRootFromArgs(process.argv));

// ─── 잣대 ────────────────────────────────────────────────────────────────────

/**
 * AUC — **어떤 신호가 정답과 비정답을 얼마나 잘 가르는가.**
 * 정답 하나와 비정답 하나를 아무렇게나 집었을 때, 신호가 정답 쪽을 더 크게 매길 확률이다.
 * **0.5 가 동전 던지기**이고 1.0 이 완벽이다. 0.5 아래면 거꾸로 가르고 있다는 뜻이다.
 *
 * 순위합(Mann-Whitney U)으로 구한다. 같은 값이 여럿이면 그 자리들의 평균 순위를 준다.
 * 함께 내놓는 `p` 는 **"신호가 아무 힘도 없는데 우연히 이만큼 갈렸을 확률"** 이다.
 */
function auc(pos, neg) {
  const n1 = pos.length, n2 = neg.length;
  if (!n1 || !n2) return null;
  const all = [...pos.map((v) => ({ v, p: 1 })), ...neg.map((v) => ({ v, p: 0 }))]
    .sort((a, b) => a.v - b.v);
  const N = all.length;
  let i = 0, rankSumPos = 0, tieTerm = 0;
  while (i < N) {
    let j = i;
    while (j + 1 < N && all[j + 1].v === all[i].v) j++;
    const t = j - i + 1;
    const midRank = (i + j) / 2 + 1;
    for (let k = i; k <= j; k++) if (all[k].p) rankSumPos += midRank;
    if (t > 1) tieTerm += t ** 3 - t;
    i = j + 1;
  }
  const U = rankSumPos - (n1 * (n1 + 1)) / 2;
  const a = U / (n1 * n2);
  const varU = ((n1 * n2) / 12) * (N + 1 - tieTerm / (N * (N - 1)));
  const z = varU > 0 ? (U - (n1 * n2) / 2) / Math.sqrt(varU) : 0;
  return { auc: a, n정답: n1, n비정답: n2, z, p: 2 * (1 - normCdf(Math.abs(z))) };
}

function normCdf(x) {
  // Abramowitz-Stegun 26.2.17
  const t = 1 / (1 + 0.2316419 * x);
  const d = 0.3989422804014327 * Math.exp(-(x * x) / 2);
  const p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
  return 1 - p;
}

/** 두 점 사이 거리(m). 부산 정도 넓이에서는 이 근사로 충분하다 */
function distM(aLat, aLon, bLat, bLon) {
  const dy = (aLat - bLat) * 111_320;
  const dx = (aLon - bLon) * 111_320 * Math.cos((aLat * Math.PI) / 180);
  return Math.hypot(dx, dy);
}

/** n 번 중 k 번 이상 나올 확률 (한쪽 꼬리). 업종 쏠림이 우연인지 보려고 쓴다 */
function binomTailGE(k, n, p) {
  if (p <= 0) return k > 0 ? 0 : 1;
  if (p >= 1) return 1;
  let logC = 0, tail = 0;
  for (let i = 0; i < n; i++) {
    if (i >= k) tail += Math.exp(logC + i * Math.log(p) + (n - i) * Math.log(1 - p));
    logC += Math.log((n - i) / (i + 1));
  }
  tail += Math.exp(logC + n * Math.log(p));
  return Math.min(1, tail);
}

// ─── 입력 ────────────────────────────────────────────────────────────────────
const samplePath = path.join(RAW_DIR, "sample-500.json");
if (!fs.existsSync(samplePath)) {
  console.error(`🔴 표본이 없다: ${samplePath}`);
  console.error("   먼저 02-sample.mjs 를 돌린다. (파기 뒤라면 다시 뽑으면 된다 — 씨앗이 같아 같은 표본이 나온다)");
  process.exit(1);
}
const sample = JSON.parse(fs.readFileSync(samplePath, "utf8"));
const tune = sample.items.filter((x) => x.role === "정답" && x.split === "조정용");
const control = sample.items.filter((x) => x.role === "비정답");
console.log(`조정용 정답 ${tune.length}곳 · 업종 맞춘 무작위 ${control.length}곳`);
console.log(`🔴 판정용 ${sample.items.filter((x) => x.split === "판정용").length}곳은 이 계산에 들어가지 않는다`);

// ─── 유동인구: 역별 일평균 승하차 ────────────────────────────────────────────
const stations = [];
{
  const txt = fs.readFileSync(IN.subwayStation).toString("utf16le");
  const rows = txt.split(/\r?\n/).filter((l) => l.trim());
  for (const l of rows.slice(1)) {
    const c = l.split("\t");
    const id = c[0]?.trim();
    const lat = Number(c[9]), lon = Number(c[10]);
    if (!id || !Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    stations.push({ id, lat, lon, ride: 0 });
  }
}

const days = new Set();
{
  const byId = new Map();
  const rl = readline.createInterface({
    input: fs.createReadStream(IN.subwayRidership, { encoding: "latin1" }),
    crlfDelay: Infinity,
  });
  let first = true;
  for await (const l of rl) {
    if (first) { first = false; continue; }
    if (!l.trim()) continue;
    const c = l.split(",");
    days.add(c[2]);
    const n = Number(c[5]);
    if (Number.isFinite(n)) byId.set(c[0], (byId.get(c[0]) ?? 0) + n);
  }
  for (const s of stations) s.ride = (byId.get(s.id) ?? 0) / days.size; // 일평균 (승차+하차)
}
const withRide = stations.filter((s) => s.ride > 0);
console.log(`역 ${stations.length}곳 (승하차 숫자가 붙은 것 ${withRide.length}곳) · ${days.size}일 평균`);

/**
 * 자리마다 유동인구 신호 넷을 만든다.
 *
 * - `가장가까운역까지_거리` — 이미 라운드3 에서 재 본 것. 견주려고 다시 넣는다
 * - `가장가까운역_유동인구` — 그 역이 하루에 몇 사람을 뱉는가. **거리에는 없던 "크기"다**
 * - `걸어서닿는_유동인구` — 가까운 역일수록 크게 치면서 3km 안을 다 더한 것 (중력합)
 * - `500m내_역유동인구` — 걸어서 바로인 역들만
 */
function footfall(p) {
  let near = null, nearD = Infinity, gravity = 0, within500 = 0;
  for (const s of withRide) {
    const d = distM(p.lat, p.lon, s.lat, s.lon);
    if (d < nearD) { nearD = d; near = s; }
    if (d <= 3000) gravity += s.ride / (1 + d / 300) ** 2;
    if (d <= 500) within500 += s.ride;
  }
  return {
    가장가까운역까지_거리: nearD,
    가장가까운역_유동인구: near ? near.ride : 0,
    걸어서닿는_유동인구: gravity,
    "500m내_역유동인구": within500,
  };
}

const posF = tune.map(footfall);
const negF = control.map(footfall);

const SIGNALS = [
  { key: "가장가까운역까지_거리", 방향: -1, 말: "지하철역이 가까울수록 맛집인가" },
  { key: "가장가까운역_유동인구", 방향: +1, 말: "가까운 역이 클수록 맛집인가" },
  { key: "걸어서닿는_유동인구", 방향: +1, 말: "🔴 걸어서 닿는 사람이 많을수록 맛집인가 — 핵심 가설의 대리 지표" },
  { key: "500m내_역유동인구", 방향: +1, 말: "역 바로 앞일수록 맛집인가" },
];

const aucRows = SIGNALS.map((s) => {
  const r = auc(posF.map((f) => s.방향 * f[s.key]), negF.map((f) => s.방향 * f[s.key]));
  return { 신호: s.key, 뜻: s.말, ...r };
});

/**
 * 자리 신호 하나 더 — **바닷가 관광 구에 있는가.**
 *
 * 무작위 350곳은 업종만 맞췄고 **구는 안 맞췄다.** 그래서 구는 이 표본으로 잴 수 있다.
 *
 * 🔴 어느 구를 볼지는 **조정용 표를 보고 골랐다.** 그러니 이 숫자는 "고른 것"이지
 *    "확인한 것"이 아니다. 확인은 판정용 47곳으로 해야 하고, 이 분석은 그것을 열지 않는다.
 */
const 관광구 = new Set(["해운대구", "수영구"]);
aucRows.push({
  신호: "해운대구·수영구인가",
  뜻: "🔴 조정용을 보고 고른 구다. 확인은 판정용으로 다시 해야 한다",
  고른방식: "조정용 표에서 배수가 가장 큰 두 구",
  ...auc(
    tune.map((p) => (관광구.has(p.sigungu) ? 1 : 0)),
    control.map((p) => (관광구.has(p.sigungu) ? 1 : 0)),
  ),
});

console.log("\n── 신호의 판별력 (조정용 111 vs 무작위 350) ──");
for (const r of aucRows) {
  console.log(`  ${r.auc.toFixed(3)}  p=${r.p.toFixed(4)}  ${r.신호}`);
}

// ─── 정답지끼리 얼마나 겹치나 ────────────────────────────────────────────────
const matched = JSON.parse(fs.readFileSync(IN.matched, "utf8"));
const truthAll = matched.items.filter((it) => it.storeId);
const has = (t, s) => (t.tasteSources ?? []).includes(s);

const 전문가 = truthAll.filter((t) => has(t, "michelin") || has(t, "blueribbon"));
const 현지인 = truthAll.filter((t) => has(t, "blog100"));
const 겹침 = 현지인.filter((t) => has(t, "michelin") || has(t, "blueribbon"));
const 오래버팀 = truthAll.filter((t) => t.isBaengnyeon);
const 오래버팀중맛 = 오래버팀.filter((t) => t.isTaste);

const N음식 = 53716;
const 겹침표 = {
  "전문가 표(미쉐린∪블루리본)": 전문가.length,
  "현지인 표(블로그100)": 현지인.length,
  "둘 다": 겹침.length,
  "현지인이 고른 집이 전문가 목록에도 있을 확률": 현지인.length ? 겹침.length / 현지인.length : null,
  "아무 식당이나 전문가 목록에 있을 확률": 전문가.length / N음식,
  자카드: 겹침.length / (전문가.length + 현지인.length - 겹침.length),
  "우연이라면 몇 곳이 겹쳤을까": (전문가.length * 현지인.length) / N음식,
  "오래 버틴 집(백년가게) 중 맛 정답지에도 있는 곳": `${오래버팀중맛.length}/${오래버팀.length}`,
};
겹침표["배수(우연 대비)"] =
  겹침표["현지인이 고른 집이 전문가 목록에도 있을 확률"] / 겹침표["아무 식당이나 전문가 목록에 있을 확률"];

console.log("\n── 전문가의 맛집과 현지인의 맛집은 같은 집인가 ──");
console.log(`  전문가 ${전문가.length}곳 · 현지인 ${현지인.length}곳 · 둘 다 ${겹침.length}곳`);
console.log(`  현지인이 고른 집이 전문가 목록에도 있을 확률: ${(겹침표["현지인이 고른 집이 전문가 목록에도 있을 확률"] * 100).toFixed(1)}%`);
console.log(`  아무 식당이나: ${(겹침표["아무 식당이나 전문가 목록에 있을 확률"] * 100).toFixed(3)}%  → ${겹침표["배수(우연 대비)"].toFixed(0)}배`);
console.log(`  오래 버틴 집(백년가게) 중 맛 정답지에도 있는 곳: ${겹침표["오래 버틴 집(백년가게) 중 맛 정답지에도 있는 곳"]}`);

// ─── 무엇이 맛집을 만드는가: 업종·구·가격대 ─────────────────────────────────
/**
 * 🔴 무작위 350곳은 **업종을 정답과 맞춰서** 뽑았다. 그래서 이 표본으로는
 *    업종의 힘을 잴 수 없다 — 일부러 지웠기 때문이다. 업종 쏠림은 표본이 아니라
 *    **부산 음식점 53,716곳 전체**와 견준다.
 */
const poolBySo = new Map();
const poolBySigungu = new Map();
{
  const rl = readline.createInterface({
    input: fs.createReadStream(IN.sbizCsv, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  let first = true;
  for await (const line of rl) {
    if (first) { first = false; continue; }
    if (!line.trim()) continue;
    const c = line.split(",").map((x) => x.replace(/^"|"$/g, ""));
    if (c[4] !== "음식") continue;
    poolBySo.set(c[8], (poolBySo.get(c[8]) ?? 0) + 1);
    poolBySigungu.set(c[14], (poolBySigungu.get(c[14]) ?? 0) + 1);
  }
}

function lift(items, poolMap, key, poolTotal, label) {
  const cnt = new Map();
  for (const t of items) cnt.set(t[key], (cnt.get(t[key]) ?? 0) + 1);
  return [...cnt]
    .map(([k, n]) => {
      const pool = poolMap.get(k) ?? 0;
      const pPool = pool / poolTotal;
      return {
        [label]: k,
        정답: n,
        "정답 중 비율": n / items.length,
        "부산 전체 중 비율": pPool,
        배수: pPool > 0 ? n / items.length / pPool : null,
        p: pPool > 0 ? binomTailGE(n, items.length, pPool) : null,
        "부산 전체 개수": pool,
      };
    })
    .sort((a, b) => b.정답 - a.정답);
}

const tasteTruth = truthAll.filter((t) => t.isTaste);
const 업종쏠림 = lift(tasteTruth, poolBySo, "so", N음식, "업종");
const 구쏠림 = lift(tasteTruth, poolBySigungu, "sigungu", N음식, "구");

console.log("\n── 어떤 업종이 맛집이 되나 (정답 158곳 vs 부산 음식 53,716곳) ──");
for (const r of 업종쏠림.slice(0, 8)) {
  console.log(`  ${String(r.정답).padStart(3)}곳  ${r.배수.toFixed(1)}배  p=${r.p.toExponential(1)}  ${r.업종}`);
}
console.log("\n── 어떤 구에 몰려 있나 ──");
for (const r of 구쏠림.slice(0, 6)) {
  console.log(`  ${String(r.정답).padStart(3)}곳  ${r.배수.toFixed(1)}배  ${r.구}`);
}

/**
 * 🔴 **해운대·수영 쏠림이 정답지가 만든 착시인지 본다.**
 *
 * 블루리본 부산 목록은 **시티투어버스 6개 테마**에서 옮겨 왔다. 시티투어버스는
 * 해운대와 광안리를 지난다 — 그러니 "해운대에 맛집이 많다" 가 아니라 **"버스가
 * 해운대를 지난다"** 를 재고 있을 수 있다.
 *
 * 가르는 법은 하나뿐이다. **버스와 상관없는 목록에서도 같은 쏠림이 나오는가.**
 * 블로그100 은 작성자가 "평가기관의 평가에 전혀 신경쓰지 않았다" 고 밝힌 개인 목록이다.
 */
const 관광구쏠림 = {};
{
  const poolHS = (poolBySigungu.get("해운대구") ?? 0) + (poolBySigungu.get("수영구") ?? 0);
  const share = poolHS / N음식;
  const 목록 = {
    미쉐린: truthAll.filter((t) => has(t, "michelin")),
    블루리본: truthAll.filter((t) => has(t, "blueribbon")),
    "블로그100(현지인)": 현지인,
  };
  for (const [name, arr] of Object.entries(목록)) {
    const hs = arr.filter((t) => 관광구.has(t.sigungu)).length;
    관광구쏠림[name] = { n: arr.length, "해운대+수영": hs, 비율: hs / arr.length, 배수: hs / arr.length / share };
  }
  관광구쏠림["부산 전체"] = { n: N음식, "해운대+수영": poolHS, 비율: share, 배수: 1 };
  console.log("\n── 해운대·수영 쏠림이 목록마다 다 나오나 ──");
  for (const [k, v] of Object.entries(관광구쏠림)) {
    console.log(`  ${k.padEnd(18)} ${String(v.n).padStart(6)}곳  해운대+수영 ${(v.비율 * 100).toFixed(1)}%  ${v.배수.toFixed(2)}배`);
  }
}

/** 미쉐린이 매긴 가격대(₩ 개수) — 부산 맛집은 비싼 집인가 */
const 가격대 = {};
for (const t of truthAll) if (t.michelinPrice) 가격대[t.michelinPrice] = (가격대[t.michelinPrice] ?? 0) + 1;
const 리본 = {};
for (const t of truthAll) if (t.blueRibbon) 리본[t.blueRibbon] = (리본[t.blueRibbon] ?? 0) + 1;
console.log(`\n── 미쉐린 가격대: ${JSON.stringify(가격대)}  · 블루리본 리본 수: ${JSON.stringify(리본)}`);

/**
 * 🔴 여기가 이 분석의 요점이다.
 *
 * 위의 업종 쏠림은 정답지 넷을 **한 덩어리로** 본 것이다. 그런데 정답지는 서로
 * 별로 겹치지 않는다(위 30.3%). 그러면 "부산 맛집의 업종" 이라는 하나의 답이 있는 게
 * 아니라 **표를 던진 사람마다 다른 업종을 고른 것**일 수 있다. 그걸 갈라서 본다.
 */
const 전문가쏠림 = lift(전문가, poolBySo, "so", N음식, "업종");
const 현지인쏠림 = lift(현지인, poolBySo, "so", N음식, "업종");

console.log("\n── 전문가가 고른 업종 (미쉐린∪블루리본 105곳) ──");
for (const r of 전문가쏠림.slice(0, 6)) console.log(`  ${String(r.정답).padStart(3)}곳  ${r.배수.toFixed(1)}배  ${r.업종}`);
console.log("\n── 현지인이 고른 업종 (블로그100, 76곳) ──");
for (const r of 현지인쏠림.slice(0, 6)) console.log(`  ${String(r.정답).padStart(3)}곳  ${r.배수.toFixed(1)}배  ${r.업종}`);

/**
 * 두 목록이 **업종을 얼마나 비슷하게 고르는가**를 한 숫자로 만든다.
 * 1 에 가까울수록 같은 업종을 같은 비율로 고른 것이다.
 * 이 숫자를 위의 **가게 단위 겹침**과 나란히 놓는 것이 이 분석의 결론이 된다.
 */
function cosine(aRows, bRows) {
  const keys = new Set([...aRows.map((r) => r.업종), ...bRows.map((r) => r.업종)]);
  const va = new Map(aRows.map((r) => [r.업종, r["정답 중 비율"]]));
  const vb = new Map(bRows.map((r) => [r.업종, r["정답 중 비율"]]));
  let dot = 0, na = 0, nb = 0;
  for (const k of keys) {
    const x = va.get(k) ?? 0, y = vb.get(k) ?? 0;
    dot += x * y; na += x * x; nb += y * y;
  }
  return dot / Math.sqrt(na * nb);
}
const 업종일치도 = cosine(전문가쏠림, 현지인쏠림);
console.log(`\n🔴 업종을 고르는 방식은 ${(업종일치도 * 100).toFixed(0)}% 같은데, 실제로 고른 가게는 ${(겹침표.자카드 * 100).toFixed(0)}% 만 같다`);

// ─── 적어 둔다 ───────────────────────────────────────────────────────────────
const out = {
  ranAt: new Date().toISOString(),
  역할: "조정용. 판정용 47곳은 열지 않았다",
  표본: { 조정용정답: tune.length, 무작위: control.length },
  유동인구: {
    출처: "부산교통공사 도시철도 역별 승하차 (bigData/data/raw/subway)",
    역수: withRide.length,
    일수: days.size,
    주의: "🔴 방문자 리뷰 수의 **대리 지표**다. '그 가게에 간 사람'이 아니라 '그 동네를 지난 사람'이다",
    auc: aucRows,
  },
  정답지겹침: 겹침표,
  업종쏠림,
  "업종쏠림-전문가만": 전문가쏠림,
  "업종쏠림-현지인만": 현지인쏠림,
  "업종일치도(전문가 vs 현지인)": 업종일치도,
  구쏠림,
  "관광구쏠림-목록별": 관광구쏠림,
  가격대: { 미쉐린: 가격대, 블루리본리본수: 리본 },
  견줄기준: {
    "라운드3 에서 가장 좋았던 공개 데이터 신호": 0.497,
    비고: "맛집 골목 밀집 0.497 · 대중교통 근접 0.481 — 둘 다 동전 던지기와 구별되지 않았다",
  },
};
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, "03-signals.json"), JSON.stringify(out, null, 1), "utf8");
console.log(`\n적었다: ${path.join(OUT_DIR, "03-signals.json")}`);
