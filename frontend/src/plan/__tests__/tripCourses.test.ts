// 코스 3안 — 시안 ③, 인계 §8.
//
// 🔴 이 시험이 지키는 것은 **없는 안을 지어내지 않는가**이다. 서버가 아직 3안을 안 보낼 때
//    화면을 채우려고 두 안을 만들어 내면, 사용자가 고른 대로 여행이 안 나온다. 그리고
//    그 사실을 아무도 모른다 — 고르는 화면에서 이건 가장 나쁜 종류의 거짓말이다.
import { adaptCourse, courseFromItinerary } from '@/plan/tripCourses';
import type { ItineraryDto } from '@/plan/itinerary';

describe('서버가 보낸 한 안을 옮긴다', () => {
  it('일차와 정차지를 그대로 들고 온다', () => {
    const course = adaptCourse({
      id: 'a', title: '바다 따라 여유롭게', tagline: '걷는 시간이 많은 코스',
      days: [{ day: 1, stops: [{ placeId: 'p1', name: '해운대 바다 산책', time: '09:30', note: '아침 산책', photoUrl: 'x.jpg' }] }],
      summary: { places: 9, moveMin: 62, walkKm: 6.4, costKrw: 286000 },
      status: 'CONFIRMED', rationale: '걷는 구간이 길어요', itineraryId: 'it-1',
    });

    expect(course.days[0].stops[0].name).toBe('해운대 바다 산책');
    expect(course.summary).toEqual({ places: 9, moveMin: 62, walkKm: 6.4, costKrw: 286000 });
    expect(course.status).toBe('CONFIRMED');
  });

  /** 🔴 모르는 상태를 「확인됨」으로 떨어뜨리면 확인 안 된 것을 확인됐다고 말하게 된다. */
  it.each([undefined, null, '', 'WHATEVER'])('모르는 상태(%s)는 추정으로 떨어진다', (status) => {
    expect(adaptCourse({ id: 'a', status: status as string }).status).toBe('ESTIMATED');
  });

  it('이름 없는 정차지는 뺀다 — 빈 줄이 화살표 사이에 끼면 「A →  → C」가 된다', () => {
    const course = adaptCourse({
      id: 'a',
      days: [{ day: 1, stops: [{ name: '해운대' }, { name: '' }, { name: '광안리' }] }],
    });

    expect(course.days[0].stops.map((stop) => stop.name)).toEqual(['해운대', '광안리']);
  });

  it('요약이 없으면 정차지 수는 세어서 채우고 나머지는 모른다고 둔다', () => {
    const course = adaptCourse({ id: 'a', days: [{ day: 1, stops: [{ name: 'x' }, { name: 'y' }] }] });

    expect(course.summary.places).toBe(2);
    expect(course.summary.costKrw).toBeNull();
    expect(course.summary.moveMin).toBeNull();
  });
});

describe('일정 하나를 한 안으로 옮긴다', () => {
  const itinerary: ItineraryDto = {
    id: 'it-1', title: '가을 부산', version: 1, tripId: 'trip-1', totalEstimatedCostKrw: 78000,
    days: [{
      date: '2026-10-03',
      items: [
        { id: 'i1', startsAt: '2026-10-03T09:30:00', title: '해운대 바다 산책', description: '아침 산책', placeId: 'p1', locked: false, walkingMeters: 1200, travelDurationMin: 20 },
        { id: 'i2', startsAt: '2026-10-03T12:00:00', title: '동백섬 산책로', placeId: 'p2', locked: false, walkingMeters: 900 },
      ],
    }],
  } as unknown as ItineraryDto;

  it('시각을 시:분으로만 남긴다', () => {
    const course = courseFromItinerary(itinerary);

    expect(course.days[0].stops[0].time).toBe('09:30');
  });

  it('걷는 거리를 km 로 바꾼다', () => {
    expect(courseFromItinerary(itinerary).summary.walkKm).toBe(2.1);
  });

  /**
   * 🔴 **한 안뿐이다.** 서버가 3안을 안 보낼 때 화면을 채우려고 두 안을 만들어 내면,
   * 사용자가 고른 대로 여행이 안 나온다.
   */
  it('🔴 안을 늘리지 않는다 — 있는 것 하나만 옮긴다', () => {
    const course = courseFromItinerary(itinerary);

    expect(course.itineraryId).toBe('it-1');
    expect(course.days).toHaveLength(1);
  });

  /** 🔴 사진 칸이 일정에는 없다. 없는 것을 지어내지 않는다 — 화면이 사진 자리를 접는다. */
  it('사진은 없다고 말한다', () => {
    expect(courseFromItinerary(itinerary).days[0].stops.every((stop) => stop.photoUrl === null)).toBe(true);
  });

  /** 🔴 만들어 낸 안은 언제나 「추정」이다. 서버가 확인해 준 것이 아니다. */
  it('상태는 추정이다', () => {
    expect(courseFromItinerary(itinerary).status).toBe('ESTIMATED');
  });
});
