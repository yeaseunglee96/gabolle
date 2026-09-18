// 연결된 소셜 계정 목록 —-1329.
//
// 🔴 이 시험이 지키는 것은 **「뗄 수 있다」를 화면이 스스로 정하지 않는가**이다. 서버가 안 준
//    값을 참으로 떨어뜨리면, 마지막 로그인 수단에 「연결 해제」가 눌리는 채로 그려진다.
//    서버가 다시 막으므로 계정이 잠기지는 않지만, 눌렀는데 거절당하는 화면이 된다.
import { adaptIdentities } from '@/auth/identitiesApi';

describe('서버가 준 연결 목록', () => {
  it('판 이름을 앱이 쓰는 소문자로 바꾼다', () => {
    const { items } = adaptIdentities({
      items: [{ provider: 'KAKAO', providerEmail: 'a@b.c', canUnlink: true }],
    });

    expect(items).toEqual([{ provider: 'kakao', providerEmail: 'a@b.c', canUnlink: true }]);
  });

  it('🔴 canUnlink 가 없으면 못 떼는 것으로 본다 — 모르는 것을 「된다」로 떨어뜨리지 않는다', () => {
    const { items } = adaptIdentities({ items: [{ provider: 'GOOGLE' }] });

    expect(items[0].canUnlink).toBe(false);
  });

  it('이메일을 안 준 판도 담는다 — 카카오 기본 동의는 이메일을 안 준다', () => {
    const { items } = adaptIdentities({ items: [{ provider: 'KAKAO', providerEmail: null }] });

    expect(items).toHaveLength(1);
    expect(items[0].providerEmail).toBeNull();
  });

  it('빈 이메일은 없는 것으로 본다 — 빈 줄을 그리게 두지 않는다', () => {
    const { items } = adaptIdentities({ items: [{ provider: 'NAVER', providerEmail: '' }] });

    expect(items[0].providerEmail).toBeNull();
  });

  it('🔴 모르는 판은 버린다 — 이름조차 못 그리는 줄을 만들지 않는다', () => {
    const { items } = adaptIdentities({ items: [{ provider: 'LINE' }, { provider: 'APPLE' }] });

    expect(items.map((item) => item.provider)).toEqual(['apple']);
  });

  it('비밀번호 여부도 모르면 거짓이다', () => {
    expect(adaptIdentities({}).canSignInWithPassword).toBe(false);
    expect(adaptIdentities({ canSignInWithPassword: true }).canSignInWithPassword).toBe(true);
  });

  it('빈 답에도 안 깨진다', () => {
    expect(adaptIdentities({})).toEqual({ items: [], canSignInWithPassword: false });
  });
});
