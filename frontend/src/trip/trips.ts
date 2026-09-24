import type { QueryClient } from '@tanstack/react-query';

import { apiRequest, ApiClientError } from '@/api/client';

// 서버가 쓰는 이름 그대로다 — PLANNING 은 조건만 저장되고 아직 일정이 없는 상태
// READY 는 일정이 만들어진 상태다. 여기 한때 ACTIVE·ARCHIVED·CANCELLED 라고 적혀 있었는데
// 서버 이름과 하나도 안 겹쳤다. `| string` 으로 열려 있고 지금은 화면이 이 값으로 분기하지
// 않아 드러나지 않았을 뿐이라, 상태 배지나 필터를 붙이는 순간 조용히 어긋났을 것이다.
export type TripStatus = 'PLANNING' | 'READY' | 'IN_PROGRESS' | 'COMPLETED' | string;
export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER' | string;

export type TripSummaryDto = {
  tripId: string;
  // null 이면 "아직 이름이 없다" 이지 "이름이 빈 문자열" 이 아니다. 서버가 날짜를 대신
  // 채워 보내지 않는 것도 같은 이유다 — 그러면 사용자가 붙인 이름과 서버가 만든 이름이
  // 한 칸에서 구분이 안 되고, 화면이 이름 있는 카드를 다르게 그릴 방법이 없어진다.
  title: string | null;
  startDate: string | null;
  endDate: string | null;
  dayCount: number;
  partySize: number;
  status: TripStatus;
  role: TripRole;
  createdAt: string;
  updatedAt: string;
  /**
   * 🔴 여행 카드에 그릴 사진. 서버가 목록과 «함께» 준다(S15P21E201-1370 · 1436).
   *
   * 전에는 화면이 카드마다 일정 → 일정 내용 → 장소 여섯을 따로 불러 스스로 찾았다. 「내 여행」을
   * 열면 1초에 51건이 나갔고, 서버 앞단이 초당 10건까지만 받아 59건이 503 으로 거절됐다
   * (그중 52건이 사진 요청). 게다가 실패를 세션 내내 기억해서, 거절된 카드는 앱을 끌 때까지
   * 사진이 안 나왔다 — 어느 카드가 그럴지는 그때그때 달랐다(S15P21E201-1435).
   *
   * null 이 흔하다. 사진 없는 카드가 기본이고 사진은 덤이다.
   */
  coverImageUrl: string | null;
  /** 첫 방문지 이름. 사진은 앞쪽 몇 곳을 훑어 찾으므로 «사진이 이 장소의 것이 아닐 수 있다». */
  firstStopNameKo: string | null;
  firstStopNameEn: string | null;
  /**
   * 카드를 누르면 열 일정 — 마지막으로 「코스 N 으로 확정」한 것, 안 골랐으면 기본 일정, 일정이 없으면 null (S15P21E201-1605).
   * 🔴 옛 서버에는 이 칸이 **아예 없다**(undefined). 그때는 {@link resolveTripItinerary} 가 일정 목록으로 대신 고른다.
   */
  currentItineraryId?: string | null;
};

/** 여행 카드에 그릴 제목 */
export function tripDisplayTitle(trip: Pick<TripSummaryDto, 'title'>, fallback: string) {
  return trip.title?.trim() || fallback;
}

type TripsFailure = { state: 'unavailable' | 'offline' | 'error'; message: string };

function failure(error: unknown): TripsFailure {
  if (error instanceof ApiClientError && error.code === 'NETWORK_ERROR') return { state: 'offline', message: error.message };
  if (error instanceof ApiClientError && (error.status === 404 || error.code === 'INVALID_RESPONSE')) return { state: 'unavailable', message: '내 여행 목록 API가 아직 준비되지 않았어요.' };
  // — 여기 걸리는 것은 우리가 예상하지 못한 실패라, 서버가 준 문장이 사람에게
  // 읽히는 말이라는 보장이 없다. 실제로 「Invalid UUID string: demo-trip」 같은 개발자용 문장이
  // 화면에 그대로 나왔다. 앞의 두 갈래는 우리가 고른 문구를 쓰므로 그대로 둔다.
  return { state: 'error', message: '여행 목록을 불러오지 못했어요.' };
}

export type TripsLoadResult = { state: 'success'; trips: TripSummaryDto[] } | TripsFailure;

// 서버는 봉투의 `data` 에 목록을 배열 그대로 싣는다 — `{ trips: [...] }` 가 아니다
// (`ApiResponse<List<TripSummaryResponse>>`). 이 자리가 한때 `dto.trips` 를 읽어 undefined 를
// 목록으로 넘겼고, 화면이 그 개수를 세다 죽어 안전망 화면("화면을 불러오지 못했어요")이 떴다
// 여행이 없는 계정에서 먼저 눈에 띄었지만 있으나 없으나 같았다.
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

// 이 배열의 순서는 계약이 아니다(jaehyeon 님 명시) — 여럿일 때 어느 것이 최신인지
// 골라 주는 규칙이 이 목록에는 없다. 여행 카드가 무엇을 열지는 resolveTripItinerary 가 정한다(S15P21E201-1605).
export type TripItinerariesResult = { state: 'success'; role: TripRole; itineraries: TripItineraryRefDto[] } | TripsFailure;

export async function loadTripItineraries(tripId: string, accessToken: string | null): Promise<TripItinerariesResult> {
  try {
    const dto = await apiRequest<{ tripId: string; role: TripRole; itineraries: TripItineraryRefDto[] }>(
      `/api/v1/trips/${encodeURIComponent(tripId)}/itineraries`,
      { accessToken },
    );
    // 여기는 서버가 객체를 준다(위 목록과 다르다). 그래도 같은 방식으로 한 번 확인한다
    // 호출한 쪽이 곧바로 개수를 세므로, 모양이 어긋나면 화면이 죽는 자리다.
    if (!Array.isArray(dto?.itineraries)) return { state: 'error', message: '일정 목록의 형식이 예상과 달라요.' };
    return { state: 'success', role: dto.role, itineraries: dto.itineraries };
  } catch (error) {
    return failure(error);
  }
}

export type TripItineraryChoice = { state: 'open'; itineraryId: string } | { state: 'none' } | TripsFailure;

/**
 * 여행 카드를 누르면 열 일정 (S15P21E201-1605). 🔴 여럿이어도 사람에게 묻지 않는다.
 * 전에는 일정이 여럿이면(B·C안을 고르면 일정이 새로 생긴다) 「열 일정을 골라주세요」 창이 떴다 —
 * 사용자: 「선택된 것만 보여 주면 되잖아. 왜 한 단계가 더 생겼지?」
 *
 * · 서버가 확정 일정(currentItineraryId)을 알려 주면 그것 — 부르지도 않는다
 * · 옛 서버(칸 없음)면 일정 목록의 **마지막**. 순서는 계약이 아니지만 서버 구현이 만든 순서(오래된 것 먼저,
 *   JpaItineraryRepository.findByTripIdOrderByCreatedAtAsc)로 주므로 마지막이 가장 최근이다. 배포 전 잠깐만 쓰는 길이다.
 */
export async function resolveTripItinerary(
  trip: Pick<TripSummaryDto, 'tripId' | 'currentItineraryId'>,
  accessToken: string | null,
): Promise<TripItineraryChoice> {
  if (typeof trip.currentItineraryId === 'string' && trip.currentItineraryId !== '') return { state: 'open', itineraryId: trip.currentItineraryId };
  if (trip.currentItineraryId === null) return { state: 'none' };
  const listed = await loadTripItineraries(trip.tripId, accessToken);
  if (listed.state !== 'success') return listed;
  const latest = listed.itineraries[listed.itineraries.length - 1];
  return latest ? { state: 'open', itineraryId: latest.itineraryId } : { state: 'none' };
}

/**
 * 여행 목록을 든 캐시(내 여행 · 홈 · 마이페이지)를 낡은 것으로 한다 — 코스를 고르면 확정 일정이 바뀐다.
 * 🔴 안 비우면 돌아가서 카드를 눌렀을 때 30초 동안은 **방금 버린 일정**이 열린다(서버는 새 값을 밀어 주지 않는다).
 */
export function invalidateTripLists(queryClient: QueryClient) {
  return queryClient.invalidateQueries({ predicate: (query) => query.queryKey.includes('trips') });
}

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
