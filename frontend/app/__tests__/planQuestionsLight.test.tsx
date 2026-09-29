// 여행 만들기 — 네 단계 + 확인 표 (UI 캔버스 ⑤, S15P21E201-1865).
//
// 🔴 전에는 질문 일곱을 하나씩 물었고(-1425), 서버가 요구하는 날짜·출발지·숙소는 질문 밖에 있었다.
//    이 시험이 지키는 것: 한 번에 한 단계 · 빈 필수 단계를 건너뛴 채 확인 표에 서지 않는다 ·
//    잠긴 단추가 이유를 말한다 · 확인 표의 「이 조건으로 일정 만들기」가 일정을 보낸다 · 자리를 이어 붙인다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { EMPTY_PLAN, type PlanDraft } from '@/plan/PlanProvider';
import { RECOMMENDED_LODGING_AREAS } from '@/plan/origins';

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
// 🔴 1박 이상이면 숙소도 필수다(S15P21E201-1584) — 해운대 동네로 골라 둔다.
const HAEUNDAE = RECOMMENDED_LODGING_AREAS.find((area) => area.externalId === 'lodging-haeundae')!;
const BASE: PlanDraft = { ...EMPTY_PLAN, startDate: '2026-10-03', endDate: '2026-10-05',
  origin: '부산역', originLat: 35.1152, originLng: 129.0403,
  lodging: '해운대', lodgingLat: HAEUNDAE.lat, lodgingLng: HAEUNDAE.lng,
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

describe('여행 만들기 — 네 단계 + 확인 표 (UI 캔버스 ⑤, S15P21E201-1865)', () => {
  it('1단계는 「언제, 누구와」이고 날짜·인원을 이 화면 안에서 고른다 · 「이전」은 없다', () => {
    const view = mount();
    expect(view.getByText('1/4')).toBeTruthy();
    expect(view.getByText('언제, 누구와 가세요?')).toBeTruthy();
    expect(view.getByText('2박 3일')).toBeTruthy();
    expect(view.getByText('성인')).toBeTruthy();
    expect(view.queryByText('이전')).toBeNull();
    // 한 번에 한 단계 — 예산 카드(+1만)는 3단계에만 있다.
    expect(view.queryByText('+1만')).toBeNull();
    fireEvent.press(view.getByText('다음'));
    expect(view.getByText('어디서 출발해서, 어떻게 다닐까요?')).toBeTruthy();
  });

  it('필수를 다 채우면 「다음」 넷으로 확인 표까지 가고, 「이 조건으로 일정 만들기」가 일정을 보낸다', async () => {
    mockDraft = REQUIRED_DONE;
    const view = mount();
    fireEvent.press(view.getByText('다음'));
    fireEvent.press(view.getByText('다음'));
    fireEvent.press(view.getByText('다음'));
    expect(view.getByText('어떤 여행이 좋아요?')).toBeTruthy();
    fireEvent.press(view.getByText('다음 · 한눈에 보기'));
    expect(view.getByText('이대로 만들까요?')).toBeTruthy();
    expect(view.getByText('TRIP PASS · 미리보기')).toBeTruthy();
    fireEvent.press(view.getByText('이 조건으로 일정 만들기'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/plan/generating', params: { jobId: 'job-1' } });
  });

  it('🔴 1박 이상인데 숙소가 없으면 저장된 자리가 확인 표여도 2단계에 선다 — 단추가 이유를 말하고, 찾기는 이 화면 위 시트로 (S15P21E201-1584)', async () => {
    mockDraft = { ...REQUIRED_DONE, lodging: '', lodgingLat: null, lodgingLng: null };
    await AsyncStorage.setItem('gabolle:plan-questions-state', JSON.stringify({ open: 4, skipped: {} }));
    const view = mount();
    await waitFor(() => expect(view.getByText('2/4')).toBeTruthy());
    expect(view.getByText('숙소 · 2박이라 필요해요')).toBeTruthy();
    fireEvent.press(view.getByText('숙소를 골라 주세요'));
    expect(mockSubmit).not.toHaveBeenCalled();
    // 추천 동네는 그 자리에서 고른다 — 홈 시작 바와 같은 값(장소 스냅샷은 안 싣는다).
    fireEvent.press(view.getByText('해운대'));
    expect(mockUpdate).toHaveBeenCalledWith(expect.objectContaining({ lodging: '해운대', lodgingPlace: null }));
    // 🔴 이름으로 찾기는 홈으로 튕겨 나가지 않는다 — 이 화면 위에 시작 바 시트를 연다.
    fireEvent.press(view.getByText('이름으로 찾기'));
    expect(mockPush).not.toHaveBeenCalled();
    expect(view.getByLabelText(/닫기|Close/)).toBeTruthy();
  });

  it('🔴 알레르기를 안 물었어도(기본값 「모름」) 경고 없이 바로 보낸다 — 조건 창이 다시 뜨지 않는다 (S15P21E201-1513)', async () => {
    mockDraft = { ...REQUIRED_DONE, allergyStatus: 'UNKNOWN', allergyAnswered: false };
    await AsyncStorage.setItem('gabolle:plan-questions-state', JSON.stringify({ open: 4, skipped: {} }));
    const view = mount();
    await waitFor(() => expect(view.getByText('이대로 만들까요?')).toBeTruthy());
    expect(view.queryByText('식단을 아직 안 알려주셨어요 · 눌러서 알려주기 ›')).toBeNull();
    fireEvent.press(view.getByText('이 조건으로 일정 만들기'));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledTimes(1));
  });

  it('🔴 날짜가 없으면 1단계 달력에서 고른다 — 홈으로 보내지 않고, 잠긴 단추가 이유를 말한다 (S15P21E201-1376)', () => {
    mockDraft = { ...BASE, startDate: '', endDate: '' };
    const view = mount();
    expect(view.getByText('여행 날짜를 골라 주세요')).toBeTruthy();
    fireEvent.press(view.getByText('2박 3일'));
    expect(mockUpdate).toHaveBeenCalledWith(expect.objectContaining({ startDate: expect.stringMatching(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/), endDate: expect.any(String) }));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('🔴 자리를 기기에 남기고 다시 열면 그 단계에서 잇는다', async () => {
    mockDraft = REQUIRED_DONE;
    const first = mount();
    fireEvent.press(first.getByText('다음'));
    await waitFor(async () => expect(JSON.parse((await AsyncStorage.getItem('gabolle:plan-questions-state')) ?? '{}').open).toBe(1));
    first.unmount();
    const again = mount();
    await waitFor(() => expect(again.getByText('어디서 출발해서, 어떻게 다닐까요?')).toBeTruthy());
  });

  it('🔴 지역을 안 답한 채 확인 표 자리로 열려도 3단계에 서고, 이유를 말한다 (S15P21E201-1540)', async () => {
    // 기기에 남은 자리는 확인 표인데 초안의 지역은 비었다 — 로그아웃이 초안만 지우고 자리는 남긴 모양.
    await AsyncStorage.setItem('gabolle:plan-questions-state', JSON.stringify({ open: 4, skipped: {} }));
    const view = mount();
    await waitFor(() => expect(view.getByText('3/4')).toBeTruthy());
    expect(view.getByText('지역을 하나 이상 골라 주세요')).toBeTruthy();
  });

  it('4단계는 건너뛸 수 있고, 확인 표가 「건너뜀」이라 적는다 · 표의 칸을 누르면 그 단계로 간다', async () => {
    mockDraft = REQUIRED_DONE;
    await AsyncStorage.setItem('gabolle:plan-questions-state', JSON.stringify({ open: 3, skipped: {} }));
    const view = mount();
    await waitFor(() => expect(view.getByText('어떤 여행이 좋아요?')).toBeTruthy());
    fireEvent.press(view.getByText('건너뛰기'));
    expect(view.getByText('이대로 만들까요?')).toBeTruthy();
    expect(view.getAllByText('건너뜀').length).toBeGreaterThan(0);
    fireEvent.press(view.getByLabelText('날짜 고치기'));
    expect(view.getByText('언제, 누구와 가세요?')).toBeTruthy();
  });
});
