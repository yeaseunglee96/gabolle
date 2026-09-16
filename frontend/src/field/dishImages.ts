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
  // 🔴 22/30. 여기 없는 여덟은 **못 찾은 것이 아니라 일부러 비웠다.** 다시 찾지 마라 —
  //    「비슷하면 받는다」로 기준을 낮추면 이렇게 된다(2026-09-16 실측):
  //
  //      콩국수  → 「땅콩국수」        다른 음식
  //      짜장면  → 「짜장면박물관」     음식이 아님
  //      회      → 「경복궁 경회루」    글자만 겹친다
  //      칼국수  → 「멍게칼국수」       다른 음식
  //
  //    비운 것: 소고기국밥 · 칼국수 · 콩국수 · 순두부찌개 · 해물파전 · 회 · 짜장면 · 탕수육
  //    채우고 싶으면 **사람이 한 장씩 골라 넣는다.** 30개는 눈으로 볼 수 있는 크기다.
  //
  // 사진은 관광사진갤러리 원본을 **긴 변 480px · 품질 78** 로 줄인 것이다(16.5MB → 0.72MB).
  // 원본과 촬영자 정보는 bigData/data/staged/dish-photos.ndjson 에 있다 — 원천이 사진을
  // 내리면 다시 못 만들어서 그쪽은 커밋해 두었다.
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

/**
 * 🔴 앞에 이 낱말이 남으면 **더 일반적인 사진으로 물러서지 않는다.**
 *
 * 「미정국밥」과 「새우국밥」은 글자만 보고 구분할 수 없다 — 앞에 붙은 것이 상호명인지
 * 재료인지 모른다. 그런데 재료가 바뀌면 사진이 거짓말이 되고, **그건 알레르기 문제로
 * 되돌아간다.** 그래서 모르면 안 보여준다.
 *
 * 뿌리는 **식약처가 표시를 의무화한 알레르기 유발 식품 22종**이다. 우리가 지어낸 것이
 * 아니라 이미 있는 기준이라, 늘리거나 줄일 때 근거를 댈 수 있다. 낱말이 여러 형태로
 * 쓰이는 것(쇠고기·소고기)은 둘 다 넣는다.
 */
export const INGREDIENT_WORDS = [
  // ── 식약처 22종 (개별 이름) ─────────────────────────────────────────
  '새우', '게', '오징어', '조개', '홍합', '전복', '굴', '고등어', '잣', '호두', '땅콩',
  '우유', '계란', '달걀', '메밀', '밀', '대두', '콩', '복숭아', '토마토', '아황산',
  '닭', '쇠고기', '소고기', '돼지',

  // ── 🔴 묶음 낱말 — 여기부터는 우리가 더한 것이다 (2026-09-16) ───────
  //
  // 22종은 **개별 이름만** 담고 있다. 그래서 「해물파전」이 「파전」 사진을 받아 갔다.
  // 시험이 잡았다. 「해물」은 조개·오징어·새우를 한꺼번에 뜻하는 말이라 **알레르기의
  // 핵심인데 22종 어느 낱말과도 글자가 안 겹친다.**
  //
  // 줄을 갈라 둔 것은 일부러다 — 위쪽은 **근거가 있는 목록**이고 아래쪽은 **우리 판단**이다.
  // 섞어 두면 나중에 누가 「식약처 목록이니까 맞겠지」로 읽는다.
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
