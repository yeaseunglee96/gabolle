// 「로컬 장소」 묶음은 맛집을 섞지 않는다 (S15P21E201-2051).
//
// 🔴 맛집이 갈래 목록에 더해지면 묶음이 «보이는 모든 갈래»를 섞어 부르는 탓에 식당 666곳이 묶음 첫 화면을 차지한다. 게다가 근처
//    조회(/places/nearby)는 맛집 낱말을 몰라 400 으로 거절한다 — 내 근처로 바꾸면 묶음 전체가 «불러오지 못했어요» 가 된다.
//    타입도 다른 시험도 이걸 못 잡는다(갈래 목록이 문자열 배열이다).
import { getNearbyPlaces, type LocalFacetEntry } from '@/discovery/localExplore';
import { getAllLocalPlaces, getNearbyLocalPlaces } from '@/discovery/localPlaces';
import { getPlacesByFacet } from '@/discovery/places';

jest.mock('@/discovery/places', () => ({ ...jest.requireActual('@/discovery/places'), getPlacesByFacet: jest.fn() }));
jest.mock('@/discovery/localExplore', () => ({ ...jest.requireActual('@/discovery/localExplore'), getNearbyPlaces: jest.fn() }));

const mockedByFacet = getPlacesByFacet as jest.MockedFunction<typeof getPlacesByFacet>;
const mockedNearby = getNearbyPlaces as jest.MockedFunction<typeof getNearbyPlaces>;

const facet = (featureKey: string): LocalFacetEntry => ({ featureKey, placeCount: 5, labelKo: featureKey, placeFeatureType: 'INTEREST_TAG' });
const FACETS = [facet('WALK'), facet('NATURE'), facet('FESTIVAL'), facet('LOCAL_RESTAURANT')];

beforeEach(() => {
  mockedByFacet.mockReset();
  mockedNearby.mockReset();
  mockedByFacet.mockResolvedValue([]);
  mockedNearby.mockResolvedValue({ state: 'success', items: [], requestedRadiusM: 1000, effectiveRadiusM: 1000, radiusExpanded: false, expansionSteps: 0, scanTruncated: false, limit: 6, purposeApplied: false, facetKeyApplied: true });
});

describe('로컬 장소 묶음', () => {
  it('🔴 부산 전체 — 갈래별 조회에 맛집이 없다(축제와 같다)', async () => {
    await getAllLocalPlaces(FACETS);

    expect(mockedByFacet.mock.calls.map((call) => call[1]).sort()).toEqual(['NATURE', 'WALK']);
  });

  it('🔴 내 근처 — 근처 조회가 맛집 낱말로 불리지 않는다', async () => {
    await getNearbyLocalPlaces(FACETS, { lat: 35.1, lng: 129.0 });

    expect(mockedNearby.mock.calls.map((call) => call[0].facetKey).sort()).toEqual(['NATURE', 'WALK']);
  });
});
