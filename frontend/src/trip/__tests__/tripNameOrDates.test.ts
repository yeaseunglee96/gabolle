// 여행을 부르는 이름 — S15P21E201-1738. 2026-09-26 실기: 같은 여행이 상세 「부산 여행」, 목록 「9월 26일 (토)」,
// 알림 「09.26 여행」, 홈 「09.26 ~ 09.26」이었다. 이 함수 하나로 맞춘다.
import { formatDayHeading } from '@/i18n/datetime';
import { tripDatesLabel, tripNameOrDates } from '@/trip/tripNaming';

const tx = (ko: string) => ko;
const LOCALE = 'ko-KR';
// 날짜 덩어리 안의 빈칸도 끊기지 않는 빈칸이다(S15P21E201-1988).
const day = (iso: string) => (formatDayHeading(iso, LOCALE) ?? iso).replace(/ /g, ' ');

describe('tripNameOrDates', () => {
  it('사람이 붙인 이름이 있으면 그것', () => {
    expect(tripNameOrDates({ title: '광안리 한 바퀴', startDate: '2026-09-26', endDate: '2026-09-26' }, tx, LOCALE)).toBe('광안리 한 바퀴');
  });

  it('🔴 이름이 없으면 날짜 — 「부산 여행」처럼 붙인 적 없는 이름을 지어내지 않는다', () => {
    const out = tripNameOrDates({ title: null, startDate: '2026-09-26', endDate: '2026-09-26' }, tx, LOCALE);
    expect(out).toBe(day('2026-09-26'));
    expect(out).not.toContain('부산 여행');
  });

  it('🔴 서버의 자리표시 제목(「2026-09-21 ~ 2026-09-23」)은 이름이 아니다', () => {
    expect(tripNameOrDates({ title: '2026-09-21 ~ 2026-09-23', startDate: '2026-09-21', endDate: '2026-09-23' }, tx, LOCALE))
      .toBe(`${day('2026-09-21')} –\u00A0${day('2026-09-23')}`);
  });

  it('여러 날이면 시작 – 끝, 하루면 하루만', () => {
    expect(tripDatesLabel('2026-09-26', '2026-09-28', LOCALE)).toBe(`${day('2026-09-26')} –\u00A0${day('2026-09-28')}`);
    // 🔴 대시는 뒤 날짜에 붙는다 — 꺾이면 「– 9월 28일」이 다음 줄로 간다(S15P21E201-1986)
    expect(tripDatesLabel('2026-09-26', '2026-09-28', LOCALE)).not.toMatch(/– /);
    expect(tripDatesLabel('2026-09-26', '2026-09-26', LOCALE)).toBe(day('2026-09-26'));
  });

  it('🔴 큰 글씨에서 날짜 가운데(「10월 / 12일」)가 꺾이지 않게 — 보통 빈칸이 하나도 없다(S15P21E201-1988)', () => {
    // 꺾일 수 있는 보통 빈칸은 대시 앞 하나뿐
    expect(tripDatesLabel('2026-10-10', '2026-10-12', LOCALE)!.split(' ')).toHaveLength(2);
    expect(tripDatesLabel('2026-10-10', '2026-10-10', LOCALE)).not.toMatch(/ /);
  });

  it('이름도 날짜도 없으면 「날짜 미정」 — 날짜를 지어내지 않는다', () => {
    expect(tripNameOrDates({ title: '  ', startDate: null, endDate: null }, tx, LOCALE)).toBe('날짜 미정');
  });
});
