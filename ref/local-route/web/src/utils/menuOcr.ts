import type { TranslatedMenuItem } from "../components/MenuTranslatorModal";

interface MenuDictEntry {
  nameEn: string;
  allergensKo: string[];
  allergensEn: string[];
  dietTagsEn: string[];
  spicyLevel: number;
}

/**
 * 실제 사진 속 한글과 매칭시키기 위한 정규화 키(공백 제거) -> 사전 항목.
 * 사전에 없는 문구는 조작(환각) 대신 "번역 준비 중"으로 표시된다 (parseMenuText 참고).
 */
const MENU_DICTIONARY: Record<string, MenuDictEntry> = {
  "돼지국밥": { nameEn: "Pork Rice Soup", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
  "순대국밥": { nameEn: "Korean Sausage Rice Soup", allergensKo: ["돼지고기", "갑각류(새우젓)"], allergensEn: ["Pork", "Shellfish"], dietTagsEn: ["Hot Soup"], spicyLevel: 1 },
  "순댓국": { nameEn: "Korean Sausage Soup", allergensKo: ["돼지고기", "갑각류(새우젓)"], allergensEn: ["Pork", "Shellfish"], dietTagsEn: ["Hot Soup"], spicyLevel: 1 },
  "수육백반": { nameEn: "Boiled Pork Slice Set", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Set Menu"], spicyLevel: 0 },
  "수육": { nameEn: "Boiled Pork Slices", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
  "맛보기수육": { nameEn: "Appetizer Boiled Pork", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
  "설렁탕": { nameEn: "Ox Bone Soup", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
  "감자탕": { nameEn: "Pork Bone Potato Stew", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Hot Pot"], spicyLevel: 1 },
  "부대찌개": { nameEn: "Army Base Stew", allergensKo: ["돼지고기", "대두", "밀"], allergensEn: ["Pork", "Soybean", "Wheat"], dietTagsEn: ["Hot Pot"], spicyLevel: 2 },
  "김치찌개": { nameEn: "Kimchi Stew", allergensKo: ["돼지고기", "대두"], allergensEn: ["Pork", "Soybean"], dietTagsEn: ["Hot Pot"], spicyLevel: 2 },
  "된장찌개": { nameEn: "Soybean Paste Stew", allergensKo: ["대두"], allergensEn: ["Soybean"], dietTagsEn: ["Hot Pot"], spicyLevel: 1 },
  "육개장": { nameEn: "Spicy Beef Soup", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Hot Soup"], spicyLevel: 2 },
  "갈비탕": { nameEn: "Short Rib Soup", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
  "삼계탕": { nameEn: "Ginseng Chicken Soup", allergensKo: ["닭고기"], allergensEn: ["Chicken"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },

  "물냉면": { nameEn: "Chilled Buckwheat Noodles in Broth", allergensKo: ["메밀", "소고기"], allergensEn: ["Buckwheat", "Beef"], dietTagsEn: ["Cold Noodle"], spicyLevel: 0 },
  "비빔냉면": { nameEn: "Spicy Mixed Buckwheat Noodles", allergensKo: ["메밀"], allergensEn: ["Buckwheat"], dietTagsEn: ["Cold Noodle"], spicyLevel: 2 },
  "냉면": { nameEn: "Cold Buckwheat Noodles", allergensKo: ["메밀"], allergensEn: ["Buckwheat"], dietTagsEn: ["Cold Noodle"], spicyLevel: 0 },
  "물밀면": { nameEn: "Chilled Wheat Noodles in Broth", allergensKo: ["밀", "소고기"], allergensEn: ["Wheat", "Beef"], dietTagsEn: ["Cold Noodle"], spicyLevel: 1 },
  "비빔밀면": { nameEn: "Spicy Mixed Wheat Noodles", allergensKo: ["밀", "대두", "땅콩"], allergensEn: ["Wheat", "Soybean", "Peanut"], dietTagsEn: ["Spicy Noodle"], spicyLevel: 3 },
  "밀면": { nameEn: "Busan Wheat Noodles", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Cold Noodle"], spicyLevel: 1 },
  "잔치국수": { nameEn: "Warm Wheat Noodle Soup", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Noodle"], spicyLevel: 0 },
  "칼국수": { nameEn: "Hand-cut Noodle Soup", allergensKo: ["밀", "조개"], allergensEn: ["Wheat", "Shellfish"], dietTagsEn: ["Noodle"], spicyLevel: 0 },
  "짜장면": { nameEn: "Black Bean Noodles", allergensKo: ["밀", "대두"], allergensEn: ["Wheat", "Soybean"], dietTagsEn: ["Noodle"], spicyLevel: 0 },
  "짬뽕": { nameEn: "Spicy Seafood Noodle Soup", allergensKo: ["밀", "갑각류", "조개"], allergensEn: ["Wheat", "Shellfish"], dietTagsEn: ["Spicy Noodle"], spicyLevel: 2 },
  "우동": { nameEn: "Udon Noodle Soup", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Noodle"], spicyLevel: 0 },
  "라면": { nameEn: "Instant Ramyeon", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Noodle"], spicyLevel: 1 },

  "삼겹살": { nameEn: "Grilled Pork Belly", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "목살": { nameEn: "Grilled Pork Neck", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "갈비": { nameEn: "Grilled Short Ribs", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "소갈비": { nameEn: "Grilled Beef Short Ribs", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "불고기": { nameEn: "Marinated Grilled Beef", allergensKo: ["소고기", "대두"], allergensEn: ["Beef", "Soybean"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "제육볶음": { nameEn: "Spicy Stir-fried Pork", allergensKo: ["돼지고기", "대두"], allergensEn: ["Pork", "Soybean"], dietTagsEn: ["Stir-fry"], spicyLevel: 2 },
  "닭갈비": { nameEn: "Spicy Stir-fried Chicken", allergensKo: ["닭고기", "대두"], allergensEn: ["Chicken", "Soybean"], dietTagsEn: ["Stir-fry"], spicyLevel: 2 },
  "곱창": { nameEn: "Grilled Beef Intestines", allergensKo: ["소고기"], allergensEn: ["Beef"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "막창": { nameEn: "Grilled Pork Intestines", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "족발": { nameEn: "Braised Pig's Feet", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
  "보쌈": { nameEn: "Boiled Pork Wraps", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },

  "물회": { nameEn: "Cold Raw Fish Soup", allergensKo: ["생선", "갑각류"], allergensEn: ["Fish", "Shellfish"], dietTagsEn: ["Raw Fish"], spicyLevel: 2 },
  "회": { nameEn: "Raw Fish Sashimi", allergensKo: ["생선"], allergensEn: ["Fish"], dietTagsEn: ["Raw Fish"], spicyLevel: 0 },
  "조개구이": { nameEn: "Grilled Shellfish", allergensKo: ["조개"], allergensEn: ["Shellfish"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "아귀찜": { nameEn: "Spicy Braised Monkfish", allergensKo: ["생선", "콩나물"], allergensEn: ["Fish"], dietTagsEn: ["Braised"], spicyLevel: 3 },
  "오뎅": { nameEn: "Fish Cake Skewer Soup", allergensKo: ["생선", "밀"], allergensEn: ["Fish", "Wheat"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "어묵": { nameEn: "Fish Cake Skewer Soup", allergensKo: ["생선", "밀"], allergensEn: ["Fish", "Wheat"], dietTagsEn: ["Street Food"], spicyLevel: 0 },

  "떡볶이": { nameEn: "Spicy Rice Cakes", allergensKo: ["밀", "대두"], allergensEn: ["Wheat", "Soybean"], dietTagsEn: ["Street Food"], spicyLevel: 2 },
  "순대": { nameEn: "Korean Blood Sausage", allergensKo: ["돼지고기"], allergensEn: ["Pork"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "튀김": { nameEn: "Assorted Fritters", allergensKo: ["밀", "대두"], allergensEn: ["Wheat", "Soybean"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "김밥": { nameEn: "Seaweed Rice Roll", allergensKo: ["계란", "밀"], allergensEn: ["Egg", "Wheat"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "라볶이": { nameEn: "Spicy Rice Cake & Ramyeon", allergensKo: ["밀", "대두"], allergensEn: ["Wheat", "Soybean"], dietTagsEn: ["Street Food"], spicyLevel: 2 },
  "수제찐만두": { nameEn: "Handmade Steamed Dumplings", allergensKo: ["돼지고기", "밀", "대두"], allergensEn: ["Pork", "Wheat", "Soybean"], dietTagsEn: ["Dumpling"], spicyLevel: 0 },
  "찐만두": { nameEn: "Steamed Dumplings", allergensKo: ["돼지고기", "밀", "대두"], allergensEn: ["Pork", "Wheat", "Soybean"], dietTagsEn: ["Dumpling"], spicyLevel: 0 },
  "군만두": { nameEn: "Fried Dumplings", allergensKo: ["돼지고기", "밀", "대두"], allergensEn: ["Pork", "Wheat", "Soybean"], dietTagsEn: ["Dumpling"], spicyLevel: 0 },
  "만두": { nameEn: "Dumplings", allergensKo: ["돼지고기", "밀", "대두"], allergensEn: ["Pork", "Wheat", "Soybean"], dietTagsEn: ["Dumpling"], spicyLevel: 0 },
  "잡채": { nameEn: "Glass Noodle Stir-fry", allergensKo: ["밀", "대두"], allergensEn: ["Wheat", "Soybean"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },

  "비빔밥": { nameEn: "Mixed Rice Bowl", allergensKo: ["계란", "대두"], allergensEn: ["Egg", "Soybean"], dietTagsEn: ["Rice Bowl"], spicyLevel: 1 },
  "돌솥비빔밥": { nameEn: "Hot Stone Bowl Mixed Rice", allergensKo: ["계란", "대두"], allergensEn: ["Egg", "Soybean"], dietTagsEn: ["Rice Bowl"], spicyLevel: 1 },
  "제육덮밥": { nameEn: "Spicy Pork Rice Bowl", allergensKo: ["돼지고기", "대두"], allergensEn: ["Pork", "Soybean"], dietTagsEn: ["Rice Bowl"], spicyLevel: 2 },
  "오징어덮밥": { nameEn: "Spicy Squid Rice Bowl", allergensKo: ["갑각류", "대두"], allergensEn: ["Shellfish", "Soybean"], dietTagsEn: ["Rice Bowl"], spicyLevel: 2 },

  "아메리카노": { nameEn: "Americano", allergensKo: [], allergensEn: [], dietTagsEn: ["Coffee"], spicyLevel: 0 },
  "카페라떼": { nameEn: "Caffe Latte", allergensKo: ["우유"], allergensEn: ["Dairy"], dietTagsEn: ["Coffee"], spicyLevel: 0 },
  "아이스티": { nameEn: "Iced Tea", allergensKo: [], allergensEn: [], dietTagsEn: ["Beverage"], spicyLevel: 0 },
  "식혜": { nameEn: "Sweet Rice Punch", allergensKo: [], allergensEn: [], dietTagsEn: ["Beverage"], spicyLevel: 0 },
  "맥주": { nameEn: "Beer", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Alcohol"], spicyLevel: 0 },
  "소주": { nameEn: "Soju", allergensKo: [], allergensEn: [], dietTagsEn: ["Alcohol"], spicyLevel: 0 },
  "막걸리": { nameEn: "Makgeolli Rice Wine", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Alcohol"], spicyLevel: 0 },

  "공기밥": { nameEn: "Steamed Rice", allergensKo: [], allergensEn: [], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
  "돼지불백": { nameEn: "Spicy Pork Bulgogi", allergensKo: ["돼지고기", "대두"], allergensEn: ["Pork", "Soybean"], dietTagsEn: ["Grill"], spicyLevel: 2 },
  "돼지불고기": { nameEn: "Spicy Pork Bulgogi", allergensKo: ["돼지고기", "대두"], allergensEn: ["Pork", "Soybean"], dietTagsEn: ["Grill"], spicyLevel: 2 },
  "계란찜": { nameEn: "Steamed Egg Custard", allergensKo: ["계란"], allergensEn: ["Egg"], dietTagsEn: ["Side Dish"], spicyLevel: 0 },
  "콩나물국밥": { nameEn: "Bean Sprout Rice Soup", allergensKo: ["대두"], allergensEn: ["Soybean"], dietTagsEn: ["Hot Soup"], spicyLevel: 1 },
  "황태해장국": { nameEn: "Dried Pollack Hangover Soup", allergensKo: ["생선"], allergensEn: ["Fish"], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
  "미역국": { nameEn: "Seaweed Soup", allergensKo: [], allergensEn: [], dietTagsEn: ["Hot Soup"], spicyLevel: 0 },
  "동태찌개": { nameEn: "Pollack Stew", allergensKo: ["생선"], allergensEn: ["Fish"], dietTagsEn: ["Hot Pot"], spicyLevel: 2 },
  "낙지볶음": { nameEn: "Spicy Stir-fried Octopus", allergensKo: ["갑각류", "대두"], allergensEn: ["Shellfish", "Soybean"], dietTagsEn: ["Stir-fry"], spicyLevel: 3 },
  "생선구이": { nameEn: "Grilled Fish", allergensKo: ["생선"], allergensEn: ["Fish"], dietTagsEn: ["Grill"], spicyLevel: 0 },
  "치킨": { nameEn: "Korean Fried Chicken", allergensKo: ["닭고기", "밀"], allergensEn: ["Chicken", "Wheat"], dietTagsEn: ["Fried"], spicyLevel: 0 },
  "탕수육": { nameEn: "Sweet and Sour Pork", allergensKo: ["돼지고기", "밀"], allergensEn: ["Pork", "Wheat"], dietTagsEn: ["Fried"], spicyLevel: 0 },
  "씨앗호떡": { nameEn: "Seed-filled Sweet Pancake", allergensKo: ["밀", "땅콩"], allergensEn: ["Wheat", "Peanut"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "호떡": { nameEn: "Sweet Korean Pancake", allergensKo: ["밀", "땅콩"], allergensEn: ["Wheat", "Peanut"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
  "팥빙수": { nameEn: "Shaved Ice with Red Beans", allergensKo: ["우유"], allergensEn: ["Dairy"], dietTagsEn: ["Dessert"], spicyLevel: 0 },
  "붕어빵": { nameEn: "Fish-shaped Red Bean Pastry", allergensKo: ["밀"], allergensEn: ["Wheat"], dietTagsEn: ["Street Food"], spicyLevel: 0 },
};

const DICTIONARY_KEYS = Object.keys(MENU_DICTIONARY).sort((a, b) => b.length - a.length);

function normalize(text: string): string {
  return text.replace(/\s+/g, "").trim();
}

const PRICE_PATTERN = /(?:₩|W)\s?(\d{1,3}(?:,\d{3})+|\d{3,6})|(\d{1,3}(?:,\d{3})+|\d{3,6})\s?원/;

function extractPrice(line: string): { price: string | null; withoutPrice: string } {
  const match = line.match(PRICE_PATTERN);
  if (!match) return { price: null, withoutPrice: line };
  const digits = (match[1] ?? match[2] ?? "").replace(/,/g, "");
  if (!digits) return { price: null, withoutPrice: line };
  const price = `₩${Number(digits).toLocaleString()}`;
  const withoutPrice = (line.slice(0, match.index) + line.slice((match.index ?? 0) + match[0].length)).trim();
  return { price, withoutPrice };
}

function findDictionaryMatch(normalizedLine: string): { key: string; entry: MenuDictEntry } | null {
  for (const key of DICTIONARY_KEYS) {
    if (normalizedLine.includes(key) || key.includes(normalizedLine)) {
      return { key, entry: MENU_DICTIONARY[key] };
    }
  }
  return null;
}

const HANGUL_PATTERN = /[가-힣]/;

/**
 * OCR로 뽑아낸 원문 텍스트를 줄 단위로 분석해 메뉴 항목을 만든다.
 * 사전에 있는 요리는 번역·알레르기·맵기를 채우고, 사전에 없는 문구는
 * 실제로 인식된 한글 그대로 보여주되 "번역 준비 중"으로 표시한다 —
 * 항목을 임의로 지어내지(환각) 않기 위함이다.
 */
export function parseMenuText(rawText: string, isEn: boolean): TranslatedMenuItem[] {
  const lines = rawText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const items: TranslatedMenuItem[] = [];
  let index = 0;

  for (const rawLine of lines) {
    const koreanCharCount = (rawLine.match(new RegExp(HANGUL_PATTERN, "g")) ?? []).length;
    if (koreanCharCount < 2) continue; // 한글이 거의 없는 줄(장식선, 잡음)은 건너뜀

    const { price, withoutPrice } = extractPrice(rawLine);
    const nameKo = normalize(withoutPrice.replace(/[·.\-_]{2,}/g, "")).replace(/^\d+[.)]?/, "");
    if (!nameKo) continue;

    const match = findDictionaryMatch(nameKo);

    // 가격도 없고 사전에도 없는 짧은 줄은 메뉴가 아닐 가능성이 높아(주소·전화번호·안내문) 제외한다.
    if (!match && !price && nameKo.length < 3) continue;

    index += 1;
    if (match) {
      items.push({
        id: `ocr-${index}`,
        nameKo: withoutPrice.trim() || match.key,
        nameEn: match.entry.nameEn,
        price: price ?? (isEn ? "Price not detected" : "가격 인식 안됨"),
        allergensKo: match.entry.allergensKo,
        allergensEn: match.entry.allergensEn,
        dietTagsEn: match.entry.dietTagsEn,
        spicyLevel: match.entry.spicyLevel,
        matched: true,
      });
    } else {
      items.push({
        id: `ocr-${index}`,
        nameKo: withoutPrice.trim() || rawLine,
        nameEn: isEn ? "Translation pending (not in dictionary)" : "번역 준비 중 (사전에 없는 메뉴)",
        price: price ?? (isEn ? "Price not detected" : "가격 인식 안됨"),
        allergensKo: [],
        allergensEn: [],
        dietTagsEn: [],
        spicyLevel: 0,
        matched: false,
      });
    }
  }

  return items;
}
