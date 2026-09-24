import { apiRequest } from '@/api/client';

export type OriginCandidate = {
  name: string;
  address: string;
  lat: number;
  lng: number;
  externalId: string;
  source: 'KAKAO_LOCAL' | 'INTERNAL_FALLBACK';
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

// 검색이 0건이거나 서버 연결 전에도 고를 수 있는 부산 주요 출발지. 실제 지명·공개 좌표다.
export const MAJOR_BUSAN_ORIGINS: OriginCandidate[] = [
  { name: '부산역', address: '부산 동구 중앙대로 206', lat: 35.1152, lng: 129.0403, externalId: 'major-busan-station', source: 'INTERNAL_FALLBACK' },
  { name: '해운대해수욕장', address: '부산 해운대구 우동', lat: 35.1587, lng: 129.1604, externalId: 'major-haeundae', source: 'INTERNAL_FALLBACK' },
  { name: '서면역', address: '부산 부산진구 가야대로', lat: 35.1578, lng: 129.0592, externalId: 'major-seomyeon', source: 'INTERNAL_FALLBACK' },
  { name: '남포동', address: '부산 중구 남포동', lat: 35.0980, lng: 129.0306, externalId: 'major-nampo', source: 'INTERNAL_FALLBACK' },
  { name: '광안리해수욕장', address: '부산 수영구 광안동', lat: 35.1532, lng: 129.1187, externalId: 'major-gwangalli', source: 'INTERNAL_FALLBACK' },
  // 🔴 비행기·시외버스로 오는 사람의 첫 출발지(S15P21E201-1591). 좌표·주소를 지어내지 않았다 —
  //    앱의 출발지 검색(GET /api/v1/origins → 카카오 로컬)이 2026-09-24 에 돌려준 첫 결과 그대로다.
  //    카카오 장소 번호: 김해국제공항 국제선청사 8239831 · 부산종합버스터미널 12479254 · 부산서부버스터미널 18166577.
  { name: '김해공항', address: '부산 강서구 대저2동 2350-1', lat: 35.172488, lng: 128.946785, externalId: 'major-gimhae-airport', source: 'INTERNAL_FALLBACK' },
  { name: '부산종합버스터미널(노포)', address: '부산 금정구 중앙대로 2238', lat: 35.284773, lng: 129.095472, externalId: 'major-nopo-terminal', source: 'INTERNAL_FALLBACK' },
  { name: '부산서부버스터미널(사상)', address: '부산 사상구 사상로 201', lat: 35.163239, lng: 128.982525, externalId: 'major-sasang-terminal', source: 'INTERNAL_FALLBACK' },
];

// 숙소 칸이 검색어 없이 보여 주는 추천 지역 — 시안 design_handoff_home_lodging. 출발지와
// 달리 특정 장소가 아니라 «동네»라서, 좌표는 그 동네를 대표하는 주요 지점을 그대로 쓴다
// (MAJOR_BUSAN_ORIGINS 의 해운대·서면·광안리·남포동과 같은 값).
export const RECOMMENDED_LODGING_AREAS: OriginCandidate[] = [
  { name: '해운대', address: '바다 앞 호텔·리조트가 모여 있어요', lat: 35.1587, lng: 129.1604, externalId: 'lodging-haeundae', source: 'INTERNAL_FALLBACK' },
  { name: '서면', address: '교통 중심 · 어디든 가기 편해요', lat: 35.1578, lng: 129.0592, externalId: 'lodging-seomyeon', source: 'INTERNAL_FALLBACK' },
  { name: '광안리', address: '야경과 카페 골목', lat: 35.1532, lng: 129.1187, externalId: 'lodging-gwangalli', source: 'INTERNAL_FALLBACK' },
  { name: '남포동 · 중앙동', address: '시장·원도심 도보 여행', lat: 35.0980, lng: 129.0306, externalId: 'lodging-nampo', source: 'INTERNAL_FALLBACK' },
];

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
