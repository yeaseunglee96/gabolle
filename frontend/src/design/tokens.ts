// Figma 에서 실측한 디자인 토큰이다. 화면에서 색·숫자를 직접 하드코딩하지 않고
// 반드시 이 파일을 거쳐서 쓴다 — 나중에 값이 바뀌어도 여기 한 곳만 고치면 되게 하기 위해서다.
//
// ─────────────────────────────────────────────────────────────────────────────
// 🔴 색은 **빈도로 뽑으면 틀린다.** 한 번 크게 틀렸으니 그 이유를 남긴다.
//
// 처음에 색을 사용 횟수로만 세고 "어두운 남색이 많으니 다크 테마" 라고 읽었다.
// 틀렸다. 그 남색들(#152238 · #14293d · #172e3b)은 배경이 아니라 **제목 글자색**이고,
// 이 앱은 화면 23개가 전부 흰색 계열 배경인 **밝은 테마**다.
//
// 그래서 다시 뽑을 때는 두 가지로 갈랐다.
//   ① 칠해진 요소가 TEXT 인가 아닌가  → 글자색과 배경색을 섞지 않는다
//   ② 요소의 폭이 얼마인가            → 전폭(336px+) 버튼과 작은 칩을 섞지 않는다
//
// 아래 주석의 "폭중앙" 은 그 색이 실제로 칠해진 요소들의 폭 중앙값이다.
// 이름을 지을 때 이 숫자를 근거로 삼았다.
// ─────────────────────────────────────────────────────────────────────────────

export const color = {
  /** 화면 바탕. Figma 는 화면마다 #ffffff / #fffdfa / #f7fbfe 로 미세하게 다른데
   *  의도가 아니라 손으로 찍은 편차로 보여 하나로 모은다. 근거가 나오면 갈라도 된다. */
  canvas: '#ffffff',

  surface: {
    /** 카드 바탕 (폭중앙 92px) */
    card: '#ffffff',
    /** 강조 배너·선택된 카드 (폭중앙 250~336px). 08 접근성 카드가 이 색이었다 */
    tint: '#ddf3fa',
    /** 연한 칩·보조 배지 (폭중앙 75px) */
    soft: '#f0faff',
    /** 입력 필드·슬라이더 트랙 (폭중앙 336px) */
    field: '#d6e8ed',
  },

  action: {
    /** 주 CTA 배경 (폭중앙 336px). CTA_다음·CTA_로그인·수정 이 전부 이 색 */
    primary: '#176b91',
    /** 보조 버튼 (폭중앙 342~347px) */
    secondary: '#2994c7',
    /** 브랜드 아이콘·작은 강조 (폭중앙 24px) */
    brand: '#1689be',
    /** 16·17·18·19(여행 준비·현장 말하기·방문 인증·여행 기록) 화면의 전폭 CTA (폭중앙 342px,
     *  #148cb8 로 네 화면 전부 정확히 일치). 사전 계획 화면들의 action.primary(#176b91)와
     *  눈으로 구분되는 별개 파랑이라 같은 값으로 합치지 않고 새로 추가한다. */
    field: '#148cb8',
  },

  text: {
    /** 제목. Figma 에 #152238·#14293d·#172e3b·#121f2e·#17313b 다섯 변종이 있는데
     *  눈으로 구분이 안 되는 차이라 가장 많이 쓰인 값(42회)으로 통일한다 */
    heading: '#152238',
    /** 본문 설명 (폭중앙 250px) */
    body: '#667882',
    /** 보조·비활성 (폭중앙 90px) */
    muted: '#64748b',
    /** 버튼·이미지 위 글자 */
    onAction: '#ffffff',
    /** 눈썹 문구(화면 번호·섹션 라벨). 08 의 "08 · 접근성" 이 이 색 */
    eyebrow: '#176b91',
    /** 수치·시각 강조 (Stats value · Timeline time) */
    accent: '#0d7aad',
  },

  state: {
    /** 경고·알림 점 (폭중앙 20px) */
    danger: '#e85d5b',
    /** 성공 배경 (폭중앙 48px) */
    successBg: '#e7f8ef',
    /** 완료 표시 글자·아이콘 (10 화면 "✓ 완료" 항목). successBg 는 배경 전용이라 글자색이 없었다 */
    success: '#30a687',
    /** "보통" 혼잡도 배지 배경 (11 화면 일정 요약). success/danger 둘 다 아닌 중간 상태라 추가했다 */
    warningBg: '#fff3d7',
    /** "보통" 배지 글자색 */
    warning: '#a46700',
    /** danger 배지 배경 (13 화면 "부산 로컬 음식" 태그, Figma 실측 #fff0ee).
     *  success/warning 은 배경+글자 색이 한 쌍인데 danger 만 배경이 없어 짝을 맞춘다 */
    dangerBg: '#fff0ee',
    /** 별점 색 (18 방문 인증·만족도 화면 실측 #ffa11f). success/warning/danger 어느 색과도
     *  가깝지 않은 금색이라 새로 추가한다 — 평점용으로만 쓴다. */
    rating: '#ffa11f',
  },
} as const;

// 한글은 Noto Sans KR, 숫자·영문은 Inter 를 쓰기로 했다.
// 폰트 파일이 아직 저장소에 없어서 지금은 이름만 넣어둔다 —
// RN 은 못 찾는 fontFamily 를 시스템 폰트로 조용히 대체하므로 화면이 깨지진 않는다.
// 다만 **디자인과 글자 모양이 다르다.** .ttf 가 들어오면 expo-font 로 로드만 붙이면 된다.
export const fontFamily = {
  kr: 'NotoSansKR',
  en: 'Inter',
} as const;

// 🔴 body 는 Figma 실측(10~13px)이 아니라 15px 이다. 실수로 되돌리지 않는다.
//    이 앱의 차별점이 접근성(휠체어·알레르기)인데 본문이 11px 이면 그 자체로 모순이고,
//    iOS 최소 권장이 11pt·Android 12sp 인데 그마저 *캡션* 기준이다.
export const type = {
  caption: { size: 11, lineHeight: 14 },
  body: { size: 15, lineHeight: 22 },
  title: { size: 18, lineHeight: 24 },
  display: { size: 22, lineHeight: 28 },
  /** 01 Welcome 히어로 브랜드 타이틀 전용(Figma 실측 34px). 다른 화면엔 이 크기가 없어서 추가했다. */
  hero: { size: 34, lineHeight: 40 },
} as const;

// Figma 는 반경이 10종(18·14·34·5·16·13·15·12·20…)이고 14.75847053527832 같은 값도 있다.
// 크기를 조정하며 붙여넣은 흔적이라 그대로 옮기지 않고 4종으로 모은다.
// 34 는 화면 프레임 자체의 반경(기기 목업)이라 앱 안에서는 안 쓴다.
export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  full: 999,
} as const;

// 4px 그리드. Figma 는 5·7·9·13·18·19px 이 섞여 있는데 시스템이 아니라 손으로 찍은 값이다.
export const spacing = {
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  6: 24,
  8: 32,
} as const;

/** 화면 좌우 여백. Figma 가 27px 인데 4px 그리드에 맞춰 24 로 쓴다 */
export const gutter = 24;

export type TypeToken = keyof typeof type;
export type RadiusToken = keyof typeof radius;
export type SpacingToken = keyof typeof spacing;
