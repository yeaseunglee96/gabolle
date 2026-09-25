// 댓글에도 사진 — S15P21E201-1651.
//
// 🔴 이 시험이 지키는 것:
//    ① 댓글 입력칸에 「사진 추가」가 있다 — 원글과 같은 부품(useStoryImages)으로 3장까지.
//    ② 올라간 사진 주소가 댓글과 함께 간다(parentStoryId + imageUrls). 전에는 imageUrls 를 늘 빈 목록으로 보냈다.
//    ③ 사진이 올라가는 중이면 「남기기」를 누를 수 없다 — 주소가 아직 없어서 사진이 빠진 채 올라간다.
//    ④ 보낸 뒤에는 고른 사진을 비운다 — 다음 댓글에 같은 사진이 또 붙지 않게.
import type { ReactElement } from 'react';
import { act, fireEvent, render as rtlRender, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.setTimeout(30000);

function render(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

// 사진 고르기·올리기는 기기 기능이라 시험에서는 결과만 흉내 낸다.
const mockPhotos = {
  images: [] as Array<{ localUri: string; imageUrl?: string; uploading?: boolean; error?: string }>,
  uploadedUrls: [] as string[],
  anyUploading: false,
  canAddMore: true,
  addImage: jest.fn(async () => {}),
  retryImage: jest.fn(),
  removeImage: jest.fn(),
  clearImages: jest.fn(),
  restoreUploaded: jest.fn(),
};
jest.mock('@/social/useStoryImages', () => ({ MAX_STORY_IMAGES: 3, useStoryImages: () => mockPhotos }));
// 사진 배치 부품은 사진 크기를 기기에 물어서 칸을 나누는데(Image.getSize), 시험 환경에는 그 기능이 없다. 몇 장을 그리는지만 남긴다.
jest.mock('@/components/PhotoGrid', () => ({
  PhotoGrid: ({ photos }: { photos: unknown[] }) => { const { Text: T } = jest.requireActual('react-native'); return <T>{`고른 사진 ${photos.length}장`}</T>; },
}));

import StoryDetail from '../feed/[id]';

const STORY_ID = '11111111-1111-1111-1111-111111111111';
const envelope = (data: unknown) => JSON.stringify({ data, error: null, meta: { requestId: 'r' } });
const json = (data: unknown) => new Response(envelope(data), { status: 200, headers: { 'content-type': 'application/json' } });
const story = (overrides: Record<string, unknown> = {}) => ({
  id: STORY_ID, author: { id: 'author', displayName: '이예승' }, body: '오늘의 기록', parentId: null, images: [], visibility: 'PUBLIC',
  publishAt: '2026-09-18T00:00:00Z', createdAt: '2026-09-18T00:00:00Z', updatedAt: '2026-09-18T00:00:00Z', mine: false, published: true, ...overrides,
});

const posted: Array<Record<string, unknown>> = [];
beforeEach(() => {
  jest.clearAllMocks();
  posted.length = 0;
  Object.assign(mockPhotos, { images: [], uploadedUrls: [], anyUploading: false, canAddMore: true });
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    if (url.includes('/auth/anonymous')) return json({ sessionId: 's', sessionToken: 't', issuedAt: '' });
    if (url.endsWith(`/api/v1/stories/${STORY_ID}`) && method === 'GET') return json(story());
    if (url.endsWith(`/api/v1/stories/${STORY_ID}/replies`)) return json([]);
    if (url.endsWith('/api/v1/stories') && method === 'POST') {
      const body = JSON.parse(String(init?.body));
      posted.push(body);
      return new Response(envelope(story({ id: 'reply-1', body: body.body, parentId: STORY_ID, mine: true, images: [] })), { status: 201, headers: { 'content-type': 'application/json' } });
    }
    if (url.includes('/profile') || url.includes('/saved')) return json({});
    throw new Error(`시험이 준비 안 한 요청: ${method} ${url}`);
  }) as unknown as typeof fetch;
});

describe('댓글 사진', () => {
  it('🔴 댓글 입력칸에 「사진 추가」가 있다 — 누르면 원글과 같은 방식으로 고른다', async () => {
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.press(view.getByLabelText('댓글에 사진 추가'));
    expect(mockPhotos.addImage).toHaveBeenCalledTimes(1);
    expect(view.getByText('사진 0/3')).toBeTruthy();
  });

  it('🔴 올라간 사진 주소가 댓글과 함께 가고, 보낸 뒤 고른 사진을 비운다', async () => {
    Object.assign(mockPhotos, { images: [{ localUri: 'file:///a.jpg', imageUrl: 'https://img.test/a.jpg' }], uploadedUrls: ['https://img.test/a.jpg'] });
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    expect(view.getByText('고른 사진 1장')).toBeTruthy();
    expect(view.getByText('사진 1/3')).toBeTruthy();
    fireEvent.changeText(view.getByLabelText('댓글 입력'), '저도 가 봤어요');
    await act(async () => { fireEvent.press(view.getByText('남기기')); });
    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toMatchObject({ body: '저도 가 봤어요', parentStoryId: STORY_ID, imageUrls: ['https://img.test/a.jpg'] });
    await waitFor(() => expect(mockPhotos.clearImages).toHaveBeenCalled());
  });

  it('🔴 사진이 올라가는 중이면 「남기기」를 누를 수 없다', async () => {
    Object.assign(mockPhotos, { images: [{ localUri: 'file:///a.jpg', uploading: true }], anyUploading: true });
    const view = render(<StoryDetail />);
    await waitFor(() => expect(view.getByText('오늘의 기록')).toBeTruthy());
    fireEvent.changeText(view.getByLabelText('댓글 입력'), '저도 가 봤어요');
    expect(view.getByRole('button', { name: '남기기' }).props.accessibilityState).toMatchObject({ disabled: true });
  });
});
