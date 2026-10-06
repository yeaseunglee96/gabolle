// 위쪽 메뉴 활성 표시 빨간 점 — 웹과 앱이 같은 자리(글자 가운데 바로 아래)에 찍힌다 (S15P21E201-2000).
//
// 전에는 점을 `position: absolute · left: '50%' · marginLeft: -2.5` 로 놓았다. 웹은 퍼센트를 칸 전체 폭으로
// 계산해 가운데에 오지만, 앱(Yoga)은 패딩을 뺀 안쪽 폭으로 계산해 패딩 절반만큼 왼쪽으로 밀렸다
// (갤럭시 탭 캡처: 「홈」 글자보다 왼쪽). 이제 칸 양끝에 붙인 감싸개(left 0 · right 0)가 점을 가운데 정렬한다 —
// 퍼센트를 쓰지 않으니 두 엔진이 같은 자리를 낸다.
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

import { TopNav } from '@/nav/TopNav';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn() }), usePathname: () => '/home' }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'tablet', desktop: true, width: 1205, height: 753, isLandscape: true }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, status: 'signedOut' }) }));
jest.mock('@/home/HomeBlocks', () => ({ TopNavWeather: () => null }));
jest.mock('@/home/useHomeData', () => ({ useHomeWeather: () => ({ state: 'loading' }) }));
jest.mock('@/home/StickySearchPill', () => ({ SearchPillButton: () => null }));
jest.mock('@/home/stickySearchStore', () => ({ searchCollapse: { addListener: jest.fn(), removeListener: jest.fn() }, useSearchHandle: () => ({ active: false }) }));
jest.mock('@/onboarding/OnboardingPreferences', () => ({ useOnboardingPreferences: () => ({ language: 'ko', setLanguage: jest.fn() }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));

describe('위쪽 메뉴 활성 표시 점', () => {
  it('🔴 점의 위치에 퍼센트를 쓰지 않는다 — 감싸개가 칸 양끝(left 0 · right 0)에 붙어 가운데 정렬한다', () => {
    const marker = render(<TopNav />).getByTestId('topnav-active-marker', { includeHiddenElements: true });
    const style = StyleSheet.flatten(marker.props.style);
    expect(style.position).toBe('absolute');
    expect(style.left).toBe(0);
    expect(style.right).toBe(0);
    expect(style.alignItems).toBe('center');
    expect(typeof style.bottom).toBe('number');
    expect(JSON.stringify(style)).not.toMatch(/%/);
  });

  it('점은 하나만, 지금 화면(홈) 칸 안에 찍힌다', () => {
    const view = render(<TopNav />);
    expect(view.getAllByTestId('topnav-active-marker', { includeHiddenElements: true })).toHaveLength(1);
  });
});
