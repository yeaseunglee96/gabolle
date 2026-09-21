// 이 여행에 적어 둔 예산 — 시안 design_handoff_itinerary 7절 (S15P21E201-1432).
//
// 🔴 **왜 따로 부르는가.** 일정 응답(ItineraryDto)에는 예산 칸이 없고, 내 여행 목록
//    (TripSummaryDto)에도 없다. 서버에서 예산을 주는 곳은 여행 상세 하나뿐이다
//    (backend TripDto.budgetKrw — `GET /api/v1/trips/{tripId}`). 그래서 일정 화면이
//    「예산 대비」를 그리려면 이 한 번을 더 불러야 한다.
//
// 🔴 **왜 trips.ts 에 안 넣었는가.** 그 파일은 다른 가지에서 방금 고쳐졌다. 한 줄
//    더하려고 같은 파일을 건드리면 합칠 때 부딪힌다. 여기 따로 둔다.
//
// 🔴 **못 받으면 null 이다. 0 이 아니다.** 0 으로 떨어뜨리면 화면이 「예산 0원을 다
//    썼어요」라고 말한다 — 예산을 안 정한 사람에게도, 서버가 잠깐 답을 안 한 순간에도.
import { apiRequest, ApiClientError } from '@/api/client';

export type TripBudgetResult =
  /** 여행을 읽었다. `budgetKrw` 가 null 이면 **예산을 안 정한 여행**이다 */
  | { state: 'success'; budgetKrw: number | null }
  /** 못 읽었다 — 권한이 없거나, 옛 서버이거나, 네트워크가 끊겼다 */
  | { state: 'unavailable' };

export async function loadTripBudget(tripId: string, accessToken: string | null): Promise<TripBudgetResult> {
  if (!tripId) return { state: 'unavailable' };
  try {
    const dto = await apiRequest<{ trip?: { budgetKrw?: number | null } }>(
      `/api/v1/trips/${encodeURIComponent(tripId)}`,
      { accessToken },
    );
    const raw = dto?.trip?.budgetKrw;
    // 🔴 모양이 예상과 다르면 **모른다고 한다.** 화면 한 줄 때문에 일정 전체를 못 열게
    //    하지 않는다 — 이 저장소가 여행 목록에서 한 번 겪은 자리다(trips.ts 주석).
    return { state: 'success', budgetKrw: typeof raw === 'number' ? raw : null };
  } catch (error) {
    // 옛 서버(404)·권한 없음(403)·끊김을 갈라 봐야 화면이 할 일이 같다 — 예산 줄을 안 그린다.
    if (error instanceof ApiClientError) return { state: 'unavailable' };
    return { state: 'unavailable' };
  }
}
