// 출발지 목록 제목 — 검색 결과가 있으면 「추천 출발지」가 아니다 (S15P21E201-1775).
import type { ReactNode } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { PlanStartBar } from '@/home/PlanStartBar';
import { EMPTY_START_BAR } from '@/home/startBarValue';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

jest.mock('@/plan/origins', () => {
  const actual = jest.requireActual('@/plan/origins');
  return { ...actual, searchOrigins: jest.fn(async () => ({ state: 'success', items: [{ externalId: 'x1', name: '해운대해수욕장', address: '부산 해운대구 우동', latitude: 35.1, longitude: 129.1 }] })) };
});

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

it('🔴 검색 결과가 오면 제목이 「검색 결과」로 바뀐다', async () => {
  jest.useFakeTimers();
  const view = render(<PlanStartBar wide={false} sheet accessToken={null} onSubmit={jest.fn()} onClose={jest.fn()} today={new Date('2026-09-24T09:00:00')} initialSection="origin" initialValue={EMPTY_START_BAR} />, { wrapper: Providers });
  expect(screen.getByTestId('origin-list-label').props.children).toBe('추천 출발지');
  fireEvent.changeText(screen.getByLabelText('출발지 검색'), 'Haeundae');
  await act(async () => { jest.advanceTimersByTime(1000); });
  expect(screen.getByTestId('origin-list-label').props.children).toBe('검색 결과');
  view.unmount();
  jest.useRealTimers();
});
