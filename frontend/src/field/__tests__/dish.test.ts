// 음식 설명·그림 — S15P21E201-1276.
//
// 🔴 이 시험들이 지키는 것은 「기능이 도는가」가 아니라 **두 가지를 안 섞는가**이다.
//    (1) 모델이 아는 것과 사진에서 읽은 것
//    (2) 「아직」과 「그만 물어봐」
import { describeDish, loadDishImage, dishImageHeaders } from '../dish';

const tx = (ko: string) => ko;

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
  jest.restoreAllMocks();
});

function respondWith(body: unknown, status = 200) {
  global.fetch = jest.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  }) as unknown as typeof fetch;
}

describe('설명을 받아 온다', () => {
  it('로그인하지 않았으면 부르지 않는다', async () => {
    global.fetch = jest.fn() as unknown as typeof fetch;

    const result = await describeDish('돼지국밥', null, tx, 'ko');

    expect(result.state).toBe('error');
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('서버가 준 설명을 그대로 낸다', async () => {
    respondWith({ data: {
      name: '돼지국밥', description: '부산의 돼지고기 국밥이에요.',
      descriptionSource: 'MODEL_KNOWLEDGE', imageStatus: 'PENDING', imageId: 'abc',
    } });

    const result = await describeDish('돼지국밥', 'token', tx, 'ko');

    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.dish.description).toBe('부산의 돼지고기 국밥이에요.');
    expect(result.dish.imageStatus).toBe('PENDING');
  });

  /**
   * 🔴 모르는 값을 「만드는 중」으로 떨어뜨리면 화면이 **오지 않을 그림을 영원히**
   * 기다린다. 모르면 「없다」로 떨어뜨리는 쪽이 안전하다.
   */
  it('🔴 모르는 imageStatus 는 NONE 으로 떨어진다 — PENDING 이 아니다', async () => {
    respondWith({ data: {
      name: '돼지국밥', description: '설명', descriptionSource: 'MODEL_KNOWLEDGE',
      imageStatus: '어쩌구', imageId: 'abc',
    } });

    const result = await describeDish('돼지국밥', 'token', tx, 'ko');

    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.dish.imageStatus).toBe('NONE');
  });

  it('설명이 비어 와도 성공이다 — 「모델이 모르는 음식」은 실패가 아니다', async () => {
    respondWith({ data: {
      name: '어쩌구', description: '', descriptionSource: 'MODEL_KNOWLEDGE',
      imageStatus: 'NONE', imageId: null,
    } });

    const result = await describeDish('어쩌구', 'token', tx, 'ko');

    expect(result.state).toBe('success');
    if (result.state !== 'success') return;
    expect(result.dish.description).toBe('');
    expect(result.dish.imageId).toBeNull();
  });
});

describe('그림을 받아 온다', () => {
  /**
   * 🔴 이 시험이 이 모듈의 핵심이다. 202 는 「아직」이고 404 는 「그만 물어봐」다.
   * 둘을 같게 다루면 화면이 영원히 다시 묻거나, 10초만 더 기다리면 올 그림을 영영
   * 안 받는다.
   */
  it('🔴 202 는 「아직」이고 404 는 「그만」이다 — 같게 다루지 않는다', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 202 }) as unknown as typeof fetch;
    expect((await loadDishImage('id', 'token')).state).toBe('pending');

    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 404 }) as unknown as typeof fetch;
    expect((await loadDishImage('id', 'token')).state).toBe('gone');
  });

  /**
   * 통신이 한 번 끊긴 것과 그림이 없는 것은 다르다. 여기서 'gone' 으로 떨어뜨리면
   * 지하철에서 한 번 끊긴 사람이 **다시는** 그림을 못 본다.
   */
  it('통신이 끊기면 「아직」으로 둔다 — 「없다」로 단정하지 않는다', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('네트워크 끊김')) as unknown as typeof fetch;

    expect((await loadDishImage('id', 'token')).state).toBe('pending');
  });

  it('다 됐으면 그릴 수 있는 주소를 낸다', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200 }) as unknown as typeof fetch;

    const result = await loadDishImage('id', 'token');

    expect(result.state).toBe('ready');
    if (result.state !== 'ready') return;
    expect(result.uri).toContain('/api/v1/dishes/images/id');
  });

  /**
   * 🔴 이 주소는 로그인을 요구한다. 앱의 `Image` 는 헤더를 보낼 수 있지만 웹의
   * `Image` 는 결국 `<img src>` 라 **못 보낸다** — 앱에서만 되는 것을 보고 다 된 줄
   * 알기 쉬운 자리라, 헤더를 붙이는 판단을 한 함수에 모아 두고 여기서 지킨다.
   */
  it('🔴 앱에서는 인증 헤더를 붙인다', () => {
    expect(dishImageHeaders('token')).toEqual({ Authorization: 'Bearer token' });
  });
});
