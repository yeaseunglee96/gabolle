import { Platform } from 'react-native';

// Figma 실측 디자인 토큰. 색·숫자의 단일 출처 — 화면에서 직접 하드코딩 금지
//
// 🔴 2026-09-19 (S15P21E201-1343) — **부산은행 톤**으로 값을 통째로 바꿨다. 이름은 하나도
//    안 바꿨다. 화면 126개가 여기서만 색을 읽어 왔기 때문에(하드코딩 금지 규칙) 값만
//    갈아 끼우면 전 화면이 따라온다 — 그 규칙을 지켜 온 것이 여기서 값을 했다.
//
// 🔴 **이 배색이 지키는 규칙 여섯.** 값을 고치기 전에 읽어라. 어기면 색은 새것인데
//    화면은 옛것처럼 읽힌다.
//
//    1. 동백(action.primary) **채움은 화면당 하나** — 주 버튼. 둘째부터는 outline 이나
//       secondary 다. 빨강이 둘이면 사람은 어느 쪽이 「그다음에 할 일」인지 못 고른다
//    2. **선택 상태에 빨강을 쓰지 않는다** — 짙은 회색(action.secondary). 안 그러면
//       「고른 것」과 「눌러야 할 것」이 같은 색이 된다
//    3. **카드에 선·그림자를 두지 않는다.** 바탕(canvas)과 흰 카드의 차이로만 뜬다.
//       예외는 둘뿐 — 하단 탭바 그림자, 진행 중 카드의 붉은 링
//    4. **state.dangerBg 는 경고·제외 전용.** 선택이나 안내 배경으로 쓰지 않는다
//    5. **text.eyebrow 는 회색이다.** 「● 진행 중」 같은 실시간 상태만 빨간 글자로 따로 준다
//    6. **state.dot 과 action.primary 를 섞지 않는다.** 점은 글자가 없는 곳에만

export const color = {
  brand: {
    /** 검정 — 글자·아이콘 선·로고 워드마크. 채움에는 action.* 를 쓴다 */
    navy: '#191919',
    // 🔴 2026-09-19 — brand.orange 를 **지웠다.** 값은 여기 있었다: '#F26532' → '#D83A48'.
    //    옛 배색에서 이 이름은 「아무 데나 쓰는 강조색」이었다. 새 값이 동백 빨강이 되면서
    //    그 쓰임이 규칙 위반이 됐고(채움은 화면당 하나), 이름을 남겨 두면 다음 사람이
    //    **옛 뜻으로 또 쓴다** — 그러면 화면은 멀쩡히 그려지고 규칙만 조용히 깨진다.
    //    빨강이 필요하면 무엇을 뜻하는지 골라라: action.primary(주 버튼) · action.outline
    //    (붉은 선) · state.dot(글자 없는 점) · state.danger(경고 글자).
    /** 모달 카드 배경 — 흰색 */
    ivory: '#FFFFFF',
  },
  /** 화면 바탕 — 카드는 이 차이로만 뜬다. 여기가 흰색이 되면 카드가 사라진다 */
  canvas: '#F5F5F7',

  surface: {
    /** 카드 바탕 — 순백. 선도 그림자도 없다 */
    card: '#FFFFFF',
    /** 선택·안내 표면 — 연회색. 🔴 빨간 틴트가 아니다(그건 state.dangerBg, 경고 전용) */
    tint: '#F0F0F3',
    /** 비활성 · 작은 보조 알약 · 코드 블록 */
    soft: '#E9E9EC',
    /** 입력 선 · 미선택 칩 선 · 슬라이더 트랙 · 토글 꺼짐 */
    field: '#DADCE2',
    /** 정보 배지(무료·고정) · 활성 단계 행 */
    subtle: '#F0F0F3',
    /** = tint */
    warm: '#F0F0F3',
    /** 🔴 목록 행 사이 구분선. **카드 테두리에는 쓰지 않는다** */
    border: '#EBEBEF',
  },

  action: {
    /** 주 CTA — 동백. 🔴 화면당 하나 */
    primary: '#D83A48',
    /** 보조 행동·선택 상태 — 짙은 회색 */
    secondary: '#2B2B2E',
    brand: '#D83A48',
    /** 현장 기능 전폭 CTA — 짙은 회색. 동백이 아니다 */
    field: '#2B2B2E',
    /** 연회색 채움 — 「다음에 하기」류. 옛 ghost 자리 */
    tertiary: '#E9E9EC',
    /** 붉은 선 버튼 — 큰 면적이 부담스러울 때 채움 대신 */
    outline: '#D83A48',
  },

  text: {
    heading: '#191919',
    body: '#444444',
    muted: '#6F6F6F',
    /** 버튼·이미지 위 글자 */
    onAction: '#FFFFFF',
    /** 어두운 카드 위 보조 글자 */
    onDarkMuted: '#DADADF',
    /** 🔴 눈썹 — **회색이다.** 빨간 눈썹은 「● 진행 중」 같은 실시간 상태에만 직접 준다 */
    eyebrow: '#6F6F6F',
    /** 수치 강조 — 검정 굵게. 빨간 숫자는 경고만 */
    accent: '#191919',
    /** 비활성 탭 글자 */
    inactiveTab: '#8B8B8B',
  },

  state: {
    danger: '#D83A48',
    /** 🔴 글자 없는 점·도장 전용 — 알림 점 · 하단 탭 점 · 현재 위치 · 프린터 램프 */
    dot: '#F25454',
    /** 🔴 경고·제외 전용 배경. 선택이나 안내에 쓰지 않는다 */
    dangerBg: '#FEF0F1',
    success: '#2E9E5B',
    successBg: '#E7F5EC',
    warning: '#B06A00',
    warningBg: '#FFF3DC',
    /** 별점 — warning 과 통일 */
    rating: '#B06A00',
    /** 정보 파랑 — 링크성 안내 */
    info: '#2C64B5',
    infoBg: '#EAF2FC',
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
//
// 🔴 2026-09-19 — 한 단계씩 올렸다(caption 11→13 · util 13→14 · display 22→26). **색만
//    바뀐 것이 아니라 줄바꿈이 달라진다** — 좁은 화면에서 넘치는 자리가 생길 수 있다.
export const type = {
  caption: { size: 13, lineHeight: 18, letterSpacing: 0 },
  body: { size: 15, lineHeight: 23, letterSpacing: 0 },
  title: { size: 18, lineHeight: 24, letterSpacing: 0 },
  display: { size: 26, lineHeight: 34, letterSpacing: -0.2 },
  /** 상단 유틸 바 전용 — caption 은 누르기에 작고 body 는 높이 36 에 무겁다 */
  util: { size: 14, lineHeight: 20, letterSpacing: 0 },
  /** Welcome 히어로 브랜드 타이틀 전용 34px */
  hero: { size: 34, lineHeight: 40, letterSpacing: -0.25 },
  /** 눈썹 · 탭 라벨 · 배지 — caption 보다 작고 자간이 넓다 */
  micro: { size: 12, lineHeight: 16, letterSpacing: 0.2 },
} as const;

// 반경. Figma 의 10종과 14.758… 같은 값은 크기 조정 흔적이고, 34 는 기기 목업 프레임
// 반경이라 앱에서 안 쓴다. chip 은 높이 36 알약 전용이다.
export const radius = {
  sm: 8,
  md: 14,
  chip: 18,
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
