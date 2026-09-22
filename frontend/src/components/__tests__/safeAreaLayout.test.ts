import { screenBottomPadding } from '../Screen';
import { bottomBarClearance, tabBarBottomMargin } from '../TabBar';
import { myPageSheetHeight } from '@/me/MyPageSheet';


// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/homeHeaderBell.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

function read(relative: string): string {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', '..', relative), 'utf8') as string;
}

describe('mobile safe-area layout', () => {
  // 탭바가 떠 있게 되면서 레이아웃 자리를 안 먹는다. 그래서 화면이 그만큼을 대신
  // 비워야 한다 — 안 비우면 스크롤 맨 끝 내용이 알약 밑에 깔린 채 드러낼 방법이 없다.
  // 기본 여백 32 + 알약 높이 64 + 알약 아래 간격 max(8, 48) = 144
  it('reserves room for the floating tab bar', () => {
    expect(screenBottomPadding(48, true)).toBe(32 + 64 + 48);
  });

  it('keeps the inset on screens without a tab bar', () => {
    expect(screenBottomPadding(48, false)).toBe(80);
  });

  it('uses the larger of the system inset and the minimum visual gap', () => {
    expect(tabBarBottomMargin(48)).toBe(48);
    expect(tabBarBottomMargin(0)).toBe(8);
  });
});

// ── 하단 고정 줄이 탭바·탐색줄 뒤로 들어가던 것 (S15P21E201-1444) ────────────────
//
// 🔴 2026-09-21 실기(SM-G973N, 1080x2280, versionCode 29). 일정 화면의 「순서 수정」이
//    탭바와 3단추 탐색줄 뒤에 통째로 깔려 «누를 방법이 없었다». 추천 코스의 하단 바는
//    bottom: 96 으로 어림해 두어 아랫단이 탭바에 덮였다. 안전영역이 48 인 기기에서는
//    탭바 윗변이 64 + 48 = 112 라 96 으로는 모자란다.
describe('bottom bars clear the floating tab bar', () => {
  it('reserves the tab bar height, the safe area and a visual gap', () => {
    expect(bottomBarClearance(48)).toBe(64 + 48 + 8);
    expect(bottomBarClearance(0)).toBe(64 + 8 + 8);
  });

  // 🔴 되돌려도 화면은 «그려지므로» 눈으로는 안 잡힌다 — 단추가 탭바 뒤에 있을 뿐이다.
  //    그래서 두 화면이 그 계산을 실제로 쓰는지를 잰다.
  it('is what the itinerary reorder bar and the course bar use', () => {
    const itinerary = read('app/trips/[id]/itinerary.tsx');
    expect(itinerary).toContain('paddingBottom: bottomBarClearance(insets.bottom)');
    expect(itinerary).not.toContain('bottomBar: { paddingHorizontal: gutter, paddingBottom: spacing[2] }');

    // 🔴 2026-09-22 (S15P21E201-1467) — 추천 화면의 탭바는 «치워지고»(hidden) 코스 바가
    //    그 자리에 선다. 그래서 이 화면은 탭바 «뒤» 를 비우는 계산(bottomBarClearance)이
    //    아니라, 탭바가 서던 바로 그 자리(tabBarBottomMargin)를 쓴다. 둘을 같이 쓰면
    //    바가 한 칸 떠서 아래에 빈 띠가 남는다.
    const recommendations = read('app/trips/[id]/recommendations.tsx');
    expect(recommendations).toContain('<TabBar active="map" hidden />');
    expect(recommendations).toContain('bottom: tabBarBottomMargin(insets.bottom)');

    // 코스 바는 흐름 밖이라 목록의 마지막 카드를 덮는다. 바가 서는 높이만큼 늘 비운다 —
    //    바는 고르기 전에도 서 있으므로 이 자리도 «늘» 있어야 한다.
    expect(recommendations).toContain('height: TAB_BAR_HEIGHT + tabBarBottomMargin(insets.bottom) + spacing[2]');
    expect(recommendations).not.toContain('bottom: 96,');
  });
});

// ── 가로에서 탭바 알약이 328dp 로 남아 화면 한가운데 뜨던 것 (S15P21E201-1475) ──
//
// 🔴 2026-09-22 실기(SM-G973N 강제 가로 2280×1080 = 829dp×393dp, APK 29)와
//    iPhone 16 Pro build 39 QA(S15P21E201-1245 의 B-09).
//
// 🔴 처음엔 「width:'100%' 가 안 풀린다」로 봤는데 «틀렸다». 진짜 상한은 BAR_MAX_WIDTH(328)
//    이고, 세로(393dp)에서는 그 값이 화면을 거의 채워서 문제가 안 보였을 뿐이다. 그때
//    dock 의 alignItems:'center' 를 뺐다가 알약이 세로에서도 «왼쪽으로 쏠렸다» — 웹을
//    393×829 로 띄워서 잡았다(오른쪽에 49px 공백). 정렬은 되돌리고, 상한만 가로에서 푼다.
describe('tab bar pill width', () => {
  const source = read('src/components/TabBar.tsx');

  it('세로 상한은 시안의 328 그대로다', () => {
    expect(source).toContain('const BAR_MAX_WIDTH = 328;');
  });

  it('가로에서는 상한을 화면 폭에 맞춘다 — 바닥에 걸친 띠가 된다', () => {
    expect(source).toContain('function barMaxWidth(layoutWidth: number, isLandscape: boolean)');
    expect(source).toContain('if (!isLandscape) return BAR_MAX_WIDTH;');
    expect(source).toContain('Math.max(BAR_MAX_WIDTH, layoutWidth - spacing[4] * 2)');
  });

  it('알약은 가운데다 — 정렬을 빼면 상한에 걸려 왼쪽으로 쏠린다', () => {
    expect(block(source, '  dock: {')).toContain("alignItems: 'center'");
    expect(block(source, '  bar: {')).toContain("alignSelf: 'center'");
  });

  it('폭은 백분율이 아니라 화면에서 받은 숫자다', () => {
    // 부모가 alignItems:'center' 라 자식 가로 크기가 «자동»이 되고, 그러면 백분율이 안 풀린다.
    expect(block(source, '  bar: {')).not.toContain("width: '100%'");
    expect(source).toContain('width: Math.max(0, layoutWidth - spacing[4] * 2)');
  });
});

/** `키:` 로 시작하는 스타일 덩이 하나를 잘라 낸다 — 닫는 `},` 까지. */
function block(source: string, startsWith: string): string {
  const from = source.indexOf(startsWith);
  expect(from).toBeGreaterThan(-1);
  const to = source.indexOf(`
  },`, from);
  expect(to).toBeGreaterThan(from);
  return source.slice(from, to);
}

/**
 * 🔴 **마이페이지 시트가 상태바 뒤로 넘치던 것** — S15P21E201-1490(B-01).
 *
 * 시트는 화면 아래에서 자라므로 윗변은 `화면높이 − 아래여백 − 시트높이` 다. 전에는
 * 시트 높이를 `화면높이 − 40`(고정)으로 셌는데, 화면높이에는 상태바·다이내믹 아일랜드가
 * 들어 있어서 시트가 위로 넘쳤다. 그러면 시트의 첫 요소인 **손잡이와 「내리기」 단추가
 * 상태바 뒤로 숨는다** — 사용자는 닫는 수단이 없다고 읽는다.
 *
 * <p>🔴 **세로에서만 났다.** 가로는 위쪽 안전영역이 0 이라 우연히 맞아떨어졌다.
 * 실기기 확인(2026-09-22)에서도 가로에서는 손잡이가 보이고 세로에서는 안 보였다.
 * 그래서 이 시험은 **두 방향을 다 잰다** — 한 방향만 재면 이 결함이 그대로 통과한다.
 */
describe('마이페이지 시트는 상태바 뒤로 넘치지 않는다', () => {
  /** 시트의 윗변이 화면 어디에 오는가. 시트는 아래에서 자란다. */
  const sheetTopY = (h: number, top: number, bottom: number) =>
    h - tabBarBottomMargin(bottom) - myPageSheetHeight(h, top, bottom);

  const DEVICES: Array<[string, number, number, number]> = [
    // 이름, 화면높이, 위 안전영역, 아래 안전영역
    ['아이폰 16 Pro 세로', 874, 62, 34],
    ['🔴 아이폰 16 Pro 가로', 402, 0, 21],
    ['노치 없는 옛 기기 세로', 667, 20, 0],
    ['안드로이드 세로(제스처바)', 800, 24, 16],
  ];

  it.each(DEVICES)('%s — 윗변이 위쪽 안전영역 아래에 있다', (_name, h, top, bottom) => {
    expect(sheetTopY(h, top, bottom)).toBeGreaterThanOrEqual(top);
  });

  it('🔴 위쪽 안전영역이 커지면 시트도 그만큼 낮아진다 — 고정값이면 이 시험이 빨개진다', () => {
    const flat = myPageSheetHeight(874, 0, 34);
    const notched = myPageSheetHeight(874, 62, 34);

    expect(flat - notched).toBe(62);
  });

  it('아무리 좁아도 최소 높이는 지킨다 — 머리와 본문 한 줄은 들어가야 한다', () => {
    expect(myPageSheetHeight(300, 62, 34)).toBe(320);
  });
});
