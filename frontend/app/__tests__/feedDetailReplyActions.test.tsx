// 댓글 수정·삭제·신고 —.
import type { ReactElement } from 'react';
import { fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

function render(ui: ReactElement) {
  return rtlRender(<OnboardingPreferencesProvider>{ui}</OnboardingPreferencesProvider>);
}

const mockBack = jest.fn();
const mockReplace = jest.fn();
const mockPush = jest.fn();
const mockCanGoBack = jest.fn(() => true);
const mockAuth = { accessToken: 'token' };

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace, push: mockPush, canGoBack: mockCanGoBack }),
  useLocalSearchParams: () => ({ id: STORY_ID }),
  useFocusEffect: (cb: () => void | (() => void)) => {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    require('react').useEffect(() => cb(), [cb]);
  },
}));

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

// 이 화면 트리에는 OnboardingPreferencesProvider 가 들어 있고, 그 안에서
// setApiLanguage/getApiLanguage 를 실제로 부른다(언어 설정을 서버 요청에 실어 보내는
// 자리다). 모듈 전체를 목으로 바꾸면서 이 둘을 빠뜨리면 "함수가 아닙니다"로 렌더
// 자체가 죽는다 — 실제로 그렇게 죽어서 다음 시험까지 "unmount된 트리" 오류로 번졌다.
jest.mock('@/api/client', () => {
  class ApiClientError extends Error {
    status: number;
    code: string;
    constructor(message: string, code: string, status: number) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }
  class ApiUnavailableError extends ApiClientError {
    cause: string | null;
    constructor(message = '서버에 연결할 수 없어요.', cause: string | null = null) {
      super(message, 'NETWORK_ERROR', 0);
      this.cause = cause;
    }
  }
  return {
    apiRequest: jest.fn(),
    ApiClientError,
    ApiUnavailableError,
    API_BASE_URL: 'http://test',
    getApiLanguage: jest.fn(() => 'ko'),
    setApiLanguage: jest.fn(),
  };
});

import StoryDetail from '../feed/[id]';

const STORY_ID = '11111111-1111-1111-1111-111111111111';
const MY_REPLY_ID = '22222222-2222-2222-2222-222222222222';
const OTHER_REPLY_ID = '33333333-3333-3333-3333-333333333333';
const AUTHOR_ID = '55555555-5555-5555-5555-555555555555';

function post() {
  return {
    id: STORY_ID,
    author: { id: AUTHOR_ID, displayName: '이예승' },
    body: '오늘의 기록',
    parentId: null,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
    replyCount: 2,
  };
}

function myReply() {
  return {
    id: MY_REPLY_ID,
    author: { id: 'me', displayName: '진미리' },
    body: '제 댓글이에요',
    parentId: STORY_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: true,
    published: true,
  };
}

function otherReply() {
  return {
    id: OTHER_REPLY_ID,
    author: { id: 'stranger', displayName: '모진성' },
    body: '남의 댓글이에요',
    parentId: STORY_ID,
    images: [],
    visibility: 'PUBLIC',
    publishAt: '2026-09-18T00:00:00Z',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
    mine: false,
    published: true,
  };
}

type ApiRequestCall = { path: string; options: Record<string, unknown> };

const api = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };
const requests: ApiRequestCall[] = [];

/** post 의 작성자가 남(mine: false)이라 화면이 팔로우 상태를 물어본다 —. */
function profile() {
  return { userId: AUTHOR_ID, displayName: '이예승', followerCount: 0, followingCount: 0, storyCount: 0, following: false };
}

function installApi() {
  requests.length = 0;
  api.apiRequest.mockReset();
  api.apiRequest.mockImplementation(async (path: string, options: Record<string, unknown> = {}) => {
    requests.push({ path, options });
    const method = (options.method as string) ?? 'GET';

    if (path === `/api/v1/stories/${STORY_ID}` && method === 'GET') return post();
    if (path === `/api/v1/users/${AUTHOR_ID}/profile`) return profile();
    if (path === `/api/v1/stories/${STORY_ID}/replies`) return [myReply(), otherReply()];
    if (path === `/api/v1/stories/${MY_REPLY_ID}` && method === 'PATCH') return { ...myReply(), body: '고친 댓글' };
    if (path === `/api/v1/stories/${MY_REPLY_ID}` && method === 'DELETE') return undefined;
    if (path === `/api/v1/stories/${OTHER_REPLY_ID}/reports` && method === 'POST') return undefined;
    throw new Error(`시험이 준비 안 한 요청: ${method} ${path}`);
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  installApi();
});

describe('댓글 카드 — 수정·삭제·신고', () => {
  it('내 댓글에는 수정·삭제가, 남의 댓글에는 신고가 보인다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    expect(view.getAllByLabelText('댓글 수정')).toHaveLength(1);
    expect(view.getAllByLabelText('댓글 삭제')).toHaveLength(1);
    expect(view.getAllByLabelText('댓글 신고')).toHaveLength(1);
    // 내 댓글에는 신고가, 남의 댓글에는 수정·삭제가 섞여 있지 않다.
    expect(view.queryAllByLabelText('댓글 수정')).toHaveLength(1);
  });

  it('🔴 수정 — PATCH 가 그 댓글 id 로 나가고, 화면이 고친 내용으로 바뀐다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 수정'));
    const editField = view.getByDisplayValue('제 댓글이에요');
    fireEvent.changeText(editField, '고친 댓글');
    fireEvent.press(view.getByText('저장'));

    await waitFor(() => expect(view.getByText('고친 댓글')).toBeTruthy());
    const patchCall = requests.find((r) => r.options.method === 'PATCH');
    expect(patchCall?.path).toBe(`/api/v1/stories/${MY_REPLY_ID}`);
    expect(patchCall?.options.body).toEqual({ body: '고친 댓글' });
  });

  it('🔴 삭제 — 확인 뒤 DELETE 가 그 댓글 id 로 나가고, 목록에서 빠진다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('제 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 삭제'));
    fireEvent.press(view.getByText('삭제 확정'));

    await waitFor(() => expect(view.queryByText('제 댓글이에요')).toBeNull(), { timeout: 5000 });
    const deleteCall = requests.find((r) => r.options.method === 'DELETE');
    expect(deleteCall?.path).toBe(`/api/v1/stories/${MY_REPLY_ID}`);
    // 남의 댓글은 그대로 남는다 — 지운 것은 딱 하나뿐이다.
    expect(view.getByText('남의 댓글이에요')).toBeTruthy();
  });

  it('🔴 신고 — 그 댓글 id 로 신고가 나가고, 화면 전체가 아니라 그 댓글 한 장만 사라진다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('남의 댓글이에요')).toBeTruthy());

    fireEvent.press(view.getByLabelText('댓글 신고'));
    fireEvent.press(view.getByText('스팸'));
    fireEvent.press(view.getByText('신고 접수'));

    await waitFor(() => expect(view.queryByText('남의 댓글이에요')).toBeNull(), { timeout: 5000 });
    const reportCall = requests.find((r) => r.path.includes('/reports'));
    expect(reportCall?.path).toBe(`/api/v1/stories/${OTHER_REPLY_ID}/reports`);
    // 원글 신고와 다르다 — "신고가 접수됐어요" 전체 화면 안내로 안 바뀐다. 원글 본문은 그대로 있다.
    expect(view.getByText('오늘의 기록')).toBeTruthy();
    expect(view.queryByText('신고가 접수됐어요')).toBeNull();
  });
});
