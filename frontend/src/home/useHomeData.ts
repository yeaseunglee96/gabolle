// 데스크톱 홈이 쓰는 값들을 한곳에서 읽는다.
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { festivalDisplayTitle, getFestivals, type Festival } from '@/discovery/festivals';
import { getFacets } from '@/discovery/localExplore';
import { getPlacesByFacet, type PlaceSearchItem } from '@/discovery/places';
import { loadFeed, type StoryDto } from '@/social/stories';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { loadWeatherForecast, type DailyForecastDto } from '@/trip/weather';


// 줄 배치로 바뀌면서 한 줄에 일곱 장이 보인다 — 셋이면 줄이 반도 안 찬다.
const HERO_STORY_COUNT = 8;

/**
 * 홈의 로컬 탐색 줄 둘. 전통시장은 로컬 탐색 화면의 「부산 전체」와 **같은 조회**를 쓴다 —
 * 홈에서 본 것과 화면을 열어서 본 것이 다르면 사용자가 둘 중 어느 쪽을 믿을지 모른다.
 *
 * 🔴 축제는 장소 검색(FESTIVAL 갈래)이 아니라 날짜를 아는 축제 조회다(S15P21E201-1594). 장소 검색은 날짜를 몰라서
 *    「지금 열리는」 줄에 2025년에 끝난 축제만 나왔다(운영 place_event_period 14건 전부 2025년).
 *    「전체 보기」도 날짜로 거르는 축제 화면(/festivals)으로 간다 — 줄과 화면이 같은 것을 말하게.
 */
const HOME_FACET_ROWS = [
  { key: 'FESTIVAL', ko: '지금 열리는 부산 축제', en: 'Festivals happening now', href: '/festivals' },
  { key: 'TRADITIONAL_MARKET', ko: '부산 전통시장 둘러보기', en: 'Browse traditional markets', href: '/explore?facet=TRADITIONAL_MARKET' },
] as const;

/** 「지금 열리는」 축제를 묻는 기간 — 오늘부터 60일. */
export const FESTIVAL_WINDOW_DAYS = 60;

/** YYYY-MM-DD, 이 기기의 날짜로. UTC 로 세면 한국 아침 9시 전에는 어제가 된다. */
function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** 축제 조회 기간 — 오늘 ~ 오늘+60일. */
export function festivalWindow(now: Date = new Date()): { startDate: string; endDate: string } {
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + FESTIVAL_WINDOW_DAYS);
  return { startDate: localDateKey(now), endDate: localDateKey(end) };
}

/** 홈 카드가 쓰는 칸만 — 이름·주소·사진. 장소 검색 결과도 축제도 이 모양으로 그린다. */
export type HomeCardPlace = Pick<PlaceSearchItem, 'placeId' | 'nameKo' | 'address' | 'photoUrl' | 'photoSource' | 'photoSubject' | 'photoLicense'>;

/** 축제를 홈 카드로. 같은 장소가 두 번(기간 둘) 오면 앞의 것만 — 한 줄에 같은 카드가 둘 서지 않게. */
export function festivalCards(festivals: Festival[]): HomeCardPlace[] {
  const seen = new Set<string>();
  return festivals.flatMap((festival) => {
    if (seen.has(festival.placeId)) return [];
    seen.add(festival.placeId);
    return [{ placeId: festival.placeId, nameKo: festivalDisplayTitle(festival), address: festival.address, photoUrl: festival.photoUrl, photoSource: festival.photoSource, photoSubject: festival.photoSubject, photoLicense: festival.photoLicense }];
  });
}

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
 *
 * 🔴 날짜로 고른다(S15P21E201-1594). 전에는 서버 status 만 봐서, 날짜가 지난 9/19 여행이 서버에서 아직
 *    COMPLETED 가 아니라는 이유로 오늘 여행보다 먼저 나왔다(서버 status 는 배치로 바뀌어 늦다).
 *    오늘이 기간 안인 여행이 먼저, 없으면 가장 먼저 떠나는 예정 여행. 끝난 날짜의 여행은 뺀다.
 *    날짜를 모르는 여행은 끝났는지 알 수 없으니 빼지 않고, 날짜 있는 예정 여행 뒤에 둔다.
 */
export function pickActiveTrip(trips: TripSummaryDto[], now: Date = new Date()): TripSummaryDto | null {
  const today = localDateKey(now);
  const lastDay = (trip: TripSummaryDto) => trip.endDate || trip.startDate;
  const byStart = (a: TripSummaryDto, b: TripSummaryDto) => (a.startDate ?? '9999').localeCompare(b.startDate ?? '9999');
  const active = trips.filter((trip) => trip.status !== 'COMPLETED' && !(lastDay(trip) && (lastDay(trip) as string) < today));
  const ongoing = active.filter((trip) => trip.startDate && trip.startDate <= today && (lastDay(trip) as string) >= today);
  if (ongoing.length) return [...ongoing].sort(byStart)[0];
  return [...active].sort(byStart)[0] ?? null;
}

/** 홈의 로컬 탐색 줄 하나. `places` 가 `null` 이면 아직 불러오는 중이다. */
export type HomeFacetRow = {
  facetKey: string;
  titleKo: string;
  titleEn: string;
  /** 「전체 보기」가 여는 곳. */
  href: string;
  places: HomeCardPlace[] | null;
};

export type HomeData = {
  signedIn: boolean;
  stories: StoryDto[] | null;
  weather: DailyForecastDto | null;
  facetRows: HomeFacetRow[];
  trip: TripSummaryDto | null;
  tripsLoaded: boolean;
  /** 받은 여행 목록 전부. 아직이거나 실패면 null — 종 점이 목록을 다시 부르지 않고 이것을 쓴다(S15P21E201-1686). */
  trips: TripSummaryDto[] | null;
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
  const { accessToken, ready } = useAuth();

  // ·995로 목록과 상세 모두 익명 출입증에 열렸다.
  // 회원 전환 전후에 공개 범위가 다르므로 캐시는 분리한다.
  const signedIn = Boolean(accessToken);
  const storiesQuery = useQuery({
    queryKey: ['home', 'stories', signedIn ? 'member' : 'guest'],
    // 🔴 로그인 복원이 끝난 뒤에(S15P21E201-1686). 복원 중에는 열쇠가 아직 없어 손님 몫을 한 번 부르고,
    //    복원이 끝나면 회원 몫을 또 불렀다 — 로그인한 사람은 홈을 열 때마다 같은 글 목록을 두 번 받았다.
    enabled: enabled && ready,
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
    queryFn: async (): Promise<Record<string, HomeCardPlace[]>> => {
      const type = exploreGroup!.placeFeatureType;
      const rows = HOME_FACET_ROWS.filter((row) => row.key !== 'FESTIVAL');
      const lists = await Promise.all(
        // 한 줄이 비어도 그 줄만 빈다 — 다른 줄까지 같이 죽이지 않는다.
        rows.map((row) => getPlacesByFacet(type, row.key, FACET_ROW_COUNT).catch(() => [])),
      );
      return Object.fromEntries(rows.map((row, index) => [row.key, lists[index]]));
    },
  });

  // 축제 — 오늘부터 60일 안에 열리는 것. 없으면 빈 배열이고 줄이 접힌다(PlaceRow). 실패도 빈 줄로 — 다른 줄은 산다.
  const window = festivalWindow();
  const festivalsQuery = useQuery({
    queryKey: ['home', 'festivals', window.startDate],
    enabled,
    queryFn: () => getFestivals(window.startDate, window.endDate).then((items) => festivalCards(items).slice(0, FACET_ROW_COUNT)).catch(() => []),
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
      href: row.href,
      places: row.key === 'FESTIVAL'
        ? festivalsQuery.data ?? (festivalsQuery.isPending ? null : [])
        : facetRowsQuery.data ? facetRowsQuery.data[row.key] ?? [] : facetRowsQuery.isPending ? null : [],
    })),
    trip: tripsQuery.data?.state === 'success' ? pickActiveTrip(tripsQuery.data.trips) : null,
    tripsLoaded: tripsQuery.data?.state === 'success',
    trips: tripsQuery.data?.state === 'success' ? tripsQuery.data.trips : null,
  };
}
