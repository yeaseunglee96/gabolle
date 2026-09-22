// 장소 상세가 「봤다」(place_view)를 보내는가 — S15P21E201-638.
//
// 🔴 지켜야 할 것 넷이 이 시험이다: 키는 placeId · 화면당 한 번 · 로딩이 끝난 뒤에만 · demoPlace 제외.
//    두 번 보내면 취향 가중치가 부풀고, not-found 를 봤다고 적으면 없는 장소가 취향이 된다.
import { act, render, waitFor } from '@testing-library/react-native';

const mockPlace = {
  placeId: 'p1', nameKo: '광안리해수욕장', nameEn: 'Gwangalli Beach', category: 'BEACH',
  address: '부산 수영구', lat: 35.15, lng: 129.12, features: [], photoUrl: null, photoSource: null,
};

const mockSend = jest.fn();
jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: (input: unknown) => mockSend(input) }));

let mockParamId = 'p1';
let mockGetPlace: () => Promise<unknown> = async () => mockPlace;
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: mockParamId }),
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, getPlace: jest.fn(() => mockGetPlace()) };
});
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'tok', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { ApiClientError } from '@/api/client';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import PlaceDetail from '../place/[id]';

const mount = () => render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
const views = () => mockSend.mock.calls.map((c) => c[0]).filter((e) => e.type === 'place_view');

beforeEach(() => { mockSend.mockClear(); mockParamId = 'p1'; mockGetPlace = async () => mockPlace; });

describe('place_view — 장소 상세를 봤다', () => {
  it('🔴 로딩이 끝난 뒤 placeId 키로 딱 한 번 보낸다 — 재렌더에도 늘지 않는다', async () => {
    const view = mount();
    await waitFor(() => expect(views()).toHaveLength(1));
    expect(views()[0]).toEqual({ type: 'place_view', accessToken: 'tok', payload: { placeId: 'p1', surface: 'place_detail' } });
    await act(async () => { view.rerender(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>); });
    expect(views()).toHaveLength(1);
    // 옛 키(place_id)가 다시 들어오면 서버 쪽 읽기가 못 찾는다 — 여기서 걸린다.
    expect(Object.keys(views()[0].payload)).not.toContain('place_id');
  });

  it('🔴 not-found 는 「봤다」가 아니다', async () => {
    mockGetPlace = async () => { throw new ApiClientError('없음', 'PLACE_NOT_FOUND', 404); };
    mount();
    await new Promise((r) => setTimeout(r, 50));
    expect(views()).toHaveLength(0);
  });

  it('demoPlace(내장 견본)는 보내지 않는다 — place_like 와 같은 규칙', async () => {
    mockParamId = 'gwangalli';
    mount();
    await new Promise((r) => setTimeout(r, 50));
    expect(views()).toHaveLength(0);
  });
});
