// 피드 「추천」 갈래·「인기순」 정렬 — 서버(1368)와의 약속을 프론트가 지키는가. S15P21E201-1411.
import { ApiClientError } from '@/api/client';
import { feedQueryKey, loadFeed, parseFeedApplied } from '@/social/stories';

const mockApiRequest = jest.fn();
jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: (...args: unknown[]) => mockApiRequest(...args) };
});

const page = (items: unknown[] = [], nextCursor: string | null = null) => ({ items, nextCursor });
const respond = (applied: string | null) => (path: string, options: { onResponse?: (r: Response) => void }) => {
  options.onResponse?.({ headers: { get: (name: string) => (name.toLowerCase() === 'x-feed-applied' ? applied : null) } } as unknown as Response);
  return Promise.resolve(page());
};

beforeEach(() => mockApiRequest.mockReset());

describe('요청 모양', () => {
  it('최신순은 sort 를 안 보낸다 — 서버 기본값이라 보내면 옛 서버가 400 을 낼 수 있다', async () => {
    mockApiRequest.mockImplementation(respond('RECENT'));
    await loadFeed({ scope: 'ALL', sort: 'RECENT', accessToken: null });
    expect(mockApiRequest.mock.calls[0][0]).not.toContain('sort=');
  });

  it('인기순은 sort=POPULAR', async () => {
    mockApiRequest.mockImplementation(respond('POPULAR'));
    await loadFeed({ scope: 'ALL', sort: 'POPULAR', accessToken: null });
    expect(mockApiRequest.mock.calls[0][0]).toContain('sort=POPULAR');
  });

  it('🔴 추천(FOR_YOU)에는 sort 를 절대 안 보낸다 — 서버가 sort=FOR_YOU 도, 추천+정렬도 받지 않는다', async () => {
    mockApiRequest.mockImplementation(respond('POPULAR'));
    await loadFeed({ scope: 'FOR_YOU', sort: 'POPULAR', accessToken: null });
    expect(mockApiRequest.mock.calls[0][0]).toContain('scope=FOR_YOU');
    expect(mockApiRequest.mock.calls[0][0]).not.toContain('sort=');
  });
});

describe('실제로 적용된 정렬', () => {
  it('머리 X-Feed-Applied 를 결과에 싣는다 — 추천을 부탁했는데 POPULAR 로 떨어지면 화면이 그렇다고 말해야 한다', async () => {
    mockApiRequest.mockImplementation(respond('POPULAR'));
    const result = await loadFeed({ scope: 'FOR_YOU', accessToken: null });
    expect(result).toMatchObject({ state: 'success', applied: 'POPULAR' });
  });

  it('머리가 없으면(옛 서버) null — 아무 말도 안 한다', async () => {
    mockApiRequest.mockImplementation(respond(null));
    const result = await loadFeed({ scope: 'FOR_YOU', accessToken: null });
    expect(result).toMatchObject({ state: 'success', applied: null });
  });

  it.each([['recent', 'RECENT'], ['POPULAR', 'POPULAR'], [' for_you ', 'FOR_YOU'], ['weird', null], [undefined, null]])('parseFeedApplied(%p) → %p', (input, expected) => {
    expect(parseFeedApplied(input as string | undefined)).toBe(expected);
  });
});

describe('커서', () => {
  it('열쇠에 정렬이 들어간다 — 갈래를 바꾸면 커서도 새로 시작해야 한다', () => {
    expect(feedQueryKey('ALL', true, 'RECENT')).not.toEqual(feedQueryKey('ALL', true, 'POPULAR'));
    // 정렬을 안 주면 최신순 — 예전 부르는 곳이 그대로 맞는다
    expect(feedQueryKey('ALL', true)).toEqual(feedQueryKey('ALL', true, 'RECENT'));
  });

  it('다른 갈래의 커서라 400(FEED_CURSOR_INVALID)이면 첫 쪽부터 다시 받고, 돌아갔다고 말한다', async () => {
    mockApiRequest
      .mockImplementationOnce(() => Promise.reject(new ApiClientError('bad cursor', 'FEED_CURSOR_INVALID', 400)))
      .mockImplementation(respond('POPULAR'));
    const result = await loadFeed({ scope: 'ALL', sort: 'POPULAR', cursor: 'stale', accessToken: null });
    expect(result).toMatchObject({ state: 'success', restarted: true });
    expect(mockApiRequest).toHaveBeenCalledTimes(2);
    expect(mockApiRequest.mock.calls[1][0]).not.toContain('cursor=');
  });
});
