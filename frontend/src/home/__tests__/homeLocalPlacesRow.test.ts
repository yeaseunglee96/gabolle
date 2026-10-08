// S15P21E201-2024 — 홈의 로컬 탐색 줄은 「전통시장」이 아니라 「로컬 장소」 묶음으로 시작한다.
jest.mock('@/discovery/places', () => ({ getPlacesByFacet: jest.fn() }));
import { getPlacesByFacet } from '@/discovery/places';
import { HOME_FACET_ROWS, loadHomeLocalPlaces } from '@/home/useHomeData';

describe('홈 로컬 탐색 줄', () => {
  it('전통시장 줄 대신 로컬 장소 줄이 로컬 탐색 첫 칸으로 연결된다', () => {
    const local = HOME_FACET_ROWS.find((row) => row.key !== 'FESTIVAL')!;
    expect(local.key).toBe('LOCAL_PLACES');
    expect(local.ko).toBe('부산 로컬 둘러보기');
    expect(local.href).toBe('/explore');
    expect(HOME_FACET_ROWS.some((row) => row.ko.includes('전통시장'))).toBe(false);
  });

  it('여러 갈래를 섞어 채우고, 장소가 없는 갈래는 부르지 않는다', async () => {
    (getPlacesByFacet as jest.Mock).mockImplementation(async (_t: string, key: string) => [{ placeId: `${key}-1` }, { placeId: 'shared' }]);
    const places = await loadHomeLocalPlaces({ placeFeatureType: 'LOCAL', keys: [
      { featureKey: 'TRADITIONAL_MARKET', placeCount: 3, labelKo: '전통시장' },
      { featureKey: 'NIGHT_VIEW', placeCount: 2, labelKo: '야경' },
      { featureKey: 'WALK', placeCount: 0, labelKo: '산책' },
    ] });
    expect(places.map((p) => p.placeId)).toEqual(['TRADITIONAL_MARKET-1', 'NIGHT_VIEW-1', 'shared']);
    expect((getPlacesByFacet as jest.Mock).mock.calls.map((c) => c[1])).toEqual(['TRADITIONAL_MARKET', 'NIGHT_VIEW']);
  });
});

describe('로컬 장소 묶음에서 축제는 뺀다', () => {
  it('장소 검색은 축제 날짜를 몰라 끝난 축제가 섞이므로 FESTIVAL 갈래는 부르지 않는다', async () => {
    (getPlacesByFacet as jest.Mock).mockReset().mockResolvedValue([]);
    await loadHomeLocalPlaces({ placeFeatureType: 'LOCAL', keys: [
      { featureKey: 'FESTIVAL', placeCount: 70, labelKo: '축제' },
      { featureKey: 'NATURE', placeCount: 5, labelKo: '자연' },
    ] });
    expect((getPlacesByFacet as jest.Mock).mock.calls.map((c) => c[1])).toEqual(['NATURE']);
  });
});
