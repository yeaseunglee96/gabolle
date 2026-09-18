// 메뉴판 읽기가 줄이기에서 막혀 요청조차 못 나가던 것 — S15P21E201-1121 의 같은 자리.
//
// 2026-09-17 안드로이드 실기기에서 「사진을 읽지 못했어요」가 떴는데, 서버 기록에
// /api/v1/menu-scans 요청이 한 줄도 없었다. 이 시험이 지키는 것은 하나다 —
// **줄이기가 실패해도 사진은 서버로 간다.**
jest.mock('@/social/imageResize', () => ({ resizeForUpload: jest.fn() }));
// 🔴 S15P21E201-1187 — 사진은 이제 **Blob 으로 바뀐 뒤에** FormData 에 들어간다.
//    그래서 「어느 사진을 보냈나」는 FormData 안이 아니라 **무엇을 읽으러 갔나**로 본다.
//    진짜 파일을 읽는 자리라 시험에서는 흔들어야 한다 — 그 자리 자체의 시험은
//    src/api/__tests__/multipart.test.ts 에 따로 있다.
jest.mock('@/api/multipart', () => ({ singleFileFormData: jest.fn(async () => new FormData()) }));
jest.mock('@/api/client', () => {
  class ApiClientError extends Error {
    status: number;
    constructor(status: number) { super('api'); this.status = status; }
  }
  return { apiRequest: jest.fn(), ApiClientError };
});

import { scanMenu } from '../menuScan';

const resize = jest.requireMock('@/social/imageResize') as { resizeForUpload: jest.Mock };
const api = jest.requireMock('@/api/client') as { apiRequest: jest.Mock; ApiClientError: new (status: number) => Error };

const tx = (ko: string) => ko;
const okScan = { lines: [{ text: '김치찌개', translatedText: '김치찌개', allergenWords: [] }], unreadLineCount: 0, evidenceStatus: 'ESTIMATED' };

const multipart = jest.requireMock('@/api/multipart') as { singleFileFormData: jest.Mock };

/** 마지막으로 읽으러 간 사진의 주소. 보낸 것이 줄인 것인지 원본인지를 이것으로 가른다. */
const sentUri = () => {
  const call = multipart.singleFileFormData.mock.calls.at(-1);
  return String((call?.[1] as { uri?: string } | undefined)?.uri ?? '');
};

beforeEach(() => {
  jest.clearAllMocks();
  api.apiRequest.mockResolvedValue(okScan);
});

describe('scanMenu — 줄이기가 실패해도 보낸다', () => {
  it('줄이기가 되면 줄인 것을 보낸다', async () => {
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 1600, height: 900 });

    const result = await scanMenu('file:///orig.jpg', 'token', tx, 'ko');

    expect(result.state).toBe('success');
    expect(api.apiRequest).toHaveBeenCalledTimes(1);
    expect(sentUri()).toContain('small.jpg');
  });

  it('🔴 줄이기가 실패해도 원본으로 보낸다 — 요청조차 안 나가던 것이 이 버그였다', async () => {
    resize.resizeForUpload.mockRejectedValue(new Error('manipulate is not a function'));

    const result = await scanMenu('file:///orig.jpg', 'token', tx, 'ko');

    expect(api.apiRequest).toHaveBeenCalledTimes(1);
    expect(sentUri()).toContain('orig.jpg');
    expect(result.state).toBe('success');
  });

  it('로그인 안 했으면 보내지 않는다', async () => {
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 800, height: 600 });

    const result = await scanMenu('file:///orig.jpg', null, tx, 'ko');

    expect(api.apiRequest).not.toHaveBeenCalled();
    expect(result).toEqual({ state: 'error', message: '로그인한 뒤에 쓸 수 있어요.' });
  });

  it('원본이 서버 상한을 넘으면 413 을 받아 그 말을 그대로 한다', async () => {
    resize.resizeForUpload.mockRejectedValue(new Error('out of memory'));
    api.apiRequest.mockRejectedValue(new api.ApiClientError(413));

    const result = await scanMenu('file:///huge.jpg', 'token', tx, 'ko');

    expect(result).toEqual({ state: 'error', message: '사진이 너무 커요. 더 작게 찍어 주세요.' });
  });
});
