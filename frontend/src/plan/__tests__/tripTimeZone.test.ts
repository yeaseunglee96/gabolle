// 기기 시간대가 부산(+09:00)이 아니어도 여행 시각은 여행지 벽시계로 보인다 (S15P21E201-2043).
// 10/10 점검: GMT 폰에서 티켓이 「첫 일정 00:09 / 마지막 일정 10:00」(실제 09:09/19:00)을 찍었다.
import { buildTripPass } from '@/plan/tripPassData';
import { formatClock } from '@/i18n/datetime';

// 🔴 jest 안에서 process.env.TZ 를 바꿔도 Date 에 안 먹는다(샌드박스의 env 는 사본이다).
// CI 러너(도커)는 UTC 라 거기서 이 시험이 실제로 문다. 손으로는 `TZ=UTC npx jest tripTimeZone` 로 돌린다 —
// 고치기 전 코드에서 둘 다 00:09 를 받고 실패하는 것을 확인했다.

describe('기기 시간대가 GMT 일 때', () => {
  it('티켓의 첫·마지막 시각이 밀리지 않는다', () => {
    const pass = buildTripPass({
      itinerary: { id: 'x1', title: 't', version: 1, days: [{ date: '2026-10-11', items: [
        { id: 'a', startsAt: '2026-10-11T09:09:00+09:00', title: '해운대', locked: false, placeId: 'p1' } as never,
        { id: 'b', startsAt: '2026-10-11T19:00:00+09:00', title: '광안리', locked: false, placeId: 'p2' } as never,
      ] }] } as never,
      origin: '서면역', startDate: '2026-10-11', endDate: '2026-10-11', transport: 'TRANSIT',
      ownerName: 'a', baseUrl: 'https://x', language: 'ko',
    });
    expect(pass.startTime).toBe('09:09');
    expect(pass.endTime).toBe('19:00');
  });

  it('일정 고치기 화면의 시각(formatClock)이 밀리지 않는다', () => {
    expect(formatClock('2026-10-11T09:09:00+09:00', 'ko')).toBe('09:09');
    expect(formatClock('2026-10-11T12:45:00+09:00', 'vi')).toBe('12:45');
  });
});
