// 위쪽 메뉴의 언어 국기 단추 — 손가락으로 누를 수 있는 크기 (S15P21E201-1952).
//
// 갤럭시 탭(753×1205)은 이제 데스크톱 판이라 위쪽 메뉴가 뜬다. 그런데 국기 단추가 30×36 이라
// 손가락으로 옆 국기를 잘못 누르기 쉬웠다. 마우스만 생각한 크기였다. 터치 기기 권장은 44 이상이다.
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

import { TopNav } from '@/nav/TopNav';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn() }), usePathname: () => '/home' }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'tablet', desktop: true, width: 753, height: 1205, isLandscape: false }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, status: 'signedOut' }) }));
jest.mock('@/home/HomeBlocks', () => ({ TopNavWeather: () => null }));
jest.mock('@/home/useHomeData', () => ({ useHomeWeather: () => ({ state: 'loading' }) }));
jest.mock('@/home/StickySearchPill', () => ({ SearchPillButton: () => null }));
jest.mock('@/home/stickySearchStore', () => ({ searchCollapse: { addListener: jest.fn(), removeListener: jest.fn() }, useSearchHandle: () => ({ active: false }) }));
jest.mock('@/onboarding/OnboardingPreferences', () => ({ useOnboardingPreferences: () => ({ language: 'ko', setLanguage: jest.fn() }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));

const TOUCH_MIN = 44;

describe('언어 국기 단추', () => {
  it('🔴 다섯 개 모두 가로 44 이상 — 탭에서 손가락으로 누른다', () => {
    const flags = render(<TopNav />).getAllByLabelText(/^언어를 .+로 변경$/);
    expect(flags).toHaveLength(5);
    for (const flag of flags) {
      const style = StyleSheet.flatten(flag.props.style);
      expect(style.width).toBeGreaterThanOrEqual(TOUCH_MIN);
    }
  });
});
