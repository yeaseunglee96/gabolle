// 하트를 켜도 앱은 place_like 이벤트를 보내지 않는다 — S15P21E201-1486.
//
// 🔴 저장 API(PUT /me/saved-places)가 서버에서 PLACE_LIKE 를 적는 것이 정본이다. 앱이 또 보내면
//    하트 한 번에 두 건이 되어 취향 가중치가 부푼다. 누군가 「분석이 안 잡힌다」며 다시 넣으면
//    여기서 걸린다. 저장 API 호출 자체는 그대로여야 한다 — 그게 서버 기록을 만드는 길이다.
import { act, renderHook, waitFor } from '@testing-library/react-native';

const mockSend = jest.fn();
jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: (input: unknown) => mockSend(input) }));
jest.mock('@/discovery/savedPlaces', () => ({
  loadSavedPlaceIds: jest.fn(async () => []),
  setSavedPlace: jest.fn(async (placeId: string, saved: boolean) => ({ ids: saved ? [placeId] : [], sync: 'synced' })),
}));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

import { setSavedPlace } from '@/discovery/savedPlaces';

import { useSavedPlaces } from '../useSavedPlaces';

beforeEach(() => { mockSend.mockClear(); (setSavedPlace as jest.Mock).mockClear(); });

describe('useSavedPlaces — 하트와 이벤트', () => {
  it('🔴 하트를 켜면 저장 API 만 부르고, 앱은 place_like 를 보내지 않는다', async () => {
    const { result } = renderHook(() => useSavedPlaces('tok'));
    // 처음 목록 조회가 끝난 뒤에 누른다 — 조회 결과가 뒤늦게 덮어쓰면 하트가 아니라 시험이 틀린다.
    await act(async () => {});
    await act(async () => { result.current.toggle('p1'); });
    await waitFor(() => expect(setSavedPlace).toHaveBeenCalledWith('p1', true, 'tok'));
    expect(result.current.likedIds.has('p1')).toBe(true);
    expect(mockSend).not.toHaveBeenCalled();
  });

  it('해제도 마찬가지 — 이벤트 없음, 저장 API 로 false 만 간다', async () => {
    const { result } = renderHook(() => useSavedPlaces('tok'));
    // 처음 목록 조회가 끝난 뒤에 누른다 — 조회 결과가 뒤늦게 덮어쓰면 하트가 아니라 시험이 틀린다.
    await act(async () => {});
    await act(async () => { result.current.toggle('p1'); });
    await act(async () => { result.current.toggle('p1'); });
    await waitFor(() => expect(setSavedPlace).toHaveBeenLastCalledWith('p1', false, 'tok'));
    expect(result.current.likedIds.has('p1')).toBe(false);
    expect(mockSend).not.toHaveBeenCalled();
  });
});
