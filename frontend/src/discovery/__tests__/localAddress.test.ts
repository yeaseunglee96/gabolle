import { addressForLanguage, englishStreetPart, koreanAddressLine, splitBusanAddress } from '@/discovery/localAddress';

const HAEUNDAE = { address: '부산광역시 해운대구 해운대해변로 264', addressEn: '264 Haeundaehaebyeon-ro, Haeundae-gu, Busan' };

describe('장소 주소를 화면 언어로 (S15P21E201-1877)', () => {
  it('🔴 관광공사 번역 주소가 있으면 그것이 먼저다', () => {
    const place = { ...HAEUNDAE, localAddresses: { ja: '釜山広域市 海雲台区 ヘウンデヘビョンロ264', 'zh-Hant': '釜山廣域市海雲臺區海雲臺海邊路264' } };
    expect(addressForLanguage(place, 'ja')).toBe('釜山広域市 海雲台区 ヘウンデヘビョンロ264');
    expect(addressForLanguage(place, 'zh-Hant')).toBe('釜山廣域市海雲臺區海雲臺海邊路264');
  });

  it('🔴 번역 주소가 없으면 영어로 물러서지 않는다 — 시·구는 그 언어 표기, 도로명은 표지판의 로마자', () => {
    expect(addressForLanguage(HAEUNDAE, 'ja')).toBe('釜山広域市 海雲台区 Haeundaehaebyeon-ro 264');
    expect(addressForLanguage(HAEUNDAE, 'zh-Hans')).toBe('釜山广域市海云台区 Haeundaehaebyeon-ro 264');
    expect(addressForLanguage(HAEUNDAE, 'zh-Hant')).toBe('釜山廣域市海雲臺區 Haeundaehaebyeon-ro 264');
    // 번지를 도로명 뒤로 — 큰 단위부터 적는 일본어·중국어 주소 순서
    expect(addressForLanguage({ address: '부산광역시 중구 중앙대로 26번길 35', addressEn: '35, Jungang-daero 26beon-gil, Jung-gu, Busan' }, 'ja')).toBe('釜山広域市 中区 Jungang-daero 26beon-gil 35');
  });

  it('영문 주소도 없으면 도로명은 한글 그대로 — 부산 도로명판에 한글이 있다. 가타카나로 옮겨 적지 않는다', () => {
    const place = { address: '부산광역시 수영구 광안해변로 219' };
    expect(addressForLanguage(place, 'ja')).toBe('釜山広域市 水営区 광안해변로 219');
    expect(addressForLanguage(place, 'en')).toBe('광안해변로 219, Suyeong-gu, Busan');
  });

  it('한국어·영어 화면은 예전 그대로', () => {
    expect(addressForLanguage(HAEUNDAE, 'ko')).toBe('부산광역시 해운대구 해운대해변로 264');
    expect(addressForLanguage(HAEUNDAE, 'en')).toBe('264 Haeundaehaebyeon-ro, Haeundae-gu, Busan');
  });

  it('부산 밖이거나 모양을 못 읽으면 영문 → 한국어', () => {
    expect(addressForLanguage({ address: '경상남도 양산시 물금읍 1', addressEn: 'Mulgeum-eup, Yangsan-si' }, 'ja')).toBe('Mulgeum-eup, Yangsan-si');
    expect(addressForLanguage({ address: '경상남도 양산시 물금읍 1' }, 'zh-Hans')).toBe('경상남도 양산시 물금읍 1');
    expect(addressForLanguage({}, 'ja')).toBe('');
  });

  it('「서구」와 「강서구」를 섞지 않는다 · 구만 있는 주소', () => {
    expect(splitBusanAddress('부산광역시 강서구 공항진입로 108')).toEqual({ gu: '강서구', rest: '공항진입로 108' });
    expect(addressForLanguage({ address: '부산광역시 강서구 공항진입로 108' }, 'zh-Hant')).toBe('釜山廣域市江西區 공항진입로 108');
    expect(addressForLanguage({ address: '부산광역시 해운대구' }, 'ja')).toBe('釜山広域市 海雲台区');
  });

  it('구역 주소 끝말 「일원·일대」를 그 언어의 같은 말로 — 한국어 낱말이 남지 않게', () => {
    const market = { address: '부산광역시 중구 신창로4가 일원' };
    expect(addressForLanguage(market, 'ja')).toBe('釜山広域市 中区 신창로4가一帯');
    expect(addressForLanguage(market, 'zh-Hant')).toBe('釜山廣域市中區 신창로4가一帶');
    expect(addressForLanguage(market, 'en')).toBe('신창로4가 area, Jung-gu, Busan');
  });

  it('영문 주소에서 구·시·나라·우편번호를 뗀다', () => {
    expect(englishStreetPart('219 Gwangan Haebyeon-ro, Suyeong-gu, Busan')).toBe('219 Gwangan Haebyeon-ro');
    expect(englishStreetPart('Udong, Haeundae-gu, Busan, Republic of Korea, 48094')).toBe('Udong');
    expect(englishStreetPart('Haeundae-gu, Busan')).toBeNull();
  });

  it('곁에 적을 한국어 원문 — 외국어 화면이고 위 줄과 다를 때만', () => {
    expect(koreanAddressLine(HAEUNDAE, 'ja')).toBe('부산광역시 해운대구 해운대해변로 264');
    expect(koreanAddressLine(HAEUNDAE, 'ko')).toBeNull();
    expect(koreanAddressLine({ address: '경상남도 양산시 물금읍 1' }, 'ja')).toBeNull();
  });
});
