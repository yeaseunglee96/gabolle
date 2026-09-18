import { Platform } from 'react-native';

// Figma 실측 디자인 토큰. 색·숫자의 단일 출처 — 화면에서 직접 하드코딩 금지

export const color = {
  brand: {
    navy: '#0b1d3a',
    orange: '#f26532',
    ivory: '#fffdf8',
  },
  /** 화면 바탕 — 웰컴·홈·계획 공통 아이보리 */
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
    /**
     * 제목. Figma 에 #152238·#14293d·#172e3b·#121f2e·#17313b 다섯 변종이 있는데
     * 눈으로 구분이 안 되는 차이라 가장 많이 쓰인 값(42회)으로 통일한다
     */
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
    /**
     * danger 배지 배경 (13 화면 "부산 로컬 음식" 태그, Figma 실측 #fff0ee).
     * success/warning 은 배경+글자 색이 한 쌍인데 danger 만 배경이 없어 짝을 맞춘다
     */
    dangerBg: '#fff0ee',
    /**
     * 별점 색 (18 방문 인증·만족도 화면 실측 #ffa11f). success/warning/danger 어느 색과도
     * 가깝지 않은 금색이라 새로 추가한다 — 평점용으로만 쓴다.
     */
    rating: '#ffa11f',
  },
} as const;

// Pretendard 한 벌로 통일한다. 원래 디자인 규칙은 한글 Noto Sans KR
// 영문 Inter 두 벌이었는데, 관리할 폰트가 하나로 줄고 한글 앱에서 이미 널리 쓰이는
// 무료(SIL OFL) 폰트라 이쪽을 골랐다 — frontend/assets/fonts 에 정적 3종(Regular·Medium
// Bold)을 넣고 app/_layout.tsx 에서 expo-font 로 로드한다.
// RN 은 굵기별로 다른 파일을 다른 이름으로 등록해야 한다 — 커스텀 폰트에 fontWeight 숫자만
// 주면 대부분 플랫폼에서 그냥 Regular 로 보인다. 그래서 굵기마다 별도 family 이름을 둔다.
export const fontFamily = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  bold: 'Pretendard-Bold',
} as const;

// 5개 국어 지원 이후 — Pretendard 는 한글·영문 글자만 그려 넣은
// 정적 폰트라 중국어(간체·번체) 글자는 애초에 들어있지 않다. 네이티브(iOS/Android)는
// OS가 알아서 시스템 CJK 폰트로 넘어가 문제가 없지만, 웹(react-native-web)은 `fontFamily`
// 가 브라우저 CSS 로 그대로 나가므로 쉼표로 이어진 대체 목록을 직접 적어야 한다
// 안 적으면 브라우저 기본 세리프체로 떨어지거나 자모가 깨져 보인다(실사용 리포트).
// 네이티브에는 이 목록을 주지 않는다 — RN 네이티브는 등록된 폰트 하나만 이름으로
// 받고, 쉼표로 이어 붙이면 그 글자 그대로를 폰트 이름으로 찾다가 못 찾아 깨진다.
// 화면 스타일(Text.tsx 등)은 fontFamily 가 아니라 이 fontFamilyStack 을 쓴다.
const CJK_FALLBACK_WEB = ', "Apple SD Gothic Neo", "Noto Sans KR", "Noto Sans SC", "Noto Sans TC", "PingFang SC", "Microsoft YaHei", "Malgun Gothic", sans-serif';

export const fontFamilyStack = Platform.select({
  web: {
    regular: `${fontFamily.regular}${CJK_FALLBACK_WEB}`,
    medium: `${fontFamily.medium}${CJK_FALLBACK_WEB}`,
    bold: `${fontFamily.bold}${CJK_FALLBACK_WEB}`,
  },
  default: fontFamily,
}) as { regular: string; medium: string; bold: string };

// body 는 Figma 실측(10~13px)이 아니라 15px 이다. 실수로 되돌리지 않는다.
// 이 앱의 차별점이 접근성(휠체어·알레르기)인데 본문이 11px 이면 그 자체로 모순이고
// iOS 최소 권장이 11pt·Android 12sp 인데 그마저 *캡션* 기준이다.
export const type = {
  caption: { size: 11, lineHeight: 14, letterSpacing: 0.1 },
  body: { size: 15, lineHeight: 23, letterSpacing: 0 },
  title: { size: 18, lineHeight: 24, letterSpacing: 0 },
  display: { size: 22, lineHeight: 28, letterSpacing: -0.15 },
  /**
   * 상단 바 1층(유틸 바)의 작은 글자 전용 — 시안 실측 13px
   * caption(11)은 언어·로그인 같은 누를 수 있는 글자로 쓰기에 작고, body(15)는 유틸 바
   * 높이 36 안에서 본문처럼 무겁다. 인계 문서가 「caption 을 쓰거나 13 을 추가」로 열어 둔
   * 자리라 추가했다 — 화면에 숫자를 직접 쓰는 것은 파트 규칙이 금지한다.
   */
  util: { size: 13, lineHeight: 18, letterSpacing: 0 },
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
