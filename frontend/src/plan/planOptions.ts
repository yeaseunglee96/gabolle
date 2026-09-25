// 조건 질문의 **선택지와 그 부제**, 그리고 「이렇게 반영돼요」 문구.
//
// 🔴 왜 화면이 아니라 여기 있나 — 이 문구들은 **추천 엔진이 하는 일을 사람 말로 옮긴 것**이다.
//    엔진 규칙이 바뀌면 여기도 같이 바뀌어야 한다. 화면 안에 흩어 두면 그 짝이 안 보인다.
//    인계 문서(§8)가 「프론트 상수로 둔다」고 정한 자리이기도 하다.
//
// 🔴 부제를 지우지 마라. 「해운대」만 있으면 고르는 사람이 무엇이 들어오는지 모른다.
//    「해변 · 동백섬 · 해리단길」이 있어야 고른 뒤에 놀라지 않는다.
import type { PlanDraft } from '@/plan/PlanProvider';
import type { QuestionKey } from '@/plan/planQuestions';

/** 선택지 하나 — 코드, 한국어/영어 이름, 한국어/영어 부제. */
export type PlanOption = readonly [code: string, ko: string, en: string, subKo: string, subEn: string];

export const AREA_OPTIONS: readonly PlanOption[] = [
  ['HAEUNDAE', '해운대', 'Haeundae', '해변 · 동백섬 · 해리단길', 'Beach · Dongbaek · Haeridan-gil'],
  ['GWANGALLI', '광안리', 'Gwangalli', '광안대교 야경 · 카페', 'Bridge at night · cafes'],
  ['NAMPO', '남포동', 'Nampo-dong', '국제시장 · 자갈치 · 부산타워', 'Markets · Busan Tower'],
  ['SEOMYEON', '서면', 'Seomyeon', '번화가 · 전포카페거리', 'Downtown · Jeonpo cafe street'],
  ['YEONGDO', '영도', 'Yeongdo', '태종대 · 흰여울마을', 'Taejongdae · Huinnyeoul village'],
  ['SONGJEONG', '송정', 'Songjeong', '서핑 · 한적한 해변', 'Surfing · a quiet beach'],
] as const;

export const CATEGORY_OPTIONS: readonly PlanOption[] = [
  ['SEA_BEACH', '바다 & 해변', 'Sea & beach', '해수욕장 · 해안 산책로', 'Beaches · coastal walks'],
  ['CITY', '도심 탐험', 'City', '시장 · 거리 · 전망', 'Markets · streets · views'],
  ['CAFE_HEALING', '카페 & 힐링', 'Cafe & rest', '뷰 카페 · 쉼', 'View cafes · slow time'],
  ['CULTURE_TEMPLE', '문화 & 사찰', 'Culture & temples', '용궁사 · 범어사 · 마을', 'Temples · old villages'],
  ['FOOD', '맛집 & 먹거리', 'Food', '국밥 · 밀면 · 시장 음식', 'Gukbap · milmyeon · market food'],
  ['NATURE_WALK', '자연 & 산책', 'Nature & walks', '이기대 · 태종대', 'Igidae · Taejongdae'],
  // 🔴 부제가 기대를 맞춘다 — 고른 여행에도 «여행 날짜에 여는 축제만» 들어간다(서버 규칙, S15P21E201-1642).
  //    낱말은 FESTIVAL_EVENT 다. FESTIVAL 은 둘러보기 사전의 「축제」라 서버가 두 사전이 겹치지 않게 지킨다.
  ['FESTIVAL_EVENT', '축제 & 행사', 'Festivals & events', '여행 날짜에 열리는 축제만', 'Only festivals on your travel dates'],
] as const;

/** 여행 취향 카드의 사진 — 글자만 있던 카드가 안 읽혔다(2026-09-21 실기, S15P21E201-1442). 온보딩용으로 그려 둔 그림(assets/taste)의 480px 축소판. */
export const CATEGORY_IMAGES: Record<string, number> = {
  SEA_BEACH: require('../../assets/taste/thumb/sea-beach.jpg'),
  CITY: require('../../assets/taste/thumb/city.jpg'),
  CAFE_HEALING: require('../../assets/taste/thumb/cafe.jpg'),
  CULTURE_TEMPLE: require('../../assets/taste/thumb/culture.jpg'),
  FOOD: require('../../assets/taste/thumb/food.jpg'),
  NATURE_WALK: require('../../assets/taste/thumb/nature.jpg'),
  // 저장소의 광안대교 야경(assets/home/gwangalli.png)을 같은 크기로 자른 것 — 부산불꽃축제·드론쇼가 열리는 자리다. 새 외부 사진이 아니다.
  FESTIVAL_EVENT: require('../../assets/taste/thumb/festival.jpg'),
};

export const ATMOSPHERE_OPTIONS: readonly PlanOption[] = [
  ['LIVELY', '활기찬', 'Lively', '시장 · 번화가 · 축제', 'Markets · downtown · festivals'],
  ['RELAXED', '여유로운', 'Relaxed', '해변 · 공원 · 산책', 'Beaches · parks · walks'],
  ['SENTIMENTAL', '감성적인', 'Sentimental', '골목 · 마을 · 서점', 'Alleys · villages · bookshops'],
  ['ROMANTIC', '낭만적인', 'Romantic', '야경 · 노을 · 다리', 'Night views · sunsets · bridges'],
] as const;

export const PACE_OPTIONS: readonly PlanOption[] = [
  ['RELAXED', '여유롭게', 'Relaxed', '하루 2–3곳 · 머무는 시간 길게', '2–3 places a day · longer stays'],
  ['BALANCED', '균형 있게', 'Balanced', '하루 3–4곳', '3–4 places a day'],
  ['PACKED', '알차게', 'Packed', '하루 5곳 이상 · 이동 빠르게', '5+ places a day · quick hops'],
] as const;

// 🔴 택시는 없다. 초안의 이동수단 칸이 셋만 받는다 — 화면에만 넣으면 고른 값이 조용히 버려진다.
export const TRANSPORT_OPTIONS: readonly PlanOption[] = [
  ['TRANSIT', '대중교통', 'Transit', '지하철·버스 환승 최소', 'Fewest subway/bus transfers'],
  ['CAR', '자동차', 'Car', '주차 가능한 곳 우선', 'Parking-friendly places first'],
  ['WALK', '도보 위주', 'Mostly walking', '한 동네 안에서 촘촘히', 'Tight loops in one area'],
] as const;

export const FOOD_SUBTITLES: Readonly<Record<string, readonly [ko: string, en: string]>> = {
  GUKBAP: ['서면 · 범일동', 'Seomyeon · Beomil-dong'],
  MILMYEON: ['여름 점심', 'Summer lunch'],
  SEAFOOD: ['자갈치 · 기장', 'Jagalchi · Gijang'],
  EOMUK: ['부평깡통시장', 'Bupyeong market'],
  HOTTEOK: ['남포동 간식', 'Nampo-dong snack'],
  CAFE: ['전포 · 해리단길', 'Jeonpo · Haeridan-gil'],
};

/**
 * 「이렇게 반영돼요」 — 지금 답으로 **일정이 어떻게 달라지는지**를 그 자리에서 말한다.
 *
 * 🔴 「왜 묻는지」를 답한 직후에 보여 주려는 것이다. 답을 안 했을 때도 문구가 있어야 한다 —
 *    빈 자리를 두면 띠가 나타났다 사라지며 카드 높이가 튄다.
 *
 * 🔴 선택지가 있는 질문 셋(여행 범위·카테고리·기분)에만 붙는다(시안). 예산·보조·꼭 가고
 *    싶은 곳은 답이 곧 설명이라 한 줄을 더 얹을 이유가 없다.
 */
type Tx = (ko: string, en: string) => string;

export function effectOf(key: QuestionKey, draft: PlanDraft, tx: Tx): string | null {
  switch (key) {
    case 'areas':
      return draft.travelAreas.length
        ? tx(`${draft.travelAreas.length}개 지역 안에서만 장소를 골라요. 지역 사이 이동은 하루에 한 번 이하로 묶어요.`, `We only pick places inside your ${draft.travelAreas.length} area(s), and cross-area moves stay at most once a day.`)
        : tx('지역을 고르면 그 안에서만 장소를 골라요.', 'Pick areas and we only choose places inside them.');
    case 'cats':
      return draft.preferences.length
        ? tx('고른 갈래가 하루 정차지의 약 70%를 차지해요. 나머지는 이동 동선에 맞춰 채워요.', 'Your picks fill about 70% of each day. The rest follows the route.')
        : tx('최대 셋. 고른 갈래가 정차지의 대부분을 차지해요.', 'Up to three. What you pick fills most of the stops.');
    case 'pace':
      switch (draft.paceLevel) {
        case 'RELAXED': return tx('하루 2–3곳, 한 곳에 1시간 반 이상 머물러요.', '2–3 places a day, 90+ minutes at each.');
        case 'BALANCED': return tx('하루 3–4곳, 점심·저녁 사이에 한 곳씩.', '3–4 places a day, one between meals.');
        case 'PACKED': return tx('하루 5곳 이상, 이동은 가까운 순으로 붙여요.', '5+ places a day, hops ordered by distance.');
        default: return tx('하루에 도는 장소 수와 머무는 시간이 정해져요.', 'This sets how many places a day and how long you stay.');
      }
    default:
      return null;
  }
}
