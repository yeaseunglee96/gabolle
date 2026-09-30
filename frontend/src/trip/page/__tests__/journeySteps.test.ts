import type { RouteDirections } from '@/map/routeDirections';

import { journeyOf } from '../journeySteps';

const ko = (text: string) => text;
const en = (_ko: string, text: string) => text;

const base: Omit<RouteDirections, 'steps' | 'mode' | 'durationMin'> = {
  distanceM: 5000, taxiFareKrw: null, tollFareKrw: null, transferCount: 0, estimated: false, estimateReason: null, provider: 'TRANSIT_NETWORK', path: [],
};

// 서버 TransitRouteAdapter.toLeg 가 만드는 문장 그대로
const bus: RouteDirections = {
  ...base, mode: 'TRANSIT', durationMin: 23,
  steps: [{ name: '139', guidance: '해운대해수욕장에서 139을(를) 타고 동백섬입구에서 내립니다.', distanceM: 2000, durationMin: 18 }],
};

describe('여정 한 줄의 걸음 문장 — S15P21E201-1884', () => {
  it('버스 — 정류장까지 걷고, 몇 번을 타서 어디서 내리고, 내려서 걷는다', () => {
    const journey = journeyOf(bus, '동백섬횟집', 'ko', ko);
    expect(journey.steps.map((step) => step.text)).toEqual([
      '해운대해수욕장 정류장까지 걸어가요',
      '139번 버스를 타고 동백섬입구에서 내려요',
      '내려서 동백섬횟집까지 걸어요',
    ]);
    // 한국어 화면에는 표지판 딱지가 없다 — 이미 같은 글자다
    expect(journey.steps.every((step) => step.sign === undefined)).toBe(true);
  });

  it('🔴 걷는 시간은 전체에서 탄 시간을 뺀 것 — 앞뒤로 반씩, 문장에는 분을 적지 않는다', () => {
    const journey = journeyOf(bus, '동백섬횟집', 'ko', ko);
    expect(journey.bar).toEqual([{ kind: 'walk', minutes: 2.5 }, { kind: 'bus', minutes: 18 }, { kind: 'walk', minutes: 2.5 }]);
    expect(journey.steps[0].text).not.toMatch(/분/);
  });

  it('외국어 화면 — 정류장 이름은 로마자, 표지판의 한국어를 따로 붙인다', () => {
    const journey = journeyOf(bus, 'Dongbaekseom Hoetjip', 'en', en);
    expect(journey.steps[0]).toMatchObject({ text: 'Walk to the Haeundaehaesuyokjang stop', sign: '해운대해수욕장' });
    expect(journey.steps[1]).toMatchObject({ text: 'Take bus 139 · get off at Dongbaekseomipgu', sign: '동백섬입구' });
  });

  it('지하철 — 타는 방면과 정거장 수, 역 이름은 공식 영문 역명', () => {
    const subway: RouteDirections = {
      ...base, mode: 'TRANSIT', durationMin: 20,
      steps: [{ name: '1호선', guidance: '서면역(1호선)에서 1호선을(를) 타고 자갈치역(1호선)에서 내립니다.', distanceM: 5000, durationMin: 14 }],
    };
    const ko1 = journeyOf(subway, '자갈치시장', 'ko', ko);
    expect(ko1.steps[0].text).toBe('서면역까지 걸어가요');
    expect(ko1.steps[1].chip).toBe('1호선');
    expect(ko1.steps[1].text).toMatch(/^다대포해수욕장 방면을 타고 \d+정거장 · 자갈치역에서 내려요$/);
    const en1 = journeyOf(subway, 'Jagalchi Market', 'en', en);
    expect(en1.steps[1].text).toMatch(/towards Dadaepo Beach/);
    expect(en1.steps[1].sign).toBe('자갈치');
  });

  it('탄 구간이 없으면(걷기·어림) 걷기 한 줄', () => {
    const walk: RouteDirections = { ...base, mode: 'WALK', durationMin: 12, steps: [] };
    const journey = journeyOf(walk, '광안리 해변', 'ko', ko);
    expect(journey.steps).toHaveLength(1);
    expect(journey.steps[0].text).toBe('광안리 해변까지 걸어서 12분');
  });
});
