// 여행 페이지가 여는 두 입구를 한 모양으로 맞춘다 (S15P21E201-1535).
//
//   · 추천에서 온다  — /trips/{tripId}/recommendations  → 코스 후보가 있고, 아직 아무것도 확정 안 됨
//   · 일정에서 온다  — /trips/{itineraryId}/itinerary   → 그 일정이 곧 확정한 코스
//
// 🔴 두 주소의 [id] 가 가리키는 것이 **다르다**(앞은 여행, 뒤는 일정). 같은 화면을 그리려면
//    둘 다 「여행 번호 + 코스 목록 + 확정한 코스」로 바꿔야 한다. 그 바꾸기를 여기 한 곳에 둔다.
import { loadItinerary } from '@/plan/itinerary';
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
    /** 서버가 3안을 보냈나. false 면 화면이 「세 코스 비교는 준비 중」을 말한다. */
    full: boolean;
    /** 확정한 코스의 id. 추천에서 왔으면 null — 확정 전에는 어느 코스도 내 일정이 아니다(시안 README). */
    confirmedCourseId: string | null;
  }
  | { state: 'error'; message: string };

export async function loadTripPageCourses(source: TripPageSource, accessToken: string | null): Promise<TripPageCourses> {
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
    return { state: 'ready', tripId: source.tripId, courses: result.courses, full: result.full, confirmedCourseId: null };
  }

  const opened = await loadItinerary(source.itineraryId, accessToken);
  if (opened.state !== 'success') return { state: 'error', message: opened.message };
  const tripId = opened.itinerary.tripId ?? '';
  // 🔴 여행 번호를 모르는 옛 서버면 코스 목록을 물을 곳이 없다 — 이 일정 하나만 코스로 쓴다.
  const listed = tripId ? await loadTripCourses(tripId, source.itineraryId, accessToken) : null;
  const courses = listed?.state === 'success' ? listed.courses : [];
  const full = listed?.state === 'success' ? listed.full : false;
  // 🔴 연 일정이 코스 목록에 없으면(다른 작업에서 만든 판 등) **그 일정을 맨 앞에 끼운다.**
  //    안 끼우면 지금 보고 있는 일정이 화면에서 사라지고 엉뚱한 코스가 「확정」으로 보인다.
  const own = courses.find((course) => course.itineraryId === source.itineraryId);
  const withOwn = own ? courses : [courseFromItinerary(opened.itinerary), ...courses];
  const confirmed = own ?? withOwn[0];
  return { state: 'ready', tripId, courses: withOwn, full, confirmedCourseId: confirmed.id };
}
