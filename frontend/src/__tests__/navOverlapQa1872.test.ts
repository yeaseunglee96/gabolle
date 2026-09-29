// S15P21E201-1872 — 1870 뒤에도 실기에서 남은 셋.
// (1) 폰의 「내 일정 보기」는 TripPageMobile(useTripPage)을 연다. 거기서 「코스 A로 확정」하면 push 로 일정에 가서
//     확정 전 화면이 뒤에 남았다(1870 은 recommendations.tsx 의 옛 build 만 고쳤다). 확정 길은 replace 여야 한다.
// (2) 피드: 떠 있는 단추가 있으면 스크롤 창이 단추 윗변에서 끝나야 첫 화면에서도 카드를 안 가린다.
// (3) 순서 수정 중에도 탭바 높이만큼 창을 비운다.
import { floatingDockReserve } from '../components/Screen';
import { TAB_BAR_HEIGHT, tabBarBottomMargin } from '../components/TabBar';

declare const require: any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const read = (p: string): string => readFileSync(require('path').join(__dirname, '..', '..', p), 'utf8');

describe('코스 확정(useTripPage.confirm)은 확정 전 화면을 남기지 않는다', () => {
  const src = read('src/trip/page/useTripPage.ts');
  const confirm = src.slice(src.indexOf('const made = await ensureCourseItinerary'), src.indexOf('return {\n    page, load'));
  it('확정 길에 router.push 가 없다', () => {
    expect(confirm.length).toBeGreaterThan(100);
    expect(confirm).not.toMatch(/router\.push\(/);
  });
  it('이름 묻기와 기본 경로 모두 replace', () => {
    expect(confirm).toMatch(/router\.replace\(`\$\{path\}\?name=1`\)/);
    expect(confirm).toMatch(/router\.replace\(path\)/);
  });
});

describe('피드 떠 있는 단추는 첫 화면에서도 카드를 가리지 않는다', () => {
  it('단추 줄이 있으면 스크롤 창을 탭바+단추 줄 위에서 끝낸다', () => {
    expect(floatingDockReserve(48, true, 64)).toBe(TAB_BAR_HEIGHT + tabBarBottomMargin(48) + 64);
    expect(floatingDockReserve(48, true, 0)).toBe(0);
  });
  it('Screen 이 그 값을 ScrollView 의 marginBottom 으로 쓴다', () => {
    const screen = read('src/components/Screen.tsx');
    expect(screen).toMatch(/style=\{reserve > 0 \? \{ marginBottom: reserve \} : undefined\}/);
  });
});

describe('순서 수정 중 탭바가 화살표를 가리지 않는다', () => {
  it('reorderMode 에 탭바 높이 빈 줄을 둔다', () => {
    const it2 = read('app/trips/[id]/itinerary.tsx');
    expect(it2).toMatch(/reorderMode \? <View testID="itinerary-reorder-tabbar-spacer"[^>]*height: bottomBarClearance\(insets\.bottom\)/);
  });
});
