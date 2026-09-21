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

// useLayout 의 kind 는 짧은 변으로 정한다 — src/layout/useLayout.ts 의 규칙을 그대로 옮긴다.
const MOCK_TABLET_MIN_SHORT_SIDE = 600;
let mockSize = { width: 420, height: 880 };
jest.mock('@/layout/useLayout', () => ({
  useLayout: () => ({
    width: mockSize.width,
    height: mockSize.height,
    kind: Math.min(mockSize.width, mockSize.height) >= MOCK_TABLET_MIN_SHORT_SIDE ? 'tablet' : 'phone',
  }),
}));

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
 * TopNav 는 kind === 'tablet' 일 때 그린다(src/nav/TopNav.tsx:71). 렌더 트리 전체를 세우기
 * 무거워 그 조건을 그대로 옮긴다 — TopNav 쪽 조건이 바뀌면 이 줄도 같이 바꿔야 한다.
 */
function topNavShown(): boolean {
  return Math.min(mockSize.width, mockSize.height) >= MOCK_TABLET_MIN_SHORT_SIDE;
}

const CASES: Array<[string, { width: number; height: number }]> = [
  ['아이폰 세로 420×880', { width: 420, height: 880 }],
  ['🔴 아이폰 가로 932×430 — 전에 둘 다 사라지던 자리', { width: 932, height: 430 }],
  ['폴드 펼침 884×1104', { width: 884, height: 1104 }],
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

  it('태블릿에서는 탭바가 비켜 준다 — 상단 바가 그 자리를 맡는다', () => {
    mockSize = { width: 1366, height: 1024 };
    expect(tabBarShown()).toBe(false);
    expect(topNavShown()).toBe(true);
  });
});
