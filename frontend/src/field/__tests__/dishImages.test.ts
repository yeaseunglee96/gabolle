import { DISH_IMAGES, findDishImage, matchDishKey, normalizeDishName } from '../dishImages';

// S15P21E201-1026 — 메뉴 이름 옆에 붙일 예시 사진을 고른다.
//
// 🔴 이 규칙이 지켜야 하는 것은 하나다 — **틀리느니 안 보여준다.**
// 예시 사진도 하나의 주장이라, 재료가 다른 사진이 나가면 알레르기 문제로 되돌아간다
// (S15P21E201-996 에서 고친 것의 작은 판).
//
// 사진은 아직 한 장도 없다(DB 세션이 모으는 중). 그래도 **규칙은 지금 붙들어 둔다** —
// 규칙이 먼저 맞아야 사진을 채우는 것이 의미가 있다.

// 사진이 들어왔을 때를 가정한 사전. 실제 DISH_IMAGES 와 같은 모양의 열쇠를 쓴다.
const KEYS = ['순대국밥', '돼지국밥', '소고기국밥', '콩나물국밥', '국밥', '밀면', '물냉면', '냉면', '삼겹살', '김치찌개', '해물파전', '파전', '비빔밥', '국수'];

describe('가격·상호명·괄호를 떼어 낸다', () => {
  it.each([
    ['돼지국밥(특) 11,000', '돼지국밥'],
    ['제주 흑돼지 삼겹살 200g', '제주흑돼지삼겹살'],
    ['해물파전 (2인) 18,000', '해물파전'],
    ['순대국밥 9,000', '순대국밥'],
  ])('%s → %s', (raw, expected) => {
    expect(normalizeDishName(raw)).toBe(expected);
  });
});

describe('가장 긴 꼬리를 고른다 — 세분화된 이름이 일반 이름에 지지 않는다', () => {
  it('🔴 할매순대국밥은 「국밥」이 아니라 「순대국밥」이다', () => {
    expect(matchDishKey('할매순대국밥', KEYS)).toBe('순대국밥');
  });

  it.each([
    ['돼지국밥(특) 11,000', '돼지국밥'],
    ['원조 돼지국밥', '돼지국밥'],
    ['소고기국밥', '소고기국밥'],
    ['부산밀면 8,000', '밀면'],
    ['잔치국수', '국수'],
    ['해물파전 (2인) 18,000', '해물파전'],
  ])('%s → %s', (raw, expected) => {
    expect(matchDishKey(raw, KEYS)).toBe(expected);
  });

  it('상호명이 앞에 붙어도 찾는다 — 「미정」은 재료가 아니다', () => {
    expect(matchDishKey('미정국밥 8000', KEYS)).toBe('국밥');
  });
});

describe('🔴 앞에 재료가 남으면 더 일반적인 사진으로 물러서지 않는다', () => {
  it.each([
    ['새우국밥 10,000', '새우'],
    ['콩국수', '콩'],
    ['소고기파전', '소고기'],
    ['닭칼국수', '닭'],
  ])('%s — %s 가 남아서 사진을 안 보여준다', (raw) => {
    expect(matchDishKey(raw, KEYS)).toBeNull();
  });

  it('🔴 「국밥」 사진이 새우국밥에 붙는 일이 없다 — 이 시험이 그것을 막는다', () => {
    // 꼬리만 보면 「국밥」이 맞는다. 그래서 보호장치가 없으면 통과해 버린다.
    expect(normalizeDishName('새우국밥').endsWith('국밥')).toBe(true);
    expect(matchDishKey('새우국밥', KEYS)).toBeNull();
  });

  it('사전에 그 이름이 생기면 저절로 통과한다 — 사전이 자랄수록 정확해진다', () => {
    expect(matchDishKey('새우국밥', [...KEYS, '새우국밥'])).toBe('새우국밥');
    expect(matchDishKey('콩국수', [...KEYS, '콩국수'])).toBe('콩국수');
  });
});

describe('모르면 안 보여준다', () => {
  it.each([['오늘의 정식'], ['김치말이국수와 함께'], ['Set Menu A'], ['   '], ['12,000']])('%s → 사진 없음', (raw) => {
    expect(matchDishKey(raw, KEYS)).toBeNull();
  });
});

describe('사진이 한 장도 없는 지금', () => {
  it('🔴 사전이 비어 있어도 아무것도 안 터진다 — 그냥 사진이 안 나온다', () => {
    expect(Object.keys(DISH_IMAGES)).toHaveLength(0);
    expect(findDishImage('할매순대국밥')).toBeNull();
  });

  it('사진이 들어오면 출처와 라이선스가 반드시 같이 있다', () => {
    for (const [key, image] of Object.entries(DISH_IMAGES)) {
      expect(typeof image.source).toBe('string');
      expect(image.source.length).toBeGreaterThan(0);
      expect(image.license.length).toBeGreaterThan(0);
      // 열쇠에 공백이 있으면 꼬리 일치가 절대 안 맞는다 (정규화가 공백을 지우므로).
      expect(key).not.toMatch(/\s/);
    }
  });
});
