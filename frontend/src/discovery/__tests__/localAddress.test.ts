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

  it('영문 주소도 없으면 일본어·중국어 화면은 도로명을 한글 그대로 — 부산 도로명판에 한글이 있다. 가타카나로 옮겨 적지 않는다', () => {
    // 🔴 영어·베트남어 화면은 이 길이 아니다 — 로마자로 적는다(S15P21E201-2056, 아래 describe).
    const place = { address: '부산광역시 수영구 광안해변로 219' };
    expect(addressForLanguage(place, 'ja')).toBe('釜山広域市 水営区 광안해변로 219');
    expect(addressForLanguage(place, 'zh-Hans')).toBe('釜山广域市水营区 광안해변로 219');
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
    // 영어는 한글을 남기지 않는다 — 「신창로4가 area」가 아니라 로마자(S15P21E201-2056)
    expect(addressForLanguage(market, 'en')).toBe('Sinchang-ro 4-ga area, Jung-gu, Busan');
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

// 영어 화면에서 장소 주소가 「신창로4가 area, Jung-gu, Busan」처럼 한국어가 섞여 나왔다 — 갤럭시 S22 실기기, 2026-10-14.
// 영문 주소(addressEn)가 없는 장소는 도로명·동 이름을 한글 그대로 뒀기 때문이다(S15P21E201-1877 의 결정).
describe('영어·베트남어 화면은 한글 도로명·동 이름을 로마자로 — S15P21E201-2056', () => {
  const english = (address: string) => addressForLanguage({ address }, 'en');
  const vietnamese = (address: string) => addressForLanguage({ address }, 'vi');

  it('🔴 실기기에서 나온 그 주소 — 「신창로4가 area」가 「Sinchang-ro 4-ga area」로', () => {
    expect(english('부산광역시 중구 신창로4가 일원')).toBe('Sinchang-ro 4-ga area, Jung-gu, Busan');
    // 베트남어 화면도 같은 영문 줄이다 — 전에는 도로명을 빼고 「Jung-gu, Busan」만 적었다(로마자로 옮길 방법이 없어서)
    expect(vietnamese('부산광역시 중구 신창로4가 일원')).toBe('Sinchang-ro 4-ga area, Jung-gu, Busan');
  });

  it('도로명 — 이름과 끝말(로·길·읍)은 하이픈으로, 번지는 띄어 쓴다', () => {
    expect(english('부산광역시 해운대구 해운대해변로 264')).toBe('Haeundaehaebyeon-ro 264, Haeundae-gu, Busan');
    expect(english('부산광역시 수영구 광안해변로 219')).toBe('Gwanganhaebyeon-ro 219, Suyeong-gu, Busan');
    expect(english('부산광역시 강서구 공항진입로 108')).toBe('Gonghangjinip-ro 108, Gangseo-gu, Busan');
    expect(english('부산광역시 기장군 기장읍 용궁길 86')).toBe('Gijang-eup Yonggung-gil 86, Gijang-gun, Busan');
    // 번지가 없는 주소
    expect(english('부산광역시 수영구 광안해변로')).toBe('Gwanganhaebyeon-ro, Suyeong-gu, Busan');
  });

  it('대로 · 번길 — 「26번길」은 «26beon-gil» 로 붙여 쓴다', () => {
    expect(english('부산광역시 중구 중앙대로 26번길 35')).toBe('Jungang-daero 26beon-gil 35, Jung-gu, Busan');
    expect(english('부산광역시 영도구 해양로301번길 45 (동삼동)')).toBe('Haeyang-ro 301beon-gil 45 (Dongsam-dong), Yeongdo-gu, Busan');
    expect(english('부산 해운대구 달맞이길62번길 13')).toBe('Dalmati-gil 62beon-gil 13, Haeundae-gu, Busan');
  });

  it('🔴 「해운대로」는 «해운 + 대로»(Haeun-daero)가 아니라 «해운대 + 로»(Haeundae-ro)', () => {
    expect(english('부산광역시 해운대구 해운대로 570')).toBe('Haeundae-ro 570, Haeundae-gu, Busan');
    // 같은 「대로」라도 다른 이름은 그대로 daero
    expect(english('부산광역시 동구 중앙대로 206')).toBe('Jungang-daero 206, Dong-gu, Busan');
  });

  it('동 이름과 번지 — 한 글자 이름도 하이픈으로(U-dong), 번지의 하이픈은 그대로', () => {
    expect(english('부산광역시 해운대구 우동 1394')).toBe('U-dong 1394, Haeundae-gu, Busan');
    expect(english('부산광역시 사상구 삼락동 29-46')).toBe('Samrak-dong 29-46, Sasang-gu, Busan');
    expect(english('부산광역시 수영구 남천동')).toBe('Namcheon-dong, Suyeong-gu, Busan');
    expect(english('부산광역시 기장군 기장읍')).toBe('Gijang-eup, Gijang-gun, Busan');
  });

  it('숫자 사이·뒤에 오는 끝말 — 「마린시티1로」는 «Marinsiti 1-ro»', () => {
    expect(english('부산광역시 해운대구 마린시티1로 10')).toBe('Marinsiti 1-ro 10, Haeundae-gu, Busan');
  });

  it('🔴 어떤 모양의 부산 주소도 한글이 한 글자도 남지 않는다 — 영어도 베트남어도', () => {
    const addresses = [
      '부산광역시 중구 신창로4가 일원', '부산광역시 중구 신창로 1', '부산광역시 해운대구 해운대해변로 264',
      '부산광역시 수영구 광안해변로', '부산광역시 강서구 공항진입로 108', '부산광역시 중구 중앙대로 26번길 35',
      '부산광역시 영도구 해양로301번길 45 (동삼동)', '부산광역시 해운대구 우동 1394', '부산광역시 사상구 삼락동 29-46',
      '부산광역시 기장군 기장읍 용궁길 86', '부산 해운대구 달맞이길62번길 13', '부산 중구 자갈치해안로', '부산 부산진구 가야대로',
      '부산 동구 중앙대로 206', '부산 금정구 오시게로 20', '부산 중구 남포동', '부산 해운대구 우동', '부산광역시 해운대구',
    ];
    for (const address of addresses) {
      for (const language of ['en', 'vi'] as const) {
        const shown = addressForLanguage({ address }, language);
        expect(shown).toMatch(/Busan$/);
        expect(shown).not.toMatch(/[가-힣ㄱ-ㆎ]/);
      }
    }
  });

  it('영문 주소가 있으면 그것이 먼저다 — 한글을 옮겨 새로 짓지 않는다', () => {
    expect(addressForLanguage(HAEUNDAE, 'en')).toBe('264 Haeundaehaebyeon-ro, Haeundae-gu, Busan');
    expect(addressForLanguage(HAEUNDAE, 'vi')).toBe('264 Haeundaehaebyeon-ro, Haeundae-gu, Busan');
  });

  it('한국어 원문은 곁줄로 그대로 나간다 — 택시 기사에게 보여 주고 지도에 붙여 넣는 글자', () => {
    const market = { address: '부산광역시 중구 신창로4가 일원' };
    expect(koreanAddressLine(market, 'en')).toBe('부산광역시 중구 신창로4가 일원');
    expect(koreanAddressLine(market, 'vi')).toBe('부산광역시 중구 신창로4가 일원');
    expect(koreanAddressLine(market, 'ko')).toBeNull();
  });

  it('한국어 · 일본어 · 중국어 화면은 바뀌지 않았다', () => {
    const market = { address: '부산광역시 중구 신창로4가 일원' };
    expect(addressForLanguage(market, 'ko')).toBe('부산광역시 중구 신창로4가 일원');
    expect(addressForLanguage(market, 'ja')).toBe('釜山広域市 中区 신창로4가一帯');
    expect(addressForLanguage(market, 'zh-Hans')).toBe('釜山广域市中区 신창로4가一带');
    expect(addressForLanguage(market, 'zh-Hant')).toBe('釜山廣域市中區 신창로4가一帶');
  });

  it('부산 밖이거나 모양을 못 읽는 주소는 전과 같다 — 영어는 원문, 베트남어는 한글을 싣지 않는다', () => {
    expect(english('경상남도 양산시 물금읍 1')).toBe('경상남도 양산시 물금읍 1');
    expect(vietnamese('경상남도 양산시 물금읍 1')).toBe('');
    expect(english('')).toBe('');
  });
});
