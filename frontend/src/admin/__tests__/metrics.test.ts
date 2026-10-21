import { formatRate, linePoints } from '../metrics';

describe('운영 지표 화면 도우미 (S15P21E201-2010)', () => {
  it('선 그래프 점 — 첫 값은 왼쪽 끝, 마지막 값은 오른쪽 끝, 최대값이 맨 위', () => {
    expect(linePoints([0, 5, 10], 100, 50)).toBe('0,50 50,25 100,0');
  });

  it('값이 모두 0이어도 0으로 나누지 않고 바닥에 눕는다', () => {
    expect(linePoints([0, 0], 100, 50)).toBe('0,50 100,50');
  });

  it('점이 하나면 가운데가 아니라 오른쪽 끝에 찍는다', () => {
    expect(linePoints([3], 100, 50)).toBe('100,0');
  });

  it('재방문율은 백분율 한 자리, 아직 못 재면 대시', () => {
    expect(formatRate(0.1234)).toBe('12.3%');
    expect(formatRate(null)).toBe('—');
  });
});
