// 여행 조건 시트를 아래로 쓸어내리면 닫힌다 — S15P21E201-1798.
//
// PanResponder(손가락 끌기를 읽는 RN 내장 장치)의 설정을 가로채, 손을 뗀 순간을
// 직접 불러 본다. 길게 끌면 바깥 누름과 같은 onClose('DISMISSED'), 짧게 끌면 안 닫힌다.
import { render, waitFor } from '@testing-library/react-native';
import { PanResponder } from 'react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { PlanProvider } from '@/plan/PlanProvider';
import { ConditionsPromptModal } from '@/plan/ConditionsPromptModal';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone' }) }));

type Config = Parameters<typeof PanResponder.create>[0];

const mount = (onClose: jest.Mock) => {
  const configs: Config[] = [];
  const real = PanResponder.create;
  jest.spyOn(PanResponder, 'create').mockImplementation((c) => { configs.push(c); return real(c); });
  const view = render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <OnboardingPreferencesProvider>
        <PlanProvider><ConditionsPromptModal visible onClose={onClose} /></PlanProvider>
      </OnboardingPreferencesProvider>
    </QueryClientProvider>,
  );
  return { view, configs };
};

const gesture = (dy: number, vy = 0) => ({ dx: 0, dy, vx: 0, vy } as never);

describe('여행 조건 시트 — 쓸어내려 닫기', () => {
  afterEach(() => jest.restoreAllMocks());

  it('손잡이가 있고, 세로로 끌 때만 잡는다', async () => {
    const { view, configs } = mount(jest.fn());
    await waitFor(() => expect(view.getByTestId('conditions-sheet-handle', { includeHiddenElements: true })).toBeTruthy());
    const c = configs[configs.length - 1];
    expect(c.onMoveShouldSetPanResponder?.({} as never, gesture(20))).toBe(true);
    expect(c.onMoveShouldSetPanResponder?.({} as never, { dx: 40, dy: 20 } as never)).toBe(false);
    expect(c.onMoveShouldSetPanResponder?.({} as never, gesture(3))).toBe(false);
  });

  it('길게 끌어 놓으면 바깥 누름과 같게 닫힌다', async () => {
    const onClose = jest.fn();
    const { configs } = mount(onClose);
    configs[configs.length - 1].onPanResponderRelease?.({} as never, gesture(150));
    await waitFor(() => expect(onClose).toHaveBeenCalledWith('DISMISSED'));
  });

  it('짧게 끌면 제자리로 돌아가고 안 닫힌다', async () => {
    const onClose = jest.fn();
    const { configs } = mount(onClose);
    configs[configs.length - 1].onPanResponderRelease?.({} as never, gesture(30));
    await new Promise((r) => setTimeout(r, 400));
    expect(onClose).not.toHaveBeenCalled();
  });
});
