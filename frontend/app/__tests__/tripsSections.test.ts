// 내 여행 — 여행을 «언제»로 나눈다(UI 캔버스 ⑥). 만든 순서로 섞여 있던 것을 지금·다가오는·지난·못 만든 것으로.
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }), useLocalSearchParams: () => ({}) }));

import { ddayLabel, splitTrips } from '../(tabs)/trips';

const tx = (ko: string) => ko;
const NOW = new Date(2026, 8, 29, 10, 0, 0); // 2026-09-29 오전
const trip = (id: string, startDate: string | null, endDate: string | null, status = 'READY') => ({
  tripId: id, title: null, startDate, endDate, dayCount: 2, partySize: 1, status, role: 'OWNER', createdAt: '', updatedAt: '',
  coverImageUrl: null, firstStopNameKo: null, firstStopNameEn: null,
}) as never;

describe('내 여행 나누기', () => {
  it('🔴 오늘이 걸친 여행은 지금 · 앞은 다가오는(가까운 순) · 끝난 것은 지난 · 날짜 지난 PLANNING 은 못 만든 것', () => {
    const { live, upcoming, past, failed } = splitTrips([
      trip('far', '2026-11-11', '2026-11-12'),
      trip('now', '2026-09-28', '2026-09-30'),
      trip('soon', '2026-09-30', '2026-10-03'),
      trip('old', '2026-09-01', '2026-09-02'),
      trip('broken', '2026-09-10', '2026-09-11', 'PLANNING'),
      trip('pending', '2026-10-20', '2026-10-21', 'PLANNING'),
    ], NOW);
    expect(live.map((t: { tripId: string }) => t.tripId)).toEqual(['now']);
    expect(upcoming.map((t: { tripId: string }) => t.tripId)).toEqual(['soon', 'pending', 'far']);
    expect(past.map((t: { tripId: string }) => t.tripId)).toEqual(['old']);
    expect(failed.map((t: { tripId: string }) => t.tripId)).toEqual(['broken']);
  });
});

describe('D-day 표', () => {
  it('오늘 · 내일은 진하게, 그 뒤는 D-n, 지난 날짜는 없음', () => {
    expect(ddayLabel({ startDate: '2026-09-29' }, tx, NOW)).toEqual({ text: '오늘', soon: true });
    expect(ddayLabel({ startDate: '2026-09-30' }, tx, NOW)).toEqual({ text: '내일', soon: true });
    expect(ddayLabel({ startDate: '2026-10-08' }, tx, NOW)).toEqual({ text: 'D-9', soon: false });
    expect(ddayLabel({ startDate: '2026-09-20' }, tx, NOW)).toBeNull();
  });
});
