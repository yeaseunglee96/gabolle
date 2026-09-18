// 🔴 날짜 계산은 눈으로 검산이 안 된다. 「1박 2일」이 이틀인지 사흘인지, 월이 바뀔 때
//    어떻게 되는지가 여기서 정해진다.
import {
  EMPTY_START_BAR,
  START_BAR_PRESETS,
  addDays,
  canAskForPlan,
  dayCount,
  nightCount,
  summarizeStartBar,
  toDateKey,
  type StartBarValue,
} from '@/home/startBarValue';

const value = (over: Partial<StartBarValue> = {}): StartBarValue => ({ ...EMPTY_START_BAR, ...over });

describe('날짜 세기', () => {
  it('같은 날은 1일 0박 — 당일치기다', () => {
    expect(dayCount('2026-09-20', '2026-09-20')).toBe(1);
    expect(nightCount('2026-09-20', '2026-09-20')).toBe(0);
  });

  it('하루 차이는 2일 1박이다', () => {
    expect(dayCount('2026-09-20', '2026-09-21')).toBe(2);
    expect(nightCount('2026-09-20', '2026-09-21')).toBe(1);
  });

  it('🔴 달을 넘어도 맞는다', () => {
    expect(dayCount('2026-09-30', '2026-10-02')).toBe(3);
    expect(addDays('2026-09-30', 2)).toBe('2026-10-02');
  });

  it('🔴 해를 넘어도 맞는다', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('🔴 윤년 2월을 안 건너뛴다', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });

  it('거꾸로 된 날짜는 0 이다 — 음수 일수를 만들지 않는다', () => {
    expect(dayCount('2026-09-21', '2026-09-20')).toBe(0);
    expect(nightCount('2026-09-21', '2026-09-20')).toBe(0);
  });

  it('🔴 날짜 열쇠는 그 기기의 날짜다 — UTC 로 만들면 한국에서 하루 밀린다', () => {
    // 한국 시각으로 9월 20일 0시 30분. UTC 로 바꾸면 9월 19일이 된다.
    expect(toDateKey(new Date(2026, 8, 20, 0, 30))).toBe('2026-09-20');
  });
});

describe('한 줄 요약', () => {
  it('채워진 것만 적는다', () => {
    expect(summarizeStartBar(value({ origin: '부산역' }), true)).toBe('부산역 · 성인 2');
  });

  it('🔴 아무것도 안 골랐으면 빈 문자열 — 「미정」으로 채우지 않는다', () => {
    expect(summarizeStartBar(value({ adults: 0 }), true)).toBe('');
  });

  it('🔴 인원만으로는 요약을 만들지 않는다 — 인원에는 기본값(성인 2)이 들어 있다', () => {
    // 아무것도 안 고른 초기 상태. 전에는 여기서 「성인 2」가 나와,
    // 알약에 안내 문구 대신 고른 적 없는 값이 찍혔다.
    expect(summarizeStartBar(value(), true)).toBe('');
    expect(summarizeStartBar(value({ adults: 4, children: 2 }), true)).toBe('');
  });

  it('출발지나 날짜가 하나라도 있으면 그때 인원도 같이 적는다', () => {
    expect(summarizeStartBar(value({ origin: '부산역' }), true)).toContain('성인 2');
    expect(summarizeStartBar(value({ startDate: '2026-09-20' }), true)).toContain('성인 2');
  });

  it('날짜와 박수와 인원을 한 줄로 붙인다', () => {
    expect(summarizeStartBar(value({ origin: '부산역', startDate: '2026-09-20', endDate: '2026-09-21', adults: 2 }), true))
      .toBe('부산역 · 9.20(일) – 9.21(월) · 1박 · 성인 2');
  });

  it('당일치기는 「0박」이 아니라 「당일치기」다', () => {
    expect(summarizeStartBar(value({ startDate: '2026-09-20', endDate: '2026-09-20' }), true))
      .toContain('당일치기');
  });

  it('어린이가 0명이면 그 칸을 안 적는다', () => {
    // 🔴 출발지를 같이 준다 — 인원만으로는 요약이 아예 안 만들어진다(위 시험 참고).
    expect(summarizeStartBar(value({ origin: '부산역', adults: 2, children: 0 }), true)).not.toContain('어린이');
    expect(summarizeStartBar(value({ origin: '부산역', adults: 2, children: 1 }), true)).toContain('어린이 1');
  });
});

describe('일정 물어보기를 누를 수 있나', () => {
  it('출발지와 날짜와 인원이 다 있어야 한다', () => {
    expect(canAskForPlan(value())).toBe(false);
    expect(canAskForPlan(value({ origin: '부산역' }))).toBe(false);
    expect(canAskForPlan(value({ origin: '부산역', startDate: '2026-09-20' }))).toBe(true);
    expect(canAskForPlan(value({ origin: '부산역', startDate: '2026-09-20', adults: 0 }))).toBe(false);
  });

  it('공백만 든 출발지는 없는 것으로 본다', () => {
    expect(canAskForPlan(value({ origin: '   ', startDate: '2026-09-20' }))).toBe(false);
  });
});

describe('바로 시작 프리셋', () => {
  // 2026-09-16 은 수요일이다. 다음 토요일은 9월 19일.
  const wednesday = new Date(2026, 8, 16);

  it('「이번 주말 1박 2일」은 토요일에서 시작한다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'weekend-1n');
    expect(preset?.apply(wednesday)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-20' });
  });

  it('「아이와 당일치기」는 같은 날이고 어린이가 한 명이다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'kid-day');
    expect(preset?.apply(wednesday)).toEqual({ startDate: '2026-09-19', endDate: '2026-09-19', adults: 2, children: 1 });
  });

  it('🔴 오늘이 토요일이면 그날이다 — 일주일 뒤로 밀지 않는다', () => {
    const saturday = new Date(2026, 8, 19);
    expect(START_BAR_PRESETS.find((item) => item.id === 'weekend-1n')?.apply(saturday)?.startDate).toBe('2026-09-19');
  });

  it('출발지 프리셋은 좌표까지 같이 넣는다 — 이름만 넣으면 지도가 못 찍는다', () => {
    const preset = START_BAR_PRESETS.find((item) => item.id === 'from-station');
    const applied = preset?.apply(wednesday);
    expect(applied?.origin).toBe('부산역');
    expect(typeof applied?.originLat).toBe('number');
    expect(typeof applied?.originLng).toBe('number');
  });
});
