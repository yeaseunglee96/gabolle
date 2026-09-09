/**
 * 라운드2 5단계 — 신호마다 **AUC**(어떤 신호가 정답과 오답을 얼마나 잘 가르는지.
 * 0.5면 동전던지기, 1.0이면 완벽)를 잰다. **조정용 정답만** 본다.
 *
 * 산출: 표준출력만.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-split-tune.json"), "utf8"));
const pos = new Set(tune.items.map((i) => i.storeId));
const rows = feat.rows;
const P = rows.filter((r) => pos.has(r.id));
const N = rows.filter((r) => !pos.has(r.id));
console.log(`후보 ${rows.length} · 조정용 정답 ${P.length} · 나머지 ${N.length}\n`);

/** 순위합(Mann-Whitney)으로 AUC 를 정확히 센다. 동점은 0.5로 나눈다. */
function auc(get) {
  const all = rows.map((r) => ({ v: Number(get(r)), y: pos.has(r.id) ? 1 : 0 }))
    .filter((x) => Number.isFinite(x.v));
  all.sort((a, b) => a.v - b.v);
  let i = 0, rankSumPos = 0, nPos = 0, nNeg = 0;
  while (i < all.length) {
    let j = i;
    while (j < all.length && all[j].v === all[i].v) j++;
    const avgRank = (i + 1 + j) / 2; // 1-based 평균 순위
    for (let k = i; k < j; k++) {
      if (all[k].y) { rankSumPos += avgRank; nPos++; } else nNeg++;
    }
    i = j;
  }
  if (!nPos || !nNeg) return NaN;
  return (rankSumPos - (nPos * (nPos + 1)) / 2) / (nPos * nNeg);
}

const SIGNALS = {
  "밀집 100m (foodDens100)": (r) => r.foodDens100,
  "밀집 300m (foodDens300)": (r) => r.foodDens300,
  "같은 업종 밀집 300m (soDens300)": (r) => r.soDens300,
  "같은 업종 비중 300m (soShare300)": (r) => r.soShare300,
  "같은 상호 개수 = 프랜차이즈 (nameCount)": (r) => r.nameCount,
  "지점명 있음 (hasBranch)": (r) => (r.hasBranch ? 1 : 0),
  "상호명 길이 (nameLen)": (r) => r.nameLen,
  "1층 (isFloor1)": (r) => (r.isFloor1 ? 1 : 0),
  "지하 (isBasement)": (r) => (r.isBasement ? 1 : 0),
  "2층 이상 (isUpper)": (r) => (r.isUpper ? 1 : 0),
  "층정보 없음 (floorMissing)": (r) => (r.floorMissing ? 1 : 0),
  "건물명 있음 (hasBldgName)": (r) => (r.hasBldgName ? 1 : 0),
  "호수 있음 (hasHo)": (r) => (r.hasHo ? 1 : 0),
  "같은 건물 음식점 수 (bldgFoodCount)": (r) => r.bldgFoodCount,
  "같은 도로 음식점 수 (roadFoodCount)": (r) => r.roadFoodCount,
  "같은 법정동 음식점 수 (bdongFoodCount)": (r) => r.bdongFoodCount,
  "산 지번 (daejiSan)": (r) => (r.daejiSan ? 1 : 0),
  "버스정류장 가까움 (-transitM)": (r) => -r.transitM,
  "지하철 가까움 (-subwayM)": (r) => -r.subwayM,
  "관광지 가까움 (-touristM)": (r) => -r.touristM,
  "관광지에서 멂 (+touristM)": (r) => r.touristM,
  "숙박 밀집 500m (lodgeDens500)": (r) => r.lodgeDens500,
  "가장 가까운 숙박 (-lodgeM)": (r) => -r.lodgeM,
  "예술·스포츠 밀집 500m (leisureDens500)": (r) => r.leisureDens500,
  "🔴 OSM 에 그려져 있음 (osmListed)": (r) => (r.osmListed ? 1 : 0),
  "🔴 OSM 태그 촘촘함 (osmTagRichness)": (r) => r.osmTagRichness,
  "🔴 OSM 전화번호 (osmHasPhone)": (r) => (r.osmHasPhone ? 1 : 0),
  "🔴 OSM 영업시간 (osmHasOpeningHours)": (r) => (r.osmHasOpeningHours ? 1 : 0),
};

const results = Object.entries(SIGNALS).map(([k, f]) => [k, auc(f)]);
results.sort((a, b) => Math.abs(b[1] - 0.5) - Math.abs(a[1] - 0.5));
console.log("신호\tAUC\t힘(0.5에서 떨어진 정도)");
for (const [k, v] of results) {
  console.log(`${v.toFixed(3)}\t${Math.abs(v - 0.5).toFixed(3)}\t${k}`);
}

// ── 업종 소분류: 조정용 정답 비율 ────────────────────────────────────────────
console.log("\n업종 소분류별 — 후보 수 / 조정용 정답 수 / 정답률 (전체 평균 대비 배수)");
const base = P.length / rows.length;
const bySo = new Map();
for (const r of rows) {
  const e = bySo.get(r.so) ?? { n: 0, p: 0 };
  e.n++;
  if (pos.has(r.id)) e.p++;
  bySo.set(r.so, e);
}
[...bySo].filter(([, e]) => e.n >= 100).sort((a, b) => b[1].p / b[1].n - a[1].p / a[1].n)
  .forEach(([k, e]) => {
    const rate = e.p / e.n;
    console.log(`  ${k}\t${e.n}\t${e.p}\t${(rate * 100).toFixed(2)}%\t×${(rate / base).toFixed(2)}`);
  });
