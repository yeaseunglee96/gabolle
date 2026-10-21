// 여행 별점(S15P21E201-1908) — 여행 하나에 구성원마다 1~5 하나. 서버: /api/v1/trips/{tripId}/rating
import { apiRequest, ApiClientError } from '@/api/client';

export type TripRating = { myScore: number | null; average: number | null; count: number };

/** 서버에 이 경로가 아직 없다(404·501) — 화면이 별 줄을 숨긴다. 구성원이 아니어도 404 라 같이 숨는다. */
export function ratingUnavailable(error: unknown): boolean {
  return error instanceof ApiClientError && (error.status === 404 || error.status === 501);
}

const path = (tripId: string) => `/api/v1/trips/${encodeURIComponent(tripId)}/rating`;

export function loadTripRating(tripId: string, accessToken: string, signal?: AbortSignal) {
  return apiRequest<TripRating>(path(tripId), { method: 'GET', accessToken, signal });
}

/** 매기거나 지운다. score 가 null 이면 지운다. */
export function saveTripRating(tripId: string, score: number | null, accessToken: string) {
  return score == null
    ? apiRequest<TripRating>(path(tripId), { method: 'DELETE', accessToken })
    : apiRequest<TripRating>(path(tripId), { method: 'PUT', accessToken, body: { score } });
}

/** 별을 눌렀을 때 저장할 값 — 지금 내 점수와 같은 별을 다시 누르면 지운다. */
export function nextScore(current: number | null, pressed: number): number | null {
  return current === pressed ? null : pressed;
}
