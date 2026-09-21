/**
 * 전통시장 300m 반경 — 음식점에 MARKET 후보를 붙인다.
 *
 * 🔴 이미 운영에 있는 MARKET 51곳(CITY/CULTURE_TEMPLE)은 "시장이라는 장소" 자체에 붙은
 *    태그다. 이 스크립트가 만드는 것은 그것과 다르다 — **시장 300m 안에 있는 음식점(FOOD)**
 *    에 MARKET 을 붙인다. 두 집합은 성격이 다르므로 겹치는지는 적재할 때(-28) DB 로 대조한다.
 *
 * 🔴 여기까지다 — 파일만 만든다. 운영 DB 적재는 안 한다.
 *
 * 입력
 *   data/raw/poi/sbiz-poi-busan-202606.csv (또는 SBIZ_CSV)   상가정보(음식만 걸러 쓴다)
 *   data/raw/market/traditional-market-standard-busan.csv    전국전통시장표준데이터 중 부산분
 *     (공공데이터포털 15012894, 원본은 CP949 라 iconv 로 UTF-8 변환해 저장해 둔 것)
 * 출력
 *   data/staged/place-market.ndjson   한 줄에 한 음식점 (300m 안에 든 것만)
 *
 * 사용
 *   node process/market-join.mjs
 *   node process/market-join.mjs --radius 500   반경을 바꿔 본다 (기본 300m)
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CSV = process.env.SBIZ_CSV ?? path.join(ROOT, "data/raw/poi/sbiz-poi-busan-202606.csv");
const MARKET_CSV = path.join(ROOT, "data/raw/market/traditional-market-standard-busan.csv");
const STAGED = path.join(ROOT, "data/staged");
const OUT = path.join(STAGED, "place-market.ndjson");

const argv = process.argv.slice(2);
const num = (name, dflt) => {
  const i = argv.indexOf(name);
  if (i < 0) return dflt;
  const v = Number(argv[i + 1]);
  if (!Number.isFinite(v)) throw new Error(`${name} 뒤에 숫자가 필요합니다`);
  return v;
};
const RADIUS_M = num("--radius", 300);

/** 큰따옴표 안에 쉼표가 있어도 안 깨지는 최소 CSV 파서 (select-2000.mjs 와 같은 것) */
function parseCsvLine(line) {
  const out = [];
  let cur = "";
  let q = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (q) {
      if (ch === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; }
        else q = false;
      } else cur += ch;
    } else if (ch === '"') q = true;
    else if (ch === ",") { out.push(cur); cur = ""; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

const R = 6371000;
function haversine(aLat, aLon, bLat, bLon) {
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLon = ((bLon - aLon) * Math.PI) / 180;
  const la1 = (aLat * Math.PI) / 180;
  const la2 = (bLat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

// ── 시장 좌표 ────────────────────────────────────────────────────────────────
const SBIZ_COL = { storeId: 0, name: 1, dae: 4, roadAddr: 31, lon: 37, lat: 38 };

async function loadMarkets() {
  const rl = readline.createInterface({
    input: fs.createReadStream(MARKET_CSV, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const markets = [];
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    const [name, , , , , latStr, lonStr] = c; // 시장명,시장유형,도로명주소,지번주소,개설주기,위도,경도,...
    const lat = Number(latStr);
    const lon = Number(lonStr);
    // 🔴 Number('') 는 0 이다 — isFinite 로 거르지 않으면 "부산 앞바다의 시장"이 생긴다
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    markets.push({ name, lat, lon });
  }
  return markets;
}

async function loadFood() {
  const rl = readline.createInterface({
    input: fs.createReadStream(CSV, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const food = [];
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    if (c[SBIZ_COL.dae] !== "음식") continue;
    const lat = Number(c[SBIZ_COL.lat]);
    const lon = Number(c[SBIZ_COL.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    food.push({ id: c[SBIZ_COL.storeId], name: c[SBIZ_COL.name], roadAddr: c[SBIZ_COL.roadAddr], lat, lon });
  }
  return food;
}

const t0 = Date.now();
console.log(`시장(${RADIUS_M}m 반경 기준)·음식점을 읽습니다 …`);
const [markets, food] = await Promise.all([loadMarkets(), loadFood()]);
console.log(`시장 ${markets.length}곳 · 음식점 ${food.length}곳`);

const matched = [];
const byMarket = new Map();
for (const f of food) {
  let best = null;
  for (const m of markets) {
    const d = haversine(f.lat, f.lon, m.lat, m.lon);
    if (d <= RADIUS_M && (!best || d < best.d)) best = { market: m, d };
  }
  if (best) {
    matched.push({
      placeId: f.id,
      name: f.name,
      roadAddr: f.roadAddr,
      marketName: best.market.name,
      distanceM: Math.round(best.d),
      value: true,
    });
    byMarket.set(best.market.name, (byMarket.get(best.market.name) ?? 0) + 1);
  }
}

console.log(`\n음식점 ${food.length}곳 중 시장 ${RADIUS_M}m 안: ${matched.length}곳 (${((matched.length / food.length) * 100).toFixed(1)}%)`);

const rank = [...byMarket.entries()].sort((a, b) => b[1] - a[1]);
console.log(`\n시장별 걸린 음식점 수 — 상위 15`);
for (const [name, n] of rank.slice(0, 15)) console.log(`  ${name} ${n}`);
console.log(`\n0곳 걸린 시장: ${markets.length - byMarket.size}/${markets.length}`);

fs.mkdirSync(STAGED, { recursive: true });
fs.writeFileSync(OUT, matched.map((m) => JSON.stringify(m)).join("\n") + "\n");
console.log(`\n→ ${path.relative(ROOT, OUT)} (${matched.length}줄). ${((Date.now() - t0) / 1000).toFixed(1)}초`);
console.log(`\n🔴 파일만 만들었다 — DB 적재는 안 했다. 기존 MARKET 51곳(CITY/CULTURE_TEMPLE)과의`);
console.log(`   겹침 여부는 이 스크립트가 확인할 수 없다(운영 DB 접근 필요) — 적재할 때 대조한다.`);
