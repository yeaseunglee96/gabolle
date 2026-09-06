// 재료가 명확히 알려진 음식만 막는다. 시장 먹거리처럼 재료가 뒤섞인 항목은
// 안전하다고도 위험하다고도 지어내지 않고 그대로 둔다.
export const FOODS = [
  ['SEAFOOD', '해산물'],
  ['PORK_SOUP', '돼지국밥'],
  ['MILMYEON', '밀면'],
  ['CAFE_DESSERT', '카페·디저트'],
  ['MARKET', '시장 먹거리'],
  ['VEGETARIAN', '채식'],
] as const;

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
export const CONFLICT_LABEL: Record<string, string> = {
  SHELLFISH_CRUSTACEAN: '갑각류', FISH: '생선', WHEAT: '밀', MILK_DAIRY: '우유·유제품',
  EGG: '달걀', HALAL: '할랄', GLUTEN_FREE: '글루텐 프리', VEGAN: '비건',
};

export type FoodConflict = { code: string; kind: 'allergy' | 'diet' };

export function conflictingFoodCode(foodKey: string, allergies: readonly string[], dietTypes: readonly string[]): FoodConflict | null {
  const allergyCode = (FOOD_ALLERGY_CONFLICTS[foodKey] ?? []).find((code) => allergies.includes(code));
  if (allergyCode) return { code: allergyCode, kind: 'allergy' };
  const dietCode = (FOOD_DIET_CONFLICTS[foodKey] ?? []).find((code) => dietTypes.includes(code));
  if (dietCode) return { code: dietCode, kind: 'diet' };
  return null;
}

export const foodLabel = (key: string) => FOODS.find(([code]) => code === key)?.[1] ?? key;
