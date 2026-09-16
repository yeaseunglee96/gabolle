// 메뉴판 읽기가 줄이기에서 막혀 요청조차 못 나가던 것 — S15P21E201-1121 의 같은 자리.
//
// 2026-09-17 안드로이드 실기기에서 「사진을 읽지 못했어요」가 떴는데, 서버 기록에
// /api/v1/menu-scans 요청이 한 줄도 없었다. 이 시험이 지키는 것은 하나다 —
// **줄이기가 실패해도 사진은 서버로 간다.**
jest.mock('@/social/imageResize', () => ({ resizeForUpload: jest.fn() }));
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
const okScan = { lines: [{ text: '김치찌개', allergenWords: [] }], unreadLineCount: 0, evidenceStatus: 'ESTIMATED' };

// FormData 안을 들여다보는 표준 방법이 환경마다 달라서, 넣는 순간을 붙잡는다.
let appended: Array<[string, unknown, unknown?]> = [];
const realAppend = FormData.prototype.append;
beforeAll(() => {
  FormData.prototype.append = function (...args: [string, unknown, unknown?]) { appended.push(args); return realAppend.apply(this, args as never); };
});
afterAll(() => { FormData.prototype.append = realAppend; });
const sentUri = () => { const part = appended.find(([name]) => name === 'image'); return JSON.stringify(part?.[1] ?? null); };

beforeEach(() => {
  jest.clearAllMocks();
  appended = [];
  api.apiRequest.mockResolvedValue(okScan);
});

describe('scanMenu — 줄이기가 실패해도 보낸다', () => {
  it('줄이기가 되면 줄인 것을 보낸다', async () => {
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 1600, height: 900 });

    const result = await scanMenu('file:///orig.jpg', 'token', tx);

    expect(result.state).toBe('success');
    expect(api.apiRequest).toHaveBeenCalledTimes(1);
    expect(sentUri()).toContain('small.jpg');
  });

  it('🔴 줄이기가 실패해도 원본으로 보낸다 — 요청조차 안 나가던 것이 이 버그였다', async () => {
    resize.resizeForUpload.mockRejectedValue(new Error('manipulate is not a function'));

    const result = await scanMenu('file:///orig.jpg', 'token', tx);

    expect(api.apiRequest).toHaveBeenCalledTimes(1);
    expect(sentUri()).toContain('orig.jpg');
    expect(result.state).toBe('success');
  });

  it('로그인 안 했으면 보내지 않는다', async () => {
    resize.resizeForUpload.mockResolvedValue({ uri: 'file:///small.jpg', width: 800, height: 600 });

    const result = await scanMenu('file:///orig.jpg', null, tx);

    expect(api.apiRequest).not.toHaveBeenCalled();
    expect(result).toEqual({ state: 'error', message: '로그인한 뒤에 쓸 수 있어요.' });
  });

  it('원본이 서버 상한을 넘으면 413 을 받아 그 말을 그대로 한다', async () => {
    resize.resizeForUpload.mockRejectedValue(new Error('out of memory'));
    api.apiRequest.mockRejectedValue(new api.ApiClientError(413));

    const result = await scanMenu('file:///huge.jpg', 'token', tx);

    expect(result).toEqual({ state: 'error', message: '사진이 너무 커요. 더 작게 찍어 주세요.' });
  });
});
