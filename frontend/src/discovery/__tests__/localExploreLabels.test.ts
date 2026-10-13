import { flattenLocalFacets, localFacetLabel, localPlaceName } from '../localExplore';

const entry = { featureKey: 'WALK', labelKo: '산책', placeCount: 3 };
it('translates existing categories without changing the server category key', () => {
  expect(localFacetLabel(entry, 'en')).toBe('Walks');
  expect(localFacetLabel(entry, 'ko')).toBe('산책');
  expect(entry.featureKey).toBe('WALK');
});
it('prefers a server English label and tolerates a blank one', () => {
  expect(localFacetLabel({ ...entry, labelEn: 'Scenic walks' }, 'en')).toBe('Scenic walks');
  expect(localFacetLabel({ ...entry, labelEn: ' ' }, 'en')).toBe('Walks');
});
it('does not invent or hide unknown server categories', () => {
  const custom = { ...entry, featureKey: 'NEW', labelKo: '새 갈래' };
  expect(localFacetLabel(custom, 'en')).toBe('새 갈래');
  const flat = flattenLocalFacets({ state: 'success', facets: [{ userInputCode: 'EXPLORE', placeFeatureType: 'EXPLORE', matchKind: 'ANY', placeCount: 3, keys: [custom] }] }, new Set(['WALK']));
  expect(flat?.[0].featureKey).toBe('NEW');
});
it('shows English place names when provided and keeps a readable fallback', () => {
  expect(localPlaceName({ nameKo: '해운대', nameEn: 'Haeundae' }, 'en')).toBe('Haeundae');
  expect(localPlaceName({ nameKo: '해운대', nameEn: ' ' }, 'en')).toBe('해운대');
  expect(localPlaceName({ nameKo: '해운대', nameEn: 'Haeundae' }, 'ko')).toBe('해운대');
});

it('🔴 일본어·중국어는 한국어 이름을 번역표에서 찾는다 — 영어 이름으로 떨어지지 않는다', () => {
  const entry = { featureKey: 'FESTIVAL', placeCount: 76, labelKo: '축제', labelEn: null };
  const ja = (ko: string, en: string) => (ko === '축제' ? '祭り' : en);
  expect(localFacetLabel(entry, 'ja', ja)).toBe('祭り');
  expect(localFacetLabel(entry, 'en', ja)).toBe('Festivals');
});

// 맛집 갈래 (S15P21E201-2051) — 여덟 갈래와 달리 서버에 자료가 있을 때만 오고, 이름은 서버가 한국어(「맛집」)만 준다.
describe('맛집 갈래 이름', () => {
  const restaurant = { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '맛집', labelEn: null };

  it('한국어는 서버 이름 그대로, 영어는 우리 이름표다', () => {
    expect(localFacetLabel(restaurant, 'ko')).toBe('맛집');
    expect(localFacetLabel(restaurant, 'en')).toBe('Restaurants');
  });

  it('일본어·중국어는 한국어 이름 「맛집」을 번역표에서 찾는다 — 영어로 떨어지지 않는다', () => {
    const table: Record<string, string> = { '맛집': 'グルメ' };
    const ja = (ko: string, en: string) => table[ko] ?? en;
    expect(localFacetLabel(restaurant, 'ja', ja)).toBe('グルメ');
  });

  it('🔴 서버가 이 갈래를 주면 보이고, 안 주거나 0곳이면 안 보인다 — 앱을 서버보다 먼저 배포해도 된다', () => {
    const known = new Set(['WALK', 'LOCAL_RESTAURANT']);
    const group = (keys: Array<{ featureKey: string; placeCount: number; labelKo: string }>) => ({ state: 'success' as const, facets: [{ userInputCode: 'EXPLORE', placeFeatureType: 'INTEREST_TAG', matchKind: 'TAG_OVERLAP', placeCount: 0, keys }] });

    const withData = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '맛집' }]), known);
    expect(withData?.map((entry) => entry.featureKey)).toEqual(['WALK', 'LOCAL_RESTAURANT']);

    const notYet = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }]), known);
    expect(notYet?.map((entry) => entry.featureKey)).toEqual(['WALK']);

    const empty = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 0, labelKo: '맛집' }]), known);
    expect(empty?.map((entry) => entry.featureKey)).toEqual(['WALK']);

    // 이름표 없이 오면(구버전 서버) 거른다 — 이름 없는 칩을 그리지 않는다.
    const unnamed = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '' }]), known);
    expect(unnamed?.map((entry) => entry.featureKey)).toEqual(['WALK']);
  });
});
