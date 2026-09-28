// 지도의 경사·그늘 겹 — S15P21E201-1569.
//
// 🔴 이 시험이 지키는 것은 「기준을 앱이 지어내지 않는다」와 「여행과 상관없는 길을 칠하지 않는다」다.
jest.mock('@/api/client', () => ({ API_BASE_URL: 'https://example.test' }));

import { areasFor, layerLines, type MobilityLayerFile } from '@/map/mobilityLayers';

const stop = { id: 's1', number: 1, name: '고재', latitude: 35.1653, longitude: 129.1586 };
// 파일의 한 줄: [경사 천분율, 그림자 백분율, [경도, 위도, …]]
const near = [129.1586, 35.1653, 129.1590, 35.1656];
const far = [129.0400, 35.1150, 129.0410, 35.1160]; // 부산역 근처 — 고재에서 10km
const file: MobilityLayerFile = {
  v: 1, area: 'HAEUNDAE', steepPermille: 83, shadowBasis: '건물 그림자 · 2026-07-15 · 9–18시 평균',
  segs: [[120, 10, near], [50, 80, near], [120, 90, far], [null, null, near]],
};

describe('경사·그늘 겹', () => {
  it('🔴 경사는 파일의 기준(8.33%) 이상만 — 앱에 숫자를 따로 박지 않는다', () => {
    const lines = layerLines([file], 'slope', [stop]);
    expect(lines).toHaveLength(1);
    expect(layerLines([{ ...file, steepPermille: 40 }], 'slope', [stop])).toHaveLength(2);
  });

  it('그늘은 그림자가 있는 구간을, 짙을수록 진하게', () => {
    const lines = layerLines([file], 'shade', [stop]);
    expect(lines).toHaveLength(2);
    expect(lines[1].opacity!).toBeGreaterThan(lines[0].opacity!);
  });

  it('🔴 정차지 둘레만 그린다 — 10km 떨어진 가파른 길은 안 칠한다', () => {
    expect(layerLines([file], 'slope', [stop]).every((line) => line.path![0].latitude > 35.16)).toBe(true);
  });

  it('파일 좌표 [경도, 위도] 를 위도·경도로 옮긴다', () => {
    const [line] = layerLines([file], 'slope', [stop]);
    expect(line.path![0]).toEqual({ latitude: 35.1653, longitude: 129.1586 });
  });

  it('정차지가 드는 여행 범위를 고른다 — 해운대 장소면 해운대 파일', () => {
    expect(areasFor([stop])).toContain('HAEUNDAE');
    expect(areasFor([{ ...stop, latitude: 37.5, longitude: 127.0 }])).toEqual([]);
  });
});
