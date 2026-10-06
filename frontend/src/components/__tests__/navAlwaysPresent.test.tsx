// 어느 화면 크기에서도 이동 수단(하단 탭바 또는 상단 바)이 하나는 있어야 한다.
//
// 🔴 2026-09-21 — 아이폰을 가로로 돌리면 둘 다 사라졌다. 탭바는 «폭 > 599»로 숨고
//    상단 바(TopNav)는 «짧은 변 >= 600(kind === 'tablet')»으로 숨었는데, 아이폰 가로
//    (932×430)는 폭은 크고 짧은 변은 작아서 두 조건에 동시에 걸렸다. app.json 이
//    orientation: default 라 실제로 일어나는 일이다.
//
// 고침은 탭바가 TopNav 와 같은 기준(kind)을 쓰는 것이다. 이 시험은 그 기준이 다시
// 갈라지는 것을 막는다 — 두 부품을 대표적인 네 크기에서 같이 재서, 「둘 다 없음」이
// 한 번도 안 나오는지 본다. 크기 하나를 박아 두는 게 아니라 조합을 본다.
import { render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, type Metrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TabBar } from '@/components/TabBar';

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn() }),
  usePathname: () => '/home',
}));

// useLayout 의 판정은 진짜를 쓴다 — 전에는 규칙을 여기 옮겨 적어서, 규칙이 바뀌면(S15P21E201-1563) 이 시험만 옛 규칙을 봤다.
let mockSize = { width: 420, height: 880 };
jest.mock('@/layout/useLayout', () => {
  const { isDesktopWindow } = jest.requireActual('@/layout/useLayout');
  return {
    isDesktopWindow,
    useLayout: () => {
      const desktop = isDesktopWindow(mockSize.width, mockSize.height);
      return { width: mockSize.width, height: mockSize.height, desktop, kind: desktop ? 'tablet' : 'phone' };
    },
  };
});

const METRICS: Metrics = { frame: { x: 0, y: 0, width: 420, height: 880 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };

function mount(node: React.ReactElement) {
  return render(
    <SafeAreaProvider initialMetrics={METRICS}>
      <OnboardingPreferencesProvider>{node}</OnboardingPreferencesProvider>
    </SafeAreaProvider>,
  );
}

/** 탭바가 그려졌는가 — 탭 하나라도 찾히면 있는 것이다. */
function tabBarShown(): boolean {
  mount(<TabBar active="home" />);
  return screen.queryByTestId('tab-home') !== null;
}

/**
 * TopNav 는 kind === 'tablet'(= 데스크톱 판정)일 때 그린다(src/nav/TopNav.tsx). 렌더 트리 전체를 세우기
 * 무거워 판정 함수를 그대로 부른다.
 */
function topNavShown(): boolean {
  return (jest.requireActual('@/layout/useLayout') as typeof import('@/layout/useLayout')).isDesktopWindow(mockSize.width, mockSize.height);
}

const CASES: Array<[string, { width: number; height: number }]> = [
  ['아이폰 세로 420×880', { width: 420, height: 880 }],
  ['🔴 아이폰 가로 932×430 — 전에 둘 다 사라지던 자리', { width: 932, height: 430 }],
  ['폴드 펼침 884×1104', { width: 884, height: 1104 }],
  ['폴드8 외부 374×918', { width: 374, height: 918 }],
  ['폴드8 외부 가로 918×374', { width: 918, height: 374 }],
  ['폴드8 펼침 세로 717×795', { width: 717, height: 795 }],
  ['폴드8 펼침 가로 795×717', { width: 795, height: 717 }],
  ['아이패드 가로 1366×1024', { width: 1366, height: 1024 }],
];

describe('어느 크기에서도 이동 수단이 하나는 있다', () => {
  it.each(CASES)('%s', (_label, dims) => {
    mockSize = dims;
    const tab = tabBarShown();
    const top = topNavShown();
    expect(tab || top).toBe(true);
  });

  it('🔴 아이폰 가로에서는 탭바가 남아 있다 — 상단 바는 태블릿 전용이라 여기서는 안 뜬다', () => {
    mockSize = { width: 932, height: 430 };
    expect(tabBarShown()).toBe(true);
    expect(topNavShown()).toBe(false);
  });

  it('🔴 폴드8 펼침 — 세로는 탭바(모바일), 가로는 상단 바(데스크톱) (S15P21E201-1563)', () => {
    mockSize = { width: 717, height: 795 };
    expect(tabBarShown()).toBe(true);
    expect(topNavShown()).toBe(false);
    mockSize = { width: 795, height: 717 };
    expect(tabBarShown()).toBe(false);
    expect(topNavShown()).toBe(true);
  });

  it('태블릿에서는 탭바가 비켜 준다 — 상단 바가 그 자리를 맡는다', () => {
    mockSize = { width: 1366, height: 1024 };
    expect(tabBarShown()).toBe(false);
    expect(topNavShown()).toBe(true);
  });
});
