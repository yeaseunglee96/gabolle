import { getCurrentLanguage } from '@/i18n/languages';
import { pickLanguage } from '@/i18n/pick';

// 재료가 명확히 알려진 음식만 막는다. 시장 먹거리처럼 재료가 뒤섞인 항목은
// 안전하다고도 위험하다고도 지어내지 않고 그대로 둔다.
export const FOODS = [
  ['SEAFOOD', '해산물', 'Seafood'],
  ['PORK_SOUP', '돼지국밥', 'Pork bone soup'],
  ['MILMYEON', '밀면', 'Milmyeon (cold noodles)'],
  ['CAFE_DESSERT', '카페·디저트', 'Cafe & Dessert'],
  ['MARKET', '시장 먹거리', 'Market food'],
  ['VEGETARIAN', '채식', 'Vegetarian'],
] as const;

/**
 * 「상관 없음」 — 음식 취향 맨 앞 칩(사용자 지시 2026-10-16). 고르면 다른 음식은 풀리고, 다른 음식을 고르면 이것이 풀린다.
 * 서버에는 그대로 ["ANY"] 로 간다 — 장소 표식과 맞는 것이 없어 추천에 가산점을 주지 않으니 「취향 없음」과 같게 동작한다.
 */
export const ANY_FOOD = 'ANY';
export const ANY_FOOD_LABEL = ['상관 없음', 'No preference'] as const;

/** 칩 하나를 눌렀을 때의 새 선택 — 「상관 없음」과 다른 음식이 함께 골라지지 않게 한다. */
export function toggleFood(values: readonly string[], code: string): string[] {
  const selected = values.includes(code);
  if (code === ANY_FOOD) return selected ? [] : [ANY_FOOD];
  const rest = values.filter((value) => value !== ANY_FOOD);
  return selected ? rest.filter((value) => value !== code) : [...rest, code];
}

export const FOOD_ALLERGY_CONFLICTS: Record<string, readonly string[]> = {
  SEAFOOD: ['SHELLFISH_CRUSTACEAN', 'FISH'],
  MILMYEON: ['WHEAT'],
  CAFE_DESSERT: ['MILK_DAIRY', 'EGG', 'WHEAT'],
};
export const FOOD_DIET_CONFLICTS: Record<string, readonly string[]> = {
  PORK_SOUP: ['HALAL'],
  MILMYEON: ['GLUTEN_FREE'],
  CAFE_DESSERT: ['VEGAN'],
};

// 화면 언어로 고른다 — 서버용 언어(ko|en 뿐)로 고르면 일본어·중국어 화면에 영어가 나갔다(S15P21E201-1776).
const t = (ko: string, en: string) => pickLanguage(getCurrentLanguage(), { ko, en });

// [ko, en] 쌍으로 둔다 — 언어가 바뀔 때마다 다시 읽혀야 하므로, CONFLICT_LABEL[code] 처럼
// 모듈 로드 시점에 한 번만 고르는 대신 호출부에서 tx(...CONFLICT_LABEL_PAIR[code]) 로 매번 고른다.
export const CONFLICT_LABEL_PAIR: Record<string, [string, string]> = {
  SHELLFISH_CRUSTACEAN: ['갑각류', 'Shellfish (crustacean)'], FISH: ['생선', 'Fish'], WHEAT: ['밀', 'Wheat'], MILK_DAIRY: ['우유·유제품', 'Milk & dairy'],
  EGG: ['달걀', 'Egg'], HALAL: ['할랄', 'Halal'], GLUTEN_FREE: ['글루텐 프리', 'Gluten-free'], VEGAN: ['비건', 'Vegan'],
};

export type FoodConflict = { code: string; kind: 'allergy' | 'diet' };

export function conflictingFoodCode(foodKey: string, allergies: readonly string[], dietTypes: readonly string[]): FoodConflict | null {
  const allergyCode = (FOOD_ALLERGY_CONFLICTS[foodKey] ?? []).find((code) => allergies.includes(code));
  if (allergyCode) return { code: allergyCode, kind: 'allergy' };
  const dietCode = (FOOD_DIET_CONFLICTS[foodKey] ?? []).find((code) => dietTypes.includes(code));
  if (dietCode) return { code: dietCode, kind: 'diet' };
  return null;
}

export const foodLabel = (key: string) => { const entry = FOODS.find(([code]) => code === key); return entry ? t(entry[1], entry[2]) : key; };
