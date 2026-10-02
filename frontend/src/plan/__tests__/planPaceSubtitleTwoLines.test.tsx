// 🔴 S15P21E201-1929 — 여행 만들기 4단계 「여행 기분」 칸 부제가 일본어에서 한 줄 제한으로 잘렸다
//    (웹 2026-10-02) — 두 줄까지 허용한다.
import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanStepBody, type PlanStepsProps } from '@/plan/PlanSteps';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));

const tx = (ko: string) => ko;

function props(): PlanStepsProps {
  return {
    step: 3,
    draft: { ...EMPTY_PLAN, startDate: '2026-10-19', endDate: '2026-10-20', adults: 1 },
    update: () => {},
    tx,
    language: 'ko',
    readiness: {} as PlanStepsProps['readiness'],
    styleSkipped: false,
    accessibilityCounts: null,
    goTo: () => {},
    onSkipStyle: () => {},
    onSearchPlace: () => {},
    onEditConditions: () => {},
    onOpenMenuScan: () => {},
  };
}

describe('여행 기분 칸 부제는 두 줄까지', () => {
  it('🔴 세 칸 모두 numberOfLines=2', () => {
    const screen = render(<OnboardingPreferencesProvider><PlanStepBody {...props()} /></OnboardingPreferencesProvider>);
    const amounts = screen.getAllByTestId('plan-pace-subtitle');
    expect(amounts).toHaveLength(3);
    for (const amount of amounts) {
      expect(amount.props.numberOfLines).toBe(2);
    }
  });
});
