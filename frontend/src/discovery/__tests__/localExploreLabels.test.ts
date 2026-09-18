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
