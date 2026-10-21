import { pickLanguage } from '@/i18n/pick';

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

// 🔴 2026-10-16 웹 점검 — 베트남어 탐색 칩이 「Festivals … Restaurants, Cafes」로 영어였다(S15P21E201-2060).
//    번역표에 베트남어가 다 있는데 localFacetLabel 이 일본어·중국어만 표로 보냈다. 갈래 열 개 × 여섯 언어를 실제 번역표로 본다.
describe('갈래 이름은 여섯 언어 모두 번역표를 거친다', () => {
  // 시험이 번역표를 직접 읽는다 — 가짜 표가 아니라 앱이 실제로 쓰는 표다(pickLanguage).
  const KEYS: Array<[string, string, string]> = [
    ['FESTIVAL', '축제', 'Festivals'], ['NIGHT_MARKET', '야시장', 'Night markets'], ['TRADITIONAL_MARKET', '전통시장', 'Traditional markets'],
    ['ACTIVITY', '액티비티', 'Activities'], ['WALK', '산책', 'Walks'], ['NATURE', '자연', 'Nature'], ['NIGHT_VIEW', '야경', 'Night views'],
    ['SOUVENIR_SHOP', '기념품샵', 'Souvenir shops'], ['LOCAL_RESTAURANT', '맛집', 'Restaurants'], ['LOCAL_CAFE', '카페', 'Cafes'],
  ];
  const VI: Record<string, string> = {
    FESTIVAL: 'Lễ hội', NIGHT_MARKET: 'Chợ đêm', TRADITIONAL_MARKET: 'Chợ truyền thống', ACTIVITY: 'Hoạt động', WALK: 'Dạo bộ',
    NATURE: 'Thiên nhiên', NIGHT_VIEW: 'Cảnh đêm', SOUVENIR_SHOP: 'Cửa hàng lưu niệm', LOCAL_RESTAURANT: 'Quán ngon', LOCAL_CAFE: 'Cà phê',
  };

  it.each(KEYS)('베트남어 %s 칩은 영어 「%s」 가 아니라 번역표의 베트남어다', (featureKey, labelKo, english) => {
    const chip = { featureKey, labelKo, labelEn: null, placeCount: 1 };
    const tx = (ko: string, en: string) => pickLanguage('vi', { ko, en });
    expect(localFacetLabel(chip, 'vi', tx)).toBe(VI[featureKey]);
    expect(localFacetLabel(chip, 'vi', tx)).not.toBe(english);
  });

  it.each(KEYS)('다른 다섯 언어 %s 칩도 그대로다 — ko 서버 이름, en 우리 이름표, ja·zh 번역표', (featureKey, labelKo, english) => {
    const chip = { featureKey, labelKo, labelEn: null, placeCount: 1 };
    const tx = (lang: 'ja' | 'zh-Hans' | 'zh-Hant') => (ko: string, en: string) => pickLanguage(lang, { ko, en });
    expect(localFacetLabel(chip, 'ko')).toBe(labelKo);
    expect(localFacetLabel(chip, 'en')).toBe(english);
    for (const lang of ['ja', 'zh-Hans', 'zh-Hant'] as const) expect(localFacetLabel(chip, lang, tx(lang))).not.toBe(english);
  });

  it('tx 를 안 주면(번역표를 못 거치는 호출) 베트남어도 영어로 물러선다 — 한국어가 새지 않는다', () => {
    expect(localFacetLabel({ featureKey: 'FESTIVAL', labelKo: '축제', labelEn: null, placeCount: 1 }, 'vi')).toBe('Festivals');
  });
});

// 맛집·카페 갈래 (S15P21E201-2051 · S15P21E201-2059) — 여덟 갈래와 달리 서버에 자료가 있을 때만 오고, 이름은 서버가 한국어(「맛집」「카페」)만 준다.
describe('맛집 갈래 이름', () => {
  const restaurant = { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '맛집', labelEn: null };
  const cafe = { featureKey: 'LOCAL_CAFE', placeCount: 262, labelKo: '카페', labelEn: null };

  it('🔴 카페도 한국어는 서버 이름 그대로, 영어는 우리 이름표(Cafes)이고, 일본어·중국어는 번역표의 「카페」를 찾는다', () => {
    expect(localFacetLabel(cafe, 'ko')).toBe('카페');
    expect(localFacetLabel(cafe, 'en')).toBe('Cafes');
    const table: Record<string, string> = { '카페': 'カフェ' };
    const ja = (ko: string, en: string) => table[ko] ?? en;
    expect(localFacetLabel(cafe, 'ja', ja)).toBe('カフェ');
  });

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
    const known = new Set(['WALK', 'LOCAL_RESTAURANT', 'LOCAL_CAFE']);
    const group = (keys: Array<{ featureKey: string; placeCount: number; labelKo: string }>) => ({ state: 'success' as const, facets: [{ userInputCode: 'EXPLORE', placeFeatureType: 'INTEREST_TAG', matchKind: 'TAG_OVERLAP', placeCount: 0, keys }] });

    const withData = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '맛집' }]), known);
    expect(withData?.map((entry) => entry.featureKey)).toEqual(['WALK', 'LOCAL_RESTAURANT']);

    const notYet = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }]), known);
    expect(notYet?.map((entry) => entry.featureKey)).toEqual(['WALK']);

    const empty = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 0, labelKo: '맛집' }]), known);
    expect(empty?.map((entry) => entry.featureKey)).toEqual(['WALK']);

    // 카페도 같은 규칙이다 — 자료가 들어오면 맛집 뒤에 붙고, 없거나 0곳이면 안 보인다.
    const withCafe = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '맛집' }, { featureKey: 'LOCAL_CAFE', placeCount: 262, labelKo: '카페' }]), known);
    expect(withCafe?.map((entry) => entry.featureKey)).toEqual(['WALK', 'LOCAL_RESTAURANT', 'LOCAL_CAFE']);
    const cafeEmpty = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_CAFE', placeCount: 0, labelKo: '카페' }]), known);
    expect(cafeEmpty?.map((entry) => entry.featureKey)).toEqual(['WALK']);

    // 이름표 없이 오면(구버전 서버) 거른다 — 이름 없는 칩을 그리지 않는다.
    const unnamed = flattenLocalFacets(group([{ featureKey: 'WALK', placeCount: 3, labelKo: '산책' }, { featureKey: 'LOCAL_RESTAURANT', placeCount: 655, labelKo: '' }]), known);
    expect(unnamed?.map((entry) => entry.featureKey)).toEqual(['WALK']);
  });
});
