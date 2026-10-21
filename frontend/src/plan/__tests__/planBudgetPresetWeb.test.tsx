// 🔴 S15P21E201-1939 — 웹(390 폭)의 일본어 여행 만들기에서 총예산 금액이 「10万ウォ…」, 분류 이름이 「グルメ・食べ…」로
//    말줄임 됐다(운영 웹 2026-10-02). adjustsFontSizeToFit 은 웹에서 동작하지 않는다 — 긴 금액은 처음부터 작은 글자로 그리고,
//    분류 이름은 두 줄까지 둔다.
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanStepBody, presetAmountFontSize, type PlanStepsProps } from '@/plan/PlanSteps';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));

const ja: Record<string, string> = { '맛집 & 먹거리': 'グルメ・食べ物' };
const tx = (ko: string) => ja[ko] ?? ko;

function props(step: number): PlanStepsProps {
  return {
    step,
    draft: { ...EMPTY_PLAN, startDate: '2026-10-19', endDate: '2026-10-20', adults: 1 },
    update: () => {},
    tx,
    language: 'ja',
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

describe('웹에서도 예산 금액·분류 이름이 잘리지 않는다', () => {
  it('🔴 일본어 「10万ウォン」은 기본보다 작은 글자', () => {
    expect(presetAmountFontSize('10万ウォン')).toBeLessThan(presetAmountFontSize('10만원'));
    expect(presetAmountFontSize('10만원')).toBe(17);
    // 중국어 「10 万韩元」은 웹에서도 들어갔다 — 그대로 둔다
    expect(presetAmountFontSize('10 万韩元')).toBe(17);
  });

  it('🔴 분류 사진 칸 이름은 두 줄까지', () => {
    const screen = render(<OnboardingPreferencesProvider><PlanStepBody {...props(3)} /></OnboardingPreferencesProvider>);
    const label = screen.getByText('グルメ・食べ物');
    expect(label.props.numberOfLines).toBe(2);
  });

  it('금액 글자 크기가 칸에 실제로 쓰인다', () => {
    const screen = render(<OnboardingPreferencesProvider><PlanStepBody {...props(2)} /></OnboardingPreferencesProvider>);
    for (const amount of screen.getAllByTestId('plan-budget-preset-amount')) {
      const size = StyleSheet.flatten(amount.props.style).fontSize;
      expect(size).toBe(presetAmountFontSize(String(amount.props.children)));
    }
  });
});
