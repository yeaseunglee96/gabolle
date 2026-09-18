// 조건 질문의 선택지 부제와 「이렇게 반영돼요」 — 시안 ①.
//
// 🔴 이 시험이 지키는 것은 「글자가 예쁜가」가 아니라 **빈 자리가 생기지 않는가**이다.
//    부제가 비면 카드 높이가 줄어 격자가 들쭉날쭉해지고, 무엇보다 고르는 사람이 그 지역에
//    무엇이 들어오는지 모른 채 고른다. 일정이 나온 뒤에야 알게 되는데 그때는 되돌리기 비싸다.
import {
  AREA_OPTIONS, ATMOSPHERE_OPTIONS, CATEGORY_OPTIONS, FOOD_SUBTITLES, PACE_OPTIONS, TRANSPORT_OPTIONS,
  effectOf,
} from '@/plan/planOptions';
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import { PLAN_QUESTIONS } from '@/plan/planQuestions';

const ALL = [
  ['여행 범위', AREA_OPTIONS],
  ['여행 카테고리', CATEGORY_OPTIONS],
  ['분위기', ATMOSPHERE_OPTIONS],
  ['여행 기분', PACE_OPTIONS],
  ['이동수단', TRANSPORT_OPTIONS],
] as const;

describe('선택지 부제', () => {
  it.each(ALL)('%s — 모든 선택지에 한국어·영어 부제가 있다', (_name, options) => {
    for (const [code, ko, en, subKo, subEn] of options) {
      expect(code).not.toBe('');
      expect(ko.trim()).not.toBe('');
      expect(en.trim()).not.toBe('');
      expect(subKo.trim()).not.toBe('');
      expect(subEn.trim()).not.toBe('');
    }
  });

  it.each(ALL)('%s — 코드가 겹치지 않는다', (_name, options) => {
    const codes = options.map(([code]) => code);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it('음식 부제는 한국어·영어 둘 다 있다', () => {
    for (const [code, pair] of Object.entries(FOOD_SUBTITLES)) {
      expect(code).not.toBe('');
      expect(pair[0].trim()).not.toBe('');
      expect(pair[1].trim()).not.toBe('');
    }
  });
});

describe('「이렇게 반영돼요」', () => {
  const draft: PlanDraft = { ...EMPTY_PLAN };

  /**
   * 🔴 답하기 **전에도** 문구가 있어야 한다. 비워 두면 답하는 순간 띠가 나타나면서
   * 카드 높이가 튀고, 눌린 자리가 손가락 아래에서 움직인다.
   */
  it.each(['areas', 'cats', 'pace', 'moods', 'foods'] as const)('%s — 답이 없어도 문구가 있다', (key) => {
    expect(effectOf(key, draft, true)).toBeTruthy();
    expect(effectOf(key, draft, false)).toBeTruthy();
  });

  it('답하면 문구가 달라진다 — 같은 말을 되풀이하면 띠를 둘 이유가 없다', () => {
    const before = effectOf('areas', draft, true);
    const after = effectOf('areas', { ...draft, travelAreas: ['HAEUNDAE', 'GWANGALLI'] }, true);
    expect(after).not.toBe(before);
    expect(after).toContain('2');
  });

  it('여행 기분은 고른 값마다 다르게 말한다', () => {
    const relaxed = effectOf('pace', { ...draft, paceLevel: 'RELAXED' }, true);
    const packed = effectOf('pace', { ...draft, paceLevel: 'PACKED' }, true);
    expect(relaxed).not.toBe(packed);
  });

  /** 🔴 선택지가 없는 질문에는 띠를 안 붙인다(시안). 답이 곧 설명이라 한 줄을 더 얹을 이유가 없다. */
  it.each(['budget', 'move', 'scales', 'aids', 'must'] as const)('%s — 띠가 없다', (key) => {
    expect(effectOf(key, draft, true)).toBeNull();
  });

  it('🔴 질문 열쇠를 빠뜨리지 않았다 — 열 개 전부 물어본다', () => {
    const keys = PLAN_QUESTIONS.map((question) => question.key);
    expect(keys).toHaveLength(10);
    for (const key of keys) {
      // 던져도 안 죽어야 한다. null 은 「띠 없음」이라 정상이다.
      expect(() => effectOf(key, draft, true)).not.toThrow();
    }
  });
});
