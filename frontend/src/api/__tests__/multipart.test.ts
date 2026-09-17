// 파일을 보내는 자리에 **`{ uri }` 객체가 다시 들어가지 않게** 붙드는 시험 — S15P21E201-1187.
//
// 🔴 이 시험이 지키는 것은 눈에 안 보인다. `formData.append('file', { uri, name, type })` 은
//    2026-09-17 까지 맞는 코드였고, Expo SDK 57 이 전역 `fetch` 를 표준 것으로 갈아끼운
//    순간부터 **앱을 조용히 망가뜨리는 코드**가 됐다. 타입 검사는 못 잡는다 —
//    그렇게 쓰려면 `as unknown as Blob` 으로 타입을 속여야 했고, 속인 뒤에는 아무도 안 본다.
//    화면에는 「서버에 연결할 수 없어요」만 뜨고 요청은 나가지도 않았다.
//
//    그래서 **FormData 에 실제로 무엇이 들어갔는지**를 여기서 본다.
import { fileUriToBlob, singleFileFormData } from '../multipart';

type FakeXhr = {
  responseType: string;
  response: unknown;
  onload: (() => void) | null;
  onerror: (() => void) | null;
  onabort: (() => void) | null;
  open: (method: string, url: string, async: boolean) => void;
  send: (body: unknown) => void;
};

/** 마지막으로 열린 가짜 XHR — 어떤 주소를 읽으러 갔는지 확인하려고 남긴다. */
let lastOpened: { method: string; url: string } | null = null;

/**
 * `file://` 을 읽는 XHR 을 흉내 낸다. 진짜 파일을 만들지 않는 이유는, 이 시험이 재는 것이
 * 「파일을 읽을 수 있나」가 아니라 **「읽은 것을 어떤 모양으로 FormData 에 넣나」** 이기 때문이다.
 */
function installFakeXhr(makeResponse: () => unknown) {
  (globalThis as unknown as { XMLHttpRequest: unknown }).XMLHttpRequest = function (this: FakeXhr) {
    this.responseType = '';
    this.response = null;
    this.onload = null;
    this.onerror = null;
    this.onabort = null;
    this.open = (method, url) => { lastOpened = { method, url }; };
    this.send = () => {
      const body = makeResponse();
      if (body === null) { this.onerror?.(); return; }
      this.response = body;
      this.onload?.();
    };
  } as unknown as typeof XMLHttpRequest;
}

describe('올릴 파일을 Blob 으로 바꾼다', () => {
  beforeEach(() => { lastOpened = null; });

  it('🔴 FormData 에 들어가는 것은 Blob 이다 — { uri } 객체가 아니다', async () => {
    installFakeXhr(() => new Blob(['사진 내용'], { type: 'image/jpeg' }));

    const formData = await singleFileFormData('file', {
      uri: 'file:///storage/emulated/0/DCIM/gwangan.jpg',
      name: 'gwangan.jpg',
      type: 'image/jpeg',
    });

    const part = formData.get('file');
    expect(part).toBeInstanceOf(Blob);
    // 이 한 줄이 이 시험의 전부다. 여기에 uri 가 있으면 새 fetch 가
    // 「Unsupported FormDataPart implementation」을 던지고 요청이 안 나간다.
    expect(part).not.toHaveProperty('uri');
  });

  it('고른 사진의 주소를 그대로 읽으러 간다', async () => {
    installFakeXhr(() => new Blob(['x'], { type: 'image/png' }));
    await fileUriToBlob('content://media/external/images/media/42', 'image/png');
    expect(lastOpened).toEqual({ method: 'GET', url: 'content://media/external/images/media/42' });
  });

  it('파일에서 종류를 못 읽어 오면 고른 사진이 알려 준 종류로 채운다', async () => {
    // content:// 는 종류가 비어 오는 일이 있다. 비운 채로 보내면 서버가 415 로 되돌린다.
    installFakeXhr(() => new Blob(['x']));
    const blob = await fileUriToBlob('content://media/external/images/media/42', 'image/webp');
    expect(blob.type).toBe('image/webp');
  });

  it('파일을 못 읽으면 **주소를 붙여서** 알려 준다 — 어디서 막혔는지가 곧 제보다', async () => {
    installFakeXhr(() => null);
    await expect(fileUriToBlob('file:///none.jpg', 'image/jpeg')).rejects.toThrow(/file:\/\/\/none\.jpg/);
  });
});
