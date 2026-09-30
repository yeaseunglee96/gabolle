// 장소 주소를 화면 언어로 — S15P21E201-1877.
//
// 🔴 전에는 일본어·중국어 화면의 주소가 영어(없으면 한국어)였다. 이름은 관광공사 번역으로 나오는데 주소만 영어라
//    「번역이 반쯤 된 앱」으로 보였다(사용자 지적 2026-09-30). 지어낸 번역 없이 가장 쓸모 있는 순서로 고른다.
//
//   1. 관광공사가 번역해 둔 주소(localAddresses, 백엔드 S15P21E201-1876) — 원본 그대로 가장 믿을 만하다.
//   2. 없으면 「시·구는 그 언어의 공식 표기 + 도로명은 거리 표지판 표기」로 짓는다.
//      - 시·구 이름은 공식 표기가 있다(釜山広域市 海雲台区 · Haeundae-gu). 부산의 구·군 16개뿐이라 표로 둔다.
//      - 도로명은 일본어·중국어 공식 표기가 없다. 부산 도로명판에는 한글과 로마자만 있으므로, 영문 주소가 있으면
//        그 도로명(로마자)을, 없으면 한글 도로명을 그대로 쓴다 — 여행자가 거리에서 보는 글자와 같다.
//        🔴 한글을 가타카나·한자로 옮겨 적지 않는다. 규칙으로 만들면 틀린 읽기가 섞이고, 그건 공식 표기가 아니다.
//   3. 부산 밖이거나 모양을 못 읽으면 영문 주소 → 한국어 주소.
//
// 한국어 원문은 따로 보여 줄 수 있게 koreanAddressLine 으로 둔다 — 기사에게 보여 주고 지도에 붙여 넣는 것은 한국어다.
import type { LanguageCode } from '@/i18n/languages';

/** 키는 앱의 언어 코드. localNames 와 같다. 없는 언어는 빠져 온다. */
export type LocalAddresses = Partial<Record<'ja' | 'zh-Hans' | 'zh-Hant', string>>;

type GuName = { en: string; ja: string; zhHans: string; zhHant: string };

// 부산의 구·군 — 영문은 로마자 표기법(도로명주소 영문 표기와 같다), 한자는 관광공사 일문·중문 주소에 쓰인 표기.
const BUSAN_GU: Record<string, GuName> = {
  중구: { en: 'Jung-gu', ja: '中区', zhHans: '中区', zhHant: '中區' },
  서구: { en: 'Seo-gu', ja: '西区', zhHans: '西区', zhHant: '西區' },
  동구: { en: 'Dong-gu', ja: '東区', zhHans: '东区', zhHant: '東區' },
  영도구: { en: 'Yeongdo-gu', ja: '影島区', zhHans: '影岛区', zhHant: '影島區' },
  부산진구: { en: 'Busanjin-gu', ja: '釜山鎮区', zhHans: '釜山镇区', zhHant: '釜山鎮區' },
  동래구: { en: 'Dongnae-gu', ja: '東萊区', zhHans: '东莱区', zhHant: '東萊區' },
  남구: { en: 'Nam-gu', ja: '南区', zhHans: '南区', zhHant: '南區' },
  북구: { en: 'Buk-gu', ja: '北区', zhHans: '北区', zhHant: '北區' },
  해운대구: { en: 'Haeundae-gu', ja: '海雲台区', zhHans: '海云台区', zhHant: '海雲臺區' },
  사하구: { en: 'Saha-gu', ja: '沙下区', zhHans: '沙下区', zhHant: '沙下區' },
  금정구: { en: 'Geumjeong-gu', ja: '金井区', zhHans: '金井区', zhHant: '金井區' },
  강서구: { en: 'Gangseo-gu', ja: '江西区', zhHans: '江西区', zhHant: '江西區' },
  연제구: { en: 'Yeonje-gu', ja: '蓮堤区', zhHans: '莲堤区', zhHant: '蓮堤區' },
  수영구: { en: 'Suyeong-gu', ja: '水営区', zhHans: '水营区', zhHant: '水營區' },
  사상구: { en: 'Sasang-gu', ja: '沙上区', zhHans: '沙上区', zhHant: '沙上區' },
  기장군: { en: 'Gijang-gun', ja: '機張郡', zhHans: '机张郡', zhHant: '機張郡' },
};

const CITY = { en: 'Busan', ja: '釜山広域市', zhHans: '釜山广域市', zhHant: '釜山廣域市' } as const;

/** 「부산광역시 해운대구 해운대해변로 264」 → 구와 나머지. 부산이 아니거나 구를 못 찾으면 null. */
export function splitBusanAddress(address: string): { gu: string; rest: string } | null {
  const m = address.trim().replace(/\s+/g, ' ').match(/^(?:부산광역시|부산시|부산)\s+(\S+?[구군])(?:\s+(.*))?$/);
  if (!m || !BUSAN_GU[m[1]]) return null;
  return { gu: m[1], rest: (m[2] ?? '').trim() };
}

/**
 * 영문 주소에서 도로명·번지만 — 「219 Gwangan Haebyeon-ro, Suyeong-gu, Busan」 → 「219 Gwangan Haebyeon-ro」.
 * 구·시·나라·우편번호 조각을 뺀다. 남는 것이 없으면 null.
 */
export function englishStreetPart(addressEn: string | null | undefined): string | null {
  if (!addressEn) return null;
  const parts = addressEn.split(',').map((part) => part.trim()).filter(Boolean)
    .filter((part) => !/-(gu|gun)$/i.test(part) && !/^busan( metropolitan city)?$/i.test(part)
      && !/^(republic of )?korea$/i.test(part) && !/^\d{5}$/.test(part));
  return parts.length ? parts.join(', ') : null;
}

/** 이 언어의 관광공사 번역 주소 — 한국어·영어 화면이거나 없으면 null. */
export function localAddressFor(localAddresses: LocalAddresses | null | undefined, language: LanguageCode): string | null {
  if (language !== 'ja' && language !== 'zh-Hans' && language !== 'zh-Hant') return null;
  return localAddresses?.[language]?.trim() || null;
}

// 「신창로4가 일원」의 「일원·일대」 — 구역을 가리키는 주소 끝말. 한국어 낱말이 외국어 화면에 남지 않게 같은 뜻의 말로
// 바꾼다(관광공사 일문·중문 주소도 一帯·一带·一帶 로 적는다).
const AREA_WORD: Record<'en' | 'ja' | 'zh-Hans' | 'zh-Hant', string> = { en: ' area', ja: '一帯', 'zh-Hans': '一带', 'zh-Hant': '一帶' };
function areaWord(rest: string, language: 'en' | 'ja' | 'zh-Hans' | 'zh-Hant'): string {
  return rest.replace(/\s*(일원|일대)$/, AREA_WORD[language]);
}

export type AddressSource = { address?: string | null; addressEn?: string | null; localAddresses?: LocalAddresses | null };

/** 화면 언어로 보여 줄 주소 한 줄. 주소가 전혀 없으면 빈 문자열. */
export function addressForLanguage(place: AddressSource, language: LanguageCode): string {
  const address = place.address?.trim() ?? '';
  const addressEn = place.addressEn?.trim() || null;
  if (language === 'ko') return address || addressEn || '';

  const official = localAddressFor(place.localAddresses, language);
  if (official) return official;

  const split = address ? splitBusanAddress(address) : null;
  if (language === 'en') {
    if (addressEn) return addressEn;
    if (!split) return address;
    const gu = BUSAN_GU[split.gu];
    return [areaWord(split.rest, 'en'), gu.en, CITY.en].filter(Boolean).join(', ');
  }
  if (!split) return addressEn ?? address;

  const gu = BUSAN_GU[split.gu];
  // 영문 주소는 번지가 앞이다(「264 Haeundaehaebyeon-ro」). 일본어·중국어 주소는 큰 단위부터 적으므로 번지를 도로명 뒤로 —
  // 「海雲台区 Haeundaehaebyeon-ro 264」. 한국어 도로명 주소와 같은 순서라 표지판·건물 번호판과도 맞는다.
  const englishStreet = englishStreetPart(addressEn);
  const street = englishStreet ? englishStreet.replace(/^(\d[\d-]*),?\s+(.+)$/, '$2 $1') : areaWord(split.rest, language);
  if (language === 'ja') return [`${CITY.ja} ${gu.ja}`, street].filter(Boolean).join(' ');
  const head = language === 'zh-Hans' ? `${CITY.zhHans}${gu.zhHans}` : `${CITY.zhHant}${gu.zhHant}`;
  return [head, street].filter(Boolean).join(' ');
}

/**
 * 곁에 적을 한국어 원문 — 한국어 화면이 아니고, 위에서 보여 준 줄과 다를 때만.
 * 기사에게 보여 주고 지도 앱에 붙여 넣는 것은 한국어 주소다.
 */
export function koreanAddressLine(place: AddressSource, language: LanguageCode): string | null {
  const address = place.address?.trim();
  if (!address || language === 'ko') return null;
  return addressForLanguage(place, language) === address ? null : address;
}
