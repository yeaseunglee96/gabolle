// 폰 홈이 여는 순간 같은 요청을 두 번씩 보내던 것 — S15P21E201-1686 (iOS 심사 공지의 알려진 문제 4).
//
// 🔴 이 시험이 지키는 것 (로컬 실측: 폰 홈이 1초 안에 요청 23개, 그중 셋이 두 번):
//    1. 피드 글 — 로그인 복원이 끝나기 전에 손님 몫으로 한 번, 끝난 뒤 회원 몫으로 또 한 번 불렀다.
//       복원이 끝난 뒤(ready) 한 번만 부른다.
//    2. 종 점 — 홈 카드가 이미 받은 여행 목록을 한 번 더 부르고, 내 여행마다(최대 12개) 활동을 동시에 불렀다.
//       홈이 받은 목록을 다시 쓰고, 몇 초 뒤에, 가장 최근에 바뀐 여행 셋만 본다(조율 세션 결정).
//    3. 여행 조건 — 홈의 「물을까」 판정과 새 여행 초안이 거의 동시에 같은 조건을 불렀다. 한 요청으로 묶는다.
import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { apiRequest } from '@/api/client';
import { getTripActivity } from '@/trip/collaboration';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { loadFeed } from '@/social/stories';

jest.mock('@/api/client', () => ({ apiRequest: jest.fn() }));
jest.mock('@/trip/collaboration', () => ({ getTripActivity: jest.fn() }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), loadTrips: jest.fn() }));
jest.mock('@/social/stories', () => ({ loadFeed: jest.fn() }));
jest.mock('@/discovery/localExplore', () => ({ getFacets: jest.fn(async () => ({ state: 'success', facets: [] })) }));
jest.mock('@/discovery/festivals', () => ({ ...jest.requireActual('@/discovery/festivals'), getFestivals: jest.fn(async () => []) }));
jest.mock('@/discovery/places', () => ({ getPlacesByFacet: jest.fn(async () => []) }));
jest.mock('@/trip/weather', () => ({ loadWeatherForecast: jest.fn(async () => ({ state: 'empty' })) }));
// 화면 초점 — 시험에는 화면이 없으니 그린 순간 한 번 도는 효과로 본다.
jest.mock('expo-router', () => ({ useFocusEffect: (effect: () => void | (() => void)) => jest.requireActual('react').useEffect(effect, [effect]) }));
let mockAuth: { accessToken: string | null; ready: boolean } = { accessToken: null, ready: false };
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));

import { useHomeData } from '@/home/useHomeData';
import { loadActivityFeed } from '@/notifications/activityFeed';
import { BELL_DOT_DELAY_MS, BELL_DOT_TRIPS, useHomeBellDot } from '@/notifications/useHomeBellDot';
import { loadTravelConditions, saveTravelConditions } from '@/plan/travelConditions';

jest.setTimeout(20000);

const tx = (ko: string) => ko;
const trip = (tripId: string, updatedAt: string): TripSummaryDto => ({
  tripId, title: tripId, startDate: null, endDate: null, dayCount: 1, partySize: 1, status: 'READY', role: 'OWNER',
  createdAt: updatedAt, updatedAt, coverImageUrl: null, firstStopNameKo: null, firstStopNameEn: null,
} as TripSummaryDto);
const FIVE = [
  trip('t-old', '2026-09-01T00:00:00Z'),
  trip('t-new', '2026-09-24T00:00:00Z'),
  trip('t-mid', '2026-09-20T00:00:00Z'),
  trip('t-oldest', '2026-08-01T00:00:00Z'),
  trip('t-newer', '2026-09-22T00:00:00Z'),
];
const activity = (at: string) => ({ state: 'success', tripId: 't', limit: 10, myRole: 'OWNER', entries: [{ itineraryId: 'i1', version: 2, operation: 'REORDER', actorId: 'u2', actorName: '지우', isMe: false, at, baseVersion: 1, revertedFromVersion: null, warningCodes: [] }] });

const mockedApi = apiRequest as jest.Mock;
const mockedActivity = getTripActivity as jest.Mock;
const mockedTrips = loadTrips as jest.Mock;
const mockedFeed = loadFeed as jest.Mock;

beforeEach(() => {
  mockedApi.mockReset();
  mockedActivity.mockReset();
  mockedTrips.mockReset();
  mockedFeed.mockReset();
  mockedActivity.mockResolvedValue(activity('2026-09-25T09:00:00Z'));
  mockedTrips.mockResolvedValue({ state: 'success', trips: FIVE });
  mockedFeed.mockResolvedValue({ state: 'success', items: [], nextCursor: null });
});

describe('1. 피드 글은 로그인 복원이 끝난 뒤 한 번', () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })}>{children}</QueryClientProvider>
  );

  it('🔴 복원 중(ready 전)에는 부르지 않고, 복원 뒤 회원 몫으로 한 번만', async () => {
    mockAuth = { accessToken: null, ready: false };
    const { rerender } = renderHook(() => useHomeData(true), { wrapper });
    await act(async () => { await Promise.resolve(); });
    expect(mockedFeed).not.toHaveBeenCalled();

    mockAuth = { accessToken: 'member-token', ready: true };
    rerender({});
    await waitFor(() => expect(mockedFeed).toHaveBeenCalledTimes(1));
    expect(mockedFeed.mock.calls[0][0].accessToken).toBe('member-token');
  });

  it('로그인 안 한 사람도 복원이 끝나면 글을 본다(손님 몫)', async () => {
    mockAuth = { accessToken: null, ready: true };
    renderHook(() => useHomeData(true), { wrapper });
    await waitFor(() => expect(mockedFeed).toHaveBeenCalledTimes(1));
    expect(mockedFeed.mock.calls[0][0].accessToken).toBeNull();
  });

  it('홈 카드가 받은 여행 목록을 밖으로 내준다 — 종 점이 다시 부르지 않게', async () => {
    mockAuth = { accessToken: 'member-token', ready: true };
    const { result } = renderHook(() => useHomeData(true), { wrapper });
    await waitFor(() => expect(result.current.trips).toHaveLength(5));
    expect(mockedTrips).toHaveBeenCalledTimes(1);
  });
});

describe('2. 종 점', () => {
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(() => { jest.useRealTimers(); });

  const bell = (props: Partial<Parameters<typeof useHomeBellDot>[0]> = {}) =>
    renderHook(() => useHomeBellDot({ userId: 'me', accessToken: 'member-token', trips: FIVE, visible: true, tx, ...props }));

  it('🔴 홈이 그려진 순간에는 부르지 않는다 — 몇 초 뒤에', async () => {
    bell();
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS - 1); });
    expect(mockedActivity).not.toHaveBeenCalled();
    await act(async () => { jest.advanceTimersByTime(1); });
    expect(mockedActivity).toHaveBeenCalled();
  });

  it('🔴 가장 최근에 바뀐 여행 셋만 본다', async () => {
    bell();
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS); });
    expect(BELL_DOT_TRIPS).toBe(3);
    expect(mockedActivity.mock.calls.map(([tripId]) => tripId)).toEqual(['t-new', 't-newer', 't-mid']);
  });

  it('🔴 홈이 받은 여행 목록을 다시 쓴다 — 목록을 또 부르지 않는다', async () => {
    bell();
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS); });
    expect(mockedTrips).not.toHaveBeenCalled();
  });

  it('안 본 활동이 있으면 점이 켜진다', async () => {
    const { result } = bell();
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS); });
    await act(async () => { await Promise.resolve(); });
    expect(result.current).toBe(true);
  });

  it('여행 목록이 아직 안 왔으면 기다린다', async () => {
    bell({ trips: null });
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS * 3); });
    expect(mockedActivity).not.toHaveBeenCalled();
    expect(mockedTrips).not.toHaveBeenCalled();
  });

  it('종이 이 화면에 안 그려지면(위쪽 메뉴가 떠 있을 때) 세지 않는다', async () => {
    bell({ visible: false });
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS * 3); });
    expect(mockedActivity).not.toHaveBeenCalled();
  });

  it('로그인 안 했으면 부르지 않는다', async () => {
    const { result } = bell({ userId: null, accessToken: null });
    await act(async () => { jest.advanceTimersByTime(BELL_DOT_DELAY_MS * 3); });
    expect(mockedActivity).not.toHaveBeenCalled();
    expect(result.current).toBe(false);
  });

  it('알림 화면은 그대로 — 목록을 직접 받아 최근 12개까지', async () => {
    jest.useRealTimers();
    mockedTrips.mockResolvedValue({ state: 'success', trips: [...Array(14)].map((_, i) => trip(`t${i}`, `2026-09-${String(i + 1).padStart(2, '0')}T00:00:00Z`)) });
    await loadActivityFeed('member-token', tx);
    expect(mockedTrips).toHaveBeenCalledTimes(1);
    expect(mockedActivity).toHaveBeenCalledTimes(12);
  });
});

describe('3. 여행 조건은 거의 동시에 부른 둘을 한 요청으로', () => {
  const PATH = '/api/v1/me/preferences/constraints';
  const gets = () => mockedApi.mock.calls.filter(([path, options]) => path === PATH && !options?.method).length;
  beforeEach(() => { mockedApi.mockResolvedValue({ status: 'SAVED', value: null }); });

  it('🔴 둘이 동시에 부르면 서버에는 한 번', async () => {
    await Promise.all([loadTravelConditions('u1', 'tok-a'), loadTravelConditions('u1', 'tok-a')]);
    expect(gets()).toBe(1);
  });

  it('🔴 앞의 답이 막 온 뒤에 부른 것도 같은 답을 쓴다 — 실측에서 둘째는 45ms 뒤였다', async () => {
    const first = await loadTravelConditions('u1', 'tok-b');
    const second = await loadTravelConditions('u1', 'tok-b');
    expect(gets()).toBe(1);
    expect(second).toEqual(first);
  });

  it('🔴 저장하고 나면 다시 서버에 묻는다 — 저장 전 답을 돌려주지 않는다', async () => {
    await loadTravelConditions('u1', 'tok-c');
    await saveTravelConditions({ userId: 'u1', accessToken: 'tok-c', status: 'LATER', conditions: null });
    await loadTravelConditions('u1', 'tok-c');
    expect(gets()).toBe(2);
  });

  it('다른 계정·다른 표면 따로 묻는다', async () => {
    await loadTravelConditions('u1', 'tok-d');
    await loadTravelConditions('u2', 'tok-e');
    expect(gets()).toBe(2);
  });

  it('잠깐이 지나면 다시 묻는다 — 서버가 정본이다', async () => {
    const now = jest.spyOn(Date, 'now');
    now.mockReturnValue(1_000_000);
    await loadTravelConditions('u1', 'tok-f');
    now.mockReturnValue(1_000_000 + 60_000);
    await loadTravelConditions('u1', 'tok-f');
    now.mockRestore();
    expect(gets()).toBe(2);
  });
});
