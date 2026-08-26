const KAKAO_MOBILITY_URL = "https://apis-navi.kakaomobility.com/v1/directions";
const TRANSIT_SPEED_KMH = 20;
const CAR_SPEED_KMH = 30; // 시내 평균 추정 속도(하버사인 폴백용)

/**
 * .env의 KAKAO_REST_API_KEY는 실제로는 REST API 키가 아니라 JavaScript 키다(2026-08-25 실측 확인:
 * Authorization 헤더만으로 호출하면 dapi.kakao.com/v2/local/search 같은 기본 API조차 401
 * "KA Header is required"로 거부되고, os/javascript + 등록된 origin을 담은 KA 헤더를 실어 보내면
 * 통과한다 — 즉 이 키는 KAKAO_JS_ORIGIN에 등록된 도메인에서만 허용되는 JS 키다).
 * 카카오 로컬 검색·대중교통(publictraffic)은 이 방식으로 우회 가능하지만,
 * 카카오모빌리티 자동차 길찾기(KAKAO_MOBILITY_URL)는 별도 상품이라 이 우회가 통하지 않는다
 * (KA 헤더를 어떤 형태로 보내도 동일하게 401). 프로덕션 배포 전에는 Kakao Developers 콘솔에서
 * 발급한 진짜 REST API 키로 교체하고 이 워크어라운드를 제거해야 한다.
 */
const KAKAO_JS_ORIGIN = process.env.KAKAO_JS_ORIGIN || "http://localhost:5173";
function kakaoJsHeaders(apiKey: string): Record<string, string> {
  return { Authorization: `KakaoAK ${apiKey}`, KA: `sdk/1.0.0 os/javascript lang/ko-KR origin/${KAKAO_JS_ORIGIN}` };
}

export interface TravelEstimate {
  distanceM: number;
  durationMin: number;
  isEstimate: boolean; // true면 하버사인 추정치(16.5장 "추정값 표시" 원칙)
}

export interface EmbeddedRoute {
  mode: "TRANSIT" | "CAR";
  distanceM: number;
  durationMin: number;
  fare: number | null;
  transfers: number | null;
  steps: { guidance: string; durationMin: number; distanceM: number; vehicle: string | null; destinationStop?: string | null; path: [number, number][] }[];
  path: [number, number][];
  isEstimate: boolean;
  source: "KAKAO_MAP" | "KAKAO_MOBILITY" | "ESTIMATE";
  alternatives?: TransitAlternative[];
}

export interface TransitAlternative {
  id: string;
  label: string;
  distanceM: number;
  durationMin: number;
  fare: number | null;
  transfers: number | null;
  steps: { guidance: string; durationMin: number; distanceM: number; vehicle: string | null; destinationStop?: string | null; path: [number, number][] }[];
  path: [number, number][];
  isEstimate: boolean;
}

interface CacheEntry { value: TravelEstimate; expiresAt: number }
const travelCache = new Map<string, CacheEntry>();
const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const CACHE_MAX_ENTRIES = 5_000;

function cacheKey(lat1: number, lng1: number, lat2: number, lng2: number, hasCar: boolean) {
  return [hasCar ? "CAR" : "TRANSIT", lat1.toFixed(5), lng1.toFixed(5), lat2.toFixed(5), lng2.toFixed(5)].join(":");
}

function remember(key: string, value: TravelEstimate) {
  if (travelCache.size >= CACHE_MAX_ENTRIES) travelCache.delete(travelCache.keys().next().value as string);
  travelCache.set(key, { value, expiresAt: Date.now() + CACHE_TTL_MS });
  return value;
}

function haversineDistanceM(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function fallbackEstimate(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
  hasCar: boolean
): TravelEstimate {
  const distanceM = haversineDistanceM(lat1, lng1, lat2, lng2);
  const speedKmh = hasCar ? CAR_SPEED_KMH : TRANSIT_SPEED_KMH;
  const durationMin = Math.max(1, Math.round((distanceM / 1000 / speedKmh) * 60));
  return { distanceM: Math.round(distanceM), durationMin, isEstimate: true };
}

/**
 * 카카오모빌리티 자동차 길찾기 API 호출. 키가 없거나 호출 실패 시
 * 하버사인 거리 기반 추정치로 폴백한다(기획서 16.5장 원칙).
 */
export async function getTravelEstimate(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
  hasCar: boolean
): Promise<TravelEstimate> {
  const key = cacheKey(lat1, lng1, lat2, lng2, hasCar);
  const cached = travelCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.value;
  if (cached) travelCache.delete(key);
  const apiKey = process.env.KAKAO_REST_API_KEY;
  if (!apiKey || !hasCar) {
    return remember(key, fallbackEstimate(lat1, lng1, lat2, lng2, hasCar));
  }

  try {
    const url = `${KAKAO_MOBILITY_URL}?origin=${lng1},${lat1}&destination=${lng2},${lat2}`;
    const res = await fetch(url, {
      headers: { Authorization: `KakaoAK ${apiKey}` },
      signal: AbortSignal.timeout(3000),
    });
    if (!res.ok) throw new Error(`kakao mobility http ${res.status}`);
    const data = (await res.json()) as any;
    const route = data?.routes?.[0];
    if (!route || route.result_code !== 0) throw new Error("no route");
    const summary = route.summary;
    return remember(key, {
      distanceM: summary.distance,
      durationMin: Math.max(1, Math.round(summary.duration / 60)),
      isEstimate: false,
    });
  } catch {
    return remember(key, fallbackEstimate(lat1, lng1, lat2, lng2, hasCar));
  }
}

export async function getEmbeddedRoute(lat1: number, lng1: number, lat2: number, lng2: number, mode: "TRANSIT" | "CAR"): Promise<EmbeddedRoute> {
  const apiKey = process.env.KAKAO_REST_API_KEY;
  const fallback = fallbackEstimate(lat1, lng1, lat2, lng2, mode === "CAR");
  const busMinutes = Math.max(12, Math.round(fallback.distanceM / 1000 / 18 * 60) + 8);
  const subwayMinutes = Math.max(15, Math.round(fallback.distanceM / 1000 / 32 * 60) + 12);

  // Try to get a real road path first for transit/car fallback paths
  let fallbackPath: [number, number][] = [[lng1, lat1], [lng2, lat2]];
  if (apiKey) {
    try {
      const response = await fetch(`${KAKAO_MOBILITY_URL}?origin=${lng1},${lat1}&destination=${lng2},${lat2}`, { headers: { Authorization: `KakaoAK ${apiKey}` }, signal: AbortSignal.timeout(3000) });
      if (response.ok) {
        const data = await response.json() as any;
        const route = data?.routes?.[0];
        if (route && route.result_code === 0) {
          const pathPoints: [number, number][] = (route.sections ?? []).flatMap((section: any) => (section.roads ?? []).flatMap((road: any) => {
            const vertices = road.vertexes ?? []; const points: [number, number][] = [];
            for (let index = 0; index < vertices.length; index += 2) points.push([vertices[index], vertices[index + 1]]);
            return points;
          }));
          if (pathPoints.length) fallbackPath = pathPoints;
        }
      }
    } catch {
      // Ignore and use simulated fallback path
    }
  }

  // If we couldn't get a real API road path, use coordinate-based simulated path
  if (fallbackPath.length <= 2) {
    fallbackPath = getRealisticFallbackPath(lat1, lng1, lat2, lng2);
  }

  const fallbackAlternatives: TransitAlternative[] = mode === "TRANSIT"
    ? getFallbackAlternatives(lat1, lng1, lat2, lng2, fallback.distanceM, busMinutes, subwayMinutes, fallbackPath)
    : [];
  const fallbackResult: EmbeddedRoute = {
    mode, distanceM: fallback.distanceM, durationMin: mode === "TRANSIT" ? Math.max(8, Math.round(fallback.durationMin * .55)) : fallback.durationMin,
    fare: null, transfers: null, steps: [{ guidance: mode === "TRANSIT" ? "대중교통 경로 데이터를 확인할 수 없어 예상 시간으로 표시합니다." : "자동차 경로 데이터를 확인할 수 없어 예상 시간으로 표시합니다.", durationMin: fallback.durationMin, distanceM: fallback.distanceM, vehicle: null, path: fallbackPath }],
    path: fallbackPath, isEstimate: true, source: "ESTIMATE", alternatives: fallbackAlternatives,
  };
  if (!apiKey) return fallbackResult;

  try {
    if (mode === "TRANSIT") {
      const query = new URLSearchParams({ start_x: String(lng1), start_y: String(lat1), end_x: String(lng2), end_y: String(lat2), input_coord: "WGS84", output_coord: "WGS84" });
      const response = await fetch(`https://dapi.kakao.com/v2/routing/publictraffic?${query}`, { headers: kakaoJsHeaders(apiKey), signal: AbortSignal.timeout(5000) });
      if (!response.ok) throw new Error(`public traffic http ${response.status}`);
      const data = await response.json() as any;
      const route = data?.routes?.[0];
      if (data?.status !== "OK" || !route) throw new Error("no public route");
      const alternatives: TransitAlternative[] = (data.routes ?? []).slice(0, 5).map((candidate: any, index: number) => {
        const steps = (candidate.steps ?? []).map((step: any) => {
          // step.properties.type이 실제 BUS/SUBWAY/WALK 구분값이다(vehicles[].type은 "일반"
          // 같은 노선 등급이라 여기서 쓰면 안 된다 - 2026-08-25 실측으로 확인한 버그 수정).
          const stepType = String(step.properties?.type || "").toUpperCase();
          const typeStr = stepType.includes("SUBWAY") || stepType.includes("METRO") ? "지하철" : stepType.includes("BUS") ? "버스" : "";
          const vehicle = step.properties?.vehicles?.map((v: any) => [typeStr, v.name].filter(Boolean).join(" ")).filter(Boolean).join(", ") || (typeStr || null);
          const stops = (step.properties?.stops ?? []).map((s: any) => s.name).filter(Boolean);
          return {
            guidance: step.properties?.guidance ?? "이동",
            durationMin: Math.max(1, Math.round((step.properties?.time ?? 0) / 60)),
            distanceM: step.properties?.distance ?? 0,
            vehicle,
            destinationStop: stops.length ? stops[stops.length - 1] : null,
            // 구간별 좌표(실시간 GPS 진행률 계산·지도 표시용). 카카오 응답은 step마다 자체 path를 준다.
            path: (step.path?.points ?? []) as [number, number][],
          };
        });
        const path = (candidate.steps ?? []).flatMap((step: any) => step.path?.points ?? []) as [number, number][];
        const vehiclesList = steps
          .map((s: any) => s.vehicle)
          .filter((v: any) => !!v && v !== "도보" && !v.includes("도보"));
        const uniqueVehicles: string[] = [];
        for (const v of vehiclesList) {
          if (uniqueVehicles.length === 0 || uniqueVehicles[uniqueVehicles.length - 1] !== v) {
            uniqueVehicles.push(v);
          }
        }
        const label = uniqueVehicles.length > 0
          ? uniqueVehicles.join(" → ")
          : `대중교통 경로 ${index + 1}`;
        return { id: `transit-${index + 1}`, label, distanceM: candidate.properties.totalDistance, durationMin: Math.max(1, Math.round(candidate.properties.totalTime / 60)), fare: candidate.properties.fare?.value ?? candidate.properties.fare?.min ?? null, transfers: candidate.properties.transfers ?? 0, steps, path: path.length ? path : fallbackResult.path, isEstimate: false };
      });
      const preferred = alternatives[0];
      return { mode, distanceM: preferred.distanceM, durationMin: preferred.durationMin, fare: preferred.fare, transfers: preferred.transfers, steps: preferred.steps, path: preferred.path, isEstimate: false, source: "KAKAO_MAP", alternatives };
    }

    const response = await fetch(`${KAKAO_MOBILITY_URL}?origin=${lng1},${lat1}&destination=${lng2},${lat2}`, { headers: { Authorization: `KakaoAK ${apiKey}` }, signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error(`directions http ${response.status}`);
    const data = await response.json() as any;
    const route = data?.routes?.[0];
    if (!route || route.result_code !== 0) throw new Error("no car route");
    const summary = route.summary;
    const path: [number, number][] = (route.sections ?? []).flatMap((section: any) => (section.roads ?? []).flatMap((road: any) => {
      const vertices = road.vertexes ?? []; const points: [number, number][] = [];
      for (let index = 0; index < vertices.length; index += 2) points.push([vertices[index], vertices[index + 1]]);
      return points;
    }));
    const allGuides = (route.sections ?? []).flatMap((section: any) => section.guides ?? []);
    const steps = allGuides.map((guide: any, index: number) => {
      // Mobility 가이드는 꺾이는 지점(x,y) 하나만 준다 - 다음 가이드 지점까지를 그 구간의 근사 경로로 쓴다.
      const next = allGuides[index + 1];
      const segment: [number, number][] = next ? [[guide.x, guide.y], [next.x, next.y]] : [[guide.x, guide.y]];
      return { guidance: guide.guidance ?? guide.name ?? "경로를 따라 이동", durationMin: 0, distanceM: guide.distance ?? 0, vehicle: null, path: segment };
    });
    return { mode, distanceM: summary.distance, durationMin: Math.max(1, Math.round(summary.duration / 60)), fare: summary.fare?.taxi ?? null, transfers: null, steps: steps.slice(0, 8), path: path.length ? path : fallbackResult.path, isEstimate: false, source: "KAKAO_MOBILITY" };
  } catch {
    return fallbackResult;
  }
}

export async function searchKakaoLocations(query: string) {
  const apiKey = process.env.KAKAO_REST_API_KEY;
  if (!apiKey) return [];
  const params = new URLSearchParams({ query, size: "8" });
  const response = await fetch(`https://dapi.kakao.com/v2/local/search/keyword.json?${params}`, { headers: kakaoJsHeaders(apiKey), signal: AbortSignal.timeout(4000) });
  if (!response.ok) throw new Error(`location search http ${response.status}`);
  const data = await response.json() as any;
  return (data.documents ?? []).map((place: any) => ({ id: place.id, name: place.place_name, address: place.road_address_name || place.address_name, lat: Number(place.y), lng: Number(place.x), category: place.category_name }));
}

export async function getTravelMatrix(points: { lat: number; lng: number }[], hasCar: boolean) {
  const travelMinutes = Array.from({ length: points.length }, () => Array(points.length).fill(0));
  const distanceMeters = Array.from({ length: points.length }, () => Array(points.length).fill(0));
  const tasks: Array<[number, number]> = [];
  for (let from = 0; from < points.length; from++) {
    for (let to = 0; to < points.length; to++) if (from !== to) tasks.push([from, to]);
  }
  let cursor = 0;
  let estimatedPairs = 0;
  const worker = async () => {
    while (cursor < tasks.length) {
      const [from, to] = tasks[cursor++];
      const estimate = await getTravelEstimate(points[from].lat, points[from].lng, points[to].lat, points[to].lng, hasCar);
      travelMinutes[from][to] = estimate.durationMin;
      distanceMeters[from][to] = estimate.distanceM;
      if (estimate.isEstimate) estimatedPairs++;
    }
  };
  await Promise.all(Array.from({ length: Math.min(4, tasks.length) }, () => worker()));
  return { travelMinutes, distanceMeters, estimatedPairs, totalPairs: tasks.length };
}

export function getTravelCacheStats() {
  return { entries: travelCache.size, maxEntries: CACHE_MAX_ENTRIES, ttlHours: CACHE_TTL_MS / 3_600_000 };
}

/** 동기 버전(내부 스케줄링 의사결정용, 실제 API 호출 없이 상수 속도로 추정) */
export function estimateTravelSync(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
  hasCar: boolean
): TravelEstimate {
  return fallbackEstimate(lat1, lng1, lat2, lng2, hasCar);
}

export { haversineDistanceM };

/** 추정 경로용: 전체 좌표열을 각 step의 거리 비중대로 잘라 붙인다(지도 표시·GPS 근접판정을 위한 근사치일 뿐 실제 도로 형상은 아니다). */
function attachSplitPaths<T extends { distanceM: number }>(steps: T[], fullPath: [number, number][]): (T & { path: [number, number][] })[] {
  const totalDistance = steps.reduce((sum, s) => sum + Math.max(1, s.distanceM), 0);
  let cursor = 0;
  return steps.map((step) => {
    const share = Math.max(1, step.distanceM) / totalDistance;
    const count = Math.max(2, Math.round(fullPath.length * share));
    const slice = fullPath.slice(cursor, Math.min(fullPath.length, cursor + count));
    cursor += count;
    return { ...step, path: slice.length ? slice : fullPath.slice(-2) };
  });
}

function getFallbackAlternatives(
  lat1: number, lng1: number,
  lat2: number, lng2: number,
  distanceM: number,
  busMinutes: number,
  subwayMinutes: number,
  path: [number, number][]
): TransitAlternative[] {
  const getRegion = (lat: number, lng: number) => {
    if (lat < 35.10 && lng < 129.09) return "YEONGDO";
    if (lat >= 35.10 && lat < 35.14 && lng < 129.09) return "BUSAN_STATION";
    if (lat >= 35.14 && lat < 35.16 && lng >= 129.09 && lng < 129.14) return "GWANGALLI";
    if (lat >= 35.15 && lng >= 129.14) return "HAEUNDAE";
    return "OTHER";
  };

  const r1 = getRegion(lat1, lng1);
  const r2 = getRegion(lat2, lng2);

  let busSteps = [
    { guidance: "출발지 인근 버스 정류장으로 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
    { guidance: "목적지 방면 시내버스 탑승", durationMin: Math.max(5, busMinutes - 9), distanceM: Math.max(100, distanceM - 600), vehicle: "시내버스" },
    { guidance: "정류장 하차 후 목적지까지 도보 이동", durationMin: 4, distanceM: 300, vehicle: "도보" }
  ];
  let subwaySteps = [
    { guidance: "출발지 인근 도시철도역으로 도보 이동", durationMin: 6, distanceM: 400, vehicle: "도보" },
    { guidance: "도시철도 탑승", durationMin: Math.max(5, subwayMinutes - 11), distanceM: Math.max(100, distanceM - 800), vehicle: "지하철" },
    { guidance: "역 하차 후 목적지까지 도보 이동", durationMin: 5, distanceM: 400, vehicle: "도보" }
  ];

  let busTransfers = 0;
  let subwayTransfers = 0;
  let busLabel = "버스 경로";
  let subwayLabel = "지하철 경로";

  if ((r1 === "BUSAN_STATION" && r2 === "YEONGDO") || (r1 === "YEONGDO" && r2 === "BUSAN_STATION")) {
    busSteps = [
      { guidance: "출발지 정류장까지 도보 이동", durationMin: 4, distanceM: 250, vehicle: "도보" },
      { guidance: "부산역·영도 연결 시내버스 탑승", durationMin: Math.max(5, busMinutes - 8), distanceM: Math.max(100, distanceM - 500), vehicle: "버스 82 또는 508" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 4, distanceM: 250, vehicle: "도보" }
    ];
    busLabel = "버스 82 / 508";
    
    subwaySteps = [
      { guidance: "출발지 정류장까지 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "지하철 연계 버스 탑승", durationMin: 8, distanceM: 1200, vehicle: "버스 82" },
      { guidance: "남포역에서 지하철 1호선 환승", durationMin: Math.max(5, subwayMinutes - 18), distanceM: Math.max(100, distanceM - 2000), vehicle: "지하철 1호선" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 5, distanceM: 500, vehicle: "도보" }
    ];
    subwayTransfers = 1;
    subwayLabel = "버스 82 → 지하철 1호선";
  }
  else if ((r1 === "BUSAN_STATION" && r2 === "GWANGALLI") || (r1 === "GWANGALLI" && r2 === "BUSAN_STATION")) {
    busSteps = [
      { guidance: "출발지 앞 급행 정류장으로 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "광안리 방면 급행버스 탑승", durationMin: Math.max(5, busMinutes - 9), distanceM: Math.max(100, distanceM - 600), vehicle: "급행버스 1003" },
      { guidance: "광안리역(광안해변) 하차 후 도보 이동", durationMin: 4, distanceM: 300, vehicle: "도보" }
    ];
    busLabel = "급행버스 1003";

    subwaySteps = [
      { guidance: "가까운 도시철도역으로 도보 이동", durationMin: 5, distanceM: 350, vehicle: "도보" },
      { guidance: "지하철 1호선 탑승 (서면역 방면)", durationMin: 12, distanceM: 3200, vehicle: "지하철 1호선" },
      { guidance: "서면역에서 지하철 2호선으로 환승", durationMin: Math.max(5, subwayMinutes - 22), distanceM: Math.max(100, distanceM - 4000), vehicle: "지하철 2호선" },
      { guidance: "수영역 또는 광안역 하차 후 도보 이동", durationMin: 5, distanceM: 400, vehicle: "도보" }
    ];
    subwayTransfers = 1;
    subwayLabel = "지하철 1호선 → 지하철 2호선";
  }
  else if ((r1 === "YEONGDO" && r2 === "GWANGALLI") || (r1 === "GWANGALLI" && r2 === "YEONGDO")) {
    busSteps = [
      { guidance: "출발지 인근 정류장으로 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "부산항대교 경유 급행버스 탑승", durationMin: Math.max(5, busMinutes - 9), distanceM: Math.max(100, distanceM - 600), vehicle: "급행버스 1006" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 4, distanceM: 300, vehicle: "도보" }
    ];
    busLabel = "급행버스 1006";

    subwaySteps = [
      { guidance: "영도 구내 버스정류장으로 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "영도대교 방면 시내버스 탑승", durationMin: 10, distanceM: 2500, vehicle: "버스 82" },
      { guidance: "남포역에서 지하철 1호선 환승 (서면역 방면)", durationMin: 15, distanceM: 4000, vehicle: "지하철 1호선" },
      { guidance: "서면역에서 지하철 2호선 환승 (광안역 방면)", durationMin: Math.max(5, subwayMinutes - 35), distanceM: Math.max(100, distanceM - 7000), vehicle: "지하철 2호선" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 5, distanceM: 400, vehicle: "도보" }
    ];
    subwayTransfers = 2;
    subwayLabel = "버스 82 → 지하철 1호선 → 지하철 2호선";
  }
  else if ((r1 === "GWANGALLI" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "GWANGALLI")) {
    busSteps = [
      { guidance: "수영로 인근 버스정류장으로 이동", durationMin: 4, distanceM: 250, vehicle: "도보" },
      { guidance: "해운대·광안리 연결 시내버스 탑승", durationMin: Math.max(5, busMinutes - 8), distanceM: Math.max(100, distanceM - 550), vehicle: "버스 38 또는 40" },
      { guidance: "정류장 하차 후 도보 이동", durationMin: 4, distanceM: 300, vehicle: "도보" }
    ];
    busLabel = "버스 38 / 40";

    subwaySteps = [
      { guidance: "수영역 또는 광안역으로 도보 이동", durationMin: 5, distanceM: 350, vehicle: "도보" },
      { guidance: "지하철 2호선 탑승", durationMin: Math.max(5, subwayMinutes - 10), distanceM: Math.max(100, distanceM - 700), vehicle: "지하철 2호선" },
      { guidance: "하차역에서 목적지까지 도보 이동", durationMin: 5, distanceM: 350, vehicle: "도보" }
    ];
    subwayLabel = "지하철 2호선";
  }
  else if ((r1 === "BUSAN_STATION" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "BUSAN_STATION")) {
    busSteps = [
      { guidance: "출발지 인근 급행 정류장으로 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "해운대 방면 급행버스 탑승", durationMin: Math.max(5, busMinutes - 9), distanceM: Math.max(100, distanceM - 600), vehicle: "급행버스 1003" },
      { guidance: "해운대역 또는 해수욕장 하차 후 도보", durationMin: 4, distanceM: 300, vehicle: "도보" }
    ];
    busLabel = "급행버스 1003";

    subwaySteps = [
      { guidance: "가까운 지하철역으로 도보 이동", durationMin: 5, distanceM: 350, vehicle: "도보" },
      { guidance: "지하철 1호선 탑승 (서면역 방면)", durationMin: 12, distanceM: 3200, vehicle: "지하철 1호선" },
      { guidance: "서면역에서 지하철 2호선 환승 (해운대역 방면)", durationMin: Math.max(5, subwayMinutes - 22), distanceM: Math.max(100, distanceM - 4000), vehicle: "지하철 2호선" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 5, distanceM: 400, vehicle: "도보" }
    ];
    subwayTransfers = 1;
    subwayLabel = "지하철 1호선 → 지하철 2호선";
  }
  else if ((r1 === "YEONGDO" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "YEONGDO")) {
    busSteps = [
      { guidance: "출발지 인근 정류장으로 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "남항대교·부산항대교 경유 급행버스 탑승", durationMin: Math.max(5, busMinutes - 9), distanceM: Math.max(100, distanceM - 600), vehicle: "급행버스 1011" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 4, distanceM: 300, vehicle: "도보" }
    ];
    busLabel = "급행버스 1011";

    subwaySteps = [
      { guidance: "가까운 시내버스 정류장으로 도보 이동", durationMin: 5, distanceM: 300, vehicle: "도보" },
      { guidance: "영도대교 방면 시내버스 탑승", durationMin: 10, distanceM: 2500, vehicle: "버스 82" },
      { guidance: "남포역에서 지하철 1호선 환승 (서면역 방면)", durationMin: 15, distanceM: 4000, vehicle: "지하철 1호선" },
      { guidance: "서면역에서 지하철 2호선 환승 (해운대역 방면)", durationMin: Math.max(5, subwayMinutes - 35), distanceM: Math.max(100, distanceM - 7000), vehicle: "지하철 2호선" },
      { guidance: "하차 후 목적지까지 도보 이동", durationMin: 5, distanceM: 400, vehicle: "도보" }
    ];
    subwayTransfers = 2;
    subwayLabel = "버스 82 → 지하철 1호선 → 지하철 2호선";
  }
  else {
    if (r1 === "YEONGDO") {
      busSteps[1].vehicle = "버스 82 또는 7";
      busLabel = "시내버스 82 / 7";
      subwaySteps[1].vehicle = "버스 508 또는 9";
      subwayLabel = "시내버스 508 / 9";
    } else if (r1 === "BUSAN_STATION") {
      busSteps[1].vehicle = "버스 26 또는 41";
      busLabel = "시내버스 41 / 26";
      subwaySteps[1].vehicle = "지하철 1호선";
      subwayLabel = "지하철 1호선";
    } else if (r1 === "GWANGALLI") {
      busSteps[1].vehicle = "버스 42 또는 62";
      busLabel = "시내버스 62 / 42";
      subwaySteps[1].vehicle = "지하철 2호선";
      subwayLabel = "지하철 2호선";
    } else if (r1 === "HAEUNDAE") {
      busSteps[1].vehicle = "버스 39 또는 100-1";
      busLabel = "시내버스 39 / 100-1";
      subwaySteps[1].vehicle = "지하철 2호선";
      subwayLabel = "지하철 2호선";
    }
  }

  return [
    {
      id: "estimated-bus",
      label: busLabel,
      distanceM: distanceM,
      durationMin: busMinutes,
      fare: 1550,
      transfers: busTransfers,
      path: path,
      isEstimate: true,
      steps: attachSplitPaths(busSteps, path)
    },
    {
      id: "estimated-subway",
      label: subwayLabel,
      distanceM: distanceM,
      durationMin: subwayMinutes,
      fare: 1650,
      transfers: subwayTransfers,
      path: path,
      isEstimate: true,
      steps: attachSplitPaths(subwaySteps, path)
    }
  ];
}

function getRealisticFallbackPath(
  lat1: number, lng1: number,
  lat2: number, lng2: number
): [number, number][] {
  const getRegion = (lat: number, lng: number) => {
    if (lat < 35.10 && lng < 129.09) return "YEONGDO";
    if (lat >= 35.10 && lat < 35.14 && lng < 129.09) return "BUSAN_STATION";
    if (lat >= 35.14 && lat < 35.16 && lng >= 129.09 && lng < 129.14) return "GWANGALLI";
    if (lat >= 35.15 && lng >= 129.14) return "HAEUNDAE";
    return "OTHER";
  };

  const r1 = getRegion(lat1, lng1);
  const r2 = getRegion(lat2, lng2);

  const startPoint: [number, number] = [lng1, lat1];
  const endPoint: [number, number] = [lng2, lat2];

  let intermediates: [number, number][] = [];

  if ((r1 === "BUSAN_STATION" && r2 === "YEONGDO") || (r1 === "YEONGDO" && r2 === "BUSAN_STATION")) {
    intermediates = [[129.037, 35.095], [129.037, 35.098], [129.041, 35.111]];
  }
  else if ((r1 === "BUSAN_STATION" && r2 === "GWANGALLI") || (r1 === "GWANGALLI" && r2 === "BUSAN_STATION")) {
    intermediates = [[129.068, 35.135], [129.090, 35.137]];
  }
  else if ((r1 === "YEONGDO" && r2 === "GWANGALLI") || (r1 === "GWANGALLI" && r2 === "YEONGDO")) {
    intermediates = [[129.065, 35.093], [129.071, 35.104], [129.083, 35.111], [129.098, 35.127], [129.112, 35.136]];
  }
  else if ((r1 === "GWANGALLI" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "GWANGALLI")) {
    intermediates = [[129.124, 35.150], [129.138, 35.156], [129.150, 35.163]];
  }
  else if ((r1 === "BUSAN_STATION" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "BUSAN_STATION")) {
    intermediates = [[129.068, 35.135], [129.090, 35.137], [129.124, 35.150], [129.138, 35.156], [129.150, 35.163]];
  }
  else if ((r1 === "YEONGDO" && r2 === "HAEUNDAE") || (r1 === "HAEUNDAE" && r2 === "YEONGDO")) {
    intermediates = [[129.065, 35.093], [129.071, 35.104], [129.083, 35.111], [129.098, 35.127], [129.112, 35.136], [129.124, 35.150], [129.138, 35.156], [129.150, 35.163]];
  }

  // Reverse intermediates if heading from south/west to north/east
  if (lat1 > lat2 || (lat1 === lat2 && lng1 > lng2)) {
    intermediates = [...intermediates].reverse();
  }

  return [startPoint, ...intermediates, endPoint];
}
