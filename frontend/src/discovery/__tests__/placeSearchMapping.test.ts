// 목록 응답을 화면 모양으로 옮길 때 칸을 흘리지 않는지 잰다 — S15P21E201-1195.
//
// 🔴 이 시험이 생긴 이유.
//
//    `toPlaceSearchItem` 은 칸을 **하나씩 열거해서** 옮긴다. 화면이 안 쓰는 칸
//    (`matchedField`)을 안 들고 오려는 것이라 그 자체는 의도한 설계다.
//
//    문제는 **칸을 더할 때**다. 타입에만 더하고 옮기는 자리를 잊으면
//    **타입 검사가 통과하고 값만 조용히 사라진다.** 화면에는 아무 오류 없이 빈칸이 뜬다.
//    영문 주소(S15P21E201-1195)에서 실제로 그럴 뻔했다.
//
// 🔴 그래서 「addressEn 이 있나」를 재지 않는다.
//
//    그렇게 쓰면 **다음에 더할 칸은 못 막는다.** 대신 「서버가 준 칸 중 일부러 뺀 것
//    말고 빠진 게 있으면 실패」로 쓴다. 앞으로 어떤 칸을 더해도 같은 자리에서 걸리고,
//    일부러 빼는 칸은 목록에 적으면 되니 **거짓 실패가 안 난다.**
//
//    이건 답을 강제하는 검사가 아니라 **질문을 그 자리에 띄우는 검사**다 — 칸을 더한
//    사람이 「목록에도?」를 한 번 대답하고 지나가게 한다.

import {
  PLACE_SEARCH_FIELDS_DROPPED_ON_PURPOSE,
  toPlaceSearchItem,
  type PlaceSearchItemDto,
} from '../places';

/** 서버가 주는 칸을 **하나도 빠짐없이** 채운 본보기. 칸이 늘면 여기도 늘려야 타입이 통과한다. */
const FULL: PlaceSearchItemDto = {
  placeId: 'p-1',
  nameKo: '광안리해수욕장',
  nameEn: 'Gwangalli Beach',
  category: 'SEA_BEACH',
  address: '부산 수영구 광안해변로 219',
  addressEn: '219 Gwangan Haebyeon-ro, Suyeong-gu, Busan',
  lat: 35.1531,
  lng: 129.1186,
  matchedField: 'NAME_KO',
  photoUrl: 'https://example.test/a.jpg',
  photoSource: '한국관광공사',
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
