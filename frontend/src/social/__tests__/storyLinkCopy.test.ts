// 링크 복사를 서버에 알리는 자리 — 세는 쪽(S15P21E201-1215)은 이미 있고 화면이 안 부르고 있었다.
//
// 🔴 이 시험이 지키는 것은 「정확히 한 번, 그리고 상세를 다시 부르지 않는다」이다.
// 다시 부르면 그 호출이 조회수를 올려서 「복사한 것」이 「본 것」으로 세어진다.
// 그 새는 것은 화면에 아무 오류도 안 내고, 숫자만 조용히 틀어진다.
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
    constructor(message = '서버에 연결할 수 없어요.') {
      super(message, 'NETWORK_ERROR', 0);
    }
  }
  return {
    apiRequest: jest.fn(),
    ApiClientError,
    ApiUnavailableError,
    API_BASE_URL: 'https://example.test',
    APP_WEB_BASE_URL: 'https://example.test',
  };
});

import { apiRequest } from '@/api/client';
import { recordStoryLinkCopy, storyShareUrl } from '@/social/stories';

const mockApiRequest = apiRequest as jest.MockedFunction<typeof apiRequest>;

const STORY = {
  id: 'story-1',
  author: { id: 'u1', displayName: '누군가' },
  body: '본문',
  images: [],
  visibility: 'PUBLIC',
  publishAt: '2026-09-18T00:00:00Z',
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
  mine: false,
  published: true,
  viewCount: 7,
  linkCopyCount: 3,
};

beforeEach(() => {
  mockApiRequest.mockReset();
});

describe('링크 복사를 서버에 알린다', () => {
  it('복사할 주소는 그 기록의 상세 주소다', () => {
    expect(storyShareUrl('story-1')).toBe('https://example.test/feed/story-1');
  });

  it('주소에 들어가면 안 되는 글자를 감싼다', () => {
    // 서버가 주는 id 를 그대로 붙이므로, 주소를 깨뜨리는 글자가 오면 링크가 안 열린다.
    expect(storyShareUrl('a/b?c')).toBe('https://example.test/feed/a%2Fb%3Fc');
  });

  it('POST 를 한 번만 보낸다 — 그리고 상세를 다시 부르지 않는다', async () => {
    mockApiRequest.mockResolvedValueOnce({ ...STORY, linkCopyCount: 4 } as never);

    const outcome = await recordStoryLinkCopy('story-1', 'token');

    expect(outcome.state).toBe('success');
    // 🔴 정확히 한 번. 두 번이면 둘째가 조회수를 올린다.
    expect(mockApiRequest).toHaveBeenCalledTimes(1);
    const [path, options] = mockApiRequest.mock.calls[0];
    expect(path).toBe('/api/v1/stories/story-1/link-copies');
    expect(options).toMatchObject({ method: 'POST', accessToken: 'token' });
    // 상세를 다시 부르는 호출이 섞이지 않았는지 — 그 경로가 한 번도 안 불렸다.
    expect(mockApiRequest.mock.calls.some(([p]) => p === '/api/v1/stories/story-1')).toBe(false);
  });

  it('돌아온 글을 그대로 준다 — 화면이 그것을 그리면 다시 안 불러도 된다', async () => {
    mockApiRequest.mockResolvedValueOnce({ ...STORY, linkCopyCount: 4 } as never);

    const outcome = await recordStoryLinkCopy('story-1', 'token');

    expect(outcome.state === 'success' && outcome.story.linkCopyCount).toBe(4);
    // 조회수는 이 요청으로 안 움직인다. 움직였다면 복사가 조회로 샌 것이다.
    expect(outcome.state === 'success' && outcome.story.viewCount).toBe(7);
  });

  it('수가 안 올라가도 성공이다 — 오늘 이미 센 사람, 작성자 본인', async () => {
    // 서버는 이 경우에도 200 에 글 전체를 준다. 앱이 오류로 읽으면 사용자가 다시 누른다.
    mockApiRequest.mockResolvedValueOnce({ ...STORY, linkCopyCount: 3 } as never);

    const outcome = await recordStoryLinkCopy('story-1', 'token');

    expect(outcome.state).toBe('success');
    expect(outcome.state === 'success' && outcome.story.linkCopyCount).toBe(3);
  });

  it('로그인 없이도 보낸다 — 비회원도 세는 대상이다', async () => {
    mockApiRequest.mockResolvedValueOnce({ ...STORY } as never);

    await recordStoryLinkCopy('story-1', null);

    expect(mockApiRequest.mock.calls[0][1]).toMatchObject({ accessToken: null });
  });

  it('서버가 실패하면 실패로 준다 — 화면이 복사 자체는 됐다고 말할 수 있게', async () => {
    mockApiRequest.mockRejectedValueOnce(new Error('터졌다'));

    const outcome = await recordStoryLinkCopy('story-1', 'token');

    expect(outcome.state).not.toBe('success');
  });
});
