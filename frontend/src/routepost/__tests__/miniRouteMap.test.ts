import { buildMiniRouteMapHtml, miniRouteData } from '../miniRouteMap';

describe('miniRouteData (S15P21E201-2027)', () => {
  const stops = [{ lat: 35.1, lng: 129.0 }, { lat: 35.2, lng: 129.1 }];

  it('numbers stops from 1 and keeps the real path when it has 2+ points', () => {
    const data = miniRouteData([[35.1, 129.0], [35.15, 129.05], [35.2, 129.1]], stops)!;
    expect(data.stops.map((s) => s.n)).toEqual([1, 2]);
    expect(data.line).toHaveLength(3);
  });

  it('falls back to straight lines between stops without a path', () => {
    expect(miniRouteData([], stops)!.line).toEqual([[35.1, 129.0], [35.2, 129.1]]);
  });

  it('drops invalid coordinates and returns null with no stops', () => {
    expect(miniRouteData([], [{ lat: Number.NaN, lng: 1 }])).toBeNull();
    expect(miniRouteData([[200, 1], [35, 129]], stops)!.line).toEqual([[35.1, 129.0], [35.2, 129.1]]);
  });
});

describe('buildMiniRouteMapHtml', () => {
  it('loads Kakao (not OSM tiles), locks the map and escapes the data', () => {
    const html = buildMiniRouteMapHtml('k&y', { line: [[35, 129], [35.1, 129.1]], stops: [{ lat: 35, lng: 129, n: 1 }] });
    expect(html).toContain('dapi.kakao.com/v2/maps/sdk.js?appkey=k%26y&autoload=false');
    expect(html).not.toMatch(/tile\.openstreetmap/);
    expect(html).toContain('draggable:false');
    expect(html).toContain('setZoomable(false)');
    expect(html).not.toMatch(/<\/script><script>alert/);
  });
});
