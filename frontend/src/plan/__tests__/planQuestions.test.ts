// 🔴 이 시험이 지키는 것은 「답했는데 다음이 안 열린다」와 그 반대다.
//    둘 다 화면을 봐도 원인이 안 보이고, 사용자는 그냥 나가 버린다.
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import {
  INITIAL_QUESTION_STATE,
  PLAN_QUESTIONS,
  allSettled,
  canAdvance,
  isSettled,
  nextOpenIndex,
  remainingCount,
  settledCount,
  type QuestionState,
} from '@/plan/planQuestions';

const draft = (over: Partial<PlanDraft> = {}): PlanDraft => ({ ...EMPTY_PLAN, ...over });
const state = (over: Partial<QuestionState> = {}): QuestionState => ({ ...INITIAL_QUESTION_STATE, ...over });

const find = (key: string) => PLAN_QUESTIONS.find((question) => question.key === key)!;

const FULL = draft({
  travelAreas: ['해운대'],
  budgetKrw: 300000,
  transport: 'TRANSIT',
  preferences: ['FOOD'],
  paceLevel: 'BALANCED',
  atmospheres: ['LIVELY'],
  localityLevel: 3,
  foods: ['pork'],
  wheelchair: false,
});

describe('질문 목록', () => {
  it('열 개이고 앞의 셋만 못 건너뛴다', () => {
    expect(PLAN_QUESTIONS).toHaveLength(10);
    expect(PLAN_QUESTIONS.slice(0, 3).every((question) => !question.skippable)).toBe(true);
    expect(PLAN_QUESTIONS.slice(3).every((question) => question.skippable)).toBe(true);
  });

  it('열쇠가 겹치지 않는다', () => {
    expect(new Set(PLAN_QUESTIONS.map((question) => question.key)).size).toBe(PLAN_QUESTIONS.length);
  });
});

describe('답한 것으로 보는 조건', () => {
  it('여행 범위는 한 곳만 골라도 답한 것이다', () => {
    expect(find('areas').answered(draft())).toBe(false);
    expect(find('areas').answered(draft({ travelAreas: ['해운대'] }))).toBe(true);
  });

  it('🔴 예산 0 은 답이 아니다 — 「0원으로 간다」는 뜻이 아니라 안 골랐다는 뜻이다', () => {
    expect(find('budget').answered(draft({ budgetKrw: 0 }))).toBe(false);
    expect(find('budget').answered(draft({ budgetKrw: 100000 }))).toBe(true);
  });

  it('🔴 카테고리는 넷 이상이면 답한 것이 아니다 — 최대 셋이다', () => {
    expect(find('cats').answered(draft({ preferences: ['A', 'B', 'C'] }))).toBe(true);
    expect(find('cats').answered(draft({ preferences: ['A', 'B', 'C', 'D'] }))).toBe(false);
  });

  it('척도 셋은 하나만 답해도 된다', () => {
    expect(find('scales').answered(draft())).toBe(false);
    expect(find('scales').answered(draft({ quietLevel: 4 }))).toBe(true);
  });

  it('🔴 이동 보조는 「아니요」도 답이다 — false 와 「안 고름(null)」은 다르다', () => {
    expect(find('aids').answered(draft())).toBe(false);
    expect(find('aids').answered(draft({ wheelchair: false }))).toBe(true);
  });

  it('🔴 꼭 가고 싶은 장소는 「없음」도 답이라 언제나 통과한다', () => {
    expect(find('must').answered(draft())).toBe(true);
  });
});

describe('진행', () => {
  it('🔴 초안의 기본값이 이미 셋을 「답한 것」으로 만든다 — 예산·이동수단·꼭 가고 싶은 장소', () => {
    expect(settledCount(draft(), state())).toBe(3);
    expect(remainingCount(draft(), state())).toBe(7);
  });

  it('건너뛴 것도 지나간 것으로 센다', () => {
    const skipped = state({ skipped: { cats: true, moods: true } });
    expect(settledCount(draft(), skipped)).toBe(5);
  });

  it('🔴 카드는 순서대로 연다 — 기본값이 든 질문도 건너뛰지 않는다', () => {
    expect(nextOpenIndex(draft({ travelAreas: ['해운대'] }), state({ open: 0 }))).toBe(1);
    // 예산에 기본값이 있어도 2번(이동수단)으로 건너뛰지 않는다
    expect(nextOpenIndex(draft(), state({ open: 0 }))).toBe(1);
  });

  it('마지막을 넘어가지 않는다', () => {
    expect(nextOpenIndex(FULL, state({ open: 9 }))).toBe(10);
    expect(nextOpenIndex(FULL, state({ open: 10 }))).toBe(10);
  });
});

describe('전부 지나갔나', () => {
  it('필수 셋을 채우고 나머지를 건너뛰면 끝난 것이다', () => {
    const only3 = draft({ travelAreas: ['해운대'], budgetKrw: 300000, transport: 'TRANSIT' });
    const skipped = state({ skipped: { cats: true, pace: true, moods: true, scales: true, foods: true, aids: true } });
    expect(allSettled(only3, skipped)).toBe(true);
  });

  it('🔴 못 건너뛰는 질문은 건너뛴 것으로 쳐 주지 않는다', () => {
    const skipAll = state({ skipped: { areas: true, budget: true, move: true, cats: true, pace: true, moods: true, scales: true, foods: true, aids: true } });
    expect(allSettled(draft(), skipAll)).toBe(false);
    expect(isSettled(find('areas'), draft(), skipAll)).toBe(true); // 화면에서는 접히지만
  });

  it('다 답하면 끝난 것이다', () => {
    expect(allSettled(FULL, state())).toBe(true);
  });
});

describe('「다음」을 누를 수 있나', () => {
  it('답해야만 누를 수 있다 — 건너뛰기는 다른 단추다', () => {
    expect(canAdvance(find('areas'), draft())).toBe(false);
    expect(canAdvance(find('areas'), draft({ travelAreas: ['해운대'] }))).toBe(true);
  });
});
