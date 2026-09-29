// 목록 응답을 화면 모양으로 옮길 때 칸을 흘리지 않는지 잰다 — S15P21E201-1195.

import {
  PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE,
  toPlaceSearchItem,
  type PlaceSearchItemDto,
} from '../places';

/** 서버가 주는 칸을 하나도 빠짐없이 채운 본보기. */
const FULL: Required<PlaceSearchItemDto> = {
  placeId: 'p-1',
  nameKo: '광안리해수욕장',
  nameEn: 'Gwangalli Beach',
  localNames: { ja: '広安里海水浴場', 'zh-Hans': '广安里海水浴场' },
  category: 'SEA_BEACH',
  address: '부산 수영구 광안해변로 219',
  addressEn: '219 Gwangan Haebyeon-ro, Suyeong-gu, Busan',
  lat: 35.1531,
  lng: 129.1186,
  matchedField: 'NAME_KO',
  photoUrl: 'https://example.test/a.jpg',
  photoSource: '한국관광공사',
  photoSubject: 'VENUE',
  photoLicense: { name: 'CC BY-SA 3.0', url: 'https://creativecommons.org/licenses/by-sa/3.0', filePage: 'https://commons.wikimedia.org/wiki/File:x.jpg' },
};

describe('toPlaceSearchItem', () => {
  it('서버가 준 칸 중 일부러 뺀 것 말고는 하나도 안 버린다', () => {
    const item = toPlaceSearchItem(FULL) as Record<string, unknown>;
    const dropped: readonly string[] = PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE;

    const missing = Object.keys(FULL).filter((key) => !dropped.includes(key) && !(key in item));

    expect(missing).toEqual([]);
  });

  it('값도 그대로 옮긴다 — 키만 있고 값이 undefined 이면 안 된다', () => {
    const item = toPlaceSearchItem(FULL) as Record<string, unknown>;
    const dropped: readonly string[] = PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE;

    for (const [key, value] of Object.entries(FULL)) {
      if (dropped.includes(key)) continue;
      expect(item[key]).toEqual(value);
    }
  });

  it('일부러 빼기로 한 칸은 안 들고 온다', () => {
    const item = toPlaceSearchItem(FULL) as Record<string, unknown>;

    for (const key of PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE) {
      expect(key in item).toBe(false);
    }
  });

  it('영문 주소가 없는 장소도 그대로 지나간다 — 키 자체가 안 올 수 있다', () => {
    const { addressEn: _drop, ...withoutEn } = FULL;
    const item = toPlaceSearchItem(withoutEn as PlaceSearchItemDto);

    expect(item.address).toBe(FULL.address);
    expect(item.addressEn).toBeUndefined();
  });
});
