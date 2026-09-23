// 여행 조건 — 한 번에 한 질문 (S15P21E201-1425).
//
// 🔴 시안이 3-장 묶음(-1377)을 버리고 질문 하나씩 보이는 스테퍼로 돌아갔다. 필수 3 + 선택 4 = 일곱.
//    로컬성·조용함·음식은 온보딩 취향이 draft 로 이식되므로 여기서 다시 묻지 않는다.
//    이 시험이 지키는 것: 한 번에 하나만 보인다 · 「지금 이대로 만들기」 갈림 카드는 없다 ·
//    필수를 다 채우면 마지막 질문의 「이 조건으로 일정 만들기」가 일정을 보낸다 · 자리를 이어 붙인다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

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

/** 날짜·출발지는 있고(서버가 요구한다) 알레르기·식단은 답해 둔다 — 안 그러면 조건 창이 먼저 뜬다. */
// 🔴 출발지도 필수다 — 없으면 서버가 일정을 안 만든다(S15P21E201-1342).
const BASE: PlanDraft = { ...EMPTY_PLAN, startDate: '2026-10-03', endDate: '2026-10-05',
  origin: '부산역', originLat: 35.1152, originLng: 129.0403,
  allergyStatus: 'NONE', allergyAnswered: true, dietStatus: 'NONE', dietAnswered: true };
/** 필수 셋(범위·예산·이동수단)까지 다 채운 초안 — 「다음」으로 끝까지 갈 수 있다. */
const REQUIRED_DONE: PlanDraft = { ...BASE, travelAreas: ['HAEUNDAE'], budgetKrw: 100000, transport: 'TRANSIT' };

// 이 화면이 품은 여행 조건 모달이 「이 조건을 판정할 장소 자료가 있나」를 서버에
// 묻는다(S15P21E201-1044). 시험에서는 재시도를 끈다 — 끝점이 없는 자리라 매번 기다린다.
const mount = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <OnboardingPreferencesProvider><PlanConditions /></OnboardingPreferencesProvider>
  </QueryClientProvider>,
);

beforeEach(async () => { jest.clearAllMocks(); mockDraft = BASE; await AsyncStorage.clear(); });

describe('여행 조건 문항 — 한 번에 하나 (S15P21E201-1425)', () => {
  it('첫 질문은 「필수 1 / 3」이고 한 번에 하나만 보인다 · 갈림 카드는 없다', () => {
    const view = mount();
    expect(view.getByText('필수 1 / 3')).toBeTruthy();
    expect(view.getByText('여행 범위')).toBeTruthy();
    // 예산 질문의 «카드»는 지금 안 보인다 — 금액 더하기 칩(+1만)은 그 카드 안에만 있다.
    //   질문 이름 「총예산」은 아래 「다음 질문」 목록에 있을 수 있으니 카드 고유의 부품으로 가른다.
    expect(view.queryByText('+1만')).toBeNull();
    // 「지금 이대로 만들기」 갈림 카드·버튼은 시안에서 걷어냈다.
    expect(view.queryByText('지금 이대로 만들기 →')).toBeNull();
    expect(view.queryByText('필수는 끝! 지금 만들 수 있어요')).toBeNull();
    // 마지막이 아니라 「다음」이지 「이 조건으로 일정 만들기」가 아니다.
    expect(view.queryByText('이 조건으로 일정 만들기')).toBeNull();
  });

  it('필수를 다 채우면 「다음」으로 마지막 질문까지 가고, 「이 조건으로 일정 만들기」가 일정을 보낸다', async () => {
    mockDraft = REQUIRED_DONE;
    const view = mount();
    expect(view.getByText('필수 1 / 3')).toBeTruthy();
    // 일곱 질문을 「다음」으로 지나 마지막(꼭 가고 싶은 장소)까지 — 여섯 번 누른다.
    for (let i = 0; i < 6; i += 1) fireEvent.press(view.getByText('다음'));
    expect(view.getByText('꼭 가고 싶은 장소')).toBeTruthy();
    expect(view.getByText('선택 4 / 4')).toBeTruthy();
    fireEvent.press(view.getByText('이 조건으로 일정 만들기'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/plan/generating', params: { jobId: 'job-1' } });
  });

  it('🔴 알레르기를 안 물었어도(기본값 「모름」) 경고 없이 바로 보낸다 — 조건 창이 다시 뜨지 않는다 (S15P21E201-1513)', async () => {
    // 조건 창이 알레르기를 더는 안 묻는다(-1497). 그래서 새 사람은 알레르기가 영영 'UNKNOWN' 이다.
    mockDraft = { ...REQUIRED_DONE, allergyStatus: 'UNKNOWN', allergyAnswered: false };
    const view = mount();
    for (let i = 0; i < 6; i += 1) fireEvent.press(view.getByText('다음'));
    expect(view.queryByText('식단을 아직 안 알려주셨어요. 눌러서 알려주세요.')).toBeNull();
    fireEvent.press(view.getByText('이 조건으로 일정 만들기'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
  });

  it('🔴 날짜가 없으면 달력 카드가 이 화면 안에 펼쳐진다 — 홈으로 보내지 않는다 (S15P21E201-1376)', () => {
    mockDraft = { ...BASE, startDate: '', endDate: '' };
    const view = mount();
    expect(view.getByText('언제 가세요?')).toBeTruthy();
    fireEvent.press(view.getByText('2박 3일'));
    expect(mockUpdate).toHaveBeenCalledWith(expect.objectContaining({ startDate: expect.stringMatching(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/), endDate: expect.any(String) }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('🔴 자리를 기기에 남기고 다시 열면 그 질문에서 잇는다', async () => {
    mockDraft = REQUIRED_DONE;
    const first = mount();
    fireEvent.press(first.getByText('다음'));
    await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem('gabolle:plan-questions-state')) ?? '{}').open).toBe(1));
    first.unmount();
    const again = mount();
    await waitFor(() => expect(again.getByText('필수 2 / 3')).toBeTruthy());
  });

  it('🔴 여행 범위를 안 답한 채 마지막 질문에서 열려도 폰 목록에 그 줄이 있고, 누르면 그 질문으로 간다 (S15P21E201-1540)', async () => {
    // 기기에 남은 자리는 마지막인데 초안의 여행 범위는 비었다 — 로그아웃이 초안만 지우고 자리는 남긴 모양.
    await AsyncStorage.setItem('gabolle:plan-questions-state', JSON.stringify({ open: 6, skipped: {} }));
    const view = mount();
    await waitFor(() => expect(view.getByText('선택 4 / 4')).toBeTruthy());
    expect(view.getByText('아직 안 답했어요 · 필수')).toBeTruthy();
    fireEvent.press(view.getByLabelText('여행 범위 답하기'));
    await waitFor(() => expect(view.getByText('필수 1 / 3')).toBeTruthy());
  });
});
