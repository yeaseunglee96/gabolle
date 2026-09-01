// Figma 에서 실측한 디자인 토큰이다. 화면에서 색·숫자를 직접 하드코딩하지 않고
// 반드시 이 파일을 거쳐서 쓴다 — 나중에 값이 바뀌어도 여기 한 곳만 고치면 되게 하기 위해서다.
//
// brandDeep · tint 는 Figma 실측 과정에서 값이 두 개로 갈렸다(예: brandDeep #176b91 / #0d7aad).
// 어느 쪽이 최종인지 아직 확인되지 않아 둘 다 남겨두고, 화면에서는 우선 첫 번째 값(Alt 가 안 붙은 쪽)을 쓴다.
// 확인되면 Alt 값을 지우고 하나로 합친다.

export const color = {
  brand: '#1689be',
  brandBright: '#2994c7',
  brandDeep: '#176b91',
  brandDeepAlt: '#0d7aad',
  bg: {
    0: '#0b161b',
    1: '#121f2e',
    2: '#14293d',
    3: '#152238',
  },
  tint: '#c7e0eb',
  tintAlt: '#d6e8ed',
  text: {
    hi: '#ffffff',
    mid: '#81929a',
    low: '#64748b',
  },
} as const;

// 한글은 Noto Sans KR, 숫자·영문은 Inter 를 쓰기로 했다.
// 폰트 파일이 아직 저장소에 없어서(자산 미도착) 지금은 이름만 토큰에 넣어둔다 —
// RN 은 못 찾는 fontFamily 를 시스템 기본 폰트로 조용히 대체하므로 지금 당장 화면이 깨지진 않는다.
// 실제 .ttf 자산이 들어오면 expo-font 로 로드하는 코드만 추가하면 된다.
export const fontFamily = {
  kr: 'NotoSansKR',
  en: 'Inter',
} as const;

// caption/body/title/display 의 크기·줄높이(px). body 는 Figma 실측(10~13px) 대신
// 접근성 때문에 15px 로 올리기로 확정된 값이다 — 실수로 되돌리지 않는다.
export const type = {
  caption: { size: 11, lineHeight: 14 },
  body: { size: 15, lineHeight: 22 },
  title: { size: 18, lineHeight: 24 },
  display: { size: 22, lineHeight: 28 },
} as const;

export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  full: 999,
} as const;

// 4px 그리드. 5·7 단계는 Figma 에 없어서 비워뒀다.
export const spacing = {
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  6: 24,
  8: 32,
} as const;

export type ColorToken = keyof typeof color;
export type TypeToken = keyof typeof type;
export type RadiusToken = keyof typeof radius;
export type SpacingToken = keyof typeof spacing;
