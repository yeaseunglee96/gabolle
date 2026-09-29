// 여행 조건 모달의 그늘 문항 — 「그늘길 우선」이 길을 골라 주는 것처럼 읽히던 것.
//
// 🔴 이 시험이 지키는 것: 그늘 답은 장소 점수에만 들어가고 걷는 길은 안 바꾼다. 그래서 문구는 「그늘 많은 곳 우선」이고,
//    저장하는 값은 전과 같은 PREFER 다(문구만 바꾸고 값은 안 바꾼다).
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';
import { PlanProvider, usePlan } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null, ready: true }) }));
jest.mock('@/plan/travelConditions', () => ({
  ...jest.requireActual('@/plan/travelConditions'),
  loadTravelConditions: jest.fn(async () => ({ status: null, conditions: null })),
  saveTravelConditions: jest.fn(async () => ({ synced: true })),
}));

let plan: ReturnType<typeof usePlan>;
function Probe() { plan = usePlan(); return null; }

const mount = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <OnboardingPreferencesProvider>
      <PlanProvider><Probe /><ConditionsPromptModal visible reprompt={false} onClose={() => {}} /></PlanProvider>
    </OnboardingPreferencesProvider>
  </QueryClientProvider>,
);

describe('여행 조건 모달 — 그늘 문항', () => {
  it('🔴 「그늘 많은 곳 우선」으로 묻는다 — 「그늘길」이라고 하지 않는다', async () => {
    const view = mount();
    await waitFor(() => expect(view.queryByText('그늘 많은 곳 우선')).toBeTruthy());
    expect(view.queryAllByText(/그늘길/)).toHaveLength(0);
  });

  it('🔴 저장하는 값은 그대로 PREFER 다', async () => {
    const view = mount();
    await waitFor(() => expect(view.queryByText('그늘 많은 곳 우선')).toBeTruthy());
    // 경사 · 계단 · 그늘 줄의 「예」 — 그늘이 마지막이다
    const yes = view.getAllByText('예');
    fireEvent.press(yes[yes.length - 1]);
    await waitFor(() => expect(plan.draft.shadePreference).toBe('PREFER'));
  });
});
