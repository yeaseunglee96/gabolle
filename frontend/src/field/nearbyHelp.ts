/**
 * 가까운 도움 — 병원·약국·경찰의 위치(UI 캔버스 ㉒-4, S15P21E201-1889).
 *
 * 긴급 도움 화면은 번호를 누르면 바로 전화가 걸리지만, 다치거나 아플 때 여행자가 먼저 궁금한 것은 「가까운 곳이
 * 어디냐」다. 서버에는 이 자료가 없어서(장소 적재기는 여행 후보만 받는다) 부산 OSM 추출본에서 뽑아 앱에 싣는다
 * — 지하철 출구(busanSubwayExits.json)와 같은 방식이다.
 *
 * 🔴 모든 곳이 있지 않다. 특히 약국은 실제보다 훨씬 적다(부산 OSM 에 70곳). 그래서 화면은 «가장 가까운 곳»이라고
 *    말하지 않고 「지도 자료에 있는 가까운 곳」이라고 말하며, 카카오맵 검색으로 더 찾게 한다.
 *    채울 길: 건강보험심사평가원 「전국 병의원 및 약국 현황」(좌표 있음) 적재 — 그 전엔 이 한계를 화면에 적는다.
 */
import data from './nearbyHelp.json';
import { straightDistanceM } from './subwayStations';

type LatLng = { latitude: number; longitude: number };

export type HelpKind = 'hospital' | 'pharmacy' | 'police';
export const HELP_KINDS: readonly HelpKind[] = ['hospital', 'pharmacy', 'police'];

export type HelpPlace = {
  kind: HelpKind;
  latitude: number;
  longitude: number;
  /** 한국어 이름 — 간판·기사님께 보일 글자 */
  name: string;
  /** OSM 영어 이름. 없으면 null */
  nameEn: string | null;
  phone: string | null;
  /** OSM 운영시간 원문(「Mo-Fr 09:00-18:00」). 읽는 법이 복잡해 풀지 않고 있다는 것만 쓴다 */
  hours: string | null;
  emergency: boolean;
};

export type NearbyHelp = HelpPlace & { distanceM: number };

type Row = [number, number, number, string, string, string, string, number];

const PLACES: HelpPlace[] = (data.places as Row[]).map(([kind, latitude, longitude, name, en, phone, hours, er]) => ({
  kind: HELP_KINDS[kind],
  latitude,
  longitude,
  name,
  nameEn: en || null,
  phone: phone || null,
  hours: hours || null,
  emergency: er === 1,
}));

/** 부산 밖이거나 자료가 멀면 없다고 말하는 게 낫다 — 20km 밖의 「가까운 약국」은 도움이 아니다. */
export const NEARBY_LIMIT_M = 5000;

/** 한 갈래에서 가까운 곳부터. 반경 밖은 뺀다. */
export function nearestHelp(kind: HelpKind, at: LatLng, limit = 5, radiusM = NEARBY_LIMIT_M): NearbyHelp[] {
  return PLACES
    .filter((place) => place.kind === kind)
    .map((place) => ({ ...place, distanceM: straightDistanceM(at, { latitude: place.latitude, longitude: place.longitude }) }))
    .filter((place) => place.distanceM <= radiusM)
    .sort((a, b) => a.distanceM - b.distanceM)
    .slice(0, limit);
}

/** 전화 걸기 주소 — 숫자와 +만 남긴다. */
export function telOf(phone: string): string {
  return `tel:${phone.replace(/[^\d+]/g, '')}`;
}

/**
 * 카카오맵에서 이 갈래를 찾는 주소(검색 결과 링크 /link/search/검색어) — 우리 자료에 없는 곳까지 본다.
 * 폰에 카카오맵이 있으면 그 앱이, 없으면 웹 지도가 열린다. 어디 근처를 찾을지는 카카오맵이 정한다.
 */
export function kakaoSearchUrl(kind: HelpKind): string {
  const query = kind === 'hospital' ? '병원' : kind === 'pharmacy' ? '약국' : '경찰서';
  return `https://map.kakao.com/link/search/${encodeURIComponent(query)}`;
}

export const HELP_SOURCE_COUNTS = data.counts;
