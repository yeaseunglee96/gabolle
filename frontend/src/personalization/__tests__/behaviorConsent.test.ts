import { reconcileConsent } from '../behaviorConsent';

// — 동의 값이 기기에만 남아 서버는 가입 때 값을 그대로 들고 있었다.
// 서버와 기기가 어긋날 때 켜는 쪽으로 맞추면, 이 값의 유일한 목적(동의 없이 보내지
// 않는 것)을 정면으로 어긴다. 어긋남은 늘 끄는 쪽으로 푼다.

describe('기기와 서버의 동의가 어긋날 때', () => {
  it('서버가 꺼져 있으면 기기도 끈다', () => {
    expect(reconcileConsent(true, false)).toBe(false);
  });

  it('🔴 서버가 켜져 있어도 기기가 꺼져 있으면 켜지 않는다', () => {
    expect(reconcileConsent(false, true)).toBe(false);
  });

  it('둘 다 켜져 있을 때만 켜진 것으로 본다', () => {
    expect(reconcileConsent(true, true)).toBe(true);
  });

  it('둘 다 꺼져 있으면 꺼진 채다', () => {
    expect(reconcileConsent(false, false)).toBe(false);
  });
});
