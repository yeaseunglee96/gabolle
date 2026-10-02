// S15P21E201-1955 — apiRequest 가 돌려주는 응답에서 visitbusan 사진 주소가 이미 프록시 주소다.
// 화면마다 고치지 않고 이 한 곳에서 바꾸므로, 여기가 빠지면 모든 화면이 다시 회색 칸이 된다.
import { API_BASE_URL, apiRequest } from '../client';

const VB = 'https://www.visitbusan.net/uploadImgs/files/cntnts/20230612143356750_ttiel';

const reply = (status: number, data: unknown = {}) => ({
  status,
  headers: { get: () => 'application/json' },
  ok: status >= 200 && status < 300,
  json: async () => ({ data, error: null, meta: { requestId: 'r' } }),
});

describe('apiRequest 공공 사진 프록시', () => {
  const realFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it('여행 표지 주소를 서버 프록시 주소로 바꿔 돌려준다', async () => {
    globalThis.fetch = jest.fn(async (url: string) => {
      if (String(url).includes('/api/v1/auth/anonymous')) {
        return reply(201, { sessionId: 's', sessionToken: 't', issuedAt: 'now' });
      }
      return reply(200, { items: [{ tripId: 't1', coverImageUrl: VB }] });
    }) as never;

    const data = await apiRequest<{ items: Array<{ coverImageUrl: string }> }>('/api/v1/trips', { accessToken: 'a' });

    expect(data.items[0].coverImageUrl).toBe(`${API_BASE_URL}/api/v1/images/proxy?url=${encodeURIComponent(VB)}`);
  });
});
