// 사진이 안 올라갈 때 **왜인지가 화면에 남는가** — S15P21E201-1187.
//
// 2026-09-17 안드로이드 실기기에서 사진 업로드가 100% 실패했다. 화면에는
// 「서버에 연결할 수 없어요」만 떴고, nginx 기록에는 요청이 한 줄도 없었다
// (네트워크까지 가지 못한 것이다). 그래서 **앱에도 서버에도 단서가 없었다** —
// 릴리스 빌드라 앱 로그도 못 본다.
//
// 이 시험이 지키는 것은 하나다 — `fetch` 가 던진 말은 버려지지 않는다.
jest.mock('@/api/client', () => {
  class ApiClientError extends Error {
    code: string;
    status: number;
    constructor(message: string, code: string, status: number) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }
  class ApiUnavailableError extends ApiClientError {
    cause: string | null;
    constructor(message = '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.', cause: string | null = null) {
      super(message, 'NETWORK_ERROR', 0);
      this.cause = cause;
    }
  }
  return { apiRequest: jest.fn(), ApiClientError, ApiUnavailableError, API_BASE_URL: 'https://example.test' };
});

import { uploadStoryImage } from '../stories';

const api = jest.requireMock('@/api/client') as {
  apiRequest: jest.Mock;
  ApiUnavailableError: new (message?: string, cause?: string | null) => Error;
};

const asset = { uri: 'file:///story.jpg', fileName: 'story.jpg', mimeType: 'image/jpeg' };

beforeEach(() => jest.clearAllMocks());

describe('uploadStoryImage — 못 올린 이유를 버리지 않는다', () => {
  it('서버에 닿지도 못하면 원인을 뒤에 붙여 보여준다', async () => {
    api.apiRequest.mockRejectedValue(new api.ApiUnavailableError(undefined, 'Could not retrieve file for uri content://media/1'));

    const result = await uploadStoryImage(asset, 'token');

    expect(result.state).toBe('offline');
    // 사람에게 할 말은 그대로 두고, 원인을 덧붙인다.
    expect(result.message).toContain('서버에 연결할 수 없어요');
    expect(result.message).toContain('Could not retrieve file for uri content://media/1');
  });

  it('원인을 못 알아냈으면 군더더기를 붙이지 않는다', async () => {
    api.apiRequest.mockRejectedValue(new api.ApiUnavailableError(undefined, null));

    const result = await uploadStoryImage(asset, 'token');

    expect(result.state).toBe('offline');
    expect(result.message).toBe('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.');
    // 🔴 빈 괄호를 남기지 않는다. 「(null)」이나 「()」은 사용자에게 고장으로 읽힌다.
    expect(result.message).not.toContain('(');
  });

  it('올라가면 아무 말도 덧붙이지 않는다', async () => {
    api.apiRequest.mockResolvedValue({ imageId: 'i1', imageUrl: '/photos/a.jpg', contentType: 'image/jpeg', byteSize: 100 });

    const result = await uploadStoryImage(asset, 'token');

    expect(result).toEqual({ state: 'success', imageUrl: '/photos/a.jpg' });
  });
});
