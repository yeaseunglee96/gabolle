// S15P21E201-1870 — 실기기 QA 두 가지.
// (1) 코스 확정은 추천 화면을 **갈아끼워야**(replace) 한다. push 면 확정 전 화면이 뒤에 남는다.
// (2) 피드의 떠 있는 「지도 표시하기」 단추 줄만큼 목록 끝을 더 비운다.
import { screenBottomPadding } from '../components/Screen';
import { TAB_BAR_HEIGHT, tabBarBottomMargin } from '../components/TabBar';
import { spacing } from '../design/tokens';

declare const require: any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const read = (p: string): string => readFileSync(require('path').join(__dirname, '..', '..', p), 'utf8');

describe('코스 확정 뒤 뒤로 가기', () => {
  const src = read('app/trips/[id]/recommendations.tsx');
  const build = src.slice(src.indexOf('const build = async'), src.indexOf('const header ='));

  it('확정한 일정으로 갈 때 push 를 쓰지 않는다', () => {
    expect(build).not.toMatch(/router\.push\(/);
  });

  it('이름 묻기 경로와 기본 경로 모두 replace 로 간다', () => {
    expect(build).toMatch(/router\.replace\(`\$\{target\}\?name=1`\)/);
    expect(build).toMatch(/router\.replace\(target\)/);
  });
});

describe('피드 떠 있는 단추가 카드를 가리지 않는다', () => {
  it('Screen 은 떠 있는 단추 줄 높이만큼 아래를 더 비운다', () => {
    const dock = 64;
    expect(screenBottomPadding(48, true, dock)).toBe(spacing[8] + TAB_BAR_HEIGHT + tabBarBottomMargin(48) + dock);
    expect(screenBottomPadding(48, true, dock) - screenBottomPadding(48, true)).toBe(dock);
  });

  it('피드는 단추를 띄울 때 그 높이를 Screen 에 넘긴다', () => {
    const feed = read('app/(tabs)/feed.tsx');
    expect(feed).toMatch(/floatingDockHeight=\{composeEntry === 'headerButton' \? FEED_FAB_DOCK_HEIGHT : 0\}/);
    expect(feed).toMatch(/FEED_FAB_DOCK_HEIGHT = 48 \+ spacing\[4\]/);
  });
});
