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
const BASE: PlanDraft = { ...EMPTY_PLAN, startDate: '2026-10-03', endDate: '2026-10-05', allergyStatus: 'NONE', allergyAnswered: true, dietStatus: 'NONE', dietAnswered: true };

const mount = () => render(<OnboardingPreferencesProvider><PlanConditions /></OnboardingPreferencesProvider>);

beforeEach(async () => { jest.clearAllMocks(); mockDraft = BASE; await AsyncStorage.clear(); });

describe('여행 조건 문항 — 필수 3 · 선택 7', () => {
  it('첫 문항은 「필수 1 / 3」이고, 필수를 다 답하기 전에는 「지금 이대로 만들기」가 없다', () => {
    const view = mount();
    expect(view.getByText('필수 1 / 3')).toBeTruthy();
    expect(view.getByText('필수 3개만 답하면 만들 수 있어요 · 나머지는 건너뛰어도 돼요')).toBeTruthy();
    expect(view.queryByText('지금 이대로 만들기 →')).toBeNull();
  });

  it('필수 셋을 답하고 4번에 오면 「선택 1 / 7」·답한 셋이 접혀 있고, 「지금 이대로 만들기」가 일정을 보낸다', async () => {
    // 여행 범위만 사람이 고른다. 예산·이동수단은 기본값이 있어서 「다음」만 누르면 답한 것으로 친다.
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const view = mount();
    fireEvent.press(view.getByText('다음'));
    fireEvent.press(view.getByText('다음'));
    fireEvent.press(view.getByText('다음'));

    expect(view.getByText('선택 1 / 7')).toBeTruthy();
    expect(view.getByText('선택 · 건너뛰어도 돼요')).toBeTruthy();
    // 답한 셋이 카드 위에 접혀 있다 — 값과 「수정」.
    expect(view.getByText('해운대')).toBeTruthy();
    // (머리의 날짜 「수정」도 같은 글자라, 접힌 줄은 접근성 이름 「… 수정」으로 센다.)
    // 날짜 ✓ 줄까지 넷(S15P21E201-1376).
    expect(view.getAllByLabelText(/ 수정$/)).toHaveLength(4);
    expect(view.getByLabelText('여행 날짜 수정')).toBeTruthy();
    // 남은 질문 목록 — 다음 번호부터.
    expect(view.getByText('다음 질문')).toBeTruthy();
    expect(view.getByText('꼭 가고 싶은 장소')).toBeTruthy();

    fireEvent.press(view.getByText('지금 이대로 만들기 →'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/plan/generating', params: { jobId: 'job-1' } });
  });

  it('🔴 날짜가 없으면 달력 카드가 이 화면 안에 펼쳐진다 — 홈으로 보내지 않는다 (S15P21E201-1376)', () => {
    mockDraft = { ...BASE, startDate: '', endDate: '' };
    const view = mount();
    expect(view.getByText('언제 가세요?')).toBeTruthy();
    expect(view.getByText('2박 3일')).toBeTruthy();
    fireEvent.press(view.getByText('2박 3일'));
    expect(mockUpdate).toHaveBeenCalledWith(expect.objectContaining({ startDate: expect.stringMatching(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/), endDate: expect.any(String) }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('필수를 막 끝낸 자리(4번)에서는 「필수 3개 끝!」 갈림 카드가 카드 위에 선다', () => {
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const view = mount();
    fireEvent.press(view.getByText('다음')); fireEvent.press(view.getByText('다음')); fireEvent.press(view.getByText('다음'));
    expect(view.getByText('필수 3개 끝! 지금 만들 수 있어요')).toBeTruthy();
  });

  it('🔴 자리를 기기에 남기고 다시 열면 그 자리에서 잇는다 — 로그인하고 돌아와도 1번이 아니다', async () => {
    mockDraft = { ...BASE, travelAreas: ['HAEUNDAE'] };
    const first = mount();
    fireEvent.press(first.getByText('다음')); fireEvent.press(first.getByText('다음')); fireEvent.press(first.getByText('다음'));
    await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem('gabolle:plan-questions-state')) ?? '{}').open).toBe(3));
    first.unmount();
    const again = mount();
    await waitFor(() => expect(again.getByText('선택 1 / 7')).toBeTruthy());
  });
});
