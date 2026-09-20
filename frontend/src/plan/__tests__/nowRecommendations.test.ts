import { getNearbyPlaces } from '@/discovery/localExplore';
import {
  buildNearbyNowResult,
  nearbyRadiusMetres,
  requestNowRecommendations,
  walkMinutes,
} from '@/plan/nowRecommendations';
import { ApiClientError } from '@/api/client';

jest.mock('@/discovery/localExplore', () => ({ getNearbyPlaces: jest.fn() }));
jest.mock('@/api/client', () => {
  const actual = jest.requireActual('@/api/client');
  return { ...actual, apiRequest: jest.fn() };
});

const { apiRequest } = jest.requireMock('@/api/client') as { apiRequest: jest.Mock };
const nearby = getNearbyPlaces as unknown as jest.Mock;

/**
 * 🔴 **「지금 갈 곳」이 장소를 지어내던 것** — S15P21E201-1345.
 *
 * 추천 API(`POST /api/v1/recommendations/now`)는 만들어진 적이 없고 운영에서 404 다.
 * 그동안 이 화면은 404 를 받으면 해운대·광안리·감천문화마을을 **지어내서** 그렸고,
 * 「이동 15분」·「영업 종료까지 45분」은 **남은 시간을 나눠 만든 숫자**였다.
 * 아무것도 재지 않은 값을 사람이 믿고 움직인다.
 *
 * <p>이제는 `GET /api/v1/places/nearby` 의 **진짜 장소·진짜 거리**로 답한다.
 * 이 시험이 지키는 것은 「무엇을 보여주는가」가 아니라 **「무엇을 지어내지 않는가」**다.
 */
describe('지금 갈 곳 — 추천 API 가 없을 때', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    apiRequest.mockRejectedValue(new ApiClientError('없음', 'NOT_FOUND', 404));
  });

  const place = (placeId: string, nameKo: string, distanceM: number) => ({
    placeId, nameKo, nameEn: null, category: 'SEA_BEACH', address: '부산', lat: 35.1, lng: 129.1, distanceM,
  });

  const input = { latitude: 35.1587, longitude: 129.1604, manualLocation: null, remainingMinutes: 60 };

  it('🔴 지어낸 장소를 안 쓴다 — 서버가 준 장소만 나온다', async () => {
    nearby.mockResolvedValue({ state: 'success', items: [place('real-1', '해운대해수욕장', 53)] });
    const result = await requestNowRecommendations(input, null);

    expect(result.candidates.map((c) => c.placeId)).toEqual(['real-1']);
    // 예전에 지어내던 가짜 식별자들
    for (const fake of ['haeundae', 'gwangalli', 'gamcheon']) {
      expect(result.candidates.some((c) => c.placeId === fake)).toBe(false);
    }
  });

  it('🔴 영업 종료 시각을 지어내지 않는다 — 자료가 없으면 미확인이다', async () => {
    nearby.mockResolvedValue({ state: 'success', items: [place('real-1', '해운대해수욕장', 53)] });
    const result = await requestNowRecommendations(input, null);
    expect(result.candidates[0].minutesUntilClose).toBeNull();
  });

  it('이동 시간은 «진짜 거리»에서 나온다', async () => {
    nearby.mockResolvedValue({ state: 'success', items: [place('real-1', '가까운 곳', 670)] });
    const result = await requestNowRecommendations(input, null);
    expect(result.candidates[0].travelMinutes).toBe(10);   // 670m ÷ 67m/분
    expect(result.candidates[0].dataStatus).toBe('ESTIMATED');
  });

  it('남은 시간 안에 못 걸어가는 곳은 뺀다', async () => {
    nearby.mockResolvedValue({
      state: 'success',
      items: [place('near', '가까운 곳', 670), place('far', '먼 곳', 67000)],
    });
    const result = await requestNowRecommendations({ ...input, remainingMinutes: 30 }, null);
    expect(result.candidates.map((c) => c.placeId)).toEqual(['near']);
  });

  it('갈 만한 곳이 없으면 빈 상태로 말한다 — 채워 넣지 않는다', async () => {
    nearby.mockResolvedValue({ state: 'success', items: [place('far', '먼 곳', 67000)] });
    const result = await requestNowRecommendations(input, null);
    expect(result.state).toBe('empty');
    expect(result.candidates).toEqual([]);
  });

  it('🔴 좌표가 없으면 아무것도 안 만들고 위치가 필요하다고 말한다', async () => {
    const result = await requestNowRecommendations(
      { latitude: null, longitude: null, manualLocation: '해운대역', remainingMinutes: 60 }, null);
    expect(result.state).toBe('unavailable');
    expect(result.candidates).toEqual([]);
    expect(nearby).not.toHaveBeenCalled();
  });

  it('주변 조회까지 실패하면 실패라고 말한다', async () => {
    nearby.mockResolvedValue({ state: 'offline', message: '연결이 끊겼어요' });
    const result = await requestNowRecommendations(input, null);
    expect(result.state).toBe('offline');
    expect(result.candidates).toEqual([]);
  });

  it('남은 시간에 맞춰 반경을 잡는다 — 절반만 이동에 쓴다', async () => {
    expect(nearbyRadiusMetres(60)).toBe(2010);      // 60분 × 0.5 × 67m
    expect(nearbyRadiusMetres(30)).toBe(1005);
    expect(nearbyRadiusMetres(1)).toBe(500);        // 최소
    expect(nearbyRadiusMetres(100000)).toBe(8000);  // 최대
  });

  it('walkMinutes 는 0분을 내지 않는다 — 「이동 0분」은 말이 안 된다', () => {
    expect(walkMinutes(5)).toBe(1);
    expect(walkMinutes(0)).toBe(1);
  });

  it('추천 API 가 살아나면 그 결과를 그대로 쓴다 — 주변 조회를 안 부른다', async () => {
    apiRequest.mockResolvedValue({
      candidates: [{ placeId: 'from-server', name: '서버가 준 곳', travelMinutes: 7, minutesUntilClose: 40, reasonCodes: ['MATCHES_TASTE'], dataStatus: 'VERIFIED' }],
      weatherApplied: true,
    });
    const result = await requestNowRecommendations(input, 'token');
    expect(result.state).toBe('success');
    expect(result.candidates[0].placeId).toBe('from-server');
    expect(nearby).not.toHaveBeenCalled();
  });

  it('buildNearbyNowResult 는 「가까워요」 말고 다른 이유를 붙이지 않는다', async () => {
    nearby.mockResolvedValue({ state: 'success', items: [place('real-1', '가까운 곳', 100)] });
    const result = await buildNearbyNowResult(input);
    // 인기·날씨·취향은 이 응답이 말해 주지 않는다 — 붙이면 지어내는 것이다
    expect(result.candidates[0].reasonCodes).toEqual(['SHORT_TRAVEL']);
  });
});
