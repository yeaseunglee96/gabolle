/**
 * 저상버스 — 노선번호를 정규화하고 노선별 저상 비중을 낸다.
 *
 * 입력이 **차량 단위**다(부산 시내버스 등록차량 2,511대, 공공데이터포털 15043689).
 * "이 차가 오늘 몇 번에 투입되는가"는 정적 등록 정보라 실시간이 아니다 — 노선별로
 * 묶어서 "이 노선에 저상차가 몇 대 배정돼 있나"의 근사치로 쓴다.
 *
 * 🔴 이 자료는 **정류장 좌표를 안 준다.** "이 정류장/구간이 짐·휠체어에 되나"를 답하려면
 *    정류장-노선 위치 정보(BIMS, `collect/bims-route-stops.mjs`)와 노선번호로 조인해야
 *    하는데, 그 자료는 아직 이 저장소에 수집돼 있지 않다 — 그래서 이 스크립트는
 *    **노선 단위 요약까지만** 낸다. 좌표 조인은 다음 단계(확인 못 함, 범위 밖).
 *
 * 입력  data/raw/bus/busan-bus-registration-lowfloor.csv (공공데이터 15043689, UTF-8 변환본)
 * 출력  data/staged/bus-lowfloor-by-route.ndjson   한 줄에 노선 하나
 *       data/staged/bus-lowfloor-vehicles.ndjson   한 줄에 차량 하나 (원본 그대로, 정규화 칸만 추가)
 *
 * 사용  node process/lowfloor-bus-normalize.mjs
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CSV = path.join(ROOT, "data/raw/bus/busan-bus-registration-lowfloor.csv");
const STAGED = path.join(ROOT, "data/staged");
const OUT_ROUTE = path.join(STAGED, "bus-lowfloor-by-route.ndjson");
const OUT_VEHICLE = path.join(STAGED, "bus-lowfloor-vehicles.ndjson");

/**
 * "88(A)" → "88", "88-1A" → "88-1" 처럼 지선·변형 표기를 벗겨 기본 노선번호로 모은다.
 * 원본(`routeRaw`)은 항상 같이 남기므로 정규화가 틀려도 원문으로 되짚을 수 있다.
 */
function normalizeRoute(raw) {
  const t = raw.trim();
  const m = t.match(/^(\d+(?:-\d+)?)/);
  return m ? m[1] : t;
}

const rl = readline.createInterface({
  input: fs.createReadStream(CSV, { encoding: "utf8" }),
  crlfDelay: Infinity,
});

const vehicles = [];
let first = true;
let unmatchedRoutes = 0;
for await (const line of rl) {
  if (!line.trim()) continue;
  const c = line.split(",");
  if (first) { first = false; continue; }
  const [company, routeRaw, plate, opType, useType, size, fuel, year] = c;
  const routeNorm = normalizeRoute(routeRaw);
  if (routeNorm !== routeRaw.trim()) unmatchedRoutes++;
  vehicles.push({
    company, routeRaw: routeRaw.trim(), route: routeNorm, plate,
    lowFloor: opType.trim() === "저상",
    size: size?.trim(), fuel: fuel?.trim(), year: Number(year),
  });
}

console.log(`차량 ${vehicles.length}대 읽음. 노선번호 정규화로 바뀐 표기 ${unmatchedRoutes}건`);

fs.mkdirSync(STAGED, { recursive: true });
fs.writeFileSync(OUT_VEHICLE, vehicles.map((v) => JSON.stringify(v)).join("\n") + "\n");

// ── 노선별 요약 ─────────────────────────────────────────────────────────────
const byRoute = new Map();
for (const v of vehicles) {
  if (!byRoute.has(v.route)) byRoute.set(v.route, { route: v.route, total: 0, lowFloor: 0 });
  const r = byRoute.get(v.route);
  r.total++;
  if (v.lowFloor) r.lowFloor++;
}
const routes = [...byRoute.values()]
  .map((r) => ({ ...r, lowFloorShare: Number((r.lowFloor / r.total).toFixed(3)) }))
  .sort((a, b) => b.total - a.total);

fs.writeFileSync(OUT_ROUTE, routes.map((r) => JSON.stringify(r)).join("\n") + "\n");

const totalLow = vehicles.filter((v) => v.lowFloor).length;
console.log(`\n전체 저상 비중: ${totalLow}/${vehicles.length} (${((totalLow / vehicles.length) * 100).toFixed(1)}%)`);
console.log(`노선 ${routes.length}개`);
console.log(`\n저상 비중 상위 10 노선 (차량 3대 이상)`);
for (const r of routes.filter((r) => r.total >= 3).slice(0, 10).sort((a, b) => b.lowFloorShare - a.lowFloorShare)) {
  console.log(`  ${r.route}번: 저상 ${r.lowFloor}/${r.total} (${(r.lowFloorShare * 100).toFixed(0)}%)`);
}
console.log(`\n저상차가 0대인 노선: ${routes.filter((r) => r.lowFloor === 0).length}/${routes.length}`);

console.log(`\n→ ${path.relative(ROOT, OUT_VEHICLE)} (${vehicles.length}줄)`);
console.log(`→ ${path.relative(ROOT, OUT_ROUTE)} (${routes.length}줄)`);
console.log(`\n🔴 노선 단위다 — 정류장 좌표는 없다. BIMS 노선-정류장 자료(미수집)와 노선번호로`);
console.log(`   조인해야 "이 정류장이 저상 되나"를 답할 수 있다(다음 단계, 확인 못 함).`);
