// 코스 3안 — 전체 일정을 통째로 비교하는 단위 (시안 ③, 인계 §4·§8).
//
// 🔴 「코스」는 장소 하나가 아니라 **여행 전체의 한 가지 안**이다. 지금까지 이 화면이
//    보여 주던 것은 장소 후보 목록이었다. 장소를 고르는 것과 일정을 고르는 것은 사람이
//    하는 판단 자체가 다르다 — 앞은 「여기 갈까」이고 뒤는 「이렇게 다닐까」다.
import { ApiClientError, apiRequest } from '@/api/client';
import { loadItinerary, type ItineraryDto } from '@/plan/itinerary';

export type CourseStatus = 'CONFIRMED' | 'ESTIMATED';

export type CourseStop = {
  placeId: string | null;
  name: string;
  /** 「09:30」. 모르면 null 이고 화면이 그 자리를 접는다. */
  time: string | null;
  note: string | null;
  photoUrl: string | null;
};

export type CourseDay = { day: number; stops: CourseStop[] };

export type CourseSummary = {
  places: number | null;
  moveMin: number | null;
  walkKm: number | null;
  costKrw: number | null;
};

export type TripCourse = {
  id: string;
  title: string;
  /** 한 줄 성격 — 「해운대 · 광안리 · 송정 — 걷는 시간이 많은 코스」. */
  tagline: string;
  days: CourseDay[];
  summary: CourseSummary;
  status: CourseStatus;
  /** 「이렇게 골랐어요」에 적을 근거. 없으면 그 카드를 안 그린다. */
  rationale: string | null;
  /** 이 안을 고르면 열릴 일정. 서버가 미리 만들어 둔 경우에만 있다. */
  itineraryId: string | null;
};

export type TripCoursesResult =
  | { state: 'success'; courses: TripCourse[]; /** 서버가 3안을 보냈나. 한 안뿐이면 false. */ full: boolean }
  | { state: 'empty'; message: string }
  | { state: 'error'; message: string };

type CourseDto = {
  id?: string; courseId?: string; title?: string; tagline?: string | null;
  days?: Array<{ day?: number; stops?: Array<{ placeId?: string | null; name?: string; time?: string | null; note?: string | null; photoUrl?: string | null }> }>;
  summary?: { places?: number | null; moveMin?: number | null; walkKm?: number | null; costKrw?: number | null } | null;
  status?: string | null;
  rationale?: string | null;
  itineraryId?: string | null;
};
type CoursesDto = { courses?: CourseDto[] | null };

function toStop(dto: NonNullable<NonNullable<CourseDto['days']>[number]['stops']>[number]): CourseStop {
  return {
    placeId: typeof dto?.placeId === 'string' ? dto.placeId : null,
    name: typeof dto?.name === 'string' ? dto.name : '',
    time: typeof dto?.time === 'string' && dto.time !== '' ? dto.time : null,
    note: typeof dto?.note === 'string' && dto.note !== '' ? dto.note : null,
    photoUrl: typeof dto?.photoUrl === 'string' && dto.photoUrl !== '' ? dto.photoUrl : null,
  };
}

/**
 * 서버가 보낸 한 안을 화면 모양으로.
 *
 * 🔴 모르는 상태는 `ESTIMATED` 로 떨어뜨린다. `CONFIRMED` 로 떨어뜨리면 **확인 안 된 것을
 *    확인됐다고 말하게 된다** — 모르는 쪽으로 기우는 것이 언제나 안전하다.
 */
export function adaptCourse(dto: CourseDto): TripCourse {
  const days = (dto?.days ?? []).map((day, index) => ({
    day: typeof day?.day === 'number' ? day.day : index + 1,
    stops: (day?.stops ?? []).map(toStop).filter((stop) => stop.name !== ''),
  }));
  const summary = dto?.summary ?? null;
  const num = (value: unknown) => (typeof value === 'number' && Number.isFinite(value) ? value : null);
  return {
    id: String(dto?.id ?? dto?.courseId ?? ''),
    title: typeof dto?.title === 'string' && dto.title !== '' ? dto.title : '',
    tagline: typeof dto?.tagline === 'string' ? dto.tagline : '',
    days,
    summary: {
      places: num(summary?.places) ?? (days.length ? days.reduce((sum, day) => sum + day.stops.length, 0) : null),
      moveMin: num(summary?.moveMin),
      walkKm: num(summary?.walkKm),
      costKrw: num(summary?.costKrw),
    },
    status: dto?.status === 'CONFIRMED' ? 'CONFIRMED' : 'ESTIMATED',
    rationale: typeof dto?.rationale === 'string' && dto.rationale !== '' ? dto.rationale : null,
    itineraryId: typeof dto?.itineraryId === 'string' && dto.itineraryId !== '' ? dto.itineraryId : null,
  };
}

/**
 * 이미 만들어진 **일정 하나**를 코스 한 안으로 옮긴다.
 *
 * 🔴 서버가 아직 3안을 안 보낼 때 쓴다. **없는 두 안을 지어내지 않는다** — 화면은 한 안만
 *    그리고, 왜 하나뿐인지 말한다. 지어낸 안을 고르게 하면 고른 대로 여행이 안 나온다.
 */
export function courseFromItinerary(itinerary: ItineraryDto): TripCourse {
  const days: CourseDay[] = itinerary.days.map((day, index) => ({
    day: index + 1,
    stops: day.items.map((item) => ({
      placeId: item.placeId ?? null,
      name: item.title ?? '',
      time: typeof item.startsAt === 'string' && item.startsAt.length > 10
        ? item.startsAt.slice(11, 16)
        : null,
      note: item.description ?? null,
      // 🔴 일정 항목에는 사진 칸이 없다. 없는 것을 지어내지 않는다 — 화면이 사진 자리를 접는다.
      photoUrl: null,
    })).filter((stop) => stop.name !== ''),
  }));
  const allItems = itinerary.days.flatMap((day) => day.items);
  const legs = allItems.filter((item) => typeof item.travelDurationMin === 'number');
  const walkM = allItems.reduce((sum, item) => sum + (item.walkingMeters ?? 0), 0);
  return {
    id: itinerary.id,
    title: itinerary.title ?? '',
    tagline: '',
    days,
    summary: {
      places: days.reduce((sum, day) => sum + day.stops.length, 0),
      moveMin: legs.length ? legs.reduce((sum, item) => sum + (item.travelDurationMin ?? 0), 0) : null,
      walkKm: walkM > 0 ? Math.round(walkM / 100) / 10 : null,
      costKrw: typeof itinerary.totalEstimatedCostKrw === 'number' ? itinerary.totalEstimatedCostKrw : null,
    },
    status: 'ESTIMATED',
    rationale: null,
    itineraryId: itinerary.id,
  };
}

/**
 * 코스 3안을 받아 온다.
 *
 * 🔴 서버가 아직 이 계약을 안 내면(404·501, 또는 `courses` 칸이 없음) **만들어진 일정
 *    하나를 한 안으로** 보여 준다. 그때 `full: false` 로 알려서, 화면이 「3안 비교는 아직」을
 *    말할 수 있게 한다. 조용히 한 안만 그리면 사용자는 비교를 놓친 줄도 모른다.
 */
export async function loadTripCourses(
  tripId: string,
  fallbackItineraryId: string | null,
  accessToken: string | null,
): Promise<TripCoursesResult> {
  const t = (ko: string) => ko;
  let dto: CoursesDto | null = null;
  try {
    dto = await apiRequest<CoursesDto>(`/api/v1/trips/${encodeURIComponent(tripId)}/recommendations`, { accessToken });
  } catch (error) {
    // 아직 없는 자리(404·501)는 실패가 아니다 — 아래에서 일정 하나로 대신한다.
    const missing = error instanceof ApiClientError && (error.status === 404 || error.status === 501);
    if (!missing) {
      return { state: 'error', message: error instanceof Error ? error.message : t('코스를 불러오지 못했어요.') };
    }
  }

  const courses = (dto?.courses ?? []).map(adaptCourse).filter((course) => course.id !== '' && course.days.length > 0);
  if (courses.length > 0) return { state: 'success', courses, full: courses.length >= 2 };

  if (!fallbackItineraryId) {
    return { state: 'empty', message: t('아직 보여 드릴 코스가 없어요.') };
  }
  const outcome = await loadItinerary(fallbackItineraryId, accessToken);
  if (outcome.state !== 'success') {
    return { state: 'error', message: outcome.message };
  }
  return { state: 'success', courses: [courseFromItinerary(outcome.itinerary)], full: false };
}
