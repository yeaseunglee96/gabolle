import { enterApp, type StackRouter } from '@/auth/enterApp';

function spyRouter(over: Partial<StackRouter> = {}) {
  const calls: string[] = [];
  const router: StackRouter = {
    replace: (href) => calls.push(`replace:${String(href)}`),
    canDismiss: () => { calls.push('canDismiss'); return true; },
    dismissAll: () => calls.push('dismissAll'),
    ...over,
  };
  return { router, calls };
}

describe('앱으로 들어가기', () => {
  it('🔴 쌓인 것을 먼저 치우고 그다음에 바꾼다 — 순서가 바뀌면 로그인 화면이 남는다', () => {
    const { router, calls } = spyRouter();
    enterApp(router, '/home');
    expect(calls).toEqual(['canDismiss', 'dismissAll', 'replace:/home']);
  });

  it('🔴 치울 것이 없으면 dismissAll 을 안 부른다 — 부르면 이동이 죽는 판이 있다', () => {
    const { router, calls } = spyRouter({ canDismiss: () => false });
    enterApp(router, '/home');
    expect(calls).toEqual(['replace:/home']);
  });

  it('🔴 치우기가 실패해도 가기는 간다 — 여기서 멈추면 사용자에게는 「로그인이 안 됐다」로 보인다', () => {
    const calls: string[] = [];
    const router: StackRouter = {
      replace: (href) => calls.push(`replace:${String(href)}`),
      canDismiss: () => true,
      dismissAll: () => { throw new Error('치울 수 없음'); },
    };
    expect(() => enterApp(router, '/home')).not.toThrow();
    expect(calls).toEqual(['replace:/home']);
  });

  it('치우는 손잡이가 아예 없는 라우터도 받는다', () => {
    const calls: string[] = [];
    enterApp({ replace: (href) => calls.push(`replace:${String(href)}`) }, '/home');
    expect(calls).toEqual(['replace:/home']);
  });

  it('돌아갈 곳이 따로 있으면 그리로 간다 — 홈으로 고정하지 않는다', () => {
    const { router, calls } = spyRouter();
    enterApp(router, '/trips/abc/itinerary');
    expect(calls).toContain('replace:/trips/abc/itinerary');
  });
});
