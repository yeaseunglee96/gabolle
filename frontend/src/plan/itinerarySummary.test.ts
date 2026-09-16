import { formatTravelLabel, itineraryStats, totalTravelMinutes } from './itinerarySummary';
import type { ItineraryDto, ItineraryItemDto } from './itinerary';

// S15P21E201-1014 — 완료 기준 둘을 여기서 잰다.
// ① 「지연」이라는 말이 안 나온다 — 이동 시간으로 바뀌었다
// ② 값이 없는 「미확인」 칸이 없다
const tx = (ko: string) => ko;

const item = (extra: Partial<ItineraryItemDto> = {}): ItineraryItemDto => ({
  id: 'i1', startsAt: '2026-09-20T09:00:00+09:00', title: '흰여울문화마을', locked: false, placeId: 'p1', ...extra,
});

const itinerary = (items: ItineraryItemDto[], days = 1): ItineraryDto => ({
  id: 'it1', title: '부산 2일', version: 3,
  days: Array.from({ length: days }, (_, index) => ({ date: `2026-09-2${index}`, items: index === 0 ? items : [] })),
});

describe('이동 시간 문구', () => {
  it('🔴 「지연」이 아니라 이동 시간으로 말한다', () => {
    const label = formatTravelLabel(item({ travelDurationMin: 38 }), tx);
    expect(label).toBe('이동 38분');
    expect(label).not.toContain('지연');
  });

  it('어림값이면 어림이라고 붙인다 — 실제 소요시간처럼 그리면 그 시간에 맞춰 움직이다 늦는다', () => {
    expect(formatTravelLabel(item({ travelDurationMin: 12, travelDataStatus: 'ESTIMATED' }), tx)).toBe('이동 12분 (어림)');
  });

  it('구간이 없으면(그날 첫 방문지) 아무 말도 만들지 않는다', () => {
    expect(formatTravelLabel(item({ travelDurationMin: null }), tx)).toBeNull();
    expect(formatTravelLabel(item(), tx)).toBeNull();
  });
});

describe('일정 통계', () => {
  it('🔴 값이 없는 비용·도보 칸을 만들지 않는다', () => {
    const stats = itineraryStats(itinerary([item(), item({ id: 'i2' })]), tx);
    expect(stats.map((stat) => stat.label)).toEqual(['방문지', '여행 기간']);
    expect(JSON.stringify(stats)).not.toContain('미확인');
  });

  it('이동 시간이 있을 때만 그 칸이 생긴다', () => {
    const stats = itineraryStats(itinerary([item({ travelDurationMin: 20 }), item({ id: 'i2', travelDurationMin: 18 })]), tx);
    expect(stats.find((stat) => stat.key === 'travel')?.value).toBe('38분');
  });

  it('고정된 장소가 없으면 그 칸도 없다', () => {
    expect(itineraryStats(itinerary([item()]), tx).some((stat) => stat.key === 'locked')).toBe(false);
    expect(itineraryStats(itinerary([item({ locked: true })]), tx).some((stat) => stat.key === 'locked')).toBe(true);
  });
});

describe('이동 시간 합계', () => {
  it('못 잰 구간은 빼고 더한다', () => {
    expect(totalTravelMinutes([item({ travelDurationMin: 10 }), item({ id: 'i2' }), item({ id: 'i3', travelDurationMin: 5 })])).toBe(15);
  });
});
