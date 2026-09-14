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
  brand: {
    navy: '#0b1d3a',
    orange: '#f26532',
    ivory: '#fffdf8',
  },
  /** 화면 바탕은 웰컴·홈·계획 화면에 공통으로 보이는 따뜻한 아이보리로 통일한다. */
  canvas: '#fffdf8',

  surface: {
    /** 카드 바탕 (폭중앙 92px) */
    card: '#ffffff',
    /** 선택·강조 표면. 브랜드 오렌지를 옅게 풀어 파란 면색을 쓰지 않는다. */
    tint: '#fff1e8',
    /** 연한 칩·보조 배지에 쓰는 따뜻한 중립색. */
    soft: '#f8f3eb',
    /** 입력 필드·슬라이더 트랙과 경계에 쓰는 따뜻한 회색. */
    field: '#e4ddd3',
    /** 피그마의 아이보리 계열 보조 입력·칩 바탕을 한 값으로 통일한다. */
    subtle: '#f6f5f2',
    /** 브랜드 오렌지를 아주 옅게 쓰는 선택·안내 표면. */
    warm: '#fff1e8',
    /** 밝은 카드 사이의 중립 경계선. */
    border: '#e8e4dd',
  },

  action: {
    /** 주 CTA는 피그마의 네이비 버튼 체계에 맞춘다. */
    primary: '#0b1d3a',
    /** 보조 행동과 작은 강조는 브랜드 오렌지로 통일한다. */
    secondary: '#f26532',
    brand: '#f26532',
    /** 현장 기능의 전폭 CTA도 네이비로 통일한다. */
    field: '#0b1d3a',
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
    /** 네이비 표면 위 보조 설명. */
    onDarkMuted: '#eee8df',
    /** 눈썹 문구·섹션 라벨은 브랜드 오렌지로 통일한다. */
    eyebrow: '#f26532',
    /** 수치·시각 강조도 브랜드 오렌지로 연결한다. */
    accent: '#f26532',
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

// Pretendard 한 벌로 통일한다(S15P21E201-640). 원래 디자인 규칙은 한글 Noto Sans KR·
// 영문 Inter 두 벌이었는데, 관리할 폰트가 하나로 줄고 한글 앱에서 이미 널리 쓰이는
// 무료(SIL OFL) 폰트라 이쪽을 골랐다 — frontend/assets/fonts 에 정적 3종(Regular·Medium·
// Bold)을 넣고 app/_layout.tsx 에서 expo-font 로 로드한다.
// RN 은 굵기별로 다른 파일을 다른 이름으로 등록해야 한다 — 커스텀 폰트에 fontWeight 숫자만
// 주면 대부분 플랫폼에서 그냥 Regular 로 보인다. 그래서 굵기마다 별도 family 이름을 둔다.
export const fontFamily = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  bold: 'Pretendard-Bold',
} as const;

// 🔴 body 는 Figma 실측(10~13px)이 아니라 15px 이다. 실수로 되돌리지 않는다.
//    이 앱의 차별점이 접근성(휠체어·알레르기)인데 본문이 11px 이면 그 자체로 모순이고,
//    iOS 최소 권장이 11pt·Android 12sp 인데 그마저 *캡션* 기준이다.
//
// letterSpacing(자간, S15P21E201-641)은 Figma 실측 값이 아니다 — 실측할 자간 자체가
// 디자인 파일에 없었다. 그래서 가독성 쪽으로 보수적으로만 정했다: 작은 글자(caption)는
// 살짝 벌려 뭉쳐 보이지 않게 하고, 큰 글자(display·hero)는 살짝 좁혀 헤드라인이 늘어져
// 보이지 않게 한다. body·title 은 그대로 0 — 이 앱은 접근성이 차별점이라, 본문 자간을
// taste 로 좁히는 모험을 하지 않는다.
export const type = {
  caption: { size: 11, lineHeight: 14, letterSpacing: 0.1 },
  body: { size: 15, lineHeight: 22, letterSpacing: 0 },
  title: { size: 18, lineHeight: 24, letterSpacing: 0 },
  display: { size: 22, lineHeight: 28, letterSpacing: -0.15 },
  /** 01 Welcome 히어로 브랜드 타이틀 전용(Figma 실측 34px). 다른 화면엔 이 크기가 없어서 추가했다. */
  hero: { size: 34, lineHeight: 40, letterSpacing: -0.25 },
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
