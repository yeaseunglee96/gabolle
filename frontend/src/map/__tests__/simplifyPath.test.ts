// 경로 선 단순화 — S15P21E201-1656.
//
// 🔴 이 시험이 지키는 것: 줌이 멀면 화면에서 안 보이는 작은 꺾임(나들목 고리·램프, 촘촘한 꺾임점)을 덜어 낸다.
//    처음과 끝은 언제나 남고, 허용치보다 크게 꺾이는 곳은 남는다. 허용치가 0 이면 그대로다.
import { simplifyPath } from '../simplifyPath';

const at = (latitude: number, longitude: number) => ({ latitude, longitude });
// 경도 0.0001 도 ≈ 9m(부산 위도) · 위도 0.0001 도 ≈ 11m
const zigzag = Array.from({ length: 41 }, (_, i) => at(35.15 + (i % 2) * 0.0001, 129.05 + i * 0.0005));

describe('경로 선 단순화', () => {
  it('🔴 허용치보다 작은 흔들림은 덜어 내고 처음과 끝은 남긴다', () => {
    const out = simplifyPath(zigzag, 30);
    expect(out).toHaveLength(2);
    expect(out[0]).toBe(zigzag[0]);
    expect(out[1]).toBe(zigzag[zigzag.length - 1]);
  });

  it('허용치보다 크게 꺾이는 곳은 남긴다', () => {
    const corner = [at(35.15, 129.05), at(35.15, 129.06), at(35.16, 129.06)];
    expect(simplifyPath(corner, 30)).toEqual(corner);
  });

  it('가까이 보면(허용치가 작으면) 흔들림을 그대로 둔다', () => {
    expect(simplifyPath(zigzag, 1)).toHaveLength(zigzag.length);
  });

  it('점이 셋보다 적거나 허용치가 0 이면 그대로 돌려준다', () => {
    const two = [at(35.15, 129.05), at(35.16, 129.06)];
    expect(simplifyPath(two, 100)).toBe(two);
    expect(simplifyPath(zigzag, 0)).toBe(zigzag);
  });
});
