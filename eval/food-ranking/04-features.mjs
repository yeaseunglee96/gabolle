/**
 * 4단계 — 후보 3,039곳마다 신호를 미리 굽는다 (배치).
 *
 * 여기서 굽는 것은 전부 **좌표에서 나오는 구조 신호**다. 리뷰·평점은 하나도 안 쓴다.
 *  · dens150 / dens300 — 반경 안의 다른 식음료 POI 수 ("맛집 골목인가")
 *  · transitM          — 가장 가까운 버스정류장·승강장까지 m
 *  · subwayM           — 가장 가까운 지하철역·출입구까지 m
 *  · touristM          — 가장 가까운 관광 기준점까지 m
 *    🔴 이 기준점 목록에는 해수욕장이 1건뿐이다 (앞 조사 1.4). 틀린 지도 위의 값이다
 *  · tagCount 등       — OSM 태그가 얼마나 촘촘한가 (= 사람이 이 동네를 얼마나 그렸나)
 *
 * 산출: data/features.json
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  loadFoodPois,
  loadTransit,
  loadTouristAnchors,
  buildGrid,
  neighbors,
  nearestDistance,
} from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));

const pois = loadFoodPois();
const { stops, subway } = loadTransit();
const anchors = loadTouristAnchors();
console.log(
  `후보 ${pois.length} · 대중교통 노드 ${stops.length} · 지하철 노드 ${subway.length} · 관광 기준점 ${anchors.length}`,
);

const gPoi = buildGrid(pois);
const gStop = buildGrid(stops);
const gSub = buildGrid(subway, 0.01);
const gAnchor = buildGrid(anchors, 0.01);

const TAGS_OF_INTEREST = [
  "name",
  "name:en",
  "cuisine",
  "opening_hours",
  "phone",
  "website",
  "wheelchair",
  "brand",
  "takeaway",
  "outdoor_seating",
  "addr:street",
  "internet_access",
  "smoking",
];

const rows = pois.map((p, idx) => {
  // 자기 자신은 빼고 센다
  const d150 = neighbors(gPoi, p.lat, p.lon, 150).length - 1;
  const d300 = neighbors(gPoi, p.lat, p.lon, 300).length - 1;
  const t = p.tags;
  const richness = TAGS_OF_INTEREST.filter((k) => t[k]).length;
  return {
    id: p.id,
    idx,
    kind: p.kind,
    lat: p.lat,
    lon: p.lon,
    name: t["name:ko"] ?? t.name ?? "",
    nameEn: t["name:en"] ?? "",
    addr: [t["addr:province"], t["addr:city"], t["addr:district"], t["addr:street"]]
      .filter(Boolean)
      .join(" "),
    dens150: Math.max(0, d150),
    dens300: Math.max(0, d300),
    transitM: Math.round(nearestDistance(gStop, p.lat, p.lon, 500)),
    subwayM: Math.round(nearestDistance(gSub, p.lat, p.lon, 1000)),
    touristM: Math.round(nearestDistance(gAnchor, p.lat, p.lon, 1000)),
    tagRichness: richness,
    hasCuisine: Boolean(t.cuisine),
    hasOpeningHours: Boolean(t.opening_hours),
    hasWebsite: Boolean(t.website),
    hasPhone: Boolean(t.phone),
    hasBrand: Boolean(t.brand),
    hasWheelchair: Boolean(t.wheelchair),
  };
});

fs.writeFileSync(
  path.join(HERE, "data", "features.json"),
  JSON.stringify({ builtAt: new Date().toISOString(), count: rows.length, rows }),
);

const med = (a) => {
  const s = [...a].sort((x, y) => x - y);
  return s[Math.floor(s.length / 2)];
};
console.log(
  `dens150 중앙값 ${med(rows.map((r) => r.dens150))} · transitM 중앙값 ${med(rows.map((r) => r.transitM))} · ` +
    `subwayM 중앙값 ${med(rows.map((r) => r.subwayM))} · touristM 중앙값 ${med(rows.map((r) => r.touristM))}`,
);
