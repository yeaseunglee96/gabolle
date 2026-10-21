// 여행 조건 창을 어디서 묻나 — UI 캔버스 ⑨.
// 🔴 로그인하자마자 홈 전체를 가리던 창을 홈에서는 안 띄운다. 일정을 만들기 시작할 때 한 번 묻는다.
jest.mock('@/plan/travelConditions', () => ({ loadTravelConditions: jest.fn(), askConditionsAgain: jest.fn(), consumeAskAgain: jest.fn() }));
import { shouldPromptBeforePlan, shouldPromptOnHome } from '@/plan/conditionsPromptState';

describe('여행 조건 창', () => {
  it('🔴 홈 첫 화면에서는 아무도 안 묻는다 — 한 번도 안 물어본 사람도', () => {
    for (const state of [null, 'LATER', 'NEVER', 'SAVED'] as const) expect(shouldPromptOnHome(state)).toBe(false);
  });
  it('일정을 만들기 시작할 때는 한 번도 안 물어본 사람과 「나중에」를 고른 사람에게 묻는다', () => {
    expect(shouldPromptBeforePlan(null)).toBe(true);
    expect(shouldPromptBeforePlan('LATER')).toBe(true);
    expect(shouldPromptBeforePlan('SAVED')).toBe(false);
    expect(shouldPromptBeforePlan('NEVER')).toBe(false);
  });
});
