// 이 시험이 지키는 것은 「티켓에 찍힌 숫자가 실제 일정의 것인가」다.
// 티켓은 그럴듯하게 생겨서, 틀린 값이 찍혀도 사람이 눈으로는 못 잡는다.
import { buildTripPass, buildTripPassDetails, shortenOrigin, tripPassCode, tripPassUrl, type TripPassInput } from '@/plan/tripPassData';
import type { ItineraryDto } from '@/plan/itinerary';

const itinerary = (over: Partial<ItineraryDto> = {}): ItineraryDto => ({
  id: 'a1b2c3d4-5e6f-7788-99aa-bbccddeeff00',
  title: '부산 이틀',
  version: 1,
  days: [
    { date: '2026-09-20', items: [
      { id: 'i1', startsAt: '2026-09-20T10:30:00', title: '감천문화마을', locked: false, placeId: 'p1' } as never,
      { id: 'i2', startsAt: '2026-09-20T14:00:00', title: '자갈치시장', locked: false, placeId: 'p2' } as never,
    ] },
    { date: '2026-09-21', items: [
      { id: 'i3', startsAt: '2026-09-21T11:00:00', title: '해운대해수욕장', locked: false, placeId: 'p3' } as never,
    ] },
  ],
  totalEstimatedCostKrw: 78000,
  totalWalkingMeters: 3200,
  ...over,
});

const input = (over: Partial<TripPassInput> = {}): TripPassInput => ({
  itinerary: itinerary(),
  origin: '부산역',
  startDate: '2026-09-20',
  endDate: '2026-09-21',
  transport: 'TRANSIT',
  travelers: 2,
  ownerName: '장효준',
  baseUrl: 'https://j15e201.p.ssafy.io',
  language: 'ko',
  ...over,
});

describe('여행 티켓에 찍히는 값', () => {
  it('실제 일정의 날짜·시각·방문지 수를 쓴다', () => {
    const pass = buildTripPass(input());
    expect(pass.dateRange).toBe('9.20(일) – 9.21(월)');
    expect(pass.startTime).toBe('10:30');
    expect(pass.endTime).toBe('11:00');
    expect(pass.fields).toContainEqual({ key: '방문지', value: '3곳' });
    expect(pass.fields).toContainEqual({ key: '일정', value: '2일' });
  });

  it('🔴 초안 날짜가 아니라 일정 날짜를 쓴다 — 요청한 날짜와 만들어진 날짜는 다를 수 있다', () => {
    const pass = buildTripPass(input({ startDate: '2026-01-01', endDate: '2026-01-02' }));
    expect(pass.dateRange).toBe('9.20(일) – 9.21(월)');
  });

  it('일정이 아직 없으면 초안 날짜로 버틴다', () => {
    const pass = buildTripPass(input({ itinerary: null }));
    expect(pass.dateRange).toBe('9.20(일) – 9.21(월)');
    expect(pass.startTime).toBeNull();
  });

  it('🔴 모르는 칸은 0 이 아니라 아예 안 만든다', () => {
    const pass = buildTripPass(input({
      itinerary: itinerary({ totalEstimatedCostKrw: null, totalWalkingMeters: null }),
      travelers: null,
    }));
    const keys = pass.fields.map((f) => f.key);
    expect(keys).not.toContain('예상 비용');
    expect(keys).not.toContain('걷는 거리');
    expect(keys).not.toContain('인원');
    expect(pass.fields.every((f) => f.value !== '0' && f.value !== '-')).toBe(true);
  });

  it('🔴 걷는 거리 0 을 「0m」로 찍지 않는다 — 「안 걷는다」와 「모른다」는 다르다', () => {
    const pass = buildTripPass(input({ itinerary: itinerary({ totalWalkingMeters: 0 }) }));
    expect(pass.fields.map((f) => f.key)).not.toContain('걷는 거리');
  });

  it('🔴 날짜만 있는 값에서 시각을 지어내지 않는다', () => {
    const only = itinerary({ days: [{ date: '2026-09-20', items: [
      { id: 'i1', startsAt: '2026-09-20', title: '감천문화마을', locked: false, placeId: 'p1' } as never,
    ] }] });
    expect(buildTripPass(input({ itinerary: only })).startTime).toBeNull();
  });

  it('칸은 여섯을 넘지 않는다 — 시안 그리드가 3열 2줄이다', () => {
    expect(buildTripPass(input()).fields.length).toBeLessThanOrEqual(6);
  });

  it('걷는 거리는 1km 부터 km 로 바꾼다', () => {
    expect(buildTripPass(input({ itinerary: itinerary({ totalWalkingMeters: 850 }) })).fields)
      .toContainEqual({ key: '걷는 거리', value: '850m' });
    expect(buildTripPass(input({ itinerary: itinerary({ totalWalkingMeters: 3200 }) })).fields)
      .toContainEqual({ key: '걷는 거리', value: '3.2km' });
  });

  it('모르는 이동 수단은 배지를 안 만든다', () => {
    expect(buildTripPass(input({ transport: 'HOVERBOARD' })).mode).toBeNull();
    expect(buildTripPass(input({ transport: 'TRANSIT' })).mode).toBe('대중교통');
  });

  it('영어에서는 요일과 단위가 같이 바뀐다', () => {
    const pass = buildTripPass(input({ language: 'en' }));
    expect(pass.dateRange).toBe('9.20(Sun) – 9.21(Mon)');
    expect(pass.toLabel).toBe('BUSAN');
    expect(pass.fields).toContainEqual({ key: 'Stops', value: '3' });
  });
});

describe('티켓 코드', () => {
  it('🔴 같은 일정이면 언제나 같은 코드가 나온다 — 바뀌면 다른 여행으로 읽힌다', () => {
    const id = 'a1b2c3d4-5e6f-7788-99aa-bbccddeeff00';
    expect(tripPassCode(id)).toBe(tripPassCode(id));
    expect(tripPassCode(id)).toBe('GB-A1B2C3');
  });

  it('일정이 없으면 코드도 없다 — 지어내지 않는다', () => {
    expect(tripPassCode(null)).toBe('');
    expect(tripPassCode('')).toBe('');
  });
});

describe('출발지 줄이기', () => {
  it('여덟 자까지는 그대로 둔다', () => {
    expect(shortenOrigin('부산역')).toBe('부산역');
    expect(shortenOrigin('김해국제공항')).toBe('김해국제공항');
  });

  it('길면 줄이고 줄였다는 표시를 남긴다', () => {
    expect(shortenOrigin('부산종합버스터미널')).toBe('부산종합버스터…');
  });

  it('없으면 빈 문자열 — 화면이 그 칸을 접는다', () => {
    expect(shortenOrigin(null)).toBe('');
    expect(shortenOrigin('   ')).toBe('');
  });
});

describe('QR 이 담는 주소', () => {
  const base = 'https://j15e201.p.ssafy.io';

  it('일정을 여는 주소를 만든다', () => {
    expect(tripPassUrl('abc-123', base)).toBe(`${base}/trips/abc-123/itinerary`);
  });

  it('🔴 주소 끝의 빗금이 두 번 겹치지 않는다', () => {
    expect(tripPassUrl('abc', `${base}/`)).toBe(`${base}/trips/abc/itinerary`);
  });

  it('🔴 일정 id 나 배포 주소가 없으면 주소를 안 만든다 — 안 열리는 QR 을 그리느니 안 그린다', () => {
    expect(tripPassUrl(null, base)).toBeNull();
    expect(tripPassUrl('abc', null)).toBeNull();
    expect(tripPassUrl('   ', base)).toBeNull();
  });

  it('id 에 이상한 글자가 있어도 주소가 깨지지 않는다', () => {
    expect(tripPassUrl('a b/c', base)).toBe(`${base}/trips/a%20b%2Fc/itinerary`);
  });

  it('티켓 값에 주소가 실린다 — 일정이 없으면 null', () => {
    expect(buildTripPass(input()).url).toBe(`${base}/trips/a1b2c3d4-5e6f-7788-99aa-bbccddeeff00/itinerary`);
    expect(buildTripPass(input({ itinerary: null })).url).toBeNull();
  });
});

describe('여행표 오른쪽 칸 (시안 p4 의 details)', () => {
  const rows = (over: Partial<TripPassInput> = {}) =>
    Object.fromEntries(buildTripPassDetails(input(over)).map((row) => [row.key, row.value]));

  it('출발지 · 첫 일정 · 마지막 일정을 실제 일정에서 가져온다', () => {
    const r = rows();
    expect(r['출발지']).toBe('부산역');
    expect(r['첫 일정']).toBe('10:30 · 감천문화마을');
    expect(r['마지막 일정']).toBe('11:00 · 해운대해수욕장');
  });

  it('🔴 방문지가 하나면 「마지막 일정」을 안 만든다 — 같은 줄을 두 번 적지 않는다', () => {
    const one = itinerary({ days: [{ date: '2026-09-20', items: [
      { id: 'i1', startsAt: '2026-09-20T10:30:00', title: '감천문화마을', locked: false, placeId: 'p1' } as never,
    ] }] });
    expect(rows({ itinerary: one })['마지막 일정']).toBeUndefined();
  });

  it('🔴 모르는 줄은 아예 안 만든다 — 「미확인」으로 채우지 않는다', () => {
    const r = rows({ itinerary: null, origin: null });
    expect(r).toEqual({});
  });

  it('🔴 이동 합계는 값이 있는 구간만 더하고 몇 구간인지 같이 적는다', () => {
    const withLegs = itinerary({ days: [{ date: '2026-09-20', items: [
      { id: 'i1', startsAt: '2026-09-20T10:00:00', title: 'A', locked: false, placeId: 'p1' } as never,
      { id: 'i2', startsAt: '2026-09-20T12:00:00', title: 'B', locked: false, placeId: 'p2', travelDurationMin: 30 } as never,
      { id: 'i3', startsAt: '2026-09-20T15:00:00', title: 'C', locked: false, placeId: 'p3', travelDurationMin: 25 } as never,
    ] }] });
    expect(rows({ itinerary: withLegs })['이동 합계']).toBe('55분 (2구간)');
  });

  it('구간 시간이 하나도 없으면 「이동 합계」 줄이 없다', () => {
    expect(rows()['이동 합계']).toBeUndefined();
  });

  it('예상 비용은 서버가 준 합계를 쓴다', () => {
    expect(rows()['예상 비용']).toBe('7.8만원');
  });
});
