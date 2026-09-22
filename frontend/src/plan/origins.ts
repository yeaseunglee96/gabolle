import { apiRequest } from '@/api/client';

export type OriginCandidate = {
  name: string;
  address: string;
  lat: number;
  lng: number;
  externalId: string;
  source: 'KAKAO_LOCAL' | 'INTERNAL_FALLBACK';
};

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
