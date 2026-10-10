// 루트 포스트(S15P21E201-2027) — 「이 루트로 여행 만들기」가 날·순서를 어떻게 옮기는지, 썸네일 좌표, 표시 문구.
import { distanceKm, durationParts, copyPlan, type RoutePostStop } from '../routePosts';
import { project } from '../RouteThumb';

const stop = (seq: number, dayIndex: number, placeId: string): RoutePostStop => ({ seq, dayIndex, placeId, name: placeId, lat: 35, lng: 129 });

describe('copyPlan', () => {
  it('루트의 날을 0부터 다시 매기고 그날 순서를 지킨다', () => {
    const plan = copyPlan({ stops: [stop(0, 2, 'a'), stop(1, 2, 'b'), stop(2, 3, 'c')] });
    expect(plan).toEqual([{ dayIndex: 0, placeIds: ['a', 'b'] }, { dayIndex: 1, placeIds: ['c'] }]);
  });

  it('🔴 같은 날 같은 곳이 두 번이면 한 번만 넣는다 — 서버가 409 로 막는다', () => {
    const plan = copyPlan({ stops: [stop(0, 0, 'a'), stop(1, 0, 'b'), stop(2, 0, 'a')] });
    expect(plan[0].placeIds).toEqual(['a', 'b']);
  });

  it('seq 순서대로 넣는다 — 받은 배열 순서가 아니라', () => {
    const plan = copyPlan({ stops: [stop(1, 0, 'second'), stop(0, 0, 'first')] });
    expect(plan[0].placeIds).toEqual(['first', 'second']);
  });
});

describe('표시 값', () => {
  it('모르는 시간·거리는 지어내지 않는다', () => {
    expect(durationParts(null)).toBeNull();
    expect(durationParts(0)).toBeNull();
    expect(distanceKm(null)).toBeNull();
  });
  it('시간과 거리를 나눈다', () => {
    expect(durationParts(150)).toEqual({ h: 2, m: 30 });
    expect(distanceKm(3240)).toBe('3.2');
    expect(distanceKm(12_400)).toBe('12');
  });
});

describe('RouteThumb project', () => {
  it('모든 점이 상자 안에 들어오고 북쪽이 위다', () => {
    const pts: Array<[number, number]> = [[35.10, 129.00], [35.20, 129.10]];
    const at = project(pts, 320, 160, 18);
    for (const p of pts) {
      const [x, y] = at(p);
      expect(x).toBeGreaterThanOrEqual(0); expect(x).toBeLessThanOrEqual(320);
      expect(y).toBeGreaterThanOrEqual(0); expect(y).toBeLessThanOrEqual(160);
    }
    expect(at(pts[1])[1]).toBeLessThan(at(pts[0])[1]);
  });
});
