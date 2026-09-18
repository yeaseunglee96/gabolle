import { mergeRegionCandidates, regionFromAddress, regionLabelOf } from '../regionSearch';

const 우리장소 = (nameKo: string, address: string, placeId = 'p-' + nameKo) => ({ placeId, nameKo, address });
const 카카오 = (name: string, address: string) => ({ name, address });

describe('주소에서 지역 뽑기', () => {
  it('구·군을 뽑는다', () => {
    expect(regionFromAddress('부산광역시 해운대구 우동 1394')).toBe('해운대구');
    expect(regionFromAddress('부산광역시 기장군 기장읍')).toBe('기장군');
  });

  it('🔴 못 뽑으면 빈 값을 준다 — 지역 칸이 전체 주소로 뒤덮이지 않게', () => {
    expect(regionFromAddress('부산광역시')).toBe('');
    expect(regionFromAddress('')).toBe('');
  });

  it('🔴 고른 장소 이름을 남긴다 — 구만 남기면 고른 것이 사라진 것처럼 보인다', () => {
    // 2026-09-18 실기기: 「해운대해수욕장」을 눌렀는데 칸에 「해운대구」만 남았다.
    expect(regionLabelOf({ name: '광안리해수욕장', address: '부산광역시 수영구 광안해변로' })).toBe('광안리해수욕장 · 수영구');
  });

  it('구·군을 못 뽑으면 이름만 쓴다', () => {
    expect(regionLabelOf({ name: '어떤 곳', address: '부산광역시' })).toBe('어떤 곳');
  });

  it('🔴 이름이 이미 구·군이면 두 번 적지 않는다 — 카카오의 지역 결과가 그렇다', () => {
    expect(regionLabelOf({ name: '해운대구', address: '부산광역시 해운대구' })).toBe('해운대구');
    expect(regionLabelOf({ name: '부산 해운대구', address: '부산광역시 해운대구' })).toBe('부산 해운대구');
  });

  it('이름이 비어 있으면 구·군으로 간다', () => {
    expect(regionLabelOf({ name: '  ', address: '부산광역시 수영구 광안해변로' })).toBe('수영구');
  });
});

describe('검색 결과 합치기', () => {
  it('🔴 우리 장소가 먼저다 — 글에 이을 수 있는 쪽을 고르게 한다', () => {
    const merged = mergeRegionCandidates(
      [우리장소('해운대해수욕장', '부산광역시 해운대구')],
      [카카오('스타벅스 해운대점', '부산광역시 해운대구')],
    );
    expect(merged[0].name).toBe('해운대해수욕장');
    expect(merged[0].placeId).toBeTruthy();
  });

  it('🔴 카카오 결과에는 placeId 가 없다 — 저장하면 안 되는 것이라 넣지 않는다', () => {
    const merged = mergeRegionCandidates([], [카카오('어떤 카페', '부산광역시 중구')]);
    expect(merged[0].placeId).toBeUndefined();
  });

  it('같은 곳이 양쪽에 있으면 우리 것만 남는다', () => {
    const merged = mergeRegionCandidates(
      [우리장소('광안리해수욕장', '부산광역시 수영구')],
      [카카오('광안리해수욕장', '부산광역시 수영구')],
    );
    expect(merged).toHaveLength(1);
    expect(merged[0].placeId).toBeTruthy();
  });

  it('한쪽이 비어도 다른 쪽은 그대로 나온다', () => {
    expect(mergeRegionCandidates([우리장소('감천문화마을', '부산광역시 사하구')], [])).toHaveLength(1);
    expect(mergeRegionCandidates([], [카카오('어디 식당', '부산광역시 동구')])).toHaveLength(1);
  });

  it('너무 많으면 자른다 — 목록이 화면을 덮지 않게', () => {
    const many = Array.from({ length: 20 }, (_, i) => 우리장소(`장소${i}`, '부산광역시 중구'));
    expect(mergeRegionCandidates(many, [], 8)).toHaveLength(8);
  });
});
