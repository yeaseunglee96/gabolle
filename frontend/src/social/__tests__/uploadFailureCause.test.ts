// 사진이 안 올라갈 때 왜인지가 화면에 남는가 — S15P21E201-1187.
jest.mock('@/api/multipart', () => ({ singleFileFormData: jest.fn(async () => new FormData()) }));
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

/** 실패한 결과에서 사람에게 보여 줄 말을 꺼낸다. */
function failureMessage(result: Awaited<ReturnType<typeof uploadStoryImage>>): string {
  if (result.state === 'success') throw new Error('실패를 재는 시험인데 업로드가 성공했다');
  return result.message;
}

beforeEach(() => jest.clearAllMocks());

describe('uploadStoryImage — 못 올린 이유를 버리지 않는다', () => {
  it('서버에 닿지도 못하면 원인을 뒤에 붙여 보여준다', async () => {
    api.apiRequest.mockRejectedValue(new api.ApiUnavailableError(undefined, 'Could not retrieve file for uri content://media/1'));

    const result = await uploadStoryImage(asset, 'token');

    expect(result.state).toBe('offline');
    // 사람에게 할 말은 그대로 두고, 원인을 덧붙인다.
    expect(failureMessage(result)).toContain('서버에 연결할 수 없어요');
    expect(failureMessage(result)).toContain('Could not retrieve file for uri content://media/1');
  });

  it('원인을 못 알아냈으면 군더더기를 붙이지 않는다', async () => {
    api.apiRequest.mockRejectedValue(new api.ApiUnavailableError(undefined, null));

    const result = await uploadStoryImage(asset, 'token');

    expect(result.state).toBe('offline');
    expect(failureMessage(result)).toBe('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.');
    // 빈 괄호를 남기지 않는다. 「(null)」이나 「」은 사용자에게 고장으로 읽힌다.
    expect(failureMessage(result)).not.toContain('(');
  });

  it('올라가면 아무 말도 덧붙이지 않는다', async () => {
    api.apiRequest.mockResolvedValue({ imageId: 'i1', imageUrl: '/photos/a.jpg', contentType: 'image/jpeg', byteSize: 100 });

    const result = await uploadStoryImage(asset, 'token');

    expect(result).toEqual({ state: 'success', imageUrl: '/photos/a.jpg' });
  });
});
