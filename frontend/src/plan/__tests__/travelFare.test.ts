import { formatTravelLabel } from '@/plan/itinerarySummary';
import type { ItineraryItemDto } from '@/plan/itinerary';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;
const item = (extra: Partial<ItineraryItemDto>): ItineraryItemDto => ({ id: 'a', startsAt: '2026-09-29T10:32:00+09:00', title: '청학시장', locked: false, placeId: 'p', travelDurationMin: 92, travelDataStatus: 'ESTIMATED', ...extra });

describe('구간 요금 (S15P21E201-1833)', () => {
  it('서버가 준 대중교통 요금을 시간 뒤에 붙인다', () => {
    expect(formatTravelLabel(item({ travelFareKrw: 1550 }), ko, 'ORIGIN')).toBe('출발지에서 92분 (어림) · 1,550원');
    expect(formatTravelLabel(item({ travelFareKrw: 1550 }), en)).toBe('92 min travel (est.) · ₩1,550');
  });

  it('요금이 없거나 0이면 안 붙인다 — 걷는 구간·옛 서버', () => {
    expect(formatTravelLabel(item({ travelFareKrw: null }), ko)).toBe('이동 92분 (어림)');
    expect(formatTravelLabel(item({ travelFareKrw: 0 }), ko)).toBe('이동 92분 (어림)');
    expect(formatTravelLabel(item({}), ko)).toBe('이동 92분 (어림)');
  });

  it('시간을 모르면 요금만 떠돌게 하지 않는다', () => {
    expect(formatTravelLabel(item({ travelDurationMin: null, travelFareKrw: 1550 }), ko)).toBeNull();
  });
});
