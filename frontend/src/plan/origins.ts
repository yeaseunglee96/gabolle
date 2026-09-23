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
 * 🔴 추천 동네(위 RECOMMENDED_LODGING_AREAS)는 장소가 아니라 **동네**다. 주소 칸에는 주소가 아니라
 *    설명(「바다 앞 호텔·리조트가 모여 있어요」)이 들어 있어서, 그대로 보내면 서버에 주소가 설명 문장인
 *    장소가 생긴다. 그래서 동네는 이름·좌표만 보낸다 — 좌표로 「어디서 묵나」는 정확히 말해진다.
 */
export function lodgingSnapshotOf(candidate: OriginCandidate): PlaceSnapshot | null {
  const snapshot = placeSnapshotOf(candidate);
  if (!snapshot) return null;
  const isArea = RECOMMENDED_LODGING_AREAS.some((area) => area.externalId === candidate.externalId);
  return isArea ? { ...snapshot, address: undefined } : snapshot;
}
