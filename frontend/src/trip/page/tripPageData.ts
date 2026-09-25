// 여행 페이지가 여는 두 입구를 한 모양으로 맞춘다 (S15P21E201-1535).
//
//   · 추천에서 온다  — /trips/{tripId}/recommendations  → 코스 후보가 있고, 아직 아무것도 확정 안 됨
//   · 일정에서 온다  — /trips/{itineraryId}/itinerary   → 그 일정이 곧 확정한 코스
//
// 🔴 두 주소의 [id] 가 가리키는 것이 **다르다**(앞은 여행, 뒤는 일정). 같은 화면을 그리려면
//    둘 다 「여행 번호 + 코스 목록 + 확정한 코스」로 바꿔야 한다. 그 바꾸기를 여기 한 곳에 둔다.
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';
import { findLatestRecommendationJob, loadRecommendationResult } from '@/plan/recommendations';
import { courseFromItinerary, loadTripCourses, type TripCourse } from '@/plan/tripCourses';

export type TripPageSource =
  | { kind: 'trip'; tripId: string; jobId?: string | null }
  | { kind: 'itinerary'; itineraryId: string };

export type TripPageCourses =
  | {
    state: 'ready';
    tripId: string;
    courses: TripCourse[];
    /** 서버가 3안을 보냈나. false 면 화면이 한 안만 그린다(「비교는 준비 중」 같은 말은 안 한다 — S15P21E201-1662). */
    full: boolean;
    /** 확정한 코스의 id. 추천에서 왔으면 null — 확정 전에는 어느 코스도 내 일정이 아니다(시안 README). */
    confirmedCourseId: string | null;
    /** 코스 목록을 아직 받는 중이다 — 연 일정 하나로 먼저 그렸다(S15P21E201-1599). 이때 `full` 은 아직 모른다. */
    coursesLoading: boolean;
  }
  | { state: 'error'; message: string };

/** 연 일정 하나로 먼저 그릴 판 — 코스 목록은 아직 오는 중이다. */
export function itineraryFirstPage(itinerary: ItineraryDto): TripPageCourses {
  return { state: 'ready', tripId: itinerary.tripId ?? '', courses: [courseFromItinerary(itinerary)], full: false, confirmedCourseId: itinerary.id, coursesLoading: true };
}

/**
 * @param onItinerary 일정에서 왔을 때, 연 일정이 도착하자마자 부른다 — 코스 목록을 기다리지 않고 먼저 그리게.
 *   🔴 코스 목록(추천 3안)은 서버에서 수 초가 걸린다(운영 3.7~12.8초). 일정은 1초 안에 와 있는데 그것까지
 *      기다렸다 그리면 화면이 그만큼 빈다(S15P21E201-1599).
 */
export async function loadTripPageCourses(
  source: TripPageSource,
  accessToken: string | null,
  onItinerary?: (itinerary: ItineraryDto) => void,
): Promise<TripPageCourses> {
  if (source.kind === 'trip') {
    // 추천 화면과 같은 순서다: 작업 번호 → 그 결과의 일정 → 코스 계약(없으면 그 일정 하나로).
    let job = source.jobId ?? null;
    if (!job && source.tripId) {
      const lookup = await findLatestRecommendationJob(source.tripId, accessToken);
      // 「없음」·「못 찾음」에는 번호 칸이 아예 없다(recommendations.tsx 와 같은 주의).
      job = 'jobId' in lookup ? lookup.jobId : null;
    }
    const fallback = job ? (await loadRecommendationResult(job, accessToken)).itineraryId : null;
    const result = await loadTripCourses(source.tripId, fallback, accessToken);
    if (result.state !== 'success') return { state: 'error', message: result.message };
    return { state: 'ready', tripId: source.tripId, courses: result.courses, full: result.full, confirmedCourseId: null, coursesLoading: false };
  }

  const opened = await loadItinerary(source.itineraryId, accessToken);
  if (opened.state !== 'success') return { state: 'error', message: opened.message };
  onItinerary?.(opened.itinerary);
  const tripId = opened.itinerary.tripId ?? '';
  // 🔴 여행 번호를 모르는 옛 서버면 코스 목록을 물을 곳이 없다 — 이 일정 하나만 코스로 쓴다.
  //    목록이 비었을 때 대신 쓸 일정은 넘기지 않는다 — 연 일정이 이미 손에 있어서, 넘기면 같은 것을 한 번 더 받는다.
  const listed = tripId ? await loadTripCourses(tripId, null, accessToken) : null;
  const courses = listed?.state === 'success' ? listed.courses : [];
  const full = listed?.state === 'success' ? listed.full : false;
  // 🔴 연 일정이 코스 목록에 없으면(다른 작업에서 만든 판 등) **그 일정을 맨 앞에 끼운다.**
  //    안 끼우면 지금 보고 있는 일정이 화면에서 사라지고 엉뚱한 코스가 「확정」으로 보인다.
  const own = courses.find((course) => course.itineraryId === source.itineraryId);
  const withOwn = own ? courses : [courseFromItinerary(opened.itinerary), ...courses];
  const confirmed = own ?? withOwn[0];
  return { state: 'ready', tripId, courses: withOwn, full, confirmedCourseId: confirmed.id, coursesLoading: false };
}
