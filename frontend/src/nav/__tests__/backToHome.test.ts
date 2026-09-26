// 안드로이드 뒤로 가기 규칙 (S15P21E201-1752).
import { shouldGoHomeOnBack } from '@/nav/backToHome';

describe('뒤로 갈 곳이 없을 때', () => {
  it.each(['/feed', '/plan', '/questions', '/trips', '/me', '/trips/123'])('🔴 %s 에서는 앱을 닫지 않고 홈으로 간다', (path) => {
    expect(shouldGoHomeOnBack(path, false)).toBe(true);
  });

  it.each(['/home', '/'])('%s 에서는 지금처럼 닫힌다', (path) => {
    expect(shouldGoHomeOnBack(path, false)).toBe(false);
  });
});

it('뒤로 갈 화면이 있으면 손대지 않는다 — 보통의 뒤로 가기', () => {
  expect(shouldGoHomeOnBack('/feed', true)).toBe(false);
});
