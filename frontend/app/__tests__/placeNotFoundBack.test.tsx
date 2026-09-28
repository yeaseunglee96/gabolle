// 장소를 못 찾을 때 — S15P21E201-1680.
//
// 🔴 이 시험이 지키는 것: 문구는 「목록으로 돌아가 다른 장소를 선택해 주세요」인데 단추는 「홈으로 돌아가기」였다 — 말과 단추가
//    서로 다른 곳을 가리켰다. 단추는 「뒤로 가기」이고, 들어온 길이 없으면(주소로 바로 연 경우) 「홈으로」다(조율 세션 결정).
//    문구도 단추에 맞춘다.
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockBack = jest.fn();
const mockReplace = jest.fn();
let mockCanGoBack = true;
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace, push: jest.fn(), canGoBack: () => mockCanGoBack }),
  useLocalSearchParams: () => ({ id: 'gone' }),
  usePathname: () => '/place/gone',
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  const { ApiClientError } = jest.requireActual('@/api/client');
  return { ...actual, getPlace: jest.fn(async () => { throw new ApiClientError('장소를 찾을 수 없습니다.', 'PLACE_NOT_FOUND', 404); }) };
});
jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: jest.fn() }));
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'tok', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import PlaceDetail from '../place/[id]';

jest.setTimeout(20000);

async function open() {
  render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
  // 첫 화면은 부품을 처음 불러오느라 몇 초 걸린다 — 기다림보다 시험 제한 시간을 길게 둔다(S15P21E201-1665 규칙).
  await waitFor(() => expect(screen.getByText('장소를 찾을 수 없어요')).toBeTruthy(), { timeout: 10000 });
}

beforeEach(() => { mockBack.mockClear(); mockReplace.mockClear(); mockCanGoBack = true; });

describe('장소를 못 찾을 때', () => {
  it('🔴 들어온 길이 있으면 「뒤로 가기」 — 누르면 뒤로 간다', async () => {
    await open();
    expect(screen.queryByText('홈으로 돌아가기')).toBeNull();
    expect(screen.getByText('뒤로 가서 다른 장소를 골라 주세요.')).toBeTruthy();
    fireEvent.press(screen.getByText('뒤로 가기'));
    expect(mockBack).toHaveBeenCalledTimes(1);
  });

  it('주소로 바로 열었으면 「홈으로」 — 문구도 홈을 가리킨다', async () => {
    mockCanGoBack = false;
    await open();
    expect(screen.getByText('홈에서 다른 장소를 골라 주세요.')).toBeTruthy();
    fireEvent.press(screen.getByText('홈으로'));
    expect(mockReplace).toHaveBeenCalledWith('/home');
  });
});
