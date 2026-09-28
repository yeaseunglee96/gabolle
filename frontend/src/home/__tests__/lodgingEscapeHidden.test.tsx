// 숙소 칸의 「숙소 아직 안 정했어요」는 1박 이상이면 안 보인다 — S15P21E201-1591.
//
// 🔴 1박 이상은 숙소가 있어야 일정을 만든다(S15P21E201-1584). 그런데 숙소 칸이 「안 정했어요 — 출발지 기준으로
//    일정을 짜요」를 내밀면, 그걸 고른 사람은 「만들기」에서 막힌다. 막힐 길을 열어 두지 않는다.
//    당일치기와 날짜를 아직 모를 때는 그대로 보인다.
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { PlanStartBar } from '@/home/PlanStartBar';
import { EMPTY_START_BAR } from '@/home/startBarValue';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? { frame: { x: 0, y: 0, width: 1280, height: 900 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);
const ESCAPE = '숙소 아직 안 정했어요';
const openLodging = (startDate: string, endDate: string) => render(
  <PlanStartBar wide accessToken={null} onSubmit={jest.fn()} today={new Date('2026-09-24T09:00:00')} initialSection="lodging" initialValue={{ ...EMPTY_START_BAR, startDate, endDate }} />,
  { wrapper: Providers },
);

describe('숙소 칸의 「숙소 아직 안 정했어요」', () => {
  it('🔴 1박 이상이면 안 보인다', () => {
    openLodging('2026-10-03', '2026-10-05');
    expect(screen.getByText('추천 숙소 지역')).toBeTruthy();
    expect(screen.queryByText(ESCAPE)).toBeNull();
  });

  it('당일치기면 그대로 보인다', () => {
    openLodging('2026-10-03', '2026-10-03');
    expect(screen.getByText(ESCAPE)).toBeTruthy();
  });

  it('날짜를 아직 안 골랐으면 모르니 그대로 보인다', () => {
    openLodging('', '');
    expect(screen.getByText(ESCAPE)).toBeTruthy();
  });
});
