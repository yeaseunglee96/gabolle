import { buildProtectedReturnTo } from '../ProtectedRoute';

describe('buildProtectedReturnTo', () => {
  it('keeps the trip preparation intent across sign-in', () => {
    expect(buildProtectedReturnTo('/trips', 'prepare')).toBe('/trips?open=prepare');
  });

  it('does not carry unrelated query values into authentication', () => {
    expect(buildProtectedReturnTo('/trips', 'unexpected')).toBe('/trips');
    expect(buildProtectedReturnTo('/me', 'prepare')).toBe('/me');
  });
});

// S15P21E201-1116 — 비회원 둘러보기가 보호 화면으로 되돌아가 무한 왕복하던 것.
import { guestDestination } from '../pendingReturnTo';

describe('guestDestination — 「비회원으로 둘러보기」가 갈 곳', () => {
  it('보호 화면에서 튕겨 온 것이면 홈으로 간다 (그 자리로 돌아가면 무한 왕복이다)', () => {
    expect(guestDestination('/me/profile', '1')).toBe('/home');
    expect(guestDestination('/trips?open=prepare', '1')).toBe('/home');
  });

  it('로그인이 필요 없는 자리에서 왔으면 그 자리로 돌려보낸다', () => {
    expect(guestDestination('/plan/confirm', undefined)).toBe('/plan/confirm');
    expect(guestDestination('/explore', null)).toBe('/explore');
  });

  it('돌아갈 자리가 없거나 안전하지 않으면 홈으로 간다', () => {
    expect(guestDestination(undefined, undefined)).toBe('/home');
    expect(guestDestination('https://example.com', undefined)).toBe('/home');
    expect(guestDestination('/sign-in', undefined)).toBe('/home');
  });
});
