// 읽기 전용 공유 일정 —(백엔드) · -335(이 화면이 쓰는 클라이언트).
// 서버 계약(SharedItineraryController#open, SharedItineraryResponse): 인증 없이 GET 하나로
// 연다. 응답에는 출발지 좌표·연락처·예산·인원이 원래 없다(보안 설계 — 값을 null 로 비운 게
// 아니라 record 자체에 칸이 없다). notShared 는 "화면 고지문이 말해야 할 항목 이름" 목록이다.
import { apiRequest, ApiClientError, APP_WEB_BASE_URL } from '@/api/client';

export type SharedItineraryItem = {
  sequence: number;
  placeName: string;
  category: string | null;
  startsAt: string | null;
  endsAt: string | null;
  stayMinutes: number | null;
};
export type SharedItineraryDay = { date: string; items: SharedItineraryItem[] };
export type SharedItineraryDto = {
  title: string;
  startDate: string;
  finishDate: string;
  version: number | null;
  days: SharedItineraryDay[];
  expiresAt: string;
  notShared: string[];
};

export type SharedItineraryResult =
  | { state: 'success'; data: SharedItineraryDto }
  | { state: 'expired' }
  | { state: 'not_found' }
  | { state: 'error'; message: string };

export async function getSharedItinerary(token: string): Promise<SharedItineraryResult> {
  try {
    const data = await apiRequest<SharedItineraryDto>(`/api/v1/shares/${encodeURIComponent(token)}`);
    return { state: 'success', data };
  } catch (error) {
    if (error instanceof ApiClientError) {
      if (error.code === 'SHARE_LINK_EXPIRED') return { state: 'expired' };
      if (error.code === 'SHARE_LINK_NOT_FOUND' || error.code === 'SHARED_TRIP_NOT_FOUND') return { state: 'not_found' };
      return { state: 'error', message: error.message };
    }
    return { state: 'error', message: '공유 일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.' };
  }
}

export type ShareLinkIssued = { shareUrl: string; token: string; expiresAt: string };

// 발급 —(ShareLinkController#issue). 소유자만 부를 수 있고, 응답의 path는
// "서버 API 경로"라 화면 주소가 아니다(ShareLinkResponse 주석) — invite 링크와 같은 이유로
// 화면 주소(/s/:token)는 여기서 직접 조립한다.
export async function issueShareLink(tripId: string, accessToken: string): Promise<ShareLinkIssued> {
  const issued = await apiRequest<{ token: string; expiresAt: string }>(`/api/v1/trips/${encodeURIComponent(tripId)}/share-links`, {
    method: 'POST',
    accessToken,
  });
  return { shareUrl: `${APP_WEB_BASE_URL}/s/${issued.token}`, token: issued.token, expiresAt: issued.expiresAt };
}
