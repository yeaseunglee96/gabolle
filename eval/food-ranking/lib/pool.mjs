/**
 * 후보 풀(candidate pool — *점수를 매길 대상 전부*) 을 만든다.
 *
 * 입력: bigData 클론의 OSM 추출본 (data/raw/pbf/*.ndjson). 이 저장소 밖이라 경로를
 *       환경변수 BIGDATA_DIR 로 바꿀 수 있다.
 * 출력: { id, lat, lon, tags, kind } 배열
 *
 * 🔴 이 파일은 읽기만 한다. 원본을 고치지 않는다.
 */
import fs from "node:fs";
import path from "node:path";

export const BIGDATA_DIR =
  process.env.BIGDATA_DIR ?? "C:/Users/SSAFY/Desktop/S15P21E201-bigdata/bigData";

const PBF = path.join(BIGDATA_DIR, "data", "raw", "pbf");

/** 먹는 곳으로 치는 OSM 태그. 앞선 조사 문서(FOOD-RANKING-ARCHITECTURE 1.0)와 같은 정의다. */
const FOOD_AMENITY = new Set([
  "restaurant",
  "cafe",
  "fast_food",
  "pub",
  "bar",
  "ice_cream",
  "food_court",
  "biergarten",
]);
const FOOD_SHOP = new Set(["bakery", "confectionery", "pastry", "deli", "coffee"]);

export function readNdjson(file) {
  const rows = [];
  const text = fs.readFileSync(file, "utf8");
  for (const line of text.split("\n")) {
    const t = line.trim();
    if (!t) continue;
    try {
      rows.push(JSON.parse(t));
    } catch {
      /* 깨진 줄은 버린다 — 원본에 인코딩 깨짐이 몇 줄 있다 */
    }
  }
  return rows;
}

export function loadFoodPois() {
  const all = readNdjson(path.join(PBF, "poi.ndjson"));
  const out = [];
  for (const p of all) {
    const t = p.tags ?? {};
    let kind = null;
    if (t.amenity && FOOD_AMENITY.has(t.amenity)) kind = t.amenity;
    else if (t.shop && FOOD_SHOP.has(t.shop)) kind = `shop:${t.shop}`;
    if (!kind) continue;
    if (!Number.isFinite(p.lat) || !Number.isFinite(p.lon)) continue;
    out.push({ id: `${p.type}/${p.id}`, lat: p.lat, lon: p.lon, tags: t, kind });
  }
  return out;
}

export function loadTransit() {
  const all = readNdjson(path.join(PBF, "transit.ndjson"));
  const stops = [];
  const subway = [];
  for (const n of all) {
    const t = n.tags ?? {};
    if (!Number.isFinite(n.lat) || !Number.isFinite(n.lon)) continue;
    const isStop =
      t.highway === "bus_stop" ||
      t.public_transport === "platform" ||
      t.public_transport === "stop_position" ||
      t.amenity === "bus_station";
    const isSubway =
      t.station === "subway" ||
      t.railway === "station" ||
      t.railway === "subway_entrance" ||
      (t.railway === "halt" && t.subway === "yes");
    if (isStop) stops.push({ lat: n.lat, lon: n.lon });
    if (isSubway) subway.push({ lat: n.lat, lon: n.lon });
  }
  return { stops, subway };
}

/** 관광 기준점 — 앞선 문서 1.4 와 같은 정의 (해안선·공원이 빠져 있다는 한계도 그대로). */
export function loadTouristAnchors() {
  const all = readNdjson(path.join(PBF, "poi.ndjson"));
  const out = [];
  for (const p of all) {
    const t = p.tags ?? {};
    const hit =
      t.tourism === "viewpoint" ||
      t.tourism === "attraction" ||
      t.tourism === "museum" ||
      t.tourism === "theme_park" ||
      t.tourism === "aquarium" ||
      t.tourism === "zoo" ||
      t.natural === "beach" ||
      (t.historic && t.historic !== "no");
    if (!hit) continue;
    if (!Number.isFinite(p.lat) || !Number.isFinite(p.lon)) continue;
    out.push({ lat: p.lat, lon: p.lon });
  }
  return out;
}

const R = 6371000;
export function haversine(aLat, aLon, bLat, bLon) {
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLon = ((bLon - aLon) * Math.PI) / 180;
  const la1 = (aLat * Math.PI) / 180;
  const la2 = (bLat * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * 격자 색인(grid index — *좌표를 칸으로 나눠 근처만 훑게 하는 것*).
 * 3,031 × 3,031 을 전부 재면 느려서 칸으로 자른다.
 */
export function buildGrid(points, cellDeg = 0.005) {
  const g = new Map();
  points.forEach((p, i) => {
    const k = `${Math.floor(p.lat / cellDeg)}|${Math.floor(p.lon / cellDeg)}`;
    if (!g.has(k)) g.set(k, []);
    g.get(k).push(i);
  });
  return { g, cellDeg, points };
}

export function neighbors(grid, lat, lon, radiusM) {
  const { g, cellDeg, points } = grid;
  const dLat = radiusM / 111320;
  const dLon = radiusM / (111320 * Math.cos((lat * Math.PI) / 180));
  const span = Math.max(Math.ceil(dLat / cellDeg), Math.ceil(dLon / cellDeg));
  const cy = Math.floor(lat / cellDeg);
  const cx = Math.floor(lon / cellDeg);
  const out = [];
  for (let y = cy - span; y <= cy + span; y++) {
    for (let x = cx - span; x <= cx + span; x++) {
      const arr = g.get(`${y}|${x}`);
      if (!arr) continue;
      for (const i of arr) {
        const d = haversine(lat, lon, points[i].lat, points[i].lon);
        if (d <= radiusM) out.push({ i, d });
      }
    }
  }
  return out;
}

/** 가장 가까운 하나까지의 거리(m). 반경 안에 없으면 반경을 키워 가며 찾는다. */
export function nearestDistance(grid, lat, lon, startM = 500, maxM = 8000) {
  let r = startM;
  while (r <= maxM) {
    const n = neighbors(grid, lat, lon, r);
    if (n.length) return Math.min(...n.map((x) => x.d));
    r *= 2;
  }
  return maxM;
}
