import { apiRequest, ApiClientError } from '@/api/client';

// jaehyeon 님 계약(2026-09-08, S15P21E201-738). 목록 한 줄에는 제목·방문지 수가 없다 —
// 여행 자체에 제목 칸이 없고(지금 화면의 제목도 실은 일정에서 가져온 값이었다), 방문지 수는
// 일정의 최신 판을 세어야 나와서 목록 한 줄마다 그 조회를 더 하는 비용이 안 맞는다고 보셨다.
// 화면에서는 날짜로 대신 보여준다.
// 🔴 서버가 쓰는 이름 그대로다 — PLANNING 은 조건만 저장되고 아직 일정이 없는 상태,
// READY 는 일정이 만들어진 상태다. 여기 한때 ACTIVE·ARCHIVED·CANCELLED 라고 적혀 있었는데
// 서버 이름과 하나도 안 겹쳤다. `| string` 으로 열려 있고 지금은 화면이 이 값으로 분기하지
// 않아 드러나지 않았을 뿐이라, 상태 배지나 필터를 붙이는 순간 조용히 어긋났을 것이다.
export type TripStatus = 'PLANNING' | 'READY' | 'IN_PROGRESS' | 'COMPLETED' | string;
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

// 🔴 서버는 봉투의 `data` 에 목록을 **배열 그대로** 싣는다 — `{ trips: [...] }` 가 아니다
// (`ApiResponse<List<TripSummaryResponse>>`). 이 자리가 한때 `dto.trips` 를 읽어 undefined 를
// 목록으로 넘겼고, 화면이 그 개수를 세다 죽어 안전망 화면("화면을 불러오지 못했어요")이 떴다
// (S15P21E201-762). 여행이 없는 계정에서 먼저 눈에 띄었지만 있으나 없으나 같았다.
// 같은 저장소의 일정 버전 목록(`plan/itinerary.ts`)은 처음부터 배열로 받고 있었다.
export async function loadTrips(accessToken: string | null): Promise<TripsLoadResult> {
  try {
    const rows = await apiRequest<TripSummaryDto[]>('/api/v1/trips', { accessToken });
    // 모양이 예상과 다르면 화면을 죽이지 말고 오류 안내로 떨어뜨린다. 목록 한 줄 때문에
    // 탭 전체가 안 열리는 것이 이 버그의 실제 피해였다.
    if (!Array.isArray(rows)) return { state: 'error', message: '여행 목록의 형식이 예상과 달라요.' };
    return { state: 'success', trips: rows };
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
    // 여기는 서버가 객체를 준다(위 목록과 다르다). 그래도 같은 방식으로 한 번 확인한다 —
    // 호출한 쪽이 곧바로 개수를 세므로, 모양이 어긋나면 화면이 죽는 자리다.
    if (!Array.isArray(dto?.itineraries)) return { state: 'error', message: '일정 목록의 형식이 예상과 달라요.' };
    return { state: 'success', role: dto.role, itineraries: dto.itineraries };
  } catch (error) {
    return failure(error);
  }
}

// jaehyeon 님 계약(2026-09-08 axmap): DELETE /api/v1/trips/{tripId} — 소유자만 204.
// 동행자가 부르면 403 TRIP_FORBIDDEN(동행자는 "나가기"이지 "삭제"가 아니다 — 별도로
// DELETE /trips/{tripId}/members/{userId} 를 쓴다). 🔴 이미 지운 여행을 소유자가 다시
// 지워도 204 다 — 응답이 끊겨 재시도한 경우 오류로 보이면 "안 지워졌다"고 착각하게 되므로,
// 여기서는 404(TRIP_NOT_FOUND)도 "이미 없다"는 뜻으로 같이 성공 취급한다.
export type DeleteTripResult = { state: 'success' } | { state: 'forbidden'; message: string } | TripsFailure;

export async function deleteTrip(tripId: string, accessToken: string | null): Promise<DeleteTripResult> {
  try {
    await apiRequest<void>(`/api/v1/trips/${encodeURIComponent(tripId)}`, { method: 'DELETE', accessToken });
    return { state: 'success' };
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { state: 'success' };
    if (error instanceof ApiClientError && error.status === 403) return { state: 'forbidden', message: error.message };
    return failure(error);
  }
}
