/**
 * 가까운 지하철역 — S15P21E201-1830.
 *
 * 주변 버스 화면이 서버의 버스 정류장만 보여줘서, 역 바로 앞에 서 있어도 「지하철」이라는 말이 없었다.
 * 서버에는 역을 찾는 API 가 없다. 대신 백엔드 길찾기가 쓰는 부산교통공사 역 좌표가 저장소에 있어서
 * (`busanSubwayStations.json` 머리의 source) 그것을 앱에 싣고 여기서 거리를 잰다 — 역은 거의 안 바뀐다.
 *
 * 🔴 1~4호선만 있다. 동해선·부산김해경전철은 원천에 없다. 없는 역을 「근처에 역이 없어요」라고
 *    단정하지 않도록, 화면은 찾은 역만 보여주고 없으면 줄을 안 그린다.
 *
 * 🔴 걷는 시간은 **어림**이다. 직선거리에 길이 돌아가는 몫(1.3배)을 곱하고 분당 70m 로 나눈다.
 *    실제 길로 잰 값이 아니므로 화면은 「약」을 붙인다.
 */
import raw from './busanSubwayStations.json';
import officialNames from './busanSubwayStationNames.json';

export type SubwayStation = { name: string; lines: number[]; latitude: number; longitude: number };
export type NearbyStation = SubwayStation & { distanceM: number; walkMin: number };
type LatLng = { latitude: number; longitude: number };

const STATIONS: SubwayStation[] = (raw.stations as Array<[string, string, number, number]>).map(([name, lines, latitude, longitude]) => ({
  name,
  lines: lines.split('').map(Number),
  latitude,
  longitude,
}));

/** 두 점 사이 직선거리(m). */
export function straightDistanceM(a: LatLng, b: LatLng): number {
  const rad = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * rad;
  const dLng = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 직선거리로 어림한 걷는 시간(분). 1분보다 짧아도 1분이다 — 「0분」은 사람이 쓰는 말이 아니다. */
export function walkMinutes(distanceM: number): number {
  return Math.max(1, Math.round((distanceM * 1.3) / 70));
}

/** 거리 글자 — 1km 아래는 10m 단위, 그 위는 소수 한 자리 km. */
/**
 * 부산교통공사가 정한 영문 역명 — 「시청」 → 「City Hall」, 「부산」 → 「Busan Station」(S15P21E201-1874).
 * 🔴 읽는 법(「Sicheong」)은 역 안내판·안내방송과 달라서 외국인이 역을 못 찾는다. 공식 표기가 있으면 그것을 쓴다.
 *    한자 역명 칸은 원천에 빠지거나 틀린 값이 섞여 있어 쓰지 않는다(busanSubwayStationNames.json 머리).
 */
export function stationEnglishName(name: string): string | null {
  return (officialNames.names as Record<string, string>)[name] ?? null;
}

/** 외국어 화면의 역 이름 한 줄 — 공식 영문 역명 + 「Station」(이미 붙어 있으면 그대로). 공식 이름이 없으면 null. */
export function stationEnglishTitle(name: string): string | null {
  const english = stationEnglishName(name);
  if (!english) return null;
  return / Station$/.test(english) ? english : `${english} Station`;
}

export function distanceText(distanceM: number): string {
  if (distanceM < 1000) return `${Math.max(10, Math.round(distanceM / 10) * 10)}m`;
  return `${(distanceM / 1000).toFixed(1)}km`;
}

/** `at` 에서 `radiusM` 안의 역을 가까운 순으로 `limit` 개. */
export function nearbyStations(at: LatLng, radiusM = 1500, limit = 3): NearbyStation[] {
  return STATIONS
    .map((station) => {
      const distanceM = straightDistanceM(at, station);
      return { ...station, distanceM, walkMin: walkMinutes(distanceM) };
    })
    .filter((station) => station.distanceM <= radiusM)
    .sort((a, b) => a.distanceM - b.distanceM)
    .slice(0, limit);
}

/** 모든 역 수 — 시험용. */
export const STATION_COUNT = STATIONS.length;
