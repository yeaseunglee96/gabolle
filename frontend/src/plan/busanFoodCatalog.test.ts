import { BUSAN_FOODS, findBusanFoodsByIngredient } from './busanFoodCatalog';

describe('BUSAN_FOODS', () => {
	it('정확히 8종이다', () => {
		expect(BUSAN_FOODS).toHaveLength(8);
	});

	it.each(BUSAN_FOODS.map((food) => [food.code, food] as const))(
		'%s — 영문 이름·발음·원재료·맵기·가격대가 비어 있지 않다',
		(_code, food) => {
			expect(food.en.trim().length).toBeGreaterThan(0);
			expect(food.pronunciation.trim().length).toBeGreaterThan(0);
			expect(food.mainIngredientsKo.length).toBeGreaterThan(0);
			expect(food.spicyLevel).toBeTruthy();
			expect(food.priceRangeKrw[0]).toBeGreaterThan(0);
			expect(food.priceRangeKrw[1]).toBeGreaterThanOrEqual(food.priceRangeKrw[0]);
		},
	);

	it('중복되는 code가 없다', () => {
		const codes = BUSAN_FOODS.map((food) => food.code);
		expect(new Set(codes).size).toBe(codes.length);
	});
});

describe('findBusanFoodsByIngredient', () => {
	it('🔴 "돼지고기"로 찾으면 돼지국밥이 나온다 — S15P21E201-445 완료 기준', () => {
		const result = findBusanFoodsByIngredient('돼지고기');
		expect(result.map((food) => food.code)).toContain('PORK_SOUP');
	});

	it('알려지지 않은 재료로 찾으면 빈 배열이다', () => {
		expect(findBusanFoodsByIngredient('땅콩버터')).toEqual([]);
	});
});
