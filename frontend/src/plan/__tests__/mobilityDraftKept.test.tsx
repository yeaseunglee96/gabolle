// 휠체어·유아차·큰 짐 답이 새로고침 뒤에도 남는다.
//
// 🔴 이 시험이 지키는 것: 이동 보조 답은 여행 조건(travelConditions.ts)에 저장되지 않는다. 그래서 초안을 기기에
//    적을 때 비우면 되살릴 곳이 없다. 전에는 비웠고, 새로고침이나 웹 소셜 로그인 한 번에 답이 null 이 되어
//    「모름(UNKNOWN)」으로 나갔다 — 휠체어를 골랐는데 휠체어 조건 없이 일정이 만들어졌다.
//    알레르기처럼 여행 조건이 다시 채우는 칸은 여전히 비운다.
import type { ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN, PlanProvider, usePlan } from '@/plan/PlanProvider';

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null, ready: true }) }));
jest.mock('@/plan/travelConditions', () => ({
  ...jest.requireActual('@/plan/travelConditions'),
  loadTravelConditions: jest.fn(async () => ({ status: null, conditions: null })),
}));

const ANONYMOUS_KEY = '@gabolle/plan-draft:anonymous';

describe('이동 보조 답 — 새로고침 뒤', () => {
  let plan: ReturnType<typeof usePlan>;
  function Probe() { plan = usePlan(); return null; }
  const Providers = ({ children }: { children: ReactNode }) => <OnboardingPreferencesProvider><PlanProvider>{children}</PlanProvider></OnboardingPreferencesProvider>;

  beforeEach(async () => { await AsyncStorage.clear(); });

  it('🔴 저장해 둔 초안의 휠체어·유아차·큰 짐 답을 그대로 되살린다', async () => {
    await AsyncStorage.setItem(ANONYMOUS_KEY, JSON.stringify({
      version: 1,
      draft: { ...EMPTY_PLAN, startDate: '2026-10-12', endDate: '2026-10-13', wheelchair: true, stroller: false, luggage: true },
    }));
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan.draft.startDate).toBe('2026-10-12'));
    expect(plan.draft.wheelchair).toBe(true);
    expect(plan.draft.stroller).toBe(false);
    expect(plan.draft.luggage).toBe(true);
  });

  it('여행 조건이 다시 채우는 알레르기는 기기 초안에서 되살리지 않는다', async () => {
    await AsyncStorage.setItem(ANONYMOUS_KEY, JSON.stringify({
      version: 1,
      draft: { ...EMPTY_PLAN, startDate: '2026-10-12', allergyStatus: 'VALUES', allergies: ['PEANUT'], wheelchair: true },
    }));
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan.draft.startDate).toBe('2026-10-12'));
    expect(plan.draft.allergyStatus).toBe('UNKNOWN');
    expect(plan.draft.allergies).toEqual([]);
    expect(plan.draft.wheelchair).toBe(true);
  });

  it('🔴 초안을 적을 때도 이동 보조 답을 남긴다', async () => {
    render(<Providers><Probe /></Providers>);
    await waitFor(() => expect(plan).toBeDefined());
    await waitFor(async () => expect(await AsyncStorage.getItem(ANONYMOUS_KEY)).not.toBeNull());
    act(() => { plan.update({ wheelchair: true, allergyStatus: 'VALUES', allergies: ['PEANUT'] }); });
    await waitFor(async () => {
      const stored = JSON.parse((await AsyncStorage.getItem(ANONYMOUS_KEY)) ?? '{}');
      expect(stored.draft.wheelchair).toBe(true);
      expect(stored.draft.allergies).toEqual([]);
    });
  });
});
