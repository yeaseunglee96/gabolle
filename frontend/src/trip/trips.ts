import { apiRequest, ApiClientError } from '@/api/client';

// jaehyeon 님 계약(2026-09-08, S15P21E201-738). 목록 한 줄에는 제목·방문지 수가 없다 —
// 여행 자체에 제목 칸이 없고(지금 화면의 제목도 실은 일정에서 가져온 값이었다), 방문지 수는
// 일정의 최신 판을 세어야 나와서 목록 한 줄마다 그 조회를 더 하는 비용이 안 맞는다고 보셨다.
// 화면에서는 날짜로 대신 보여준다.
export type TripStatus = 'ACTIVE' | 'ARCHIVED' | 'CANCELLED' | string;
export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER' | string;

export type TripSummaryDto = {
  tripId: string;
  startDate: string | null;
  endDate: string | null;
  dayCount: number;
  partySize: number;
  status: TripStatus;
  role: TripRole;
  createdAt: string;
  updatedAt: string;
};

type TripsFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): TripsFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && (error.status === 404 || error.code === 'INVALID_RESPONSE')) return { state: 'unavailable', message: '내 여행 목록 API가 아직 준비되지 않았어요.' };
  return { state: 'error', message: error instanceof Error ? error.message : '여행 목록을 불러오지 못했어요.' };
}

export type TripsLoadResult = { state: 'success'; trips: TripSummaryDto[] } | TripsFailure;

export async function loadTrips(accessToken: string | null): Promise<TripsLoadResult> {
  try {
    const dto = await apiRequest<{ trips: TripSummaryDto[] }>('/api/v1/trips', { accessToken });
    return { state: 'success', trips: dto.trips };
  } catch (error) {
    return failure(error);
  }
}

export type TripItineraryRefDto = { itineraryId: string; latestVersion: number };

// 🔴 이 배열의 순서는 계약이 아니다(jaehyeon 님 명시) — 여럿일 때 어느 것이 최신인지
// 골라 주는 규칙이 서버에 아직 없다. 하나면 그 하나를 열고, 여럿이면 사용자에게 고르게 한다.
export type TripItinerariesResult = { state: 'success'; role: TripRole; itineraries: TripItineraryRefDto[] } | TripsFailure;

export async function loadTripItineraries(tripId: string, accessToken: string | null): Promise<TripItinerariesResult> {
  try {
    const dto = await apiRequest<{ tripId: string; role: TripRole; itineraries: TripItineraryRefDto[] }>(
      `/api/v1/trips/${encodeURIComponent(tripId)}/itineraries`,
      { accessToken },
    );
    return { state: 'success', role: dto.role, itineraries: dto.itineraries };
  } catch (error) {
    return failure(error);
  }
}
