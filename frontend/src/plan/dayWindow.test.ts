// 이 시험이 지키는 것 하나 — **「0800」이 통과하지 않는 것.**
//
// 이 자리는 한 번 뚫려 있었다. 마스크(maskTimeInput)도, 형식 검사(validateTripBasics)도
// 만들어져 있었는데 **화면이 둘 다 안 불렀다.** 함수는 초록이고, 아무도 안 부른다는 사실은
// 어떤 단위 시험도 실패로 만들지 않는다. 그래서 여기서는 **질문의 통과 조건 자체**를 건다.
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import { PLAN_QUESTIONS, dayWindowIssue } from '@/plan/planQuestions';
import { maskTimeInput } from '@/plan/inputMasks';

const draft = (over: Partial<PlanDraft> = {}): PlanDraft => ({ ...EMPTY_PLAN, ...over });
const move = PLAN_QUESTIONS.find((q) => q.key === 'move')!;

describe('dayWindowIssue', () => {
  it('제대로 된 시각은 문제가 없다', () => {
    expect(dayWindowIssue({ dayStartTime: '09:00', dayEndTime: '18:00' })).toBeNull();
  });

  it('🔴 콜론이 없으면 형식 문제다 — 서버가 조용히 버리는 그 값이다', () => {
    expect(dayWindowIssue({ dayStartTime: '0800', dayEndTime: '1800' })).toBe('FORMAT');
  });

  it('자리수가 모자라도 형식 문제다', () => {
    expect(dayWindowIssue({ dayStartTime: '9:00', dayEndTime: '18:00' })).toBe('FORMAT');
    expect(dayWindowIssue({ dayStartTime: '09:0', dayEndTime: '18:00' })).toBe('FORMAT');
    expect(dayWindowIssue({ dayStartTime: '', dayEndTime: '18:00' })).toBe('FORMAT');
  });

  it('없는 시각도 형식 문제다', () => {
    expect(dayWindowIssue({ dayStartTime: '25:00', dayEndTime: '26:00' })).toBe('FORMAT');
    expect(dayWindowIssue({ dayStartTime: '09:70', dayEndTime: '18:00' })).toBe('FORMAT');
  });

  it('🔴 끝이 시작보다 이르거나 같으면 순서 문제다 — 서버는 이것을 안 막는다', () => {
    expect(dayWindowIssue({ dayStartTime: '18:00', dayEndTime: '09:00' })).toBe('ORDER');
    expect(dayWindowIssue({ dayStartTime: '09:00', dayEndTime: '09:00' })).toBe('ORDER');
  });
});

describe('「하루 여행 시간 · 이동수단」 질문이 넘어가는 조건', () => {
  it('🔴 이동수단만 골라서는 못 넘어간다 — 예전에는 넘어갔다', () => {
    expect(move.answered(draft({ transport: 'TRANSIT', dayStartTime: '0800', dayEndTime: '1800' }))).toBe(false);
  });

  it('시각만 맞고 이동수단이 없어도 못 넘어간다', () => {
    expect(move.answered(draft({ transport: null as never, dayStartTime: '09:00', dayEndTime: '18:00' }))).toBe(false);
  });

  it('끝이 시작보다 이르면 못 넘어간다', () => {
    expect(move.answered(draft({ transport: 'TRANSIT', dayStartTime: '18:00', dayEndTime: '09:00' }))).toBe(false);
  });

  it('둘 다 맞으면 넘어간다', () => {
    expect(move.answered(draft({ transport: 'TRANSIT', dayStartTime: '09:00', dayEndTime: '18:00' }))).toBe(true);
  });

  it('🔴 이 질문은 건너뛸 수 없다 — 건너뛰기로 검사를 우회하면 안 된다', () => {
    expect(move.skippable).toBe(false);
  });
});

describe('마스크를 거치면 통과 조건을 만족한다 — 화면이 실제로 하는 일', () => {
  it('「0800」을 치면 「08:00」이 되어 넘어갈 수 있다', () => {
    const typed = { dayStartTime: maskTimeInput('0800'), dayEndTime: maskTimeInput('1800') };
    expect(typed.dayStartTime).toBe('08:00');
    expect(dayWindowIssue(typed)).toBeNull();
    expect(move.answered(draft({ transport: 'TRANSIT', ...typed }))).toBe(true);
  });

  it('🔴 마스크는 이미 콜론이 있는 값도 망가뜨리지 않는다 — 한 글자씩 칠 때 매번 거친다', () => {
    expect(maskTimeInput('09:00')).toBe('09:00');
    expect(maskTimeInput(maskTimeInput('09:00'))).toBe('09:00');
  });
});
