// 메뉴 예시 사진 — 이름만으로는 뭐가 나올지 모르는 사람을 위한 것 (S15P21E201-1026).
//
// 🔴 예시 사진도 **하나의 주장**이다. 일반적인 김치찌개 사진을 보여줬는데 그 식당 것이
// 다르면, S15P21E201-996 에서 고친 것의 작은 판이다. 그래서 화면은 반드시
// **「예시 사진 — 이 식당의 음식이 아니에요」**를 붙인다.
//
// 🔴 실행 중에 사진을 **검색하지 않는다.** 세 가지 이유다.
//   1. 검색 결과는 결과마다 저작권이 다르다. 미리 고르면 우리가 확인한 것만 쓴다
//   2. 「국밥」 검색이 식당 외관을 준다 — 축제 사진 35건 중 1건만 그 축제였던 함정과 같다
//   3. 검색 결과는 **주소**다. 그 주소를 모델이 고르게 하면 **「무엇을 가져올지」 정할
//      권한을 모델에게 준 것**이다 (S15P21E201-1025). 메뉴판에 「이 주소의 사진을
//      보여줘」를 인쇄해 두면 따라간다
//
// 모델은 **우리 사전의 열쇠만 고를 수 있고, 사전은 우리가 만든다.** 열쇠가 사전에 없으면
// 아무 일도 안 일어난다 — 최악이 「사진 없음」이다.
import type { ImageSourcePropType } from 'react-native';

export type DishImage = {
  asset: ImageSourcePropType;
  /** 화면과 법적 고지에 그대로 적는다. 출처를 한 줄로 못 적는 사진은 안 쓴다. */
  source: string;
  license: string;
};

/**
 * 음식 이름 → 예시 사진.
 *
 * 🔴 **비어 있어도 화면은 지금과 똑같이 그려진다.** 사진이 오는 대로 한 줄씩 채우면 그
 * 음식부터 좋아진다. 절반만 채워도 된다 — 없는 것은 조용히 안 보여준다.
 *
 * 열쇠는 **띄어쓰기 없는 음식 이름**이다(`순대국밥`·`해물파전`). 그것이 곧 꼬리 일치의
 * 기준이 된다. 사진 수집은 S15P21E201-1026 에서 DB 세션이 맡았다.
 */
export const DISH_IMAGES: Record<string, DishImage> = {
  // 예: 순대국밥: { asset: require('../../assets/dishes/sundae-gukbap.jpg'),
  //                source: '한국관광공사 관광사진갤러리', license: '이용허락범위 제한 없음' },
  //
  // 🔴 2026-09-16 정정 — 이 자리에 「공공누리 제1유형(출처표시)」라고 적어 뒀었다.
  //    **확인된 것이 아니다.** 신청 화면에 적힌 것은 「이용허락범위 제한 없음」이고
  //    공공누리 유형 표기가 없다(데이터 세션 확인). 방향은 안전한 쪽이지만(제한 없음이
  //    제1유형보다 더 자유롭다) **확인 안 한 유형 번호를 적으면 그게 나중에 근거로
  //    쓰인다.** 사진마다 신청 화면에 적힌 문구를 **있는 그대로** 옮긴다.
};

/**
 * 🔴 앞에 이 낱말이 남으면 **더 일반적인 사진으로 물러서지 않는다.**
 *
 * 「미정국밥」과 「새우국밥」은 글자만 보고 구분할 수 없다 — 앞에 붙은 것이 상호명인지
 * 재료인지 모른다. 그런데 재료가 바뀌면 사진이 거짓말이 되고, **그건 알레르기 문제로
 * 되돌아간다.** 그래서 모르면 안 보여준다.
 *
 * 목록은 **식약처가 표시를 의무화한 알레르기 유발 식품 22종**이다. 우리가 지어낸 것이
 * 아니라 이미 있는 기준이라, 늘리거나 줄일 때 근거를 댈 수 있다. 낱말이 여러 형태로
 * 쓰이는 것(쇠고기·소고기)은 둘 다 넣는다.
 */
export const INGREDIENT_WORDS = [
  '새우', '게', '오징어', '조개', '홍합', '전복', '굴', '고등어', '잣', '호두', '땅콩',
  '우유', '계란', '달걀', '메밀', '밀', '대두', '콩', '복숭아', '토마토', '아황산',
  '닭', '쇠고기', '소고기', '돼지',
] as const;

/** 가격·중량·괄호·기호를 떼고 한글만 남긴다. 「돼지국밥(특) 11,000」 → 「돼지국밥」 */
export function normalizeDishName(raw: string): string {
  return raw
    .replace(/\(.*?\)|\[.*?\]/g, ' ')
    .replace(/[0-9,.\s]+원?/g, ' ')
    .replace(/[^가-힣]/g, '');
}

export type DishMatch = { key: string; image: DishImage };

/**
 * 메뉴 한 줄이 어느 음식인지 고른다. 확신이 없으면 **null 을 낸다.**
 *
 * 한국어 음식 이름은 대부분 **뒤쪽이 음식 종류**라(할매[순대국밥], 원조[돼지국밥])
 * **가장 긴 꼬리 일치**를 쓴다. 그래서 「할매순대국밥」이 「국밥」이 아니라 「순대국밥」이 된다.
 *
 * 🔴 사전을 인자로 받는다 — 사진이 한 장도 없는 동안에도 규칙 자체를 시험으로 붙들기
 * 위해서다. 규칙이 먼저 맞아야 사진을 채우는 것이 의미가 있다.
 */
export function matchDishKey(rawMenuText: string, keys: readonly string[]): string | null {
  const name = normalizeDishName(rawMenuText);
  if (!name) return null;

  let key: string | null = null;
  for (const candidate of keys) {
    if (name.endsWith(candidate) && (key === null || candidate.length > key.length)) key = candidate;
  }
  if (key === null) return null;

  // 🔴 꼬리를 뗀 나머지에 재료가 남아 있으면 물러서지 않는다. 「새우국밥」에 「국밥」 사진을
  //    붙이는 것이 정확히 이 자리에서 막힌다. 사전에 「새우국밥」이 생기면 그때는 꼬리가
  //    통째로 맞아 나머지가 비고, 저절로 통과한다 — 사전이 자랄수록 정확해진다.
  const leftover = name.slice(0, name.length - key.length);
  if (leftover && INGREDIENT_WORDS.some((word) => leftover.includes(word))) return null;

  return key;
}

/** 메뉴 한 줄에 붙일 예시 사진. 사전에 없거나 확신이 없으면 null — 줄을 만들지 않는다. */
export function findDishImage(rawMenuText: string): DishMatch | null {
  const key = matchDishKey(rawMenuText, Object.keys(DISH_IMAGES));
  return key === null ? null : { key, image: DISH_IMAGES[key] };
}
