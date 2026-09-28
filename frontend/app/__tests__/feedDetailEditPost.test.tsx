// 올린 기록 수정 — S15P21E201-1807. 메뉴에 「수정」이 없어 작성자가 글을 못 고쳤다.
import type { ReactElement } from 'react';
import { fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';
import { View } from 'react-native';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

// 상세가 저장 여부를 react-query 로 읽는다(S15P21E201-1358) — 목록과 같은 열쇠를 쓰려고. 공급자가 없으면 렌더가 죽는다.
function render(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return rtlRender(<QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{ui}</OnboardingPreferencesProvider></QueryClientProvider>);
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

import StoryDetail from '../feed/[id]';

const STORY_ID = '11111111-1111-1111-1111-111111111111';
const AUTHOR_ID = '44444444-4444-4444-4444-444444444444';

function envelope(data: unknown) {
  return JSON.stringify({ data, error: null, meta: { requestId: 'r' } });
}

function post(overrides: Record<string, unknown> = {}) {
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
    ...overrides,
  };
}

const requests: Array<{ url: string; method: string; body: unknown }> = [];
let following = false;

function installFetch() {
  requests.length = 0;
  following = false;
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url.includes('/auth/anonymous')) {
      return new Response(envelope({ sessionId: 's', sessionToken: 't', issuedAt: '' }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    requests.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });

    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') {
      return new Response(envelope(post({ mine: mineStory })), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) {
      return new Response(envelope([]), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/users/${AUTHOR_ID}/profile`) && method === 'GET') {
      return new Response(envelope({ userId: AUTHOR_ID, displayName: '이예승', followerCount: 0, followingCount: 0, storyCount: 0, following }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/users/${AUTHOR_ID}/follow`) && (method === 'PUT' || method === 'DELETE')) {
      following = method === 'PUT';
      return new Response(envelope({ userId: AUTHOR_ID, following, followerCount: following ? 1 : 0, followingCount: 0 }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/reports`) && method === 'POST') {
      return new Response(null, { status: 204 });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === "PATCH") {
      if (patchFails) return new Response(JSON.stringify({ data: null, error: { code: "FORBIDDEN", message: "고칠 수 없어요" }, meta: { requestId: "r" } }), { status: 403, headers: { "content-type": "application/json" } });
      const sent = JSON.parse(String(init?.body));
      return new Response(envelope(post({ mine: true, body: sent.body })), { status: 200, headers: { "content-type": "application/json" } });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'DELETE') {
      return new Response(null, { status: 204 });
    }
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
}

let mineStory = false;
let patchFails = false;

// ⋯ 메뉴는 버튼 자리를 잰 뒤에 열린다(S15P21E201-1576). 시험 환경의 View 는 재는 함수가 아무것도 안 하는
// 가짜라 그대로 두면 메뉴가 영영 안 열린다 — 버튼 하나의 자리를 돌려주게 한다.
type Measurable = { measureInWindow: (callback: (x: number, y: number, width: number, height: number) => void) => void };
function measureButtonsAt(x: number, y: number) {
  jest.spyOn((View as unknown as { prototype: Measurable }).prototype, 'measureInWindow')
    .mockImplementation((callback) => callback(x, y, 32, 32));
}

beforeEach(() => {
  jest.clearAllMocks();
  mineStory = false;
  patchFails = false;
  installFetch();
  measureButtonsAt(300, 100);
});
describe('올린 기록 수정', () => {
  it('🔴 내 글이면 ⋯ 메뉴에 「수정」이 있다', async () => {
    mineStory = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('더 보기'));
    expect(view.getByText('수정')).toBeTruthy();
  });

  it('남의 글이면 「수정」이 없다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('더 보기'));
    await waitFor(() => expect(view.getByText('이 글 신고')).toBeTruthy());
    expect(view.queryByText('수정')).toBeNull();
  });

  it('🔴 고쳐서 저장하면 새 본문으로 PATCH 가 나가고 화면이 바뀐다', async () => {
    mineStory = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('더 보기'));
    fireEvent.press(view.getByText('수정'));
    fireEvent.changeText(view.getByLabelText('기록 수정'), '  고친 기록  ');
    fireEvent.press(view.getAllByText('저장')[0]);

    await waitFor(() => expect(view.getByText('고친 기록')).toBeTruthy());
    const patch = requests.find((r) => r.method === 'PATCH');
    expect(patch?.url.endsWith(`/api/v1/stories/${STORY_ID}`)).toBe(true);
    expect(patch?.body).toEqual({ body: '고친 기록' });
    expect(view.queryByLabelText('기록 수정')).toBeNull();
    expect(view.getByText('저장했어요')).toBeTruthy();
  });

  it('취소하면 요청 없이 원래 본문이 남는다', async () => {
    mineStory = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('더 보기'));
    fireEvent.press(view.getByText('수정'));
    fireEvent.changeText(view.getByLabelText('기록 수정'), '버릴 초안');
    fireEvent.press(view.getByText('취소'));
    expect(view.getByText('오늘의 기록')).toBeTruthy();
    expect(requests.some((r) => r.method === 'PATCH')).toBe(false);
  });

  it('🔴 서버가 거절하면 편집을 닫지 않고 이유를 보여준다', async () => {
    mineStory = true;
    patchFails = true;
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('더 보기'));
    fireEvent.press(view.getByText('수정'));
    fireEvent.changeText(view.getByLabelText('기록 수정'), '고친 기록');
    fireEvent.press(view.getAllByText('저장')[0]);
    await waitFor(() => expect(view.getAllByRole('alert').length).toBeGreaterThan(0));
    expect(view.getByLabelText('기록 수정')).toBeTruthy();
  });
});
