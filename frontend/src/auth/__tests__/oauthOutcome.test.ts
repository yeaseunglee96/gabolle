// 소셜 로그인 창이 코드 없이 끝났을 때 사용자 탓으로 단정하지 않는가 — S15P21E201-1480.
//
// 🔴 결과만으로는 «사용자가 취소»와 «못 돌아와서 닫음»을 가를 수 없다(oauth.ts 머리말 —
//    iOS 는 콜백 없이 끝나면 무조건 cancel). 그래서 두 결과 모두 «둘 다에 참인 말»을 해야 한다.
jest.mock('expo-web-browser', () => ({ maybeCompleteAuthSession: jest.fn(), openAuthSessionAsync: jest.fn() }));
jest.mock('expo-crypto', () => ({}));

import { AUTH_SESSION_NOT_FINISHED, authSessionFailure } from '@/auth/oauth';
import { MESSAGE_EN } from '@/i18n/messages';

describe('소셜 로그인 창이 코드 없이 끝났을 때', () => {
  it('🔴 cancel 도 dismiss 도 「취소되었어요」라고 단정하지 않는다', () => {
    for (const type of ['cancel', 'dismiss']) {
      const error = authSessionFailure(type);
      expect(error.message).toBe(AUTH_SESSION_NOT_FINISHED);
      expect(error.message).not.toContain('취소되었어요');
    }
  });

  it('🔴 연결 탓으로도 단정하지 않는다 — 일부러 닫은 사람도 같은 말을 본다', () => {
    // 「창을 닫았거나 · 연결이 끊겼을 수」 둘 다를 말한다.
    expect(AUTH_SESSION_NOT_FINISHED).toContain('창을 닫았거나');
    expect(AUTH_SESSION_NOT_FINISHED).toContain('연결이 끊겼을 수');
  });

  it('그 밖의 결과(locked 등)는 전처럼 「완료하지 못했어요」', () => {
    expect(authSessionFailure('locked').message).toBe('소셜 로그인을 완료하지 못했어요.');
    expect(authSessionFailure('locked').code).toBe('OAUTH_FAILED');
  });

  it('영어 화면 문구가 있다 — 없으면 영어 화면에 한국어가 그대로 나간다', () => {
    expect(MESSAGE_EN[AUTH_SESSION_NOT_FINISHED]).toBeTruthy();
    expect(MESSAGE_EN[AUTH_SESSION_NOT_FINISHED]).not.toMatch(/cancel/i);
  });
});
