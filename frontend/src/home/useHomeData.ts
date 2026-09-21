// 데스크톱 홈이 쓰는 값들을 한곳에서 읽는다.
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { getFacets } from '@/discovery/localExplore';
import { getPlacesByFacet, type PlaceSearchItem } from '@/discovery/places';
import { loadFeed, type StoryDto } from '@/social/stories';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { loadWeatherForecast, type DailyForecastDto } from '@/trip/weather';


// 줄 배치로 바뀌면서 한 줄에 일곱 장이 보인다 — 셋이면 줄이 반도 안 찬다.
const HERO_STORY_COUNT = 8;

/**
 * 홈의 로컬 탐색 줄 둘. 로컬 탐색 화면의 「부산 전체」와 **같은 조회**를 쓴다 —
 * 홈에서 본 것과 화면을 열어서 본 것이 다르면 사용자가 둘 중 어느 쪽을 믿을지 모른다.
 */
const HOME_FACET_ROWS = [
  { key: 'FESTIVAL', ko: '지금 열리는 부산 축제', en: 'Festivals happening now' },
  { key: 'TRADITIONAL_MARKET', ko: '부산 전통시장 둘러보기', en: 'Browse traditional markets' },
] as const;

/** 한 줄에 실을 장소 수. 일곱 장이 보이고 캐러셀로 더 넘긴다. */
const FACET_ROW_COUNT = 10;


function today() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * 히어로에 올릴 기록 셋을 고른다. 사진이 있는 글을 먼저 쓴다 — 카드가 사진 자리를
 * 가지고 있어서, 사진 없는 글만 걸리면 회색 칸 셋이 된다.
 */
export function pickHeroStories(items: StoryDto[]): StoryDto[] {
  const withImage = items.filter((item) => item.images.length > 0);
  const rest = items.filter((item) => item.images.length === 0);
  return [...withImage, ...rest].slice(0, HERO_STORY_COUNT);
}



/**
 * 예정·진행 중인 여행 하나. 끝난 여행은 홈에 올리지 않는다.
 *
 * 마이페이지의 「내 여행」 칸도 같은 것을 쓴다 — 두 화면이 다른 여행을 가리키면
 * 어느 쪽이 맞는지 눌러 봐야만 안다.
 */
export function pickActiveTrip(trips: TripSummaryDto[]): TripSummaryDto | null {
  const active = trips.filter((trip) => trip.status !== 'COMPLETED');
  if (!active.length) return null;
  // 진행 중이 있으면 그것이 먼저다. 없으면 가장 먼저 떠나는 것.
  const inProgress = active.find((trip) => trip.status === 'IN_PROGRESS');
  if (inProgress) return inProgress;
  return [...active].sort((a, b) => (a.startDate ?? '9999').localeCompare(b.startDate ?? '9999'))[0];
}

/** 홈의 로컬 탐색 줄 하나. `places` 가 `null` 이면 아직 불러오는 중이다. */
export type HomeFacetRow = {
  facetKey: string;
  titleKo: string;
  titleEn: string;
  places: PlaceSearchItem[] | null;
};

export type HomeData = {
  signedIn: boolean;
  stories: StoryDto[] | null;
  weather: DailyForecastDto | null;
  facetRows: HomeFacetRow[];
  trip: TripSummaryDto | null;
  tripsLoaded: boolean;
};


/**
 * 날씨만 따로 읽는다.
 *
 * <p>상단 바(TopNav)는 루트 레이아웃에서 그려져서 홈 화면이 값을 건네줄 수 없다.
 * 그렇다고 상단 바가 홈 데이터를 통째로 읽으면 기록·축제·여행까지 같이 불려서
 * 홈이 아닌 화면에서도 요청이 헛돈다.
 *
 * <p>질의 열쇠가 useHomeData 의 것과 «같다». 그래서 홈에서 둘 다 불려도 서버에는
 * 한 번만 나간다 — 캐시가 같은 열쇠를 하나로 묶는다.
 */
export function useHomeWeather(enabled = true): DailyForecastDto | null {
  const { accessToken } = useAuth();
  const weatherQuery = useQuery({
    // 날씨도 스토리와 같다 — 익명으로는 401 이다.
    queryKey: ['home', 'weather', today()],
    enabled: enabled && Boolean(accessToken),
    queryFn: () => loadWeatherForecast(today(), accessToken),
  });
  return weatherQuery.data?.state === 'success' ? weatherQuery.data.forecast : null;
}

/**
 * @param enabled 데스크톱 홈에서만 켠다. 훅은 조건 없이 불러야 하는데(React 규칙) 폰 랜딩은
 * 이 값을 하나도 안 그리므로, 끄지 않으면 폰에서 볼 때마다 요청 다섯 개가 헛돈다.
 */
export function useHomeData(enabled = true): HomeData {
  const { accessToken } = useAuth();

  // ·995로 목록과 상세 모두 익명 출입증에 열렸다.
  // 회원 전환 전후에 공개 범위가 다르므로 캐시는 분리한다.
  const signedIn = Boolean(accessToken);
  const storiesQuery = useQuery({
    queryKey: ['home', 'stories', signedIn ? 'member' : 'guest'],
    enabled,
    queryFn: () => loadFeed({ scope: 'ALL', limit: 12, accessToken }),
  });

  const facetsQuery = useQuery({ queryKey: ['home', 'facets'], enabled, queryFn: () => getFacets() });

  const weather = useHomeWeather(enabled);

  /**
   * 갈래 줄이 쓸 조회 종류(`placeFeatureType`)는 서버가 정한다 — 코드에 박지 않는다.
   * 로컬 탐색 화면도 같은 값을 `getFacets()` 에서 받아 쓴다.
   */
  const exploreGroup = facetsQuery.data?.state === 'success'
    ? facetsQuery.data.facets.find((item) => item.userInputCode === 'EXPLORE')
    : undefined;

  const facetRowsQuery = useQuery({
    queryKey: ['home', 'facetRows', exploreGroup?.placeFeatureType ?? ''],
    // 조회 종류를 모르면 부르지 않는다. 짐작한 값으로 부르면 빈 줄이 나오는데,
    // 그건 「그 갈래에 장소가 없다」와 화면에서 구분이 안 된다.
    enabled: enabled && Boolean(exploreGroup),
    queryFn: async (): Promise<Record<string, PlaceSearchItem[]>> => {
      const type = exploreGroup!.placeFeatureType;
      const lists = await Promise.all(
        // 한 줄이 비어도 그 줄만 빈다 — 다른 줄까지 같이 죽이지 않는다.
        HOME_FACET_ROWS.map((row) => getPlacesByFacet(type, row.key, FACET_ROW_COUNT).catch(() => [])),
      );
      return Object.fromEntries(HOME_FACET_ROWS.map((row, index) => [row.key, lists[index]]));
    },
  });

  const tripsQuery = useQuery({
    queryKey: ['home', 'trips'],
    enabled: enabled && signedIn,
    queryFn: () => loadTrips(accessToken),
  });

  return {
    // null 은 아직 불러오는 중이라는 뜻만 가져야 한다. 실패까지 null 로 묶으면 화면이
    // 스켈레톤을 영원히 그린다 — 서버가 죽었을 때 실제로 그랬다. 실패는 빈 배열로 내려
    // 「아직 기록이 없어요」 자리로 보낸다.
    signedIn,
    stories: storiesQuery.isPending
        ? null
        : storiesQuery.data?.state === 'success' ? pickHeroStories(storiesQuery.data.items) : [],
    weather,
    // 여기서도 null 은 「아직 불러오는 중」만 뜻한다. 실패는 빈 배열로 내려 「없어요」 자리로 보낸다.
    facetRows: HOME_FACET_ROWS.map((row) => ({
      facetKey: row.key,
      titleKo: row.ko,
      titleEn: row.en,
      places: facetRowsQuery.data ? facetRowsQuery.data[row.key] ?? [] : facetRowsQuery.isPending ? null : [],
    })),
    trip: tripsQuery.data?.state === 'success' ? pickActiveTrip(tripsQuery.data.trips) : null,
    tripsLoaded: tripsQuery.data?.state === 'success',
  };
}
