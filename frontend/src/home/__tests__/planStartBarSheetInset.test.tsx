// 홈 시작 바의 전체 화면 시트 — ✕ 가 상태 표시줄 밑으로 들어가지 않는다 (S15P21E201-1772).
import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { PlanStartBar } from '@/home/PlanStartBar';
import { EMPTY_START_BAR } from '@/home/startBarValue';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const TOP = 44;
const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: TOP, left: 0, right: 0, bottom: 0 } }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

it('🔴 시트 머리는 위 안전영역만큼 내려온다', () => {
  jest.useFakeTimers();
  const view = render(<PlanStartBar wide={false} sheet accessToken={null} onSubmit={jest.fn()} onClose={jest.fn()} today={new Date('2026-09-24T09:00:00')} initialValue={EMPTY_START_BAR} />, { wrapper: Providers });
  const head = StyleSheet.flatten(screen.getByTestId('plan-start-sheet-head').props.style);
  expect(head.paddingTop).toBeGreaterThanOrEqual(TOP);
  view.unmount();
  jest.useRealTimers();
});
