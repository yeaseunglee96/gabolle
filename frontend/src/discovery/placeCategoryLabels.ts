// 장소 분류 코드를 사람이 읽는 말로 — 여행 화면(TripPageDesktop · TripPageMobile)이 쓴다.
// 🔴 S15P21E201-1589 — 부슐랭(컬렉션)을 앱에서 지우면서, 그 화면만 쓰던 휠 순서·지역 이름표·「직접 쓰기」·
//    categoryLabel 을 걷어냈다. 부슐랭이 다시 들어올 때는 디자인을 새로 한다(사용자 결정).

export type LocalizedLabel = readonly [ko: string, en: string];

/** 아는 분류 코드. */
export const PLACE_CATEGORY_LABELS: Readonly<Record<string, LocalizedLabel>> = {
  FOOD: ['맛집', 'Food'],
  CAFE_HEALING: ['카페', 'Cafe'],
  SEA_BEACH: ['바다', 'Beach'],
  NATURE_WALK: ['자연', 'Nature'],
  CULTURE: ['문화', 'Culture'],
  SHOPPING: ['쇼핑', 'Shopping'],
  NIGHT_VIEW: ['야경', 'Night view'],
  ACTIVITY: ['액티비티', 'Activity'],
  MARKET: ['전통시장', 'Market'],
  STAY: ['숙소', 'Stay'],
  // 🔴 서버 적재기가 실제로 쓰는 코드(S15P21E201-1677) — 위 이름과 달라서 이름표가 없었고, 여행 화면에서 분류가 조용히 빠졌다.
  //    OsmPlaceCategory·SbizPlaceLoader·AppFoodVocabulary 가 내는 낱말이다.
  CULTURE_TEMPLE: ['문화', 'Culture'],
  LODGING: ['숙소', 'Stay'],
  CITY: ['도시', 'City'],
  FESTIVAL: ['축제', 'Festival'],
};
