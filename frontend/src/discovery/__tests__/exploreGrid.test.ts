// 폭이 정하는 열 수와 카드 폭.
//
// 이 계산이 화면 안에 있었으면 재려면 그려야 하고, 폭 하나하나를 그려 보는 일은
// 실제로 아무도 안 한다 — 그래서 그 자리가 조용히 틀린다. 떼어 두고 여기서 잰다.
import { EXPLORE_GRID_GAP, exploreCardWidth, exploreColumns } from '@/discovery/exploreGrid';
import { MAX_CONTENT_WIDTH, MAX_SPLIT_WIDTH } from '@/components/Screen';
import { gutter } from '@/design/tokens';

describe('로컬 탐색 격자', () => {
  it.each([
    [360, 2], [420, 2], [599, 2],
    [600, 3], [800, 3], [1023, 3],
    [1024, 4], [1440, 4], [1920, 4],
  ])('폭 %i 에서 %i 열', (width, columns) => {
    expect(exploreColumns(width)).toBe(columns);
  });

  it('한 줄에 딱 들어간다 — 카드와 간격을 더해도 쓸 수 있는 폭을 안 넘는다', () => {
    for (const width of [360, 420, 600, 800, 1024, 1440, 1920]) {
      const columns = exploreColumns(width);
      const cap = width >= 1024 ? MAX_SPLIT_WIDTH : width >= 600 ? MAX_CONTENT_WIDTH : width;
      const available = Math.min(width, cap) - gutter * 2;
      const used = exploreCardWidth(width) * columns + EXPLORE_GRID_GAP * (columns - 1);
      expect(used).toBeLessThanOrEqual(available);
      // 한 열이 더 들어갈 만큼 남으면 그건 열 수가 틀린 것이다.
      expect(available - used).toBeLessThan(exploreCardWidth(width));
    }
  });

  it('🔴 넓은 화면에서 카드가 한없이 커지지 않는다 — 내용은 최대 폭에서 멈춘다', () => {
    // 상한을 빼먹으면 1920 에서 카드가 화면 끝까지 벌어진다.
    expect(exploreCardWidth(1920)).toBe(exploreCardWidth(1440));
  });

  it('폰에서 카드가 손가락으로 누를 만하다', () => {
    expect(exploreCardWidth(360)).toBeGreaterThan(120);
  });

  it('재는 방법 자체가 살아 있다 — 폭이 0 이면 0 이다', () => {
    // 이 줄이 없으면 exploreCardWidth 가 늘 같은 수를 돌려줘도 위 시험이 통과한다.
    expect(exploreCardWidth(0)).toBe(0);
  });
});
