// 여행 카드를 누르면 무엇을 여나 — S15P21E201-1605.
//
// 🔴 이 시험이 지키는 것: 일정이 여럿이어도 「열 일정을 골라주세요」를 묻지 않는다. 서버가 알려 준 확정 일정
//    (currentItineraryId)을 바로 연다. 사용자: 「선택된 것만 보여 주면 되잖아. 왜 한 단계가 더 생겼지?」
import { QueryClient } from '@tanstack/react-query';

import { apiRequest } from '@/api/client';
import { invalidateTripLists, resolveTripItinerary } from '@/trip/trips';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const request = jest.mocked(apiRequest);

beforeEach(() => request.mockReset());

describe('여행 카드를 누르면 여는 일정', () => {
  it('🔴 서버가 확정 일정을 알려 주면 묻지도 부르지도 않고 그것을 연다', async () => {
    await expect(resolveTripItinerary({ tripId: 't1', currentItineraryId: 'it-b' }, 'token')).resolves.toEqual({ state: 'open', itineraryId: 'it-b' });
    expect(request).not.toHaveBeenCalled();
  });

  it('서버가 「일정 없음」(null)이라고 하면 없다', async () => {
    await expect(resolveTripItinerary({ tripId: 't1', currentItineraryId: null }, 'token')).resolves.toEqual({ state: 'none' });
    expect(request).not.toHaveBeenCalled();
  });

  it('🔴 옛 서버(칸 없음)면 일정 목록의 마지막 — 가장 최근에 만든 것 — 을 연다. 여럿이어도 묻지 않는다', async () => {
    request.mockResolvedValue({ tripId: 't1', role: 'OWNER', itineraries: [{ itineraryId: 'it-a', latestVersion: 3 }, { itineraryId: 'it-c', latestVersion: 1 }] });
    await expect(resolveTripItinerary({ tripId: 't1' }, 'token')).resolves.toEqual({ state: 'open', itineraryId: 'it-c' });
  });

  it('옛 서버에서 일정이 하나도 없으면 없다 · 못 불러오면 그 까닭을 돌려준다', async () => {
    request.mockResolvedValue({ tripId: 't1', role: 'OWNER', itineraries: [] });
    await expect(resolveTripItinerary({ tripId: 't1' }, 'token')).resolves.toEqual({ state: 'none' });
    request.mockRejectedValue(new Error('끊김'));
    await expect(resolveTripItinerary({ tripId: 't1' }, 'token')).resolves.toMatchObject({ state: 'error' });
  });
});

describe('코스를 고른 뒤 여행 목록 캐시', () => {
  it('🔴 여행 목록을 든 캐시(내 여행 · 홈 · 마이페이지)를 전부 낡은 것으로 — 돌아가 눌렀을 때 옛 일정을 열지 않게', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } });
    for (const key of [['trips', 'me'], ['home', 'trips'], ['me', 'trips', 'me'], ['home', 'stories', 'member']]) client.setQueryData(key, 'x');
    await invalidateTripLists(client);
    const stale = (key: unknown[]) => client.getQueryState(key)?.isInvalidated;
    expect([stale(['trips', 'me']), stale(['home', 'trips']), stale(['me', 'trips', 'me'])]).toEqual([true, true, true]);
    expect(stale(['home', 'stories', 'member'])).toBe(false);
  });
});
