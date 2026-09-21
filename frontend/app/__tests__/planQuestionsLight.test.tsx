// 여행 조건 열 문항을 가볍게 — S15P21E201-1371.
//
// 문항은 열 개 그대로인데 「필수 3 · 선택 7」로 세고, 필수 셋을 답하면 「지금 이대로 만들기」가 뜬다.
// 이 둘이 이 MR 의 전부라서, 이 둘만 시험한다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';

const mockPush = jest.fn();
const mockSubmit = jest.fn(async () => ({ state: 'submitting', jobId: 'job-1', progress: null, stage: null, canCancel: false, errorMessage: null, resultRef: null }));
const mockUpdate = jest.fn();
let mockDraft: PlanDraft;

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn() }),
  useLocalSearchParams: () => ({}),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/plan/PlanProvider', () => {
  const actual = jest.requireActual('@/plan/PlanProvider');
  return { ...actual, usePlan: () => ({ draft: mockDraft, ready: true, update: mockUpdate, completeStep: jest.fn(), clear: jest.fn(), basicComplete: true, foodConflictNotice: null, clearFoodConflictNotice: jest.fn() }) };
});
jest.mock('@/plan/recommendationJob', () => ({ createRecommendationJobAdapter: () => ({ submit: mockSubmit }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import PlanConditions from '../(plan)/questions';

/** 날짜는 있고(서버가 요구한다) 알레르기·식단은 답해 둔다 — 안 그러면 조건 창이 먼저 뜬다. */
// 🔴 출발지도 필수다 — 없으면 서버가 일정을 안 만든다(S15P21E201-1342).
//    예전에는 이 초안에 출발지가 없어도 「필수는 끝!」 갈림 카드가 떴는데,
//    그때 눌러도 서버가 originLat 으로 거절했다. 이제 화면이 먼저 막으므로
//    「다 채운 상태」를 나타내는 이 초안에도 출발지가 있어야 한다.
const BASE: PlanDraft = { ...EMPTY_PLAN, startDate: '2026-10-03', endDate: '2026-10-05',
  origin: '부산역', originLat: 35.1152, originLng: 129.0403,
  allergyStatus: 'NONE', allergyAnswered: true, dietStatus: 'NONE', dietAnswered: true };

const mount = () => render(<OnboardingPreferencesProvider><PlanConditions /></OnboardingPreferencesProvider>);

beforeEach(async () => { jest.clearAllMocks(); mockDraft = BASE; await AsyncStorage.clear(); });

describe('여행 조건 문항 — 세 장 (S15P21E201-1377)', () => {
  it('1장은 「1 / 3 · 기본」이고 필수 셋이 한 장에 다 있다 · 범위를 안 고르면 다음 단추가 이유를 말한다', () => {
    const view = mount();
    expect(view.getByText('1 / 3 · 기본')).toBeTruthy();
    expect(view.getByText('여행 범위')).toBeTruthy();
    expect(view.getByText('총예산')).toBeTruthy();
    expect(view.getByText('하루 여행 시간 · 이동수단')).toBeTruthy();
    expect(view.getByText('여행 범위를 골라 주세요')).toBeTruthy();
    expect(view.queryByText('지금 이대로 만들기 →')).toBeNull();
  });

  it('범위를 고르면 2장으로 넘어가고, 1장 요약 ✓ 줄과 「필수는 끝!」 갈림 카드가 서며, 「지금 이대로 만들기」가 일정을 보낸다', async () => {
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const view = mount();
    fireEvent.press(view.getByText('취향도 알려주기 →'));
    expect(view.getByText('2 / 3 · 취향')).toBeTruthy();
    expect(view.getByText('해운대 · 10만원 · 09:00–18:00 · 대중교통')).toBeTruthy();
    expect(view.getByLabelText('여행 날짜 수정')).toBeTruthy();
    expect(view.getByText('필수는 끝! 지금 만들 수 있어요')).toBeTruthy();
    expect(view.getByText('여행 카테고리')).toBeTruthy();
    expect(view.getByText('여행 기분')).toBeTruthy();
    fireEvent.press(view.getByText('지금 이대로 만들기 →'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/plan/generating', params: { jobId: 'job-1' } });
  });

  it('선택 장은 통째로 건너뛸 수 있고 3장이 마지막이다', () => {
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const view = mount();
    fireEvent.press(view.getByText('취향도 알려주기 →'));
    fireEvent.press(view.getByText('이 장은 건너뛰기'));
    expect(view.getByText('3 / 3 · 세부')).toBeTruthy();
    expect(view.getByText('이 조건으로 일정 만들기')).toBeTruthy();
  });

  it('🔴 날짜가 없으면 달력 카드가 이 화면 안에 펼쳐진다 — 홈으로 보내지 않는다 (S15P21E201-1376)', () => {
    mockDraft = { ...BASE, startDate: '', endDate: '' };
    const view = mount();
    expect(view.getByText('언제 가세요?')).toBeTruthy();
    fireEvent.press(view.getByText('2박 3일'));
    expect(mockUpdate).toHaveBeenCalledWith(expect.objectContaining({ startDate: expect.stringMatching(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/), endDate: expect.any(String) }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('🔴 자리를 기기에 남기고 다시 열면 그 장에서 잇는다', async () => {
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const first = mount();
    fireEvent.press(first.getByText('취향도 알려주기 →'));
    await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem('gabolle:plan-questions-state')) ?? '{}').open).toBe(1));
    first.unmount();
    const again = mount();
    await waitFor(() => expect(again.getByText('2 / 3 · 취향')).toBeTruthy());
  });
});
