// 글 상세 「공동 작성자 ›」 — S15P21E201-1673.
//
// 🔴 이 시험이 지키는 것(사용자 결정): 남의 글·공동 작성자가 없는 글에도 모두 보였고, 남의 글에서 누르면 빈 목록이 나왔다.
//    내 글이면 늘 보인다 — 공동 작성자를 초대하는 입구다. 남의 글이면 공동 작성자(수락한 사람)가 있을 때만 보인다.
//    서버가 공동 작성자 칸을 안 보내는 옛 판이면 남의 글에서는 안 보인다 — 모르는 것을 있다고 하지 않는다.
import type { ReactElement } from 'react';
import { render as rtlRender, waitFor } from '@testing-library/react-native';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

// 상세가 저장 여부를 react-query 로 읽는다 — 공급자가 없으면 렌더가 죽는다(feedDetailMenu.test 와 같은 사정).
function render(ui: ReactElement) {
  // gcTime: Infinity — 보관소 정리 타이머(기본 5분)를 만들지 않는다. 만들면 이 파일만 돌릴 때 시험이 끝나도 5분 동안 안 닫힌다.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return rtlRender(<QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{ui}</OnboardingPreferencesProvider></QueryClientProvider>);
}

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: STORY_ID }),
  useFocusEffect: (cb: () => void | (() => void)) => {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    require('react').useEffect(() => cb(), [cb]);
  },
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token' }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import StoryDetail from '../feed/[id]';

const STORY_ID = '11111111-1111-1111-1111-111111111111';
const AUTHOR_ID = '44444444-4444-4444-4444-444444444444';
const ENTRY = '공동 작성자 보기';

let story: Record<string, unknown> = {};

function envelope(data: unknown) {
  return JSON.stringify({ data, error: null, meta: { requestId: 'r' } });
}

beforeEach(() => {
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const json = (data: unknown) => new Response(envelope(data), { status: 200, headers: { 'content-type': 'application/json' } });
    if (url.includes('/auth/anonymous')) return json({ sessionId: 's', sessionToken: 't', issuedAt: '' });
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') {
      return json({
        id: STORY_ID, author: { id: AUTHOR_ID, displayName: '이예승' }, body: '오늘의 기록', parentId: null, images: [], visibility: 'PUBLIC',
        publishAt: '2026-09-18T00:00:00Z', createdAt: '2026-09-18T00:00:00Z', updatedAt: '2026-09-18T00:00:00Z', published: true, ...story,
      });
    }
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) return json([]);
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
});

async function open() {
  const view = render(<StoryDetail />);
  await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
  return view;
}

describe('글 상세 「공동 작성자 ›」', () => {
  it('내 글이면 늘 보인다 — 공동 작성자가 아직 없어도(초대 입구)', async () => {
    story = { mine: true, coauthors: [] };
    expect((await open()).getByLabelText(ENTRY)).toBeTruthy();
  });

  it('🔴 남의 글에 공동 작성자가 없으면 안 보인다 — 눌러도 빈 목록뿐이다', async () => {
    story = { mine: false, coauthors: [] };
    expect((await open()).queryByLabelText(ENTRY)).toBeNull();
  });

  it('남의 글이라도 공동 작성자가 있으면 보인다', async () => {
    story = { mine: false, coauthors: [{ id: 'u2', displayName: '진미리' }] };
    expect((await open()).getByLabelText(ENTRY)).toBeTruthy();
  });

  it('서버가 공동 작성자 칸을 안 보내면(옛 판) 남의 글에서는 안 보인다', async () => {
    story = { mine: false };
    expect((await open()).queryByLabelText(ENTRY)).toBeNull();
  });
});
