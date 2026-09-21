import { screenBottomPadding } from '../Screen';
import { bottomBarClearance, tabBarBottomMargin } from '../TabBar';

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
