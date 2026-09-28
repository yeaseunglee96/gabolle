// 영어 화면의 시간·개수 — S15P21E201-1683 (iOS 심사 공지의 알려진 문제 1번).
//
// 🔴 이 시험이 지키는 것:
//    ① 이동 시간을 「25m travel」처럼 m 으로 줄여 적어서 미터로 읽혔다 — 「25 min travel」.
//    ② 「1 travelers」 — 하나면 단수. 날수도 「1 day」.
import type { ItineraryDto, ItineraryItemDto } from '@/plan/itinerary';
import { formatTravelLabel, itineraryStats } from '@/plan/itinerarySummary';
import { enCount } from '@/i18n/format';

const en = (_ko: string, english: string) => english;
const item = (travel: number | null, estimated = true): ItineraryItemDto => ({
  id: 'i', startsAt: '2030-10-03T10:00:00', title: 'x', locked: false, placeId: 'p',
  travelDurationMin: travel, travelDataStatus: travel === null ? null : estimated ? 'ESTIMATED' : 'VERIFIED',
});

describe('영어 — 이동 시간은 min', () => {
  it('🔴 m 으로 줄이지 않는다 — 미터로 읽힌다', () => {
    expect(formatTravelLabel(item(25), en)).toBe('25 min travel (est.)');
    expect(formatTravelLabel(item(25, false), en)).toBe('25 min travel');
    expect(formatTravelLabel(item(40), en, true)).toBe('40 min from start (est.)');
    expect(formatTravelLabel(item(18, false), en, 'LODGING')).toBe('18 min from your stay');
  });

  it('여정 요약의 총 이동 시간·날수', () => {
    const itinerary = { id: 'it', days: [{ date: '2030-10-03', items: [item(25), item(20)] }] } as unknown as ItineraryDto;
    const stats = itineraryStats(itinerary, en);
    expect(stats.find((s) => s.key === 'travel')?.value).toBe('45 min');
    expect(stats.find((s) => s.key === 'days')?.value).toBe('1 day');
  });
});

describe('영어 — 개수의 단수·복수', () => {
  it('🔴 하나면 단수 — 「1 traveler」·「1 day」', () => {
    expect(enCount(1, 'traveler', 'travelers')).toBe('1 traveler');
    expect(enCount(2, 'traveler', 'travelers')).toBe('2 travelers');
    expect(enCount(1, 'day', 'days')).toBe('1 day');
    expect(enCount(0, 'day', 'days')).toBe('0 days');
  });
});
