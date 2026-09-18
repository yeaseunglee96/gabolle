// 메뉴 예시 사진 — 이름만으로는 뭐가 나올지 모르는 사람을 위한 것
import type { ImageSourcePropType } from 'react-native';

export type DishImage = {
  asset: ImageSourcePropType;
  /** 화면과 법적 고지에 그대로 적는다. 출처를 한 줄로 못 적는 사진은 안 쓴다. */
  source: string;
  license: string;
};

/** 음식 이름 → 예시 사진. */
export const DISH_IMAGES: Record<string, DishImage> = {
  // 콩국수 → 「땅콩국수」 다른 음식
  // 짜장면 → 「짜장면박물관」 음식이 아님
  // 회 → 「경복궁 경회루」 글자만 겹친다
  // 칼국수 → 「멍게칼국수」 다른 음식
  돼지국밥: { asset: require('../../assets/dishes/dwaeji-gukbap.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  순대국밥: { asset: require('../../assets/dishes/sundae-gukbap.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  콩나물국밥: { asset: require('../../assets/dishes/kongnamul-gukbap.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  밀면: { asset: require('../../assets/dishes/milmyeon.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  물냉면: { asset: require('../../assets/dishes/mul-naengmyeon.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  비빔냉면: { asset: require('../../assets/dishes/bibim-naengmyeon.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  잔치국수: { asset: require('../../assets/dishes/janchi-guksu.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  김치찌개: { asset: require('../../assets/dishes/kimchi-jjigae.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  된장찌개: { asset: require('../../assets/dishes/doenjang-jjigae.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  부대찌개: { asset: require('../../assets/dishes/budae-jjigae.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  삼겹살: { asset: require('../../assets/dishes/samgyeopsal.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  갈비탕: { asset: require('../../assets/dishes/galbitang.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  삼계탕: { asset: require('../../assets/dishes/samgyetang.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  불고기: { asset: require('../../assets/dishes/bulgogi.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  비빔밥: { asset: require('../../assets/dishes/bibimbap.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  김밥: { asset: require('../../assets/dishes/gimbap.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  떡볶이: { asset: require('../../assets/dishes/tteokbokki.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  순대: { asset: require('../../assets/dishes/sundae.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  어묵: { asset: require('../../assets/dishes/eomuk.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  씨앗호떡: { asset: require('../../assets/dishes/ssiat-hotteok.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  파전: { asset: require('../../assets/dishes/pajeon.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
  짬뽕: { asset: require('../../assets/dishes/jjamppong.jpg'), source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음 (공공데이터포털)' },
};

/** 앞에 이 낱말이 남으면 더 일반적인 사진으로 물러서지 않는다. */
export const INGREDIENT_WORDS = [
  // ── 식약처 22종 (개별 이름) ─────────────────────────────────────────
  '새우', '게', '오징어', '조개', '홍합', '전복', '굴', '고등어', '잣', '호두', '땅콩',
  '우유', '계란', '달걀', '메밀', '밀', '대두', '콩', '복숭아', '토마토', '아황산',
  '닭', '쇠고기', '소고기', '돼지',

  // 22종은 개별 이름만 담고 있다. 그래서 「해물파전」이 「파전」 사진을 받아 갔다.
  // 시험이 잡았다. 「해물」은 조개·오징어·새우를 한꺼번에 뜻하는 말이라 알레르기의
  // 핵심인데 22종 어느 낱말과도 글자가 안 겹친다.
  '해물', '해산물', '모둠',
] as const;

/** 가격·중량·괄호·기호를 떼고 한글만 남긴다. 「돼지국밥(특) 11,000」 → 「돼지국밥」 */
export function normalizeDishName(raw: string): string {
  return raw
    .replace(/\(.*?\)|\[.*?\]/g, ' ')
    .replace(/[0-9,.\s]+원?/g, ' ')
    .replace(/[^가-힣]/g, '');
}

export type DishMatch = { key: string; image: DishImage };

/** 메뉴 한 줄이 어느 음식인지 고른다. 확신이 없으면 null 을 낸다. */
export function matchDishKey(rawMenuText: string, keys: readonly string[]): string | null {
  const name = normalizeDishName(rawMenuText);
  if (!name) return null;

  let key: string | null = null;
  for (const candidate of keys) {
    if (name.endsWith(candidate) && (key === null || candidate.length > key.length)) key = candidate;
  }
  if (key === null) return null;

  // 꼬리를 뗀 나머지에 재료가 남아 있으면 물러서지 않는다. 「새우국밥」에 「국밥」 사진을
  // 붙이는 것이 정확히 이 자리에서 막힌다. 사전에 「새우국밥」이 생기면 그때는 꼬리가
  // 통째로 맞아 나머지가 비고, 저절로 통과한다 — 사전이 자랄수록 정확해진다.
  const leftover = name.slice(0, name.length - key.length);
  if (leftover && INGREDIENT_WORDS.some((word) => leftover.includes(word))) return null;

  return key;
}

/** 메뉴 한 줄에 붙일 예시 사진. 사전에 없거나 확신이 없으면 null — 줄을 만들지 않는다. */
export function findDishImage(rawMenuText: string): DishMatch | null {
  const key = matchDishKey(rawMenuText, Object.keys(DISH_IMAGES));
  return key === null ? null : { key, image: DISH_IMAGES[key] };
}
