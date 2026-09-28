// 택시 카드 — 장소에 주소가 없을 때(S15P21E201-1736). 2026-09-26 실기: 모모스에서 기사가 읽을 큰 칸이 비었다.
import { render, waitFor } from '@testing-library/react-native';

import { SafeAreaProvider } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('expo-clipboard', () => ({ setStringAsync: jest.fn() }));

let mockParams: Record<string, string> = { id: 'place-1' };
const mockGetTaxiCard = jest.fn();
jest.mock('@/discovery/taxiCard', () => ({ getTaxiCard: (...args: unknown[]) => mockGetTaxiCard(...args) }));

import TaxiCardScreen from '../taxi-card/[id]';

const METRICS = { frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };
const mount = () => render(<SafeAreaProvider initialMetrics={METRICS}><OnboardingPreferencesProvider><TaxiCardScreen /></OnboardingPreferencesProvider></SafeAreaProvider>);

beforeEach(() => { mockParams = { id: 'place-1' }; mockGetTaxiCard.mockReset(); });

describe('택시 카드 — 주소가 없는 장소', () => {
  it('🔴 큰 칸에 장소 이름을 그리고, 복사할 주소가 없으니 「주소 복사」를 안 보인다', async () => {
    // 서버는 NON_NULL 이라 addressKo 키 자체를 뺀다.
    mockGetTaxiCard.mockResolvedValue({ placeId: 'place-1', nameKo: '모모스', resolvedLanguage: 'ko', driverSentence: '이 주소로 가주세요, 모모스' });
    const view = mount();
    await waitFor(() => expect(view.getByText('모모스')).toBeTruthy());
    expect(view.queryByText('주소 복사')).toBeNull();
  });

  it('주소가 있으면 큰 칸은 주소이고 「주소 복사」가 있다', async () => {
    mockGetTaxiCard.mockResolvedValue({ placeId: 'place-1', nameKo: '모모스', addressKo: '부산 동래구 오시게로 20', resolvedLanguage: 'ko', driverSentence: '이 주소로 가주세요, 부산 동래구 오시게로 20' });
    const view = mount();
    await waitFor(() => expect(view.getByText('부산 동래구 오시게로 20')).toBeTruthy());
    expect(view.getByText('주소 복사')).toBeTruthy();
  });
});

describe('택시 카드 — 장소 이름을 크게 (S15P21E201-1742)', () => {
  it('🔴 주소가 있어도 이름이 가장 큰 칸이고, 주소는 그 아래에 있다 — 기사는 내비에 이름을 넣는 일이 많다', async () => {
    mockGetTaxiCard.mockResolvedValue({ placeId: 'place-1', nameKo: '모모스커피 부산본점', addressKo: '부산 금정구 오시게로 20', resolvedLanguage: 'ko', driverSentence: '이 주소로 가주세요, 부산 금정구 오시게로 20' });
    const view = mount();
    await waitFor(() => expect(view.getByTestId('taxi-card-name')).toBeTruthy());
    expect(view.getByTestId('taxi-card-name').props.children).toBe('모모스커피 부산본점');
    expect(view.getByTestId('taxi-card-address').props.children).toBe('부산 금정구 오시게로 20');
  });

  it('🔴 카카오에서 고른 곳은 서버에 묻지 않고 받은 이름·주소로 그린다', async () => {
    mockParams = { id: 'external', name: '부산역', address: '부산 동구 중앙대로 206' };
    const view = mount();
    await waitFor(() => expect(view.getByTestId('taxi-card-name').props.children).toBe('부산역'));
    expect(view.getByTestId('taxi-card-address').props.children).toBe('부산 동구 중앙대로 206');
    expect(view.getByText('이 주소로 가주세요, 부산 동구 중앙대로 206')).toBeTruthy();
    expect(view.getByText('주소 복사')).toBeTruthy();
    expect(mockGetTaxiCard).not.toHaveBeenCalled();
  });
});
