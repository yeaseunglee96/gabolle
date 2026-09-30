// 여행 조건 창이 「음식 취향의 채식도 반드시 지켜요」를 말하는가 — S15P21E201-1878 (서버 S15P21E201-1873).
import { render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';

let mockDraft: PlanDraft = EMPTY_PLAN;
// update 는 한 벌만 — 그릴 때마다 새 함수를 주면 창의 효과가 되풀이 돈다.
const mockUpdate = jest.fn();
jest.mock('@/plan/PlanProvider', () => ({
  ...jest.requireActual('@/plan/PlanProvider'),
  usePlan: () => ({ draft: mockDraft, update: mockUpdate }),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));
jest.mock('@/plan/travelConditions', () => ({
  ...jest.requireActual('@/plan/travelConditions'),
  saveTravelConditions: jest.fn(async () => ({ synced: true })),
}));

const mount = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <OnboardingPreferencesProvider>
      <ConditionsPromptModal visible reprompt={false} onClose={() => {}} />
    </OnboardingPreferencesProvider>
  </QueryClientProvider>,
);
const withTaste = (patch: Partial<PlanDraft>): PlanDraft => ({
  ...EMPTY_PLAN, foods: ['VEGETARIAN'], preferenceAnswerStatus: { ...EMPTY_PLAN.preferenceAnswerStatus, foodPreference: 'SELECTED' }, ...patch,
});

describe('여행 조건 창 — 음식 취향의 식단', () => {
  it('🔴 음식 취향에 채식이 있으면 이번 여행에도 반드시 지킨다고 말하고, 빼는 길(해당 없음)을 알려 준다', async () => {
    mockDraft = withTaste({});
    const view = mount();
    await waitFor(() => expect(view.getByTestId('taste-diet-note')).toBeTruthy());
    expect(view.getByTestId('taste-diet-note').props.children).toContain('채식');
    expect(view.getByTestId('taste-diet-note').props.children).toContain('해당 없음');
  });

  it('「해당 없음」을 고르면 알림이 사라진다 — 서버도 더하지 않는다', async () => {
    mockDraft = withTaste({ dietStatus: 'NONE', dietAnswered: true });
    const view = mount();
    await waitFor(() => expect(view.queryByText('여행 조건 미리 알려주기')).toBeTruthy());
    expect(view.queryByTestId('taste-diet-note')).toBeNull();
  });

  it('음식 취향에 채식이 없으면 아무 말도 안 한다', async () => {
    mockDraft = withTaste({ foods: ['SEAFOOD'] });
    const view = mount();
    await waitFor(() => expect(view.queryByText('여행 조건 미리 알려주기')).toBeTruthy());
    expect(view.queryByTestId('taste-diet-note')).toBeNull();
  });
});
