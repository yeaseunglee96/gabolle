// 추천 코스 카드가 «출처 없이» 사진을 걸지 못하게 한다 — S15P21E201-1496.
//
// 🔴 여기 실리는 사진은 TourAPI 등 공공누리 자료라 «출처 표기가 이용 조건»이다.
//    서버는 photoUrl 과 photoSource 를 짝으로 보내는데(RecommendationResultResponse 주석:
//    "주소만 보내면 화면이 출처 없이 사진을 건다"), 화면이 주소만 읽으면 그 조건을 깬다.
//
//    그래서 이 파일이 지키는 것은 둘이다.
//      ① 출처가 없는 사진은 «안 고른다» — 안 보이는 것은 눈에 띄어 고쳐지지만,
//         출처 없이 걸린 것은 아무도 모른다
//      ② 응답의 photoSource 가 CourseStop 까지 «실제로» 실려 온다 — 파싱에서 흘리면
//         모든 사진이 조용히 사라진다(①이 전부 걸러 낸다)
import { adaptCourse } from '@/plan/tripCourses';

// CourseCard 가 사진을 고르는 규칙 그대로. 화면 파일은 React 라 여기서 못 부르므로
// 같은 조건을 두고, 규칙이 바뀌면 이 시험이 먼저 어긋나게 둔다.
const pickPhotos = (stops: Array<{ photoUrl: string | null; photoSource: string | null; name: string }>) =>
  stops
    .filter((stop) => stop.photoUrl && stop.photoSource)
    .slice(0, 3)
    .map((stop) => ({ url: stop.photoUrl as string, name: stop.name, source: stop.photoSource as string }));

const stop = (name: string, url: string | null, source: string | null) => ({
  placeId: 'p-' + name, name, time: null, note: null, photoUrl: url, photoSource: source, lat: null, lng: null,
});

describe('코스 표지 사진과 출처', () => {
  it('🔴 출처가 없는 사진은 안 고른다 — 걸면 공공누리 이용 조건을 깬다', () => {
    const picked = pickPhotos([
      stop('해운대', 'https://x/1.jpg', null),
      stop('감천문화마을', 'https://x/2.jpg', '한국관광공사'),
    ]);
    expect(picked.map((p) => p.name)).toEqual(['감천문화마을']);
  });

  it('출처가 빈 문자열이어도 안 고른다 — 「출처 없음」을 빈 칸으로 넘기지 않는다', () => {
    expect(pickPhotos([stop('해운대', 'https://x/1.jpg', '')])).toEqual([]);
  });

  it('둘 다 있으면 고른다 — 기능을 끈 것이 아니다', () => {
    const picked = pickPhotos([stop('광안리', 'https://x/3.jpg', '부산광역시')]);
    expect(picked).toHaveLength(1);
    expect(picked[0].source).toBe('부산광역시');
  });
});

describe('응답 파싱', () => {
  it('🔴 photoSource 를 CourseStop 까지 싣는다 — 여기서 흘리면 사진이 통째로 사라진다', () => {
    const course = adaptCourse({
      id: 'c-1', title: '부산 2박 3일',
      days: [{ day: 1, stops: [{ placeId: 'p-1', name: '감천문화마을',
                                 photoUrl: 'https://x/2.jpg', photoSource: '한국관광공사' }] }],
    } as never);
    const found = course.days[0].stops[0];
    expect(found.photoSource).toBe('한국관광공사');
    expect(found.photoUrl).toBe('https://x/2.jpg');
  });

  it('출처가 안 오면 null 이다 — 빈 문자열로 남기지 않는다', () => {
    const course = adaptCourse({
      id: 'c-1', title: 'x',
      days: [{ day: 1, stops: [{ placeId: 'p-1', name: '해운대', photoUrl: 'https://x/1.jpg' }] }],
    } as never);
    expect(course.days[0].stops[0].photoSource).toBeNull();
  });
});
