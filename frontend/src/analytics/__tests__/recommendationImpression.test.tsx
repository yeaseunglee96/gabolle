// 추천 노출 기록(recommendation_impression) — S15P21E201-1696 (계획서 8.1 P0 · 07 계약 · 조율 세션 결정).
//
// 🔴 이 시험이 지키는 것:
//    1. 카드가 «실제로 화면에» 절반 넘게 보일 때만 보낸다. 목록에 들었다는 이유로는 안 보낸다.
//    2. 한 화면에서 같은 (추천 요청, 장소)는 한 번. 화면 이름이 바뀌면(코스 고르기 → 확정한 일정) 다시 센다.
//    3. 추천 요청 번호가 없는 곳(손으로 더한 곳)·로그인 안 한 사람은 안 보낸다.
//    4. 행동 동의와 무관하게 보낸다(추천 품질 통계). 다른 행동 기록은 여전히 동의가 있어야 한다.
//    5. 본문은 낙타 표기 {placeId, sourceScreen} — 서버의 읽는 뷰가 그 이름으로 읽는다.
import { Dimensions } from 'react-native';
import { act, renderHook } from '@testing-library/react-native';

import { apiRequest } from '@/api/client';
import { loadBehaviorConsent } from '@/personalization/behaviorConsent';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn(async () => ({})) }));
jest.mock('@/personalization/behaviorConsent', () => ({ loadBehaviorConsent: jest.fn(async () => false) }));
jest.mock('expo-crypto', () => ({ randomUUID: () => 'uuid-1' }));

import { sendAppEvent } from '@/analytics/appEvents';
import { IMPRESSION_CHECK_MS, useImpressionTracker, visibleFraction, type ImpressionScreen } from '@/analytics/impressions';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8') as string;

const WINDOW = Dimensions.get('window');
const flush = async () => { for (let i = 0; i < 6; i += 1) await Promise.resolve(); };
const impressionCalls = () => (apiRequest as jest.Mock).mock.calls.filter(([path, options]) => path === '/api/v1/events' && options?.body?.eventType === 'recommendation_impression');
/** 화면 위치를 알려 주는 가짜 카드. y 를 바꾸면 스크롤한 것과 같다. */
const fakeCard = (frame: { y: number; height?: number }) => ({
  measureInWindow: (callback: (x: number, y: number, width: number, height: number) => void) => callback(0, frame.y, 300, frame.height ?? 200),
}) as never;

beforeEach(() => {
  jest.useFakeTimers();
  (apiRequest as jest.Mock).mockClear();
  (loadBehaviorConsent as jest.Mock).mockResolvedValue(false);
});
afterEach(() => { jest.useRealTimers(); });

describe('창 안에 보인 비율', () => {
  it('다 들어오면 1, 반만 들어오면 0.5, 밖이면 0', () => {
    const window = { width: 400, height: 800 };
    expect(visibleFraction({ x: 0, y: 100, width: 400, height: 200 }, window)).toBe(1);
    expect(visibleFraction({ x: 0, y: 700, width: 400, height: 200 }, window)).toBe(0.5);
    expect(visibleFraction({ x: 0, y: 900, width: 400, height: 200 }, window)).toBe(0);
    expect(visibleFraction({ x: 300, y: 0, width: 200, height: 100 }, window)).toBe(0.5);
  });
});

describe('노출 재기', () => {
  const mount = (props: { accessToken?: string | null; sourceScreen?: ImpressionScreen; active?: boolean } = {}) =>
    renderHook((p: { accessToken: string | null; sourceScreen: ImpressionScreen; active: boolean }) => useImpressionTracker(p), {
      initialProps: { accessToken: 'tok', sourceScreen: 'TRIP_ITINERARY', active: true, ...props },
    });

  it('🔴 화면에 보이면 한 번 보낸다 — 본문은 {placeId, sourceScreen}, 추천 요청 번호를 싣는다', async () => {
    const { result } = mount();
    result.current.register('p1', 'req-1')?.(fakeCard({ y: 100 }));
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS); await flush(); });
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS * 3); await flush(); });
    const calls = impressionCalls();
    expect(calls).toHaveLength(1);
    expect(calls[0][1].body).toMatchObject({ eventType: 'recommendation_impression', requestId: 'req-1', payload: { placeId: 'p1', sourceScreen: 'TRIP_ITINERARY' } });
  });

  it('🔴 목록에 있어도 화면 밖이면 안 보낸다 — 내려서 보이면 그때 보낸다', async () => {
    const { result } = mount();
    let y = WINDOW.height + 500;
    result.current.register('p2', 'req-1')?.({ measureInWindow: (cb: (x: number, y: number, w: number, h: number) => void) => cb(0, y, 300, 200) } as never);
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS * 2); await flush(); });
    expect(impressionCalls()).toHaveLength(0);
    y = 50;
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS); await flush(); });
    expect(impressionCalls()).toHaveLength(1);
  });

  it('🔴 절반이 안 보이면 아직 아니다', async () => {
    const { result } = mount();
    result.current.register('p3', 'req-1')?.(fakeCard({ y: WINDOW.height - 60, height: 200 }));
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS * 2); await flush(); });
    expect(impressionCalls()).toHaveLength(0);
  });

  it('🔴 추천 요청 번호가 없는 곳(손으로 더한 곳)은 재지도 않는다', async () => {
    const { result } = mount();
    expect(result.current.register('p4', null)).toBeUndefined();
    expect(result.current.register('p4', undefined)).toBeUndefined();
  });

  it('로그인 안 했거나 그 목록이 안 보이는 판이면 안 보낸다', async () => {
    const guest = mount({ accessToken: null });
    guest.result.current.register('p5', 'req-1')?.(fakeCard({ y: 100 }));
    const hidden = mount({ active: false });
    hidden.result.current.register('p6', 'req-1')?.(fakeCard({ y: 100 }));
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS * 2); await flush(); });
    expect(impressionCalls()).toHaveLength(0);
  });

  it('🔴 화면 이름이 바뀌면(코스 고르기 → 확정한 일정) 다시 센다', async () => {
    const { result, rerender } = mount({ sourceScreen: 'TRIP_COURSES' });
    result.current.register('p7', 'req-1')?.(fakeCard({ y: 100 }));
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS); await flush(); });
    rerender({ accessToken: 'tok', sourceScreen: 'TRIP_ITINERARY', active: true });
    await act(async () => { jest.advanceTimersByTime(IMPRESSION_CHECK_MS); await flush(); });
    expect(impressionCalls().map((call) => call[1].body.payload.sourceScreen)).toEqual(['TRIP_COURSES', 'TRIP_ITINERARY']);
  });
});

describe('동의', () => {
  it('🔴 노출은 행동 동의가 꺼져 있어도 보낸다(추천 품질 통계)', async () => {
    sendAppEvent({ type: 'recommendation_impression', accessToken: 'tok', requestId: 'req-1', payload: { placeId: 'p1', sourceScreen: 'TRIP_ITINERARY' }, requiresConsent: false });
    await act(async () => { await flush(); });
    expect(impressionCalls()).toHaveLength(1);
  });

  it('🔴 다른 행동 기록은 여전히 동의가 있어야 한다', async () => {
    sendAppEvent({ type: 'place_view', accessToken: 'tok', payload: { placeId: 'p1', surface: 'place_detail' } });
    await act(async () => { await flush(); });
    expect((apiRequest as jest.Mock).mock.calls.filter(([path]) => path === '/api/v1/events')).toHaveLength(0);
  });
});

describe('화면이 카드를 잰다', () => {
  it('폰 여행 화면(펼친 일정 · 접었을 때 띠)과 넓은 화면 카드 격자', () => {
    const mobile = source('src/trip/page/TripPageMobile.tsx');
    const desktop = source('src/trip/page/TripPageDesktop.tsx');
    expect(mobile.match(/<ImpressionView key=\{item\.id\} tracker=\{impressions\}/g)).toHaveLength(2);
    expect(desktop.match(/<ImpressionView key=\{item\.id\} tracker=\{impressions\}/g)).toHaveLength(1);
    for (const page of [mobile, desktop]) expect(page).toContain("sourceScreen: confirmed ? 'TRIP_ITINERARY' : 'TRIP_COURSES'");
  });
});
