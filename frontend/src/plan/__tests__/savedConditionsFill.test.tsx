// 저장해 둔 여행 조건은 이번 여행에서 아직 답하지 않은 칸에만 얹는다.
//
// 🔴 이 시험이 지키는 것:
//    ① 평소 취향(경사)과 여행 조건 중 어느 응답이 먼저 오든 경사 답이 같다 — 전에는 여행 조건이 나중에 오면
//       저장값의 slopeConstraint: null 이 평소 취향이 채운 AVOID 를 지웠다.
//    ② 로그인 열쇠가 바뀌어도(한 시간마다) 이번 여행에서 고친 답을 저장값으로 되돌리지 않는다 — 전에는 알레르기·식단이
//       「모름」인 동안 열쇠가 바뀔 때마다 다시 덮어썼다.
import type { ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN, fillSavedConditions, PlanProvider, usePlan } from '@/plan/PlanProvider';
import { EMPTY_CONDITIONS, type TravelConditions, type TravelConditionsRecord } from '@/plan/travelConditions';
import type { TasteAnswers } from '@/preferences/tasteProfile';

const auth = { user: { userId: 'u1' } as { userId: string } | null, accessToken: 'token-1' as string | null, ready: true };
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
jest.mock('@/plan/travelConditions', () => ({ ...jest.requireActual('@/plan/travelConditions'), loadTravelConditions: jest.fn() }));
jest.mock('@/preferences/tasteProfile', () => ({ ...jest.requireActual('@/preferences/tasteProfile'), getTasteProfile: jest.fn() }));

const { loadTravelConditions } = jest.requireMock('@/plan/travelConditions') as { loadTravelConditions: jest.Mock };
const { getTasteProfile } = jest.requireMock('@/preferences/tasteProfile') as { getTasteProfile: jest.Mock };

/** 손으로 풀어 주는 약속 — 두 응답의 도착 순서를 시험이 정한다. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => { resolve = r; });
  return { promise, resolve };
}

const saved = (extra: Partial<TravelConditions>): TravelConditionsRecord => ({ status: 'SAVED', conditions: { ...EMPTY_CONDITIONS, ...extra } });

let plan: ReturnType<typeof usePlan>;
function Probe() { plan = usePlan(); return null; }
const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider><PlanProvider>{children}</PlanProvider></OnboardingPreferencesProvider>;

beforeEach(async () => {
  await AsyncStorage.clear();
  auth.user = { userId: 'u1' };
  auth.accessToken = 'token-1';
  loadTravelConditions.mockReset();
  getTasteProfile.mockReset();
});

describe('저장한 여행 조건 얹기 — 순수 함수', () => {
  it('🔴 null·UNKNOWN 인 칸만 채우고 값이 있는 칸은 그대로 둔다', () => {
    const current = { ...EMPTY_PLAN, slopeConstraint: 'AVOID' as const, dietStatus: 'NONE' as const, dietAnswered: true };
    const next = fillSavedConditions(current, { ...EMPTY_CONDITIONS, slopeConstraint: null, allergyStatus: 'VALUES', allergies: ['PEANUT'], dietStatus: 'VALUES', dietTypes: ['VEGAN'], stairsConstraint: 'AVOID' });
    expect(next.slopeConstraint).toBe('AVOID');
    expect(next.allergyStatus).toBe('VALUES');
    expect(next.allergies).toEqual(['PEANUT']);
    expect(next.allergyAnswered).toBe(true);
    expect(next.dietStatus).toBe('NONE');
    expect(next.dietTypes).toEqual([]);
    expect(next.stairsConstraint).toBe('AVOID');
  });

  it('바뀐 것이 없으면 같은 초안을 돌려준다', () => {
    const current = { ...EMPTY_PLAN, slopeConstraint: 'ALLOW' as const };
    expect(fillSavedConditions(current, { ...EMPTY_CONDITIONS, slopeConstraint: 'AVOID' })).toBe(current);
  });
});

describe('저장한 여행 조건 얹기 — 두 응답의 도착 순서', () => {
  const taste: TasteAnswers = { slope: 'AVOID' };
  const conditions = saved({ allergyStatus: 'NONE', slopeConstraint: null });

  it('🔴 평소 취향이 먼저 오고 여행 조건이 나중에 와도 경사 답(AVOID)이 남는다', async () => {
    const t = deferred<TasteAnswers>();
    const c = deferred<TravelConditionsRecord>();
    getTasteProfile.mockReturnValue(t.promise);
    loadTravelConditions.mockReturnValue(c.promise);
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(loadTravelConditions).toHaveBeenCalled());
    await waitFor(() => expect(getTasteProfile).toHaveBeenCalled());
    await act(async () => { t.resolve(taste); });
    await waitFor(() => expect(plan.draft.slopeConstraint).toBe('AVOID'));
    await act(async () => { c.resolve(conditions); });
    await waitFor(() => expect(plan.draft.allergyStatus).toBe('NONE'));
    expect(plan.draft.slopeConstraint).toBe('AVOID');
  });

  it('🔴 여행 조건이 먼저 오고 평소 취향이 나중에 와도 같다', async () => {
    const t = deferred<TasteAnswers>();
    const c = deferred<TravelConditionsRecord>();
    getTasteProfile.mockReturnValue(t.promise);
    loadTravelConditions.mockReturnValue(c.promise);
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(getTasteProfile).toHaveBeenCalled());
    await act(async () => { c.resolve(conditions); });
    await waitFor(() => expect(plan.draft.allergyStatus).toBe('NONE'));
    await act(async () => { t.resolve(taste); });
    await waitFor(() => expect(plan.draft.slopeConstraint).toBe('AVOID'));
    expect(plan.draft.allergyStatus).toBe('NONE');
  });
});

describe('저장한 여행 조건 얹기 — 로그인 열쇠가 바뀔 때', () => {
  it('🔴 이번 여행에서 고친 답을 열쇠가 바뀌어도 저장값으로 되돌리지 않는다 — 다시 묻지도 않는다', async () => {
    getTasteProfile.mockResolvedValue({});
    // 알레르기·식단은 「모름」 — 전에는 이때 열쇠가 바뀔 때마다 저장값 전체를 다시 덮었다
    loadTravelConditions.mockResolvedValue(saved({ slopeConstraint: 'AVOID', maxWalkingDistanceM: 1000 }));
    const view = render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan.draft.slopeConstraint).toBe('AVOID'));
    expect(plan.draft.maxWalkingDistanceM).toBe(1000);

    act(() => { plan.update({ slopeConstraint: 'ALLOW', maxWalkingDistanceM: 500 }); });
    auth.accessToken = 'token-2';
    view.rerender(<Providers><Probe /></Providers>);
    await act(async () => { await Promise.resolve(); });

    expect(plan.draft.slopeConstraint).toBe('ALLOW');
    expect(plan.draft.maxWalkingDistanceM).toBe(500);
    expect(loadTravelConditions).toHaveBeenCalledTimes(1);
  });

  it('받기 전에 열쇠가 바뀌어 첫 답을 버렸으면 새 열쇠로 다시 받아 얹는다', async () => {
    getTasteProfile.mockResolvedValue({});
    const first = deferred<TravelConditionsRecord>();
    loadTravelConditions.mockReturnValueOnce(first.promise).mockResolvedValue(saved({ stairsConstraint: 'AVOID' }));
    const view = render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(loadTravelConditions).toHaveBeenCalledTimes(1));
    auth.accessToken = 'token-2';
    view.rerender(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan.draft.stairsConstraint).toBe('AVOID'));
    await act(async () => { first.resolve(saved({ stairsConstraint: 'ALLOW' })); });
    expect(plan.draft.stairsConstraint).toBe('AVOID');
    expect(loadTravelConditions).toHaveBeenCalledTimes(2);
  });

  it('여행을 만들고 초안을 비우면(clear) 다음 여행에 저장값을 다시 기본값으로 얹는다', async () => {
    getTasteProfile.mockResolvedValue({});
    loadTravelConditions.mockResolvedValue(saved({ shadePreference: 'PREFER' }));
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan.draft.shadePreference).toBe('PREFER'));
    await act(async () => { await plan.clear(); });
    await waitFor(() => expect(loadTravelConditions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(plan.draft.shadePreference).toBe('PREFER'));
  });
});
