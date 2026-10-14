// 장소 상세 화면의 «근처 주차장» 한 줄 (S15P21E201-2052) — 이 장소의 좌표로 묻고, 누르면 근처 편의시설 화면이 이 장소 기준으로 열린다.
// 🔴 지키는 것: ① 서버 장소(좌표가 있는 것)에만 — 내장 견본 장소에는 줄도 요청도 없다 ② 못 그릴 때(0곳·실패)는 줄만 없고 화면은 그대로다
//    ③ 열리는 화면에 넘기는 이름은 화면에 그려진 그 이름(고른 언어)이다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockPlace = {
  placeId: 'beach-1', nameKo: '광안리해수욕장', nameEn: 'Gwangalli Beach', category: 'BEACH',
  address: '부산광역시 수영구 광안해변로 219', addressEn: null, lat: 35.1532, lng: 129.1186, photoUrl: null, photoSource: null,
  features: [],
};

const mockPush = jest.fn();
let mockParamId = 'beach-1';

jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: jest.fn() }));
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: mockPush, canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: mockParamId }),
  usePathname: () => '/place/beach-1',
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, getPlace: jest.fn(async () => mockPlace) };
});
jest.mock('@/field/nearbyFacilities', () => ({ ...jest.requireActual('@/field/nearbyFacilities'), getNearbyFacilities: jest.fn() }));
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'tok', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { getNearbyFacilities, type FacilityItem, type NearbyFacilities } from '@/field/nearbyFacilities';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import PlaceDetail from '../place/[id]';

const getMock = getNearbyFacilities as jest.Mock;

const lot = (id: string, distanceM: number, estimatedWon: number | null): FacilityItem => ({
  id, name: id, address: null, lat: 35.15, lng: 129.12, distanceM, kind: 'PARKING',
  detail: { operator: null, free: false, spaces: null, fee: null, estimatedWon, note: null, openNow: null, todayHours: null, closesAt: null, allDay: false },
});
const answer = (items: FacilityItem[]): NearbyFacilities => ({ kind: 'PARKING', radiusM: 700, stayMinutes: 60, total: items.length, limit: 20, items });

async function mount(language: 'ko' | 'en') {
  await AsyncStorage.setItem('gabolle:onboarding-preferences', JSON.stringify({ language, mobility: 'none', hasEnteredApp: true }));
  return render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
}

beforeEach(() => {
  jest.clearAllMocks();
  mockParamId = 'beach-1';
  getMock.mockResolvedValue(answer([lot('싼', 240, 1500)]));
});
afterEach(async () => { await AsyncStorage.clear(); });

describe('장소 상세 — 근처 주차장 한 줄', () => {
  it('🔴 이 장소의 좌표로 1시간 기준을 묻고, 가장 싼 곳의 요금·거리를 한 줄로 보인다', async () => {
    await mount('ko');

    await waitFor(() => expect(screen.getByTestId('place-nearby-parking')).toBeTruthy());
    expect(getMock).toHaveBeenCalledWith('PARKING', { latitude: 35.1532, longitude: 129.1186 }, 'tok', expect.objectContaining({ radiusM: 700, stayMinutes: 60, exact: true }));
    expect(screen.getByText('근처 주차장')).toBeTruthy();
    expect(screen.getByTestId('place-nearby-parking-value').props.children).toBe('1시간 1,500원 · 240m');
  });

  it('누르면 근처 편의시설 화면의 주차장 탭이 이 장소 기준으로 열린다 — 이름은 화면에 그려진 그대로(한국어 이름 + 영어 이름)', async () => {
    await mount('ko');
    await waitFor(() => expect(screen.getByTestId('place-nearby-parking')).toBeTruthy());

    fireEvent.press(screen.getByTestId('place-nearby-parking'));

    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/field/nearby',
      params: { kind: 'PARKING', lat: '35.1532', lng: '129.1186', name: '광안리해수욕장 (Gwangalli Beach)', stay: '1' },
    });
  });

  it('영어 화면 — 이름도 화면에 그려진 그대로(영어 이름 + 한국어 이름) 넘기고, 줄도 영어', async () => {
    await mount('en');
    await waitFor(() => expect(screen.getByTestId('place-nearby-parking')).toBeTruthy());

    expect(screen.getByText('Nearby parking')).toBeTruthy();
    expect(screen.getByTestId('place-nearby-parking-value').props.children).toBe('₩1,500 for 1 hour · 240m');
    fireEvent.press(screen.getByTestId('place-nearby-parking'));
    const { params } = mockPush.mock.calls[0][0] as { params: Record<string, string> };
    expect(params.name).toBe('Gwangalli Beach (광안리해수욕장)');
  });

  it('🔴 근처에 하나도 없으면 줄만 없다 — 장소 화면은 그대로', async () => {
    getMock.mockResolvedValue(answer([]));
    await mount('ko');

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getAllByText('광안리해수욕장 (Gwangalli Beach)').length).toBeGreaterThan(0));
    expect(screen.queryByTestId('place-nearby-parking')).toBeNull();
    expect(screen.queryByText('근처 주차장')).toBeNull();
  });

  it('서버가 실패해도 줄만 없다', async () => {
    getMock.mockRejectedValue(new Error('boom'));
    await mount('ko');

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getAllByText('광안리해수욕장 (Gwangalli Beach)').length).toBeGreaterThan(0));
    expect(screen.queryByTestId('place-nearby-parking')).toBeNull();
  });

  it('🔴 내장 견본 장소에는 줄도 요청도 없다 — 좌표가 없는 장소다', async () => {
    mockParamId = 'gwangalli';
    await mount('ko');

    await new Promise((resolve) => setTimeout(resolve, 60));
    expect(getMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId('place-nearby-parking')).toBeNull();
  });
});
