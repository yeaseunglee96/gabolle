// 장소 상세가 서버 값을 **사람이 읽는 말**로 옮기는가 — S15P21E201-1202.
//
// 🔴 이 시험이 지키는 규칙은 하나다: **화면에 JSON 이 나가지 않는다.**
//
//    `S15P21E201-478` 에서 영업시간이 `{"raw":"매일 10:00-22:00"}` 로 찍힌 적이 있고,
//    그때 「JSON 대신 사람이 읽을 문장으로 물러선다」로 고쳤다. 그런데 경사도 함수 하나가
//    그 규칙을 안 거쳐서, 2026-09-18 실기기에서 다시 나왔다 —
//    `{"score":2.7,"radiusM":200,"segments":25,"walkLengthM":8227} (추정)`.
//
//    타입 검사는 이걸 못 잡는다. `unknown` 을 문자열로 만드는 방법이 여럿이고 그중 하나가
//    조용히 틀린 것뿐이다. 그래서 **나가는 글자**를 여기서 본다.
import { formatFeatureSlot, formatOpeningHoursValue, formatSlopePercent } from '../places';
import type { Place } from '../places';

const tx = (ko: string) => ko;

/** 서버가 실제로 주는 모양 그대로 (2026-09-18 영도다리축제 실측). */
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
    expect(text).toBe('2.7% (추정)');
  });

  it('🔴 어떤 모양이 와도 중괄호가 화면에 안 나간다', () => {
    for (const value of [SLOPE_VALUE, { radiusM: 200 }, { nested: { a: 1 } }, [1, 2], 'x']) {
      const text = formatSlopePercent(placeWith('SLOPE_PERCENT', value), tx) ?? '';
      expect(text).not.toContain('{');
      expect(text).not.toContain('"');
    }
  });

  it('숫자로 오면 예전처럼 % 를 붙인다', () => {
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', 3.5), tx)).toBe('3.5% (추정)');
  });

  it('못 읽는 모양이면 JSON 이 아니라 문장으로 물러선다', () => {
    expect(formatSlopePercent(placeWith('SLOPE_PERCENT', { radiusM: 200 }), tx))
      .toBe('확인했지만 형식을 읽지 못했어요');
  });
});

describe('영업시간 — 서버가 읽었다고 한 것을 화면도 읽는다', () => {
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
