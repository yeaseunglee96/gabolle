// 🔴 S15P21E201-1924 — 여행 만들기 3단계 총예산 칸 셋의 금액이 일본어 화면에서 「10万ウォ」「ン」 두 줄로 갈라졌다
//    (실기기 2026-10-02). 칸 폭이 좁아 단어 가운데서 줄이 바뀐다 — 한 줄로 두고 넘치면 글자를 줄인다.
import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanStepBody, type PlanStepsProps } from '@/plan/PlanSteps';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));

const tx = (ko: string) => ko;

function props(): PlanStepsProps {
  return {
    step: 2,
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

describe('총예산 칸 금액은 한 줄', () => {
  it('🔴 세 칸 모두 numberOfLines=1 · 넘치면 글자를 줄인다', () => {
    const screen = render(<OnboardingPreferencesProvider><PlanStepBody {...props()} /></OnboardingPreferencesProvider>);
    const amounts = screen.getAllByTestId('plan-budget-preset-amount');
    expect(amounts).toHaveLength(3);
    for (const amount of amounts) {
      expect(amount.props.numberOfLines).toBe(1);
      expect(amount.props.adjustsFontSizeToFit).toBe(true);
    }
  });
});
