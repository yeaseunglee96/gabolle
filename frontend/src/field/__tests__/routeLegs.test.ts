import { estimateReasonText, formatDuration, modeFareLine, modeFromTravelModes, parseTransitGuidance, parseTravelMode, toMapPath, transitStepKind } from '@/field/routeLegs';
import type { RouteDirections } from '@/map/routeDirections';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;

const base: RouteDirections = { mode: 'CAR', distanceM: 23181, durationMin: 42, taxiFareKrw: 22800, tollFareKrw: 2400, transferCount: null, estimated: false, estimateReason: null, provider: 'KAKAO_MOBILITY', path: [], steps: [] };

describe('경로 상세 재료 (S15P21E201-1831)', () => {
  it('서버 경로는 [경도, 위도] — 지도는 {위도, 경도}', () => {
    expect(toMapPath([[129.1604, 35.1587]])).toEqual([{ latitude: 35.1587, longitude: 129.1604 }]);
    expect(toMapPath(null)).toEqual([]);
  });

  it('수단 글자 — 모르면 null', () => {
    expect(parseTravelMode('TRANSIT')).toBe('TRANSIT');
    expect(parseTravelMode('BUS')).toBeNull();
    expect(modeFromTravelModes(['BUS', 'SUBWAY'])).toBe('TRANSIT');
    expect(modeFromTravelModes(['PRIVATE_CAR'])).toBe('CAR');
    expect(modeFromTravelModes(['WALK'])).toBe('WALK');
    expect(modeFromTravelModes([])).toBeNull();
  });

  it('택시비는 자동차에만 — 대중교통 요금은 지어내지 않는다', () => {
    expect(modeFareLine(base, ko)).toBe('택시 약 22,800원');
    expect(modeFareLine({ ...base, mode: 'TRANSIT', taxiFareKrw: null }, ko)).toBeNull();
    expect(modeFareLine({ ...base, taxiFareKrw: null }, ko)).toBeNull();
  });

  it('걸리는 시간 — 한 시간이 넘으면 시간·분', () => {
    expect(formatDuration(42, ko)).toBe('42분');
    expect(formatDuration(247, ko)).toBe('4시간 7분');
    expect(formatDuration(120, en)).toBe('2 h');
  });

  it('안내 문장에서 타는 곳·내리는 곳을 뽑는다', () => {
    expect(parseTransitGuidance('해운대해수욕장입구에서 1003번을(를) 타고 자갈치역.비프광장에서 내립니다.')).toEqual({ kind: 'ride', from: '해운대해수욕장입구', to: '자갈치역.비프광장' });
    expect(parseTransitGuidance('서면역에서 부전시장까지 걸어서 갈아탑니다.')).toEqual({ kind: 'walk', from: '서면역', to: '부전시장' });
    expect(parseTransitGuidance('다른 모양의 문장')).toBeNull();
  });

  it('단계 종류와 어림 사유', () => {
    expect(transitStepKind({ name: '도보', guidance: '', distanceM: 0, durationMin: 0 })).toBe('walk');
    expect(transitStepKind({ name: '2호선', guidance: '', distanceM: 0, durationMin: 0 })).toBe('subway');
    expect(transitStepKind({ name: '1003번', guidance: '', distanceM: 0, durationMin: 0 })).toBe('bus');
    expect(estimateReasonText('시각표가 없어 노선의 평균 배차간격과 정거장 수로 계산한 값입니다.', en)).toMatch(/timetable/);
    expect(estimateReasonText('처음 보는 사유', en)).toBe('처음 보는 사유');
    expect(estimateReasonText(null, en)).toBeNull();
  });
});
