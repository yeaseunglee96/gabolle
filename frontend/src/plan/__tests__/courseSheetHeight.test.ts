// 추천 코스 시트는 화면 높이를 넘지 않는다 (S15P21E201-1788).
//
// 🔴 가로에서 터진다. 아이폰 가로는 높이가 402 뿐인데 시트 기본값은 560 이다.
//    `bottom` 기준으로 560 을 세우면 위로 160 넘게 넘쳐서 코스 제목과
//    「해당 코스 일정 보기」 단추가 화면 밖으로 나간다 — 코스를 못 고른다.
//
//    TabBar.tsx 가 TAB_BAR_SHEET_HEIGHT 옆에 「화면마다 다르다 … 여기 박아 두면
//    두 화면 중 하나는 반드시 틀린다」고 적어 뒀다. 마이페이지는 myPageSheetHeight()
//    로 화면에 맞추는데 이 화면만 상수를 그대로 썼다.
import { TAB_BAR_HEIGHT, TAB_BAR_SHEET_HEIGHT, tabBarBottomMargin } from '@/components/TabBar';
import { spacing } from '@/design/tokens';

// 앱 tsconfig 에는 node 타입이 없다 — 다른 소스 검사 시험과 같은 방식으로 읽는다
// (S15P21E201-1782 에서 맞춰 둔 모양).
declare const __dirname: string;

const { readFileSync } = require('fs');
const { join } = require('path');

/** 화면에 있는 것과 같은 식 — 여기서 어긋나면 시험이 거짓으로 통과한다. */
const sheetHeight = (screenHeight: number, insetTop: number, insetBottom: number) =>
  Math.min(
    TAB_BAR_SHEET_HEIGHT,
    Math.max(TAB_BAR_HEIGHT, screenHeight - insetTop - tabBarBottomMargin(insetBottom) - spacing[2]),
  );

describe('추천 코스 시트 높이', () => {
  it('화면 높이에서 계산한다 — 상수를 그대로 쓰지 않는다', () => {
    const source = readFileSync(join(__dirname, '..', '..', '..', 'app', 'trips', '[id]', 'recommendations.tsx'), 'utf8') as string;
    // 시트가 자랄 때의 목표 높이가 상수 그대로면 안 된다.
    expect(source).not.toMatch(/outputRange:\s*\[TAB_BAR_HEIGHT,\s*TAB_BAR_SHEET_HEIGHT\]/);
    expect(source).toContain('const sheetHeight = Math.min(');
    // 화면 높이를 실제로 읽어야 한다 — useLayout() 에서 height 를 꺼낸다.
    expect(source).toMatch(/const \{[^}]*height[^}]*\} = useLayout\(\)/);
  });

  it('아이폰 가로(402) — 시트가 화면 안에 들어간다', () => {
    const screen = 402;
    const insetTop = 0;
    const insetBottom = 21; // 가로에서의 홈 인디케이터
    const height = sheetHeight(screen, insetTop, insetBottom);
    expect(height).toBeLessThan(TAB_BAR_SHEET_HEIGHT);
    // 시트는 bottom 기준으로 서므로, 높이 + 아래여백이 화면을 넘으면 위가 잘린다.
    expect(height + tabBarBottomMargin(insetBottom)).toBeLessThanOrEqual(screen);
  });

  it('세로(874) — 예전 값 그대로다. 가로만 고치고 세로는 안 건드린다', () => {
    expect(sheetHeight(874, 62, 34)).toBe(TAB_BAR_SHEET_HEIGHT);
  });

  it('아주 낮은 화면에서도 접힌 막대보다 작아지지 않는다', () => {
    expect(sheetHeight(120, 0, 0)).toBeGreaterThanOrEqual(TAB_BAR_HEIGHT);
  });
});
