// 로고는 한 화면에 한 번만 — 갤럭시 폴드 펼침(717×795)에서 위쪽 메뉴와 화면 머리가 둘 다 그렸다 (S15P21E201-1547).
//
// 🔴 고치면서 한 번 더 틀렸다 — 화면 머리의 로고를 숨기는 규칙이 위쪽 메뉴 자신의 로고까지 지웠다
//    (메뉴도 같은 부품을 쓴다). 화면을 띄워 보고서야 알았다. 두 경우를 다 붙든다.
import { render } from '@testing-library/react-native';

import { BrandLogoLink } from '@/components/BrandLogoLink';

let mockKind: 'phone' | 'tablet' = 'phone';
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: mockKind, width: 717, height: 795, isLandscape: false }) }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ replace: jest.fn() }), usePathname: () => '/home' }));

const logoCount = (element: React.ReactElement) => render(element).queryAllByRole('link').length;

describe('로고는 한 번만', () => {
  it('폰(위쪽 메뉴 없음) — 화면 머리가 로고를 그린다', () => {
    mockKind = 'phone';
    expect(logoCount(<BrandLogoLink />)).toBe(1);
  });

  it('🔴 태블릿·폴드 펼침(위쪽 메뉴 있음) — 화면 머리는 로고를 안 그린다', () => {
    mockKind = 'tablet';
    expect(logoCount(<BrandLogoLink />)).toBe(0);
  });

  it('🔴 위쪽 메뉴 자신의 로고는 언제나 그린다 — 고치다 이것까지 지웠었다', () => {
    mockKind = 'tablet';
    expect(logoCount(<BrandLogoLink inTopNav />)).toBe(1);
  });
});
