/**
 * 그림이 화면에 실제로 그려지는가 — S15P21E201-1335.
 *
 * 🔴 2026-09-19 안드로이드 실기(운영 빌드 versionCode 22)에서 **그림 칸이 흰색으로만
 * 떴다.** 서버는 멀쩡했다 — 같은 주소를 같은 토큰으로 부르면 512×512 JPEG 가 온다.
 * 다른 것은 하나뿐이었다: 앱에서는 `<Image source={{ uri, headers }}>` 가 **스스로 다시**
 * 받아 오게 했고, 그 두 번째 요청만 그림을 못 가져왔다.
 *
 * 그래서 이 시험은 «주소를 어떻게 만드는가»가 아니라 **그리는 쪽이 통신을 하게 되는가**를
 * 잰다. `loadDishImage` 가 내주는 uri 가 `http` 로 시작하면 그리는 쪽이 다시 받아 와야
 * 한다는 뜻이고, 그것이 바로 고친 것이다.
 */
import { Platform } from 'react-native';

import { loadDishImage } from '@/field/dish';

const JPEG = 'data:image/jpeg;base64,/9j/4AAQSkZJRg==';

/** React Native 의 FileReader 를 대신한다 — readAsDataURL 만 쓴다. */
class FakeFileReader {
  result: string | null = null;
  error: unknown = null;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readAsDataURL() {
    this.result = JPEG;
    this.onload?.();
  }
}

function respond(status: number, body = 'bytes') {
  return {
    status,
    ok: status >= 200 && status < 300,
    blob: async () => ({ size: body.length, type: 'image/jpeg' }),
  };
}

describe('loadDishImage', () => {
  const originalOs = Platform.OS;
  let fetchMock: jest.Mock;

  beforeEach(() => {
    fetchMock = jest.fn();
    (globalThis as { fetch?: unknown }).fetch = fetchMock;
    (globalThis as { FileReader?: unknown }).FileReader = FakeFileReader;
  });

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', { value: originalOs, configurable: true });
  });

  function onNative() {
    Object.defineProperty(Platform, 'OS', { value: 'android', configurable: true });
  }

  it('🔴 그리는 쪽이 다시 받아 오지 않는다 — 바이트가 uri 안에 들어 있다', async () => {
    onNative();
    fetchMock.mockResolvedValue(respond(200));

    const verdict = await loadDishImage('img-1', 'token-1');

    expect(verdict.state).toBe('ready');
    if (verdict.state !== 'ready') return;
    // 이 한 줄이 이 버그 전체다. http 주소를 내주면 `<Image>` 가 인증 헤더를 붙여
    // 스스로 한 번 더 받아 와야 하고, 안드로이드에서 그것이 안 됐다.
    expect(verdict.uri.startsWith('http')).toBe(false);
    expect(verdict.uri.startsWith('data:image/')).toBe(true);
  });

  it('바이트를 한 번만 받는다 — 같은 그림을 두 번 내려받지 않는다', async () => {
    onNative();
    fetchMock.mockResolvedValue(respond(200));

    await loadDishImage('img-1', 'token-1');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer token-1');
  });

  it('202 는 「아직」이라 다시 물어본다', async () => {
    onNative();
    fetchMock.mockResolvedValue(respond(202));
    await expect(loadDishImage('img-1', 'token-1')).resolves.toEqual({ state: 'pending' });
  });

  it('404 는 「그만 물어봐」다', async () => {
    onNative();
    fetchMock.mockResolvedValue(respond(404));
    await expect(loadDishImage('img-1', 'token-1')).resolves.toEqual({ state: 'gone' });
  });

  it('통신이 끊긴 것은 그림이 없는 것과 다르다 — 다시 물어본다', async () => {
    onNative();
    fetchMock.mockRejectedValue(new Error('offline'));
    await expect(loadDishImage('img-1', 'token-1')).resolves.toEqual({ state: 'pending' });
  });
});
