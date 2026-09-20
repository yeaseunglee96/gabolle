// 여행 카드의 「언제인가」 — 날짜 계산은 눈으로 검산이 안 된다.
import { tripStatusLabel, tripTimingLabel } from '@/trip/tripStatus';

const KO = (ko: string) => ko;
const now = new Date(2026, 8, 21, 15, 0, 0); // 2026-09-21 오후

describe('여행이 언제인가', () => {
  it('앞으로면 「n일 뒤 출발」, 하루 전이면 「내일」, 당일이면 「오늘」', () => {
    expect(tripTimingLabel({ startDate: '2026-09-24', endDate: '2026-09-26' }, KO, now)).toBe('3일 뒤 출발');
    expect(tripTimingLabel({ startDate: '2026-09-22', endDate: '2026-09-22' }, KO, now)).toBe('내일 출발');
    expect(tripTimingLabel({ startDate: '2026-09-21', endDate: '2026-09-23' }, KO, now)).toBe('오늘 출발');
  });

  it('여행 중이면 며칠째인지', () => {
    expect(tripTimingLabel({ startDate: '2026-09-20', endDate: '2026-09-23' }, KO, now)).toBe('여행 2일째');
  });

  it('끝났으면 「지난 여행」, 날짜가 없으면 아무 말도 안 한다 — 지어내지 않는다', () => {
    expect(tripTimingLabel({ startDate: '2026-09-10', endDate: '2026-09-12' }, KO, now)).toBe('지난 여행');
    expect(tripTimingLabel({ startDate: null, endDate: null }, KO, now)).toBeNull();
    expect(tripTimingLabel({ startDate: 'nope', endDate: null }, KO, now)).toBeNull();
  });

  it('상태 이름 — 모르는 값은 예정으로', () => {
    expect(tripStatusLabel('IN_PROGRESS', KO)).toBe('진행 중');
    expect(tripStatusLabel('SOMETHING_NEW', KO)).toBe('예정');
  });
});
