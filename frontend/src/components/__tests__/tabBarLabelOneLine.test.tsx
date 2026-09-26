// 탭 글자는 한 줄이다 (S15P21E201-1747).
//
// 🔴 글자 크기 1.1 인 폰(Galaxy S10, Play 35)에서 「여행 만들기」가 두 줄로 꺾여 그 칸만
//    아래로 내려갔다. 시험 환경에는 글자 크기 설정이 없어서 꺾임 자체는 못 재고,
//    대신 「한 줄로 묶고 넘치면 줄인다」는 약속을 박아 둔다.
import { render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, type Metrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TabBar } from '@/components/TabBar';

jest.mock('expo-router', () => ({ useRouter: () => ({ replace: jest.fn(), push: jest.fn() }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ width: 360, height: 760, kind: 'phone' }) }));

const METRICS: Metrics = { frame: { x: 0, y: 0, width: 360, height: 760 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };

it('🔴 탭 다섯의 글자가 모두 한 줄로 묶이고, 넘치면 줄어든다', () => {
  render(
    <SafeAreaProvider initialMetrics={METRICS}>
      <OnboardingPreferencesProvider><TabBar active="home" /></OnboardingPreferencesProvider>
    </SafeAreaProvider>,
  );
  for (const label of ['홈', '피드', '여행 만들기', '내 여행', '마이페이지']) {
    const text = screen.getByText(label);
    expect(text.props.numberOfLines).toBe(1);
    expect(text.props.adjustsFontSizeToFit).toBe(true);
  }
});
