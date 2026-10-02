// 데스크톱 판 일정의 카드 열·지도 칸 (S15P21E201-1947).
// 🔴 갤럭시 탭 세로(본문 약 690)에서 카드 넷을 한 줄에 억지로 넣어 한 장이 45px 이 되고 글자가 세로로 쌓였다.
import { desktopCardGrid } from '@/trip/page/desktopGrid';

describe('desktopCardGrid', () => {
  it.each([
    // [본문 폭, 지도 폭 이하, 열 수]
    [688, 300, 2], // 탭 세로 753 — 좌우 여백을 뺀 본문
    [1125, 440, 3], // 탭 가로 1205
    [1360, 440, 4], // PC 1440 (시안)
  ])('본문 %d → 지도 ≤ %d · 카드 %d열', (body, mapMax, cols) => {
    const g = desktopCardGrid(body);
    expect(g.mapWidth).toBeLessThanOrEqual(mapMax);
    expect(g.columns).toBe(cols);
  });

  it('카드 한 장은 언제나 읽을 만한 폭 이상이다', () => {
    for (const body of [600, 640, 688, 760, 900, 1024, 1125, 1360, 1840]) {
      const g = desktopCardGrid(body);
      expect(g.cardWidth).toBeGreaterThanOrEqual(150);
      expect(g.mapWidth).toBeGreaterThanOrEqual(260);
    }
  });

  it('처음 재기 전(0)에는 시안 값을 준다', () => {
    expect(desktopCardGrid(0)).toEqual(expect.objectContaining({ mapWidth: 440, columns: 4 }));
  });
});
