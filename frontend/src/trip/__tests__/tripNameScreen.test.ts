import { MAX_SHOWN_SUGGESTIONS, planNameStep } from '../tripNaming';

// 이름 짓기 화면이 어느 자리에 서는가 (S15P21E201-1036). 시안 `design_handoff_trip_name`.
//
// 🔴 이 시험이 지키는 것은 하나다 — **이름을 못 지은 것을 고장으로 그리지 않는다.**
//
// 서버가 실패해도, 후보가 비어도, 이 화면은 오류 화면이 아니라 「직접 쓰거나 건너뛰는」
// 자리로 간다. 그리고 그때 **후보를 지어내지 않는다.** 이 판단이 화면 안에만 있으면
// 「실패하면 오류를 띄우자」로 조용히 바뀌어도 아무것도 빨개지지 않는다.

const success = (suggestions: string[], source = 'MODEL', discardedCount = 0) =>
  ({ state: 'success' as const, suggestions, source, discardedCount });

describe('후보를 받아 온 뒤 어느 화면으로 가나', () => {
  it('후보가 있으면 고르는 화면으로 간다', () => {
    const next = planNameStep(success(['해운대 이틀', '광안리 밤바다']));
    expect(next.step).toBe('suggestions');
    expect(next.suggestions).toEqual(['해운대 이틀', '광안리 밤바다']);
    expect(next.source).toBe('MODEL');
  });

  it('후보가 비면 빈 화면으로 간다 — 오류가 아니다', () => {
    expect(planNameStep(success([])).step).toBe('empty');
  });

  // 🔴 여기가 핵심이다. 서버가 죽어도 사용자에게는 「이름을 못 지었다」이지
  // 「무언가 고장났다」가 아니다.
  it('불러오기가 실패해도 오류 화면으로 가지 않는다', () => {
    for (const state of ['error', 'offline', 'unavailable', 'forbidden', 'invalid'] as const) {
      const next = planNameStep({ state, message: '아무 말' });
      expect(next.step).toBe('empty');
      // 🔴 그리고 후보를 지어내지 않는다.
      expect(next.suggestions).toEqual([]);
      expect(next.discardedCount).toBe(0);
    }
  });

  it('실패했을 때의 출처는 모델이 지은 것으로 두지 않는다', () => {
    expect(planNameStep({ state: 'error', message: '' }).source).not.toBe('MODEL');
  });

  it('후보가 많이 와도 다섯 개까지만 그린다', () => {
    const many = ['하나', '둘', '셋', '넷', '다섯', '여섯', '일곱'];
    const next = planNameStep(success(many));
    expect(next.suggestions).toHaveLength(MAX_SHOWN_SUGGESTIONS);
    expect(next.suggestions).toEqual(['하나', '둘', '셋', '넷', '다섯']);
  });

  it('버린 후보 수를 그대로 전한다 — 숨기지 않는다', () => {
    expect(planNameStep(success(['해운대 이틀'], 'MODEL', 3)).discardedCount).toBe(3);
  });

  it('틀로 만든 후보도 후보 화면으로 간다 — 다만 출처가 그대로 남는다', () => {
    const next = planNameStep(success(['부산 이틀'], 'TEMPLATE'));
    expect(next.step).toBe('suggestions');
    expect(next.source).toBe('TEMPLATE');
  });
});
