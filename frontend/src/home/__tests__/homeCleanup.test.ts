// 홈 정리 — 「지금 열리는 축제」 · 홈 「내 여행」 — S15P21E201-1594.
//
// 🔴 이 시험이 지키는 것:
//    ① 축제는 오늘부터 60일, 이 기기의 날짜로 묻는다 — UTC 로 세면 한국 아침엔 어제부터 묻는다.
//    ② 홈 「내 여행」은 날짜로 고른다. 서버 status 는 배치로 늦어서, 날짜가 지난 9/19 여행이 서버에서 아직
//       READY 라는 이유로 오늘 여행보다 먼저 나왔다(실측).
import type { Festival } from '@/discovery/festivals';
import { festivalCards, festivalWindow, pickActiveTrip } from '@/home/useHomeData';
import type { TripSummaryDto } from '@/trip/trips';

const trip = (tripId: string, startDate: string | null, endDate: string | null, status: TripSummaryDto['status'] = 'READY'): TripSummaryDto => ({
  tripId, title: tripId, startDate, endDate, dayCount: 2, partySize: 2, status, role: 'OWNER', createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
  coverImageUrl: null, firstStopNameKo: null, firstStopNameEn: null,
});
// 2026-09-24 한국 오전 8시 — UTC 로는 아직 23일이다.
const NOW = new Date(2026, 8, 24, 8, 0, 0);

describe('「지금 열리는 축제」 조회 기간', () => {
  it('오늘부터 60일 — 이 기기의 날짜로 센다', () => {
    expect(festivalWindow(NOW)).toEqual({ startDate: '2026-09-24', endDate: '2026-11-23' });
  });

  it('축제를 홈 카드로 — 이름은 행사 제목, 같은 장소가 두 번 오면 한 장', () => {
    const festival = (placeId: string, title: string | null): Festival => ({ placeId, title, nameKo: `장소-${placeId}`, address: '부산 수영구', startDate: '2026-10-01', endDate: '2026-10-05', overlapDates: [], photoUrl: 'https://img/p.jpg' });
    const cards = festivalCards([festival('p1', '광안리 어방축제'), festival('p1', '광안리 어방축제'), festival('p2', null)]);
    expect(cards.map((card) => [card.placeId, card.nameKo])).toEqual([['p1', '광안리 어방축제'], ['p2', '장소-p2']]);
    expect(cards[0]).toMatchObject({ address: '부산 수영구', photoUrl: 'https://img/p.jpg' });
  });
});

describe('홈 「내 여행」 — 날짜로 고른다', () => {
  it('🔴 오늘이 기간 안인 여행이 먼저 — 서버 status 가 아직 READY 여도', () => {
    const today = trip('today', '2026-09-24', '2026-09-25', 'READY');
    const next = trip('next', '2026-09-27', '2026-09-28', 'READY');
    expect(pickActiveTrip([next, today], NOW)?.tripId).toBe('today');
  });

  it('🔴 끝난 날짜의 여행은 뺀다 — 서버가 아직 COMPLETED 로 안 바꿨어도', () => {
    const past = trip('past', '2026-09-19', '2026-09-20', 'READY');
    const next = trip('next', '2026-10-03', '2026-10-05', 'READY');
    expect(pickActiveTrip([past, next], NOW)?.tripId).toBe('next');
    expect(pickActiveTrip([past], NOW)).toBeNull();
  });

  it('진행 중이 없으면 가장 먼저 떠나는 예정 여행', () => {
    const later = trip('later', '2026-10-10', '2026-10-11');
    const sooner = trip('sooner', '2026-09-30', '2026-10-01');
    expect(pickActiveTrip([later, sooner], NOW)?.tripId).toBe('sooner');
  });

  it('서버가 COMPLETED 라고 한 여행은 뺀다 · 날짜를 모르는 여행은 날짜 있는 예정 여행 뒤', () => {
    const done = trip('done', '2026-09-24', '2026-09-26', 'COMPLETED');
    const undated = trip('undated', null, null, 'PLANNING');
    const next = trip('next', '2026-10-03', '2026-10-05');
    expect(pickActiveTrip([done, undated, next], NOW)?.tripId).toBe('next');
    expect(pickActiveTrip([done, undated], NOW)?.tripId).toBe('undated');
  });

  it('하루짜리 여행(오는 날 없음)도 그날은 진행 중이다', () => {
    const dayTrip = trip('day', '2026-09-24', null);
    const next = trip('next', '2026-09-25', '2026-09-26');
    expect(pickActiveTrip([next, dayTrip], NOW)?.tripId).toBe('day');
  });
});
