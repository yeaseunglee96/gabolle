// 데스크톱 홈이 쓰는 값들을 한곳에서 읽는다 (S15P21E201-970).
//
// 🔴 여기 있는 것은 **전부 실제 API 가 주는 값**이다. 인계 문서의 데이터 표에는 지금 서버에
// 없는 것이 섞여 있었다(장소 태그 · 취향×태그 매핑 · 여행 제목 · 장소 사진). 그것들은 이 훅에
// 넣지 않는다 — 없는 값을 채워 주는 자리를 만들면 화면이 거짓말을 시작한다.
import { useQuery } from '@tanstack/react-query';

import { useAuth } from '@/auth/AuthProvider';
import { getFacets, getNearbyPlaces, type FacetKeyEntry, type NearbyPlaceItem } from '@/discovery/localExplore';
import { loadFeed, type StoryDto } from '@/social/stories';
import { loadTrips, type TripSummaryDto } from '@/trip/trips';
import { loadWeatherForecast, type DailyForecastDto } from '@/trip/weather';

// weather.ts 가 같은 값을 모듈 안에 두고 내보내지 않아 여기에 다시 적는다. 부산 한 곳만
// 보는 화면이라 좌표가 화면마다 달라질 일이 없다.
const BUSAN = { lat: 35.1796, lng: 129.0756 };

const HERO_STORY_COUNT = 3;
const PLACE_PICK_COUNT = 4;
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
  stories: StoryDto[] | null;
  chips: FacetKeyEntry[];
  weather: DailyForecastDto | null;
  places: NearbyPlaceItem[];
  trip: TripSummaryDto | null;
  tripsLoaded: boolean;
};

/**
 * @param enabled 데스크톱 홈에서만 켠다. 훅은 조건 없이 불러야 하는데(React 규칙) 폰 랜딩은
 *   이 값을 하나도 안 그리므로, 끄지 않으면 폰에서 볼 때마다 요청 다섯 개가 헛돈다.
 */
export function useHomeData(enabled = true): HomeData {
  const { accessToken } = useAuth();

  // 🔴 비로그인도 그대로 부른다. 서버에 익명 출입증(S15P21E201-303)이 있어서 로그인 없이도
  //    공개 글이 온다 — api/client.ts 가 X-Session-Token 을 알아서 붙인다.
  const storiesQuery = useQuery({
    queryKey: ['home', 'stories'],
    enabled,
    queryFn: () => loadFeed({ scope: 'ALL', limit: 12, accessToken }),
  });

  const facetsQuery = useQuery({ queryKey: ['home', 'facets'], enabled, queryFn: () => getFacets() });

  const weatherQuery = useQuery({
    queryKey: ['home', 'weather', today()],
    enabled,
    queryFn: () => loadWeatherForecast(today(), accessToken),
  });

  const placesQuery = useQuery({
    queryKey: ['home', 'places'],
    enabled,
    queryFn: () => getNearbyPlaces({ ...BUSAN, limit: PLACE_PICK_COUNT }),
  });

  const tripsQuery = useQuery({
    queryKey: ['home', 'trips'],
    enabled: enabled && Boolean(accessToken),
    queryFn: () => loadTrips(accessToken),
  });

  return {
    // 🔴 null 은 **아직 불러오는 중**이라는 뜻만 가져야 한다. 실패까지 null 로 묶으면 화면이
    //    스켈레톤을 영원히 그린다 — 서버가 죽었을 때 실제로 그랬다. 실패는 빈 배열로 내려
    //    「아직 기록이 없어요」 자리로 보낸다.
    stories: storiesQuery.isPending
      ? null
      : storiesQuery.data?.state === 'success' ? pickHeroStories(storiesQuery.data.items) : [],
    // 장소가 하나도 없는 갈래는 칩으로 내지 않는다 — 눌러도 빈 화면이 나온다.
    chips: facetsQuery.data?.state === 'success'
      ? facetsQuery.data.facets.flatMap((group) => group.keys).filter((key) => key.placeCount > 0).slice(0, FACET_CHIP_COUNT)
      : [],
    weather: weatherQuery.data?.state === 'success' ? weatherQuery.data.forecast : null,
    places: placesQuery.data?.state === 'success' ? placesQuery.data.items.slice(0, PLACE_PICK_COUNT) : [],
    trip: tripsQuery.data?.state === 'success' ? pickActiveTrip(tripsQuery.data.trips) : null,
    tripsLoaded: tripsQuery.data?.state === 'success',
  };
}
