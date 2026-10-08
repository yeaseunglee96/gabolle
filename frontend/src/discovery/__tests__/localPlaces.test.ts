// S15P21E201-2024 — 로컬 탐색은 「전통시장」 한 갈래가 아니라 「로컬 장소」 묶음으로 시작한다.
import { LOCAL_PLACES_KEY, isLocalPlaces, localPlacesLabel, mergeLocalPlaces, withLocalPlacesFirst } from '@/discovery/localPlaces';
import type { LocalFacetEntry } from '@/discovery/localExplore';

const facet = (featureKey: string, placeCount = 3): LocalFacetEntry => ({ featureKey, placeCount, labelKo: featureKey, placeFeatureType: 'LOCAL' });

describe('withLocalPlacesFirst', () => {
  it('맨 앞에 「로컬 장소」를 두고, 전통시장은 갈래 하나로 남긴다', () => {
    const list = withLocalPlacesFirst([facet('TRADITIONAL_MARKET'), facet('NIGHT_MARKET')]);
    expect(list.map((e) => e.featureKey)).toEqual([LOCAL_PLACES_KEY, 'TRADITIONAL_MARKET', 'NIGHT_MARKET']);
    expect(list[0].labelKo).toBe('로컬 장소');
    expect(isLocalPlaces(list[0])).toBe(true);
    expect(isLocalPlaces(list[1])).toBe(false);
  });
  it('갈래가 없으면 묶음도 만들지 않는다', () => {
    expect(withLocalPlacesFirst([])).toEqual([]);
  });
});

describe('localPlacesLabel', () => {
  it('다섯 언어로 말한다', () => {
    expect(localPlacesLabel('ko')).toBe('로컬 장소');
    expect(localPlacesLabel('en')).toBe('Local places');
    expect(localPlacesLabel('ja')).toBe('ローカルスポット');
    expect(localPlacesLabel('zh-Hans')).toBe('本地好去处');
    expect(localPlacesLabel('zh-Hant')).toBe('在地好去處');
  });
});

describe('mergeLocalPlaces', () => {
  it('갈래를 번갈아 섞고, 겹치는 장소는 한 번만 둔다', () => {
    const merged = mergeLocalPlaces([
      [{ placeId: 'a' }, { placeId: 'b' }],
      [{ placeId: 'c' }, { placeId: 'a' }],
      [],
    ], 10);
    expect(merged.map((p) => p.placeId)).toEqual(['a', 'c', 'b']);
  });
  it('limit 을 넘지 않는다', () => {
    expect(mergeLocalPlaces([[{ placeId: 'a' }, { placeId: 'b' }], [{ placeId: 'c' }]], 2)).toHaveLength(2);
  });
});
