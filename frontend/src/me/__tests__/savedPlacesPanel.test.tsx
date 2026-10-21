// 마이페이지 「저장한 장소」 — S15P21E201-1969.
//
// 🔴 둘러보기에서 「내 여행 후보에 저장」을 누르면 서버에 저장은 됐지만, 그 목록을 보는 화면으로 가는
//    단추가 앱 어디에도 없었다. 마이페이지에서 열리고, 빼면 서버에도 빠지는지를 지킨다.
import { act, fireEvent, render, screen } from '@testing-library/react-native';

const mockPush = jest.fn();
const mockSetSaved = jest.fn(async () => ({ ids: [], sync: 'server' as const }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 't', user: { userId: 'u1' } }) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ push: mockPush }), useFocusEffect: (cb: () => void) => { const r = jest.requireActual('react') as typeof import('react'); r.useEffect(() => cb(), [cb]); } }));
jest.mock('@/discovery/savedPlaces', () => ({
  ...jest.requireActual('@/discovery/savedPlaces'),
  loadSavedPlaceIds: jest.fn(async () => ['p-1']),
  setSavedPlace: (...args: unknown[]) => mockSetSaved(...(args as [])),
}));
jest.mock('@/discovery/places', () => ({
  ...jest.requireActual('@/discovery/places'),
  getPlace: jest.fn(async () => ({ placeId: 'p-1', nameKo: '자갈치시장', nameEn: 'Jagalchi Market', category: '전통시장', address: '부산 중구', lat: 35.09, lng: 129.03, features: [] })),
}));
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { isPanelKey, myPanelBody, panelTitle } from '@/me/myPanels';
import { SavedPlacesBody } from '@/me/panels/SavedPlacesBody';

declare const require: (id: string) => any;
declare const __dirname: string;
const read = (relative: string): string => require('fs').readFileSync(require('path').join(__dirname, '..', '..', '..', relative), 'utf8');

const tx = (ko: string) => ko;

describe('마이페이지 「저장한 장소」 판', () => {
  it('🔴 패널 열쇠가 있고 제목이 「저장한 장소」다 — 남의 글을 담는 「저장한 기록」과 다른 판이다', () => {
    expect(isPanelKey('saved-places')).toBe(true);
    expect(panelTitle('saved-places', tx).title).toBe('저장한 장소');
    expect(panelTitle('saved', tx).title).toBe('저장한 기록');
    expect(myPanelBody('saved-places')).toBeTruthy();
  });

  it('🔴 마이페이지에 이 판으로 가는 줄이 있다 — 없으면 저장은 되는데 볼 길이 없다', () => {
    expect(read('app/(tabs)/me.tsx')).toContain("openPanel('saved-places')");
  });

  it('저장한 후보가 보이고, 빼면 서버에도 빼고 목록에서 사라진다', async () => {
    render(<OnboardingPreferencesProvider><SavedPlacesBody /></OnboardingPreferencesProvider>);
    expect(await screen.findByText('자갈치시장 (Jagalchi Market)')).toBeTruthy();
    await act(async () => { fireEvent.press(screen.getByLabelText('자갈치시장 (Jagalchi Market) 후보에서 빼기')); });
    expect(mockSetSaved).toHaveBeenCalledWith('p-1', false, 't');
    expect(screen.queryByText('자갈치시장 (Jagalchi Market)')).toBeNull();
    // 다 빼면 빈 안내와 둘러보기 단추
    fireEvent.press(screen.getByText('부산 둘러보기'));
    expect(mockPush).toHaveBeenCalledWith('/explore');
  });
});
