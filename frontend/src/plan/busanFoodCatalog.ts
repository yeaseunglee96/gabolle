import { getApiLanguage } from '@/api/client';

// — 부산 음식 8종(밀면·돼지국밥·씨앗호떡·회/해산물·동래파전·복국·부산어묵
// 낙곱새)의 영문명·발음·원재료·맵기·가격대를 코드가 아니라 설정으로 둔다. 이 값을 고치면
// 화면(태그·상세 카드 등)이 코드 변경 없이 그대로 따라간다.
export type AllergenCode =
	| 'PEANUT'
	| 'TREE_NUT'
	| 'SHELLFISH_CRUSTACEAN'
	| 'FISH'
	| 'EGG'
	| 'MILK_DAIRY'
	| 'WHEAT'
	| 'SOY';

export type SpicyLevel = 'NONE' | 'MILD' | 'MEDIUM' | 'HOT';

export type BusanFood = {
	code: string;
	ko: string;
	en: string;
	/** 한글 표기 발음 — 로마자 표기가 아니라, 외국인에게 그대로 읽어 줄 수 있는 형태다. */
	pronunciation: string;
	/** 사람이 읽는 원재료 이름. 알레르기 8대 원재료가 아닌 것(돼지고기 등)도 포함한다. */
	mainIngredientsKo: string[];
	/** 8대 알레르기 원재료와 대조할 때만 쓰는 코드 — constraints.tsx 의 ALLERGIES 와 동일해야 한다. */
	allergenCodes: AllergenCode[];
	spicyLevel: SpicyLevel;
	priceRangeKrw: readonly [number, number];
	/** 대표 사진 주소 — 실제 촬영본이 정해지기 전까지는 비워 둔다(완료 기준에 없는 필드). */
	photoUrl: string | null;
};

const t = (ko: string, en: string) => (getApiLanguage() === 'en' ? en : ko);

export const BUSAN_FOODS: readonly BusanFood[] = [
	{
		code: 'MILMYEON',
		ko: '밀면',
		en: 'Milmyeon (Busan Cold Wheat Noodles)',
		pronunciation: '밀-면 (mil-myeon)',
		mainIngredientsKo: ['밀가루면', '육수(소고기 또는 사골)', '무', '오이', '삶은 달걀'],
		allergenCodes: ['WHEAT', 'EGG'],
		spicyLevel: 'MEDIUM',
		priceRangeKrw: [7000, 10000],
		photoUrl: null,
	},
	{
		code: 'PORK_SOUP',
		ko: '돼지국밥',
		en: 'Dwaeji-gukbap (Pork Bone Soup with Rice)',
		pronunciation: '돼지국밥 (dwae-ji-guk-bap)',
		mainIngredientsKo: ['돼지고기', '돼지뼈 육수', '밥', '부추', '새우젓'],
		allergenCodes: ['SHELLFISH_CRUSTACEAN'],
		spicyLevel: 'NONE',
		priceRangeKrw: [9000, 11000],
		photoUrl: null,
	},
	{
		code: 'SSIAT_HOTTEOK',
		ko: '씨앗호떡',
		en: 'Ssiat Hotteok (Seed-filled Sweet Pancake)',
		pronunciation: '씨앗호떡 (ssi-at ho-tteok)',
		mainIngredientsKo: ['밀가루', '설탕', '견과류(해바라기씨·호박씨 등)', '계핏가루'],
		allergenCodes: ['WHEAT', 'TREE_NUT'],
		spicyLevel: 'NONE',
		priceRangeKrw: [2000, 3000],
		photoUrl: null,
	},
	{
		code: 'SEAFOOD',
		ko: '회·해산물',
		en: 'Hoe (Fresh Raw Seafood)',
		pronunciation: '회 (hoe)',
		mainIngredientsKo: ['생선회', '해산물(문어·전복 등)', '초고추장'],
		allergenCodes: ['FISH', 'SHELLFISH_CRUSTACEAN'],
		spicyLevel: 'MILD',
		priceRangeKrw: [20000, 50000],
		photoUrl: null,
	},
	{
		code: 'DONGNAE_PAJEON',
		ko: '동래파전',
		en: 'Dongnae Pajeon (Green Onion & Seafood Pancake)',
		pronunciation: '동래파전 (dong-nae pa-jeon)',
		mainIngredientsKo: ['쪽파', '밀가루', '찹쌀가루', '해산물(굴·홍합 등)', '계란'],
		allergenCodes: ['WHEAT', 'SHELLFISH_CRUSTACEAN', 'EGG'],
		spicyLevel: 'NONE',
		priceRangeKrw: [15000, 25000],
		photoUrl: null,
	},
	{
		code: 'BOKGUK',
		ko: '복국',
		en: 'Bokguk (Pufferfish Soup)',
		pronunciation: '복국 (bok-guk)',
		mainIngredientsKo: ['복어', '미나리', '콩나물'],
		allergenCodes: ['FISH'],
		spicyLevel: 'MILD',
		priceRangeKrw: [13000, 18000],
		photoUrl: null,
	},
	{
		code: 'BUSAN_EOMUK',
		ko: '부산어묵',
		en: 'Busan Eomuk (Fish Cake)',
		pronunciation: '부산어묵 (bu-san eo-muk)',
		mainIngredientsKo: ['연육(생선살)', '밀가루', '야채'],
		allergenCodes: ['FISH', 'WHEAT'],
		spicyLevel: 'NONE',
		priceRangeKrw: [1000, 3000],
		photoUrl: null,
	},
	{
		code: 'NAKGOPSAE',
		ko: '낙곱새',
		en: 'Nakgopsae (Octopus, Beef Intestine & Shrimp Stir-fry)',
		pronunciation: '낙곱새 (nak-gop-sae)',
		mainIngredientsKo: ['낙지', '소곱창', '새우', '고추장 양념'],
		allergenCodes: ['SHELLFISH_CRUSTACEAN'],
		spicyLevel: 'HOT',
		priceRangeKrw: [15000, 20000],
		photoUrl: null,
	},
] as const;

export const busanFoodLabel = (code: string): string => {
	const entry = BUSAN_FOODS.find((food) => food.code === code);
	return entry ? t(entry.ko, entry.en) : code;
};

/**
 * 원재료 이름(한글, 예: "돼지고기")으로 그 재료가 들어간 음식을 찾는다
 * 완료 기준("돼지고기"를 원재료로 가진 음식을 찾으면 돼지국밥이 나온다) 그대로다.
 */
export function findBusanFoodsByIngredient(ingredientKo: string): readonly BusanFood[] {
	return BUSAN_FOODS.filter((food) => food.mainIngredientsKo.some((ingredient) => ingredient.includes(ingredientKo)));
}
