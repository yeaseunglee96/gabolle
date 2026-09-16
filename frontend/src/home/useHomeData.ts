// 데스크톱 홈이 쓰는 값들을 한곳에서 읽는다 (S15P21E201-970).
//
// 🔴 여기 있는 것은 **전부 실제 API 가 주는 값**이다. 인계 문서의 데이터 표에는 지금 서버에
// 없는 것이 섞여 있었다(장소 태그 · 취향×태그 매핑 · 여행 제목 · 장소 사진). 그것들은 이 훅에
// 넣지 않는다 — 없는 값을 채워 주는 자리를 만들면 화면이 거짓말을 시작한다.
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { getFacets, getNearbyPlaces, type FacetGroup, type FacetKeyEntry, type NearbyPlaceItem } from '@/discovery/localExplore';
import { getPlacesByFacet } from '@/discovery/places';
import { loadFeed, type StoryDto } from '@/social/stories';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { loadWeatherForecast, type DailyForecastDto } from '@/trip/weather';

// weather.ts 가 같은 값을 모듈 안에 두고 내보내지 않아 여기에 다시 적는다. 부산 한 곳만
// 보는 화면이라 좌표가 화면마다 달라질 일이 없다.
const BUSAN = { lat: 35.1796, lng: 129.0756 };

const HERO_STORY_COUNT = 3;
const PLACE_PICK_COUNT = 4;
// 후보를 넉넉히 받아 category 로 골고루 뽑을 여지를 둔다 — 부산 중심 5km 안이 특정
// 갈래(식당 등)로 쏠려 있으면 그냥 가까운 순으로만 넷을 자를 때 넷 다 같은 갈래로
// 몰릴 수 있다(사용자 실사용 리포트, 2026-09-16).
const PLACE_CANDIDATE_POOL = 16;
const FACET_CHIP_COUNT = 6;

function today() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * 히어로에 올릴 기록 셋을 고른다. **사진이 있는 글을 먼저** 쓴다 — 카드가 사진 자리를
 * 가지고 있어서, 사진 없는 글만 걸리면 회색 칸 셋이 된다.
 *
 * 서버에 "사진 있는 것만" 거르는 조회가 없어서 넉넉히 받아 여기서 고른다.
 */
function pickHeroStories(items: StoryDto[]): StoryDto[] {
  const withImage = items.filter((item) => item.images.length > 0);
  const rest = items.filter((item) => item.images.length === 0);
  return [...withImage, ...rest].slice(0, HERO_STORY_COUNT);
}

/**
 * 가까운 순으로 받은 후보 중에서 갈래(`category`)가 겹치지 않게 최대 `count`개를 고른다.
 * 넷 다 같은 갈래(예: 식당)로 몰리는 것을 막는다 — 부산 중심 5km 안이 그 갈래로 쏠려
 * 있으면 실제로 그렇게 된다(사용자 실사용 리포트, 2026-09-16).
 *
 * 🔴 `category` 값의 정확한 목록은 모른다(places.ts의 isFoodPlace와 같은 사정) — 그래서
 * 정해진 갈래 이름을 코드에 나열하지 않고, "이미 고른 갈래와 문자열이 같은가"만 본다.
 * 서로 다른 곳이 우연히 같은 category 문자열을 가지면 같은 갈래로 취급되는 정도가
 * 이 방식의 한계다. 가까운 순서 자체는 그대로 지킨다 — 갈래 안에서 순서를 바꾸지 않는다.
 */
function diversifyByCategory<T extends { category: string | null }>(items: T[], count: number): T[] {
  const seenCategories = new Set<string>();
  const picked: T[] = [];
  const leftover: T[] = [];
  for (const item of items) {
    if (picked.length >= count) { leftover.push(item); continue; }
    if (item.category && seenCategories.has(item.category)) { leftover.push(item); continue; }
    if (item.category) seenCategories.add(item.category);
    picked.push(item);
  }
  // 갈래 수가 count 보다 적으면(예: 갈래 둘뿐) 자리가 남는다 — 가까운 순서대로 채운다.
  for (const item of leftover) {
    if (picked.length >= count) break;
    picked.push(item);
  }
  return picked;
}

/**
 * 칩으로 쓸 갈래를 고른다.
 *
 * 🔴 **`EXPLORE` 묶음만 쓴다.** 운영에서 실제 응답을 확인하니 `labelKo` 가 오는 것은 이 묶음뿐이고
 * `CATEGORY`·`FOOD_PREFERENCE` 는 전부 null 이다. 그냥 전부 펼쳐 앞에서 여섯을 자르면 CATEGORY 가
 * 먼저 걸려서 **글자 없는 칩 여섯 개**가 된다.
 *
 * 장소가 0곳인 갈래도 뺀다 — 눌러도 빈 화면이 나온다(explore.tsx 가 같은 규칙을 쓴다).
 */
function pickChips(facets: FacetGroup[]): FacetKeyEntry[] {
  const explore = facets.find((group) => group.userInputCode === 'EXPLORE');
  if (!explore) return [];
  return explore.keys.filter((key) => key.placeCount > 0 && key.labelKo).slice(0, FACET_CHIP_COUNT);
}

/** 예정·진행 중인 여행 하나. 끝난 여행은 홈에 올리지 않는다. */
function pickActiveTrip(trips: TripSummaryDto[]): TripSummaryDto | null {
  const active = trips.filter((trip) => trip.status !== 'COMPLETED');
  if (!active.length) return null;
  // 진행 중이 있으면 그것이 먼저다. 없으면 가장 먼저 떠나는 것.
  const inProgress = active.find((trip) => trip.status === 'IN_PROGRESS');
  if (inProgress) return inProgress;
  return [...active].sort((a, b) => (a.startDate ?? '9999').localeCompare(b.startDate ?? '9999'))[0];
}

export type HomeData = {
  signedIn: boolean;
  stories: StoryDto[] | null;
  chips: FacetKeyEntry[];
  weather: DailyForecastDto | null;
  places: HomePlaceItem[];
  trip: TripSummaryDto | null;
  tripsLoaded: boolean;
};

export type HomePlaceItem = Omit<NearbyPlaceItem, 'distanceM'> & { distanceM?: number };

/**
 * @param enabled 데스크톱 홈에서만 켠다. 훅은 조건 없이 불러야 하는데(React 규칙) 폰 랜딩은
 *   이 값을 하나도 안 그리므로, 끄지 않으면 폰에서 볼 때마다 요청 다섯 개가 헛돈다.
 */
export function useHomeData(enabled = true): HomeData {
  const { accessToken } = useAuth();

  // 🔴 로그인했을 때만 부른다. 운영에서 실제로 불러 보니 **익명 출입증으로는
  //    `GET /api/v1/stories` 가 401** 이다(날씨도 같다). 익명 인증이 통과하는 것과 그 경로가
  //    익명을 허용하는 것은 다르다. 안 부르면 될 것을 불러서 401 을 쌓지 않는다.
  const signedIn = Boolean(accessToken);
  const storiesQuery = useQuery({
    queryKey: ['home', 'stories'],
    enabled: enabled && signedIn,
    queryFn: () => loadFeed({ scope: 'ALL', limit: 12, accessToken }),
  });

  const facetsQuery = useQuery({ queryKey: ['home', 'facets'], enabled, queryFn: () => getFacets() });

  const weatherQuery = useQuery({
    // 날씨도 스토리와 같다 — 익명으로는 401 이다.
    queryKey: ['home', 'weather', today()],
    enabled: enabled && signedIn,
    queryFn: () => loadWeatherForecast(today(), accessToken),
  });

  const placesQuery = useQuery({
    queryKey: ['home', 'places'],
    enabled,
    queryFn: async (): Promise<HomePlaceItem[]> => {
      const nearby = await getNearbyPlaces({ ...BUSAN, limit: PLACE_CANDIDATE_POOL });
      if (nearby.state === 'success' && nearby.items.length) return diversifyByCategory(nearby.items, PLACE_PICK_COUNT);

      // 부산 중심 5km 안이 비면 섹션을 숨기지 않고, 실제로 데이터가 있는 로컬 갈래 하나를
      // 부산 전체에서 조회한다. 홈 제목은 거리 순위나 대표성을 약속하지 않는 「부산 둘러보기」다.
      const facets = await getFacets();
      if (facets.state !== 'success') return [];
      const group = facets.facets.find((item) => item.userInputCode === 'EXPLORE');
      const key = group?.keys.find((item) => item.placeCount > 0 && item.labelKo);
      if (!group || !key) return [];
      return (await getPlacesByFacet(group.placeFeatureType, key.featureKey, PLACE_PICK_COUNT)).slice(0, PLACE_PICK_COUNT);
    },
  });

  const tripsQuery = useQuery({
    queryKey: ['home', 'trips'],
    enabled: enabled && signedIn,
    queryFn: () => loadTrips(accessToken),
  });

  return {
    // 🔴 null 은 **아직 불러오는 중**이라는 뜻만 가져야 한다. 실패까지 null 로 묶으면 화면이
    //    스켈레톤을 영원히 그린다 — 서버가 죽었을 때 실제로 그랬다. 실패는 빈 배열로 내려
    //    「아직 기록이 없어요」 자리로 보낸다.
    signedIn,
    stories: !signedIn
      ? []
      : storiesQuery.isPending
        ? null
        : storiesQuery.data?.state === 'success' ? pickHeroStories(storiesQuery.data.items) : [],
    chips: facetsQuery.data?.state === 'success' ? pickChips(facetsQuery.data.facets) : [],
    weather: weatherQuery.data?.state === 'success' ? weatherQuery.data.forecast : null,
    places: placesQuery.data ?? [],
    trip: tripsQuery.data?.state === 'success' ? pickActiveTrip(tripsQuery.data.trips) : null,
    tripsLoaded: tripsQuery.data?.state === 'success',
  };
}
