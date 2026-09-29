// 장소 상세가 서버 값을 사람이 읽는 말로 옮기는가 — S15P21E201-1202.
import { formatFeatureSlot, formatOpeningHoursValue, formatSlopePercent } from '../places';
import type { Place } from '../places';

const tx = (ko: string) => ko;

const SLOPE_VALUE = { score: 2.7, radiusM: 200.0, segments: 25.0, walkLengthM: 8227.0 };
const HOURS_VALUE = {
  raw: { restField: null, restValue: null, hoursField: 'playtime', hoursValue: '10:00~20:00' },
  byDay: {
    mon: [['10:00', '20:00']], tue: [['10:00', '20:00']], wed: [['10:00', '20:00']],
    thu: [['10:00', '20:00']], fri: [['10:00', '20:00']], sat: [['10:00', '20:00']],
    sun: [['10:00', '20:00']],
  },
  notes: [],
  status: 'PARSED',
};

function placeWith(featureType: string, value: unknown, evidenceStatus = 'ESTIMATED'): Place {
  return { features: [{ featureType, value, evidenceStatus }] } as unknown as Place;
}

describe('경사도 — JSON 을 화면에 내보내지 않는다', () => {
  it('🔴 서버가 객체로 줘도 사람이 읽는 값이 나간다', () => {
    const text = formatSlopePercent(placeWith('SLOPE_PERCENT', SLOPE_VALUE), tx);
    expect(text).toBe('완만해요 · 2.7% (추정)');
  });

  it('🔴 어떤 모양이 와도 중괄호가 화면에 안 나간다', () => {
    for (const value of [SLOPE_VALUE, { radiusM: 200 }, { nested: { a: 1 } }, [1, 2], 'x']) {
      const text = formatSlopePercent(placeWith('SLOPE_PERCENT', value), tx) ?? '';
      expect(text).not.toContain('{');
      expect(text).not.toContain('"');
    }
  });

  it('숫자로 오면 예전처럼 % 를 붙인다', () => {
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', 3.5), tx)).toBe('완만해요 · 3.5% (추정)');
  });

  it('못 읽는 모양이면 JSON 이 아니라 문장으로 물러선다', () => {
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', { radiusM: 200 }), tx))
      .toBe('확인했지만 형식을 읽지 못했어요');
  });
});

describe('경사 — 숫자보다 말을 먼저(지도 경사 색과 같은 문턱)', () => {
  it('5% 미만은 완만 · 8.33% 까지는 조금 가파름 · 그 위는 가파름', () => {
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', 4.9, 'VERIFIED'), tx)).toBe('완만해요 · 4.9%');
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', 8.33, 'VERIFIED'), tx)).toBe('조금 가파라요 · 8.33%');
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', 12, 'VERIFIED'), tx)).toBe('가파라요 · 12%');
  });
});

describe('영업시간 — 서버가 읽었다고 한 것을 화면도 읽는다', () => {
  it('🔴 이어진 요일의 같은 시간은 한 덩어리로, 쉬는 날(closedDays)은 따로 말한다 — 운영 국제시장 응답', () => {
    const day = [['09:00', '20:00']];
    const value = { byDay: { mon: day, tue: day, wed: day, thu: day, fri: day, sat: day, sun: [] }, closedDays: ['sun'], status: 'PARSED' };
    expect(formatOpeningHoursValue(value, tx)).toBe('월–토 09:00~20:00\n일요일 휴무');
  });

  it('둘만 이어지면 가운뎃점, 끊기면 따로', () => {
    const a = [['10:00', '18:00']]; const b = [['11:00', '15:00']];
    const value = { byDay: { mon: a, tue: a, thu: a, sat: b, sun: b } };
    expect(formatOpeningHoursValue(value, tx)).toBe('월·화 10:00~18:00 · 목 10:00~18:00 · 토·일 11:00~15:00');
  });

  it('closedDays 가 없으면 쉬는 날 줄도 없다 — 모르는 것을 지어내지 않는다', () => {
    const day = [['09:00', '20:00']];
    expect(formatOpeningHoursValue({ byDay: { mon: day, tue: day, wed: day } }, tx)).toBe('월–수 09:00~20:00');
  });

  it('🔴 일곱 요일이 같으면 「매일」 한 줄로 묶는다', () => {
    expect(formatOpeningHoursValue(HOURS_VALUE, tx)).toBe('매일 10:00~20:00');
  });

  it('요일마다 다르면 요일별로 그린다 — 월요일부터', () => {
    const value = { byDay: { mon: [['09:00', '18:00']], sat: [['10:00', '15:00']] } };
    expect(formatOpeningHoursValue(value, tx)).toBe('월 09:00~18:00 · 토 10:00~15:00');
  });

  it('byDay 가 없으면 raw 안의 문자열을 쓴다', () => {
    const value = { raw: { hoursField: 'playtime', hoursValue: '10:00~20:00' } };
    expect(formatOpeningHoursValue(value, tx)).toBe('10:00~20:00');
  });

  it('읽을 것이 없으면 null 을 돌려 부르는 쪽이 물러서게 한다 — 여기서 지어내지 않는다', () => {
    expect(formatOpeningHoursValue({ notes: [], status: 'FAILED' }, tx)).toBeNull();
    expect(formatOpeningHoursValue(null, tx)).toBeNull();
  });

  it('🔴 화면에 실제로 나가는 글자에도 중괄호가 없다', () => {
    const shown = formatFeatureSlot({ value: HOURS_VALUE, evidenceStatus: 'ESTIMATED' } as never, tx) ?? '';
    expect(shown).toBe('매일 10:00~20:00 (추정)');
    expect(shown).not.toContain('{');
  });
});
