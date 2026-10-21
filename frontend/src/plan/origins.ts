import { apiRequest } from '@/api/client';
import type { PlaceSearchItem } from '@/discovery/places';

export type OriginCandidate = {
  name: string;
  address: string;
  lat: number;
  lng: number;
  externalId: string;
  source: 'KAKAO_LOCAL' | 'INTERNAL_FALLBACK';
  /**
   * 우리 장소 목록에서 찾은 같은 장소의 영어 이름 — S15P21E201-1781. 카카오는 한국어 이름만 준다.
   * 화면에 그릴 때만 쓴다 — 서버로 보내는 이름(스냅샷·출발지 이름)은 언제나 `name`(한국어)이다.
   */
  nameEn?: string | null;
};

/**
 * 검색에서 고른 장소를 서버에 넘기는 모양 — 서버 `PlaceSnapshotRequest` 와 같다.
 *
 * 카카오·대체 목록 결과에는 우리 place_id 가 없다. 서버가 `(source, externalId)` 로 장소를 찾거나
 * 만들어 id 를 준다. 🔴 **기록(S15P21E201-1527)과 숙소(S15P21E201-1536)가 이 한 벌을 같이 쓴다** —
 * 서버도 한 벌이다. 두 벌로 두면 한쪽에만 칸이 늘어 「어떤 화면에서 고른 장소만 안 붙는」 결함이 된다.
 * 🔴 `source` 는 `OriginCandidate.source` 그대로다 — 바꾸면 이미 적재된 같은 장소와 다른 행이 된다.
 */
export type PlaceSnapshot = {
  source: OriginCandidate['source'];
  externalId: string;
  name: string;
  address?: string;
  lat: number;
  lng: number;
};

/** 후보를 스냅샷으로. 서버가 거절할 모양(이름·출처·식별자 없음, 좌표 한쪽만)이면 안 만든다 — 400 대신 안 싣는다. */
export function placeSnapshotOf(item: Pick<OriginCandidate, 'name' | 'address'> & Partial<OriginCandidate>): PlaceSnapshot | undefined {
  const name = (item.name ?? '').trim();
  if (!name || !item.source || !item.externalId) return undefined;
  if (!Number.isFinite(item.lat) || !Number.isFinite(item.lng)) return undefined;
  return { source: item.source, externalId: item.externalId, name, address: item.address || undefined, lat: item.lat as number, lng: item.lng as number };
}

type OriginSearchDto = {
  items: OriginCandidate[];
  degraded: boolean;
  degradedReason: string | null;
  limit: number;
};

export type OriginSearchResult =
  | { state: 'success'; items: OriginCandidate[]; degraded: boolean }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

// 서버가 두 글자 미만이면 QUERY_TOO_SHORT 로 거절한다(외부 호출 전에 막는 것이 완료 기준이다)
// 그 호출 자체를 안 나가게 화면에서 먼저 거른다.
export async function searchOrigins(query: string, accessToken: string | null, signal?: AbortSignal): Promise<OriginSearchResult> {
  try {
    const dto = await apiRequest<OriginSearchDto>(`/api/v1/origins?query=${encodeURIComponent(query)}&limit=8`, { accessToken, signal });
    return { state: 'success', items: dto.items, degraded: dto.degraded };
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') return { state: 'success', items: [], degraded: false };
    return { state: 'error', message: error instanceof Error ? error.message : '출발지를 검색하지 못했어요.' };
  }
}

/** 이 거리(미터) 안이면서 이름이 겹치면 같은 장소로 본다 — 카카오와 우리 좌표는 같은 원천이라 대개 몇 미터 안이다. */
const SAME_PLACE_METERS = 80;

function metersBetween(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

const squash = (text: string) => text.replace(/\s+/g, '');

/**
 * 카카오 결과에 우리 장소의 영어 이름을 붙인다 — S15P21E201-1781(고지혁 QA).
 *
 * 🔴 출발지·숙소는 카카오(한국어만)를, 꼭 갈 곳은 우리 장소 목록(name_en 있음)을 불러서 영어 화면에서
 *    한쪽만 영어였다. 같은 장소라고 볼 때만 붙인다 — 한국어 이름이 같거나, 가까우면서 한쪽 이름이 다른 쪽을 품을 때.
 *    못 찾으면 붙이지 않는다(영어 이름을 지어내지 않는다) — 화면이 읽는 법(로마자)을 대신 붙인다.
 */
export function attachEnglishNames(origins: OriginCandidate[], places: Pick<PlaceSearchItem, 'nameKo' | 'nameEn' | 'lat' | 'lng'>[]): OriginCandidate[] {
  const named = places.filter((place) => place.nameEn?.trim());
  if (!named.length) return origins;
  return origins.map((origin) => {
    if (origin.nameEn) return origin;
    const name = squash(origin.name);
    const match = named.find((place) => squash(place.nameKo) === name)
      ?? named.find((place) => {
        const other = squash(place.nameKo);
        const overlaps = name.includes(other) || other.includes(name);
        return overlaps && Number.isFinite(place.lat) && Number.isFinite(place.lng)
          && metersBetween(origin, { lat: place.lat as number, lng: place.lng as number }) <= SAME_PLACE_METERS;
      });
    return match ? { ...origin, nameEn: match.nameEn!.trim() } : origin;
  });
}

// 검색이 0건이거나 서버 연결 전에도 고를 수 있는 부산 주요 출발지. 실제 지명·공개 좌표다.
// 🔴 nameEn 은 화면용이다 — S15P21E201-1795(고지혁 QA). 비어 있으면 영어 화면에 「부산역 (Busanyeok)」처럼
//    읽는 법이 나왔다. 부산역·해운대해수욕장·광안리해수욕장은 우리 장소 목록(place.name_en)과 같은 철자다 —
//    검색해서 고른 것과 추천에서 고른 것이 다른 이름으로 보이지 않게. 서버로 가는 이름은 여전히 name(한국어)이다.
export const MAJOR_BUSAN_ORIGINS: OriginCandidate[] = [
  { name: '부산역', nameEn: 'Busan Station', address: '부산 동구 중앙대로 206', lat: 35.1152, lng: 129.0403, externalId: 'major-busan-station', source: 'INTERNAL_FALLBACK' },
  { name: '해운대해수욕장', nameEn: 'Haeundae Beach', address: '부산 해운대구 우동', lat: 35.1587, lng: 129.1604, externalId: 'major-haeundae', source: 'INTERNAL_FALLBACK' },
  { name: '서면역', nameEn: 'Seomyeon Station', address: '부산 부산진구 가야대로', lat: 35.1578, lng: 129.0592, externalId: 'major-seomyeon', source: 'INTERNAL_FALLBACK' },
  { name: '남포동', nameEn: 'Nampo-dong', address: '부산 중구 남포동', lat: 35.0980, lng: 129.0306, externalId: 'major-nampo', source: 'INTERNAL_FALLBACK' },
  { name: '광안리해수욕장', nameEn: 'Gwangalli Beach', address: '부산 수영구 광안동', lat: 35.1532, lng: 129.1187, externalId: 'major-gwangalli', source: 'INTERNAL_FALLBACK' },
  // 🔴 비행기·시외버스로 오는 사람의 첫 출발지(S15P21E201-1591). 좌표·주소를 지어내지 않았다 —
  //    앱의 출발지 검색(GET /api/v1/origins → 카카오 로컬)이 2026-09-24 에 돌려준 첫 결과 그대로다.
  //    카카오 장소 번호: 김해국제공항 국제선청사 8239831 · 부산종합버스터미널 12479254 · 부산서부버스터미널 18166577.
  { name: '김해공항', nameEn: 'Gimhae International Airport', address: '부산 강서구 대저2동 2350-1', lat: 35.172488, lng: 128.946785, externalId: 'major-gimhae-airport', source: 'INTERNAL_FALLBACK' },
  { name: '부산종합버스터미널(노포)', nameEn: 'Busan Central Bus Terminal (Nopo)', address: '부산 금정구 중앙대로 2238', lat: 35.284773, lng: 129.095472, externalId: 'major-nopo-terminal', source: 'INTERNAL_FALLBACK' },
  { name: '부산서부버스터미널(사상)', nameEn: 'Busan Seobu Bus Terminal (Sasang)', address: '부산 사상구 사상로 201', lat: 35.163239, lng: 128.982525, externalId: 'major-sasang-terminal', source: 'INTERNAL_FALLBACK' },
];

// 숙소 칸이 검색어 없이 보여 주는 추천 지역 — 시안 design_handoff_home_lodging. 출발지와
// 달리 특정 장소가 아니라 «동네»라서, 좌표는 그 동네를 대표하는 주요 지점을 그대로 쓴다
// (MAJOR_BUSAN_ORIGINS 의 해운대·서면·광안리·남포동과 같은 값).
// 🔴 address 칸의 설명 줄은 주소가 아니라 화면 문구다 — 다른 언어로는 PlanStartBar 의 lodgingAreaNote 가
//    번역표를 거쳐 그린다(S15P21E201-1795). 여기 문장을 바꾸면 그쪽 tx 원문과 번역표 줄도 같이 바꾼다.
export const RECOMMENDED_LODGING_AREAS: OriginCandidate[] = [
  { name: '해운대', nameEn: 'Haeundae', address: '바다 앞 호텔·리조트가 모여 있어요', lat: 35.1587, lng: 129.1604, externalId: 'lodging-haeundae', source: 'INTERNAL_FALLBACK' },
  { name: '서면', nameEn: 'Seomyeon', address: '교통 중심 · 어디든 가기 편해요', lat: 35.1578, lng: 129.0592, externalId: 'lodging-seomyeon', source: 'INTERNAL_FALLBACK' },
  { name: '광안리', nameEn: 'Gwangalli', address: '야경과 카페 골목', lat: 35.1532, lng: 129.1187, externalId: 'lodging-gwangalli', source: 'INTERNAL_FALLBACK' },
  { name: '남포동 · 중앙동', nameEn: 'Nampo-dong · Jungang-dong', address: '시장·원도심 도보 여행', lat: 35.0980, lng: 129.0306, externalId: 'lodging-nampo', source: 'INTERNAL_FALLBACK' },
];

/**
 * 위 두 목록의 일본어·중국어 이름 — S15P21E201-1923. 전에는 일본어·중국어 화면의 칩이 「Busan Station (부산역)」처럼
 * 영어였고, 3단계 지역 칸은 「海雲臺」라 같은 동네가 두 이름이었다. 열쇠는 목록의 한국어 name 그대로다.
 */
const KNOWN_PLACE_LOCAL_NAMES: Record<string, { ja: string; 'zh-Hans': string; 'zh-Hant': string }> = {
  '부산역': { ja: '釜山駅', 'zh-Hans': '釜山站', 'zh-Hant': '釜山站' },
  '해운대해수욕장': { ja: '海雲台海水浴場', 'zh-Hans': '海云台海水浴场', 'zh-Hant': '海雲臺海水浴場' },
  '서면역': { ja: '西面駅', 'zh-Hans': '西面站', 'zh-Hant': '西面站' },
  '남포동': { ja: '南浦洞', 'zh-Hans': '南浦洞', 'zh-Hant': '南浦洞' },
  '광안리해수욕장': { ja: '広安里海水浴場', 'zh-Hans': '广安里海水浴场', 'zh-Hant': '廣安里海水浴場' },
  '김해공항': { ja: '金海国際空港', 'zh-Hans': '金海国际机场', 'zh-Hant': '金海國際機場' },
  '부산종합버스터미널(노포)': { ja: '釜山総合バスターミナル（老圃）', 'zh-Hans': '釜山综合巴士客运站（老圃）', 'zh-Hant': '釜山綜合巴士客運站（老圃）' },
  '부산서부버스터미널(사상)': { ja: '釜山西部バスターミナル（沙上）', 'zh-Hans': '釜山西部巴士客运站（沙上）', 'zh-Hant': '釜山西部巴士客運站（沙上）' },
  '해운대': { ja: '海雲台', 'zh-Hans': '海云台', 'zh-Hant': '海雲臺' },
  '서면': { ja: '西面', 'zh-Hans': '西面', 'zh-Hant': '西面' },
  '광안리': { ja: '広安里', 'zh-Hans': '广安里', 'zh-Hant': '廣安里' },
  '남포동 · 중앙동': { ja: '南浦洞・中央洞', 'zh-Hans': '南浦洞 · 中央洞', 'zh-Hant': '南浦洞 · 中央洞' },
};

/** 정해 둔 출발지·동네의 일본어·중국어 이름. 그 밖의 언어·이름이면 null — 검색해 고른 이름은 지어내지 않는다. */
export function knownPlaceLocalName(name: string, language: string): string | null {
  if (language !== 'ja' && language !== 'zh-Hans' && language !== 'zh-Hant') return null;
  return KNOWN_PLACE_LOCAL_NAMES[name.trim()]?.[language] ?? null;
}

/**
 * 서버가 돌려준 숙소·출발지 이름(「해운대」「부산역」)을 화면 언어로 — S15P21E201-1917.
 * 🔴 위 두 목록의 이름과 **글자 그대로 같을 때만** 바꾼다. 검색해 고른 숙소 이름은 그대로 둔다 — 지어내지 않는다.
 */
export function knownPlaceLabel(label: string, tx: (ko: string, en: string) => string): string {
  const hit = [...RECOMMENDED_LODGING_AREAS, ...MAJOR_BUSAN_ORIGINS].find((candidate) => candidate.name === label);
  return hit?.nameEn ? tx(hit.name, hit.nameEn) : label;
}

/**
 * 숙소로 고른 후보를 스냅샷으로 — S15P21E201-1536.
 *
 * 🔴 **추천 동네(위 RECOMMENDED_LODGING_AREAS)는 싣지 않는다** — 실제 숙소(검색 결과)만 싣는다.
 *    동네는 장소가 아니다. 서버에는 같은 넷이 이미 여행 범위 코드(TravelArea: HAEUNDAE·SEOMYEON·
 *    GWANGALLI·NAMPO — 좌표까지 같다)로 있어서, place 에 「해운대」 행을 또 만들면 같은 동네의
 *    이름이 세 벌(lodging-haeundae · HAEUNDAE · 새 행)이 되고 서로 이어지지 않는다. place 를 훑는
 *    다음 작업(사진·피처·갈래 세기)도 그 행을 «갈 수 있는 한 곳»으로 본다(고지혁 판단, 2026-09-23).
 *    동네를 받을 올바른 자리는 아직 없다 — accommodation_place_id 는 장소 외래키다. 숙소를 날마다의
 *    중심으로 쓸지(인수인계 결정 4번, S15P21E201-1493 과 충돌)가 정해지면 TravelArea 어휘로 칸이 생긴다.
 */
export function lodgingSnapshotOf(candidate: OriginCandidate): PlaceSnapshot | null {
  if (RECOMMENDED_LODGING_AREAS.some((area) => area.externalId === candidate.externalId)) return null;
  return placeSnapshotOf(candidate) ?? null;
}

/** 추천 동네 → 서버 여행 범위 코드(TravelArea). 좌표가 같은 값이라 잇는 이름만 적는다. */
const LODGING_AREA_CODE: Record<string, string> = {
  'lodging-haeundae': 'HAEUNDAE',
  'lodging-seomyeon': 'SEOMYEON',
  'lodging-gwangalli': 'GWANGALLI',
  'lodging-nampo': 'NAMPO',
};

/**
 * 숙소로 고른 것이 추천 동네면 그 동네의 여행 범위 코드 — S15P21E201-1566.
 *
 * 🔴 위 lodgingSnapshotOf 가 동네를 «장소로» 안 싣는 까닭은 그대로다. 대신 서버가 이제 동네를 받는 칸
 *    (accommodationArea)을 갖고, 그 동네 중심을 둘째 날 출발점·하루 끝 돌아가는 자리로 쓴다(S15P21E201-1565).
 *    전에는 이 칸이 없어 **동네 숙소를 고른 여행 전부가 숙소 없이 짜였다** — 운영 최근 여행 20개 중 숙소가 쓰인 것 0개.
 *    초안에는 동네의 이름과 좌표만 남으므로 좌표로 되짚는다(두 곳이 같은 상수를 쓴다).
 */
export function lodgingAreaCodeOf(lat: number | null, lng: number | null): string | null {
  if (lat == null || lng == null) return null;
  const area = RECOMMENDED_LODGING_AREAS.find((candidate) => candidate.lat === lat && candidate.lng === lng);
  return area ? LODGING_AREA_CODE[area.externalId] ?? null : null;
}
