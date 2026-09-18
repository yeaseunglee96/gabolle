// 언어 다섯을 다 받는다. 이 함수가 'ko' | 'en' 만 받으면 부르는 쪽
// 열다섯 곳이 각자 떨어뜨려야 하고, 한 곳만 빠뜨리면 일본어 사용자가 한국어 이름을 본다.
// 떨어뜨리는 일은 여기 한 자리에서 한다.
import { resolveTextLanguage, type LanguageCode } from '@/i18n/languages';
// 장소 분류 코드를 사람이 읽는 말로. 시안 `design_handoff_collection`.

export type LocalizedLabel = readonly [ko: string, en: string];

/** 아는 분류 코드. 순서가 곧 휠에 보이는 순서다. */
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

/** 지역 여섯. 여행 만들기(`travelAreas`)와 같은 코드를 쓴다 — 두 벌이 되면 안 맞는다. */
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

/** 코드를 사람이 읽는 말로. 모르는 코드는 코드 그대로 돌려준다. */
export function categoryLabel(code: string, language: LanguageCode, table = PLACE_CATEGORY_LABELS) {
  const pair = table[code];
  if (!pair) return code;
  return resolveTextLanguage(language) === 'en' ? pair[1] : pair[0];
}

export const localityLabel = (code: string, language: LanguageCode) =>
  categoryLabel(code, language, PLACE_LOCALITY_LABELS);

/**
 * 휠에 놓을 순서. 아는 코드가 표에 적힌 순서대로 먼저, 모르는 코드는 그 뒤에
 * 서버가 준 순서 그대로.
 */
export function sortCategoryCodes(codes: readonly string[], table = PLACE_CATEGORY_LABELS) {
  const order = Object.keys(table);
  const known = order.filter((code) => codes.includes(code));
  const unknown = codes.filter((code) => !order.includes(code));
  return [...known, ...unknown];
}

/** 휠에 놓을 분류 목록을 정한다 */
export function categoryWheelCodes(serverCodes: readonly string[] | null | undefined) {
  const known = Object.keys(PLACE_CATEGORY_LABELS);
  if (!serverCodes || serverCodes.length === 0) return known;
  // 서버가 준 것 + 우리가 아는 것을 합친다. 둘 중 하나만 쓰면 한쪽이 사라진다.
  return sortCategoryCodes([...new Set([...serverCodes, ...known])]);
}
