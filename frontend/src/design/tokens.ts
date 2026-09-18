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
    /** 카드 바탕 */
    card: '#ffffff',
    /** 선택·강조 표면 — 오렌지 옅게, 파란 면색 미사용 */
    tint: '#fff1e8',
    /** 칩·보조 배지의 따뜻한 중립색 */
    soft: '#f8f3eb',
    /** 입력 필드·슬라이더 트랙·경계의 따뜻한 회색 */
    field: '#e4ddd3',
    /** 보조 입력·칩 바탕 통일값 */
    subtle: '#f6f5f2',
    /** 선택·안내 표면 — 오렌지 최옅 */
    warm: '#fff1e8',
    /** 카드 사이 중립 경계선 */
    border: '#e8e4dd',
  },

  action: {
    /** 주 CTA — 네이비 */
    primary: '#0b1d3a',
    /** 보조 행동·작은 강조 — 오렌지 */
    secondary: '#f26532',
    brand: '#f26532',
    /** 현장 기능 전폭 CTA — 네이비 */
    field: '#0b1d3a',
  },

  text: {
    /** 제목 — Figma 다섯 변종 중 최다 사용값(42회)으로 통일 */
    heading: '#152238',
    /** 본문 설명 */
    body: '#667882',
    /** 보조·비활성 */
    muted: '#64748b',
    /** 버튼·이미지 위 글자 */
    onAction: '#ffffff',
    /** 네이비 표면 위 보조 설명 */
    onDarkMuted: '#eee8df',
    /** 눈썹 문구·섹션 라벨 — 오렌지 */
    eyebrow: '#f26532',
    /** 수치·시각 강조 — 오렌지 */
    accent: '#f26532',
  },

  state: {
    /** 경고·알림 점 */
    danger: '#e85d5b',
    /** 성공 배경 */
    successBg: '#e7f8ef',
    /** 완료 표시 글자·아이콘 — successBg 는 배경 전용 */
    success: '#30a687',
    /** 「보통」 혼잡도 배지 배경 — success·danger 중간 상태 */
    warningBg: '#fff3d7',
    /** 「보통」 배지 글자색 */
    warning: '#a46700',
    /** danger 배지 배경 — success·warning 과 배경+글자 짝 맞춤 */
    dangerBg: '#fff0ee',
    /** 별점 금색 — 평점 전용 */
    rating: '#ffa11f',
  },
} as const;

// Pretendard 한 벌(SIL OFL). RN 은 굵기별 파일을 별도 family 이름으로 등록해야 한다 —
// fontWeight 숫자만 주면 대부분 플랫폼에서 Regular 로 보인다.
export const fontFamily = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  bold: 'Pretendard-Bold',
} as const;

// 웹 전용 대체 폰트 목록. Pretendard 에 중국어 글자가 없어 웹(react-native-web)은 CSS 대체가
// 필요하고, 네이티브는 OS 가 CJK 로 넘긴다 — 네이티브에 쉼표 목록을 주면 그 글자를 폰트
// 이름으로 찾다가 깨진다. 화면은 fontFamily 가 아니라 fontFamilyStack 을 쓴다.
const CJK_FALLBACK_WEB = ', "Apple SD Gothic Neo", "Noto Sans KR", "Noto Sans SC", "Noto Sans TC", "PingFang SC", "Microsoft YaHei", "Malgun Gothic", sans-serif';

export const fontFamilyStack = Platform.select({
  web: {
    regular: `${fontFamily.regular}${CJK_FALLBACK_WEB}`,
    medium: `${fontFamily.medium}${CJK_FALLBACK_WEB}`,
    bold: `${fontFamily.bold}${CJK_FALLBACK_WEB}`,
  },
  default: fontFamily,
}) as { regular: string; medium: string; bold: string };

// body 15px — Figma 실측(10~13px)을 안 따른다. 접근성이 이 앱의 차별점인데 본문 11px 은
// 그 자체로 모순이고, iOS 11pt·Android 12sp 는 캡션 기준이다.
export const type = {
  caption: { size: 11, lineHeight: 14, letterSpacing: 0.1 },
  body: { size: 15, lineHeight: 23, letterSpacing: 0 },
  title: { size: 18, lineHeight: 24, letterSpacing: 0 },
  display: { size: 22, lineHeight: 28, letterSpacing: -0.15 },
  /** 상단 유틸 바 전용 13px — caption(11)은 누르기에 작고 body(15)는 높이 36 에 무겁다 */
  util: { size: 13, lineHeight: 18, letterSpacing: 0 },
  /** Welcome 히어로 브랜드 타이틀 전용 34px */
  hero: { size: 34, lineHeight: 40, letterSpacing: -0.25 },
} as const;

// 반경 4종으로 통일. Figma 의 10종과 14.758… 같은 값은 크기 조정 흔적이고, 34 는 기기
// 목업 프레임 반경이라 앱에서 안 쓴다.
export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  full: 999,
} as const;

// 4px 그리드. Figma 의 5·7·9·13·18·19px 은 시스템이 아니라 손으로 찍은 값.
export const spacing = {
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  6: 24,
  8: 32,
} as const;

/** 화면 좌우 여백 — 4px 그리드에 맞춘 24 */
export const gutter = 24;

/**
 * 데스크톱 좌우 여백 — 상단 바(TopNav)와 같은 40.
 *
 * spacing 에 40 이 없어서 따로 둔다. 4px 그리드에는 맞지만 spacing 은 「요소 사이 간격」의
 * 눈금이고 이것은 「화면 가장자리까지의 거리」다. 같은 표에 섞으면 요소 사이에도 40 이
 * 쓰이기 시작한다. 그래서 gutter 와 나란히 둔다.
 */
export const desktopGutter = 40;

export type TypeToken = keyof typeof type;
export type RadiusToken = keyof typeof radius;
export type SpacingToken = keyof typeof spacing;
