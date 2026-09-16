// 장소 분류 코드를 사람이 읽는 말로 (S15P21E201-1071). 시안 `design_handoff_collection`.
//
// 서버는 `GET /api/v1/places/categories` 로 **코드만** 준다(`{ code, placeCount }[]`).
// 코드를 한글로 바꾸는 표가 이 저장소에 없어서, 화면이 코드를 그대로 그리고 있었다.
//
// 🔴 **표에 없는 코드를 지어내지 않는다.** 모르는 코드는 **코드 그대로** 보여 주고 목록
// 맨 뒤로 보낸다. 「기타」로 뭉뚱그리면 서로 다른 코드 셋이 한 줄이 되고, 사용자는 자기가
// 무엇을 고른 건지 모르게 된다.
//
// 🔴 **코드에서 실제로 확인된 것은 FOOD·CAFE_HEALING·SEA_BEACH·NATURE_WALK 뿐이다**
// (`src/assistant/intent.ts` 의 PREFERENCES). 나머지는 서버가 주는 대로 받는다.

export type LocalizedLabel = readonly [ko: string, en: string];

/**
 * 아는 분류 코드. 순서가 곧 휠에 보이는 순서다.
 *
 * 🔴 여기 없는 코드도 **버리지 않는다.** `sortCategoryCodes` 가 뒤에 붙인다.
 */
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
};

/** 지역 여섯. 여행 만들기(`travelAreas`)와 **같은 코드**를 쓴다 — 두 벌이 되면 안 맞는다. */
export const PLACE_LOCALITY_LABELS: Readonly<Record<string, LocalizedLabel>> = {
  HAEUNDAE: ['해운대', 'Haeundae'],
  GWANGALLI: ['광안리', 'Gwangalli'],
  SONGJEONG: ['송정', 'Songjeong'],
  NAMPO: ['남포동', 'Nampo-dong'],
  YEONGDO: ['영도', 'Yeongdo'],
  SEOMYEON: ['서면', 'Seomyeon'],
};

/** 휠 맨 뒤에 붙는 항목. 이걸 고르면 직접 쓸 수 있다. */
export const WRITE_MY_OWN = '__WRITE_MY_OWN__';

/**
 * 코드를 사람이 읽는 말로. **모르는 코드는 코드 그대로 돌려준다.**
 *
 * 🔴 「기타」·「알 수 없음」 같은 말로 바꾸지 않는다. 그건 서버가 보낸 것을 우리가
 * 지운 것이지 사용자에게 도움이 되는 것이 아니다.
 */
export function categoryLabel(code: string, language: 'ko' | 'en', table = PLACE_CATEGORY_LABELS) {
  const pair = table[code];
  if (!pair) return code;
  return language === 'en' ? pair[1] : pair[0];
}

export const localityLabel = (code: string, language: 'ko' | 'en') =>
  categoryLabel(code, language, PLACE_LOCALITY_LABELS);

/**
 * 휠에 놓을 순서. **아는 코드가 표에 적힌 순서대로 먼저**, 모르는 코드는 그 뒤에
 * 서버가 준 순서 그대로.
 *
 * 🔴 모르는 코드를 **버리지 않는다.** 버리면 서버에는 있는 분류를 사용자가 영영 못 고른다.
 */
export function sortCategoryCodes(codes: readonly string[], table = PLACE_CATEGORY_LABELS) {
  const order = Object.keys(table);
  const known = order.filter((code) => codes.includes(code));
  const unknown = codes.filter((code) => !order.includes(code));
  return [...known, ...unknown];
}

/**
 * 휠에 놓을 분류 목록을 정한다 (S15P21E201-1071).
 *
 * 서버가 주는 목록(`GET /api/v1/places/categories`)을 쓰되, **못 받았으면 우리가 아는
 * 코드로 채운다.**
 *
 * 🔴 **휠이 비면 아무것도 못 고른다.** 이 폼은 로그인 없이도 열리고, 서버가 안 되는 날에도
 * 사용자는 장소를 담는다. 「목록을 못 받았어요」라고 비워 두면 그날은 분류를 못 고른다 —
 * 그건 서버 사정이지 사용자 사정이 아니다.
 *
 * 🔴 **서버가 준 코드를 버리지 않는다.** 표에 없는 코드도 그대로 들어간다(`sortCategoryCodes`
 * 가 뒤로 보낸다). 버리면 서버에는 있는 분류를 영영 못 고른다.
 */
export function categoryWheelCodes(serverCodes: readonly string[] | null | undefined) {
  const known = Object.keys(PLACE_CATEGORY_LABELS);
  if (!serverCodes || serverCodes.length === 0) return known;
  // 서버가 준 것 + 우리가 아는 것을 합친다. 둘 중 하나만 쓰면 한쪽이 사라진다.
  return sortCategoryCodes([...new Set([...serverCodes, ...known])]);
}
