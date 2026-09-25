// 마이페이지 「맞춤 추천」 스위치의 자리 — S15P21E201-1644.
//
// 🔴 이 시험이 지키는 것: 스위치는 「내 계정」 묶음의 「여행 취향」 바로 아래다(사용자 결정).
//    전에는 「앱」 묶음 맨 아래(약관·고지 다음)라 폰에서는 탭바에 반쯤 가렸고, 취향과 떨어져 있어 찾기 어려웠다.
//    폰과 넓은 화면은 같은 묶음을 그리므로 넓은 화면 하나로 순서를 본다.
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react-native';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }), useLocalSearchParams: () => ({}) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', user: { userId: 'me', displayName: '김부산', email: 'busan@example.test' }, signOut: jest.fn() }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'desktop', desktop: true, width: 1280, height: 900, isLandscape: true }) }));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ clear: jest.fn() }) }));
jest.mock('@/me/myPageCounts', () => ({ useMyPageCounts: () => ({ answeredPreferences: 0, storyCount: 0, followerCount: 0, followingCount: 0 }) }));
jest.mock('@/me/profileAvatar', () => ({ loadProfileAvatar: jest.fn(async () => null) }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), loadTrips: jest.fn(async () => ({ state: 'success', trips: [] })) }));
jest.mock('@/social/stories', () => ({ ...jest.requireActual('@/social/stories'), loadUserStories: jest.fn(async () => ({ state: 'success', items: [] })) }));
jest.mock('@/home/tripNavigation', () => ({ resolveHomeTripDestination: jest.fn(async () => '/trip/t1') }));
jest.mock('@/components/DongbaekMascot', () => ({ GabolleMascot: () => null }));
// 알림 창 부품이 불러오기만 해도 「Expo Go 에서는 푸시가 안 된다」 경고를 찍는다 — 이 시험은 알림 창을 안 연다.
jest.mock('expo-notifications', () => ({}));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import Me from '../(tabs)/me';

const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={client}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>
);

/** 화면에 그려진 글자를 위에서 아래 순서대로 모은다. */
type Json = { children?: Array<Json | string> | null } | string | null;
const textsInOrder = (node: Json | Json[]): string[] => {
  if (node === null) return [];
  if (Array.isArray(node)) return node.flatMap(textsInOrder);
  if (typeof node === 'string') return [node];
  return (node.children ?? []).flatMap(textsInOrder);
};

describe('마이페이지 「맞춤 추천」 스위치 자리', () => {
  it('🔴 「내 계정」 묶음의 「여행 취향」 바로 아래 — 「앱」 묶음보다 위', async () => {
    render(<Me />, { wrapper });
    await waitFor(() => expect(screen.getByText('맞춤 추천')).toBeTruthy());
    // 프로필 사진·동의 값을 읽어 오는 약속이 끝나게 둔다 — 안 그러면 시험 뒤에 화면이 바뀌어 경고가 난다
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });
    const texts = textsInOrder(screen.toJSON() as Json | Json[]);
    const at = (text: string) => texts.indexOf(text);
    expect(at('여행 취향')).toBeGreaterThanOrEqual(0);
    expect(at('맞춤 추천')).toBeGreaterThan(at('여행 취향'));
    expect(at('맞춤 추천')).toBeLessThan(at('연결된 소셜 계정'));
    expect(at('맞춤 추천')).toBeLessThan(at('약관·고지'));
    // 스위치는 한 벌이다 — 옮기면서 두 자리에 남지 않는다
    expect(screen.getAllByText('맞춤 추천')).toHaveLength(1);
  });
});
