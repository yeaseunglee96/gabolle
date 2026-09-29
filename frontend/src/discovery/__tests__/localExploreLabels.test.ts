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
