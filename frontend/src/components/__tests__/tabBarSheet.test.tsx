// 탭바가 지도 시트로 늘어날 때 무엇이 눌릴 수 있는가.
//
// 🔴 이 변경에서 제일 위험한 것은 「안 보이는데 눌리는 것」이다. 투명도만 0으로 두면
//    지도를 누르려던 손가락이 탭을 누르고, 화면 읽기 프로그램은 탭 다섯 개를 계속 읽는다.
//    눈으로는 절대 안 보이는 종류라 시험으로 박아 둔다.
//
// 그리고 이 부품은 홈·내 여행·마이페이지가 전부 그린다 — 아무것도 안 넘기는 화면이
// 지금과 똑같이 동작하는지도 함께 본다.
import { render, screen } from '@testing-library/react-native';
import { Text as RNText } from 'react-native';
import { SafeAreaProvider, type Metrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TabBar } from '@/components/TabBar';

jest.mock('expo-router', () => ({ useRouter: () => ({ replace: jest.fn(), push: jest.fn() }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ width: 420, height: 880, kind: 'phone' }) }));

// 이 막대는 화면 아래 시스템 바를 피하려고 안전영역을 읽는다 — 시험에서는 그 값을 준다.
const METRICS: Metrics = { frame: { x: 0, y: 0, width: 420, height: 880 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };

function mount(node: React.ReactElement) {
  return render(
    <SafeAreaProvider initialMetrics={METRICS}>
      <OnboardingPreferencesProvider>{node}</OnboardingPreferencesProvider>
    </SafeAreaProvider>,
  );
}

describe('탭바가 지도 시트로 늘어날 때', () => {
  it('아무것도 안 넘기면 지금과 같다 — 탭 다섯이 그대로 눌린다', () => {
    mount(<TabBar active="feed" />);
    expect(screen.getByTestId('tab-home')).toBeTruthy();
    expect(screen.getByTestId('tab-me')).toBeTruthy();
  });

  it('시트를 넘겨도 늘어나기 전에는 탭이 그대로 눌린다', () => {
    mount(<TabBar active="feed" expanded={false}>{<RNText>시트 안</RNText>}</TabBar>);
    expect(screen.getByTestId('tab-home')).toBeTruthy();
  });

  it('🔴 늘어나면 탭 다섯을 찾을 수 없다 — 화면 읽기 프로그램에도 없다', () => {
    mount(<TabBar active="feed" expanded>{<RNText>시트 안</RNText>}</TabBar>);
    for (const key of ['home', 'feed', 'schedule', 'map', 'me']) {
      // 기본 찾기는 숨겨진 것을 건너뛴다. 못 찾는다는 것이 곧 「숨었다」는 뜻이다.
      expect(screen.queryByTestId(`tab-${key}`)).toBeNull();
      // 그런데 그려지긴 해야 한다 — 줄어들 때 다시 나타나야 하니까.
      expect(screen.getByTestId(`tab-${key}`, { includeHiddenElements: true })).toBeTruthy();
    }
  });

  it('늘어나면 시트 내용이 그려진다', () => {
    mount(<TabBar active="feed" expanded>{<RNText>시트 안</RNText>}</TabBar>);
    expect(screen.getByText('시트 안')).toBeTruthy();
  });

  it('찾는 방법 자체가 살아 있다 — 없는 탭은 없다고 말한다', () => {
    mount(<TabBar active="feed" />);
    expect(screen.queryByTestId('tab-이런건없다')).toBeNull();
  });
});
