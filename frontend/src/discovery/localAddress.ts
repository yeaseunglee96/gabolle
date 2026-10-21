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
//
// 🔴 영어·베트남어 화면의 한글 도로명·동 이름은 로마자로 바꾼다 — S15P21E201-2056.
//    위 2번은 영어 화면에서 「한글 도로명을 그대로」 뒀다(S15P21E201-1877 — 도로명판에 한글이 있다). 그런데 영문 주소가
//    없는 장소가 「신창로4가 area, Jung-gu, Busan」처럼 한 줄에 한국어와 영어가 섞여 나왔다(갤럭시 S22 실기기, 2026-10-14).
//    도로명판 글자가 필요한 사람에게는 한글 원문이 곁줄(koreanAddressLine)로 계속 나가므로, 이 줄은 로마자로 적는다.
//    일본어·중국어 화면은 전과 같다(위 2번) — 거기는 가나·한자로 옮길 공식 표기가 없어 한글을 그대로 둔다.
import { romanizeKorean } from '@/discovery/romanize';
import type { LanguageCode } from '@/i18n/languages';

/** 키는 앱의 언어 코드. localNames 와 같다. 없는 언어는 빠져 온다. */
export type LocalAddresses = Partial<Record<'ja' | 'zh-Hans' | 'zh-Hant', string>>;

type GuName = { en: string; ja: string; zhHans: string; zhHant: string };

// 부산의 구·군 — 영문은 로마자 표기법(도로명주소 영문 표기와 같다), 한자는 관광공사 일문·중문 주소에 쓰인 표기.
export const BUSAN_GU: Readonly<Record<string, GuName>> = {
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

// ── 한글 도로명·동 이름 → 로마자 (영어·베트남어 화면) — S15P21E201-2056 ─────────────────────────────────────
//
// 도로명 주소의 영문 표기 관례를 따른다. 이름과 끝말(로·길·동…)은 하이픈으로 잇고, 번지·구역 번호는 띄어 쓴다.
//
//   신창로4가       → Sinchang-ro 4-ga           해운대해변로   → Haeundaehaebyeon-ro
//   해양로301번길   → Haeyang-ro 301beon-gil     우동 1394      → U-dong 1394
//   중앙대로 26번길 → Jungang-daero 26beon-gil   기장읍 용궁길  → Gijang-eup Yonggung-gil
//
// 글자 하나하나는 기존 romanizeKorean(한글을 소리 나는 대로 로마자로 적는 함수)이 옮긴다. 이것은 공식 표기가 아니라
// 「읽는 법」이다(romanize.ts 의 약속과 같다) — 소리가 이어질 때 바뀌는 발음(달맞이 → 달마지)까지는 따르지 않는다.

/** 이름 뒤에 붙는 끝말. 「대로」가 「로」보다 앞이어야 「중앙대로」가 「Jungangdae-ro」가 되지 않는다. */
const NAME_SUFFIXES = ['대로', '로', '길', '가', '동', '읍', '면', '리'] as const;

/** 「해운대로」는 «해운 + 대로»가 아니라 «해운대 + 로»다(Haeundae-ro). 이름이 「대」로 끝나는 길을 여기 적는다. */
const DAE_PLACE_NAMES = ['해운대'] as const;

/**
 * 한글 덩어리 하나(띄어쓰기·숫자·기호가 없는 연속된 한글)를 옮긴다.
 * @param glued 바로 앞이 숫자나 영문자라 앞말에 붙는 자리 — 「4가」의 「가」, 「301번길」의 「번길」. 대문자로 시작하지 않는다.
 */
function romanizeHangulRun(run: string, glued: boolean): string {
  const lowerIfGlued = (word: string) => (glued ? word.toLowerCase() : word);
  let suffix: string | undefined = NAME_SUFFIXES.find((candidate) => run.endsWith(candidate));
  if (suffix === '대로' && DAE_PLACE_NAMES.some((name) => run.endsWith(`${name}로`))) suffix = '로';
  if (suffix) {
    const stem = run.slice(0, -suffix.length);
    const tail = (romanizeKorean(suffix) ?? '').toLowerCase();
    if (stem) return `${lowerIfGlued(romanizeKorean(stem) ?? stem)}-${tail}`;
    // 이름 없이 끝말뿐인데 숫자·영문자 뒤다 — 「신창로4가」의 「가」, 「마린시티1로」의 「로」, 「B동」의 「동」
    if (glued) return `-${tail}`;
  }
  return lowerIfGlued(romanizeKorean(run) ?? run);
}

/** 띄어쓰기로 나눈 주소 한 덩어리. 숫자(번지 29-46 · 「4가」의 4)는 그대로 두고 그 사이의 한글만 옮긴다. */
function romanizeAddressToken(token: string): string {
  let out = '';
  let afterRoman = false; // 바로 앞 조각이 우리가 로마자로 옮긴 한글로 끝났나
  token.split(/(\d+(?:-\d+)*)/).forEach((piece, index) => {
    if (!piece) return;
    if (index % 2 === 1) {
      // 숫자 조각 — 로마자 이름 뒤면 한 칸 띄운다(「Haeyang-ro 301」). 처음이거나 괄호 뒤면 붙인다.
      out += afterRoman ? ` ${piece}` : piece;
      afterRoman = false;
      return;
    }
    let hadHangul = false;
    out += piece.replace(/[가-힣]+/g, (run, offset: number) => {
      hadHangul = true;
      const before = offset === 0 ? out.slice(-1) : piece[offset - 1];
      return romanizeHangulRun(run, /[0-9A-Za-z]/.test(before));
    });
    afterRoman = hadHangul && /[A-Za-z]$/.test(out);
  });
  return out;
}

/** 시·구 뒤의 나머지 주소(「신창로4가 일원」·「해운대해변로 264」·「우동 1394」)를 로마자로. 「일원·일대」는 「area」. */
function romanizeAddressPart(rest: string): string {
  const area = /\s*(일원|일대)$/.test(rest);
  const roman = rest.replace(/\s*(일원|일대)$/, '').split(' ').map(romanizeAddressToken).join(' ').trim();
  return `${roman}${area ? AREA_WORD.en : ''}`.trim();
}

const HANGUL = /[가-힣ㄱ-ㆎ]/;

export type AddressSource = { address?: string | null; addressEn?: string | null; localAddresses?: LocalAddresses | null };

/** 화면 언어로 보여 줄 주소 한 줄. 주소가 전혀 없으면 빈 문자열. */
export function addressForLanguage(place: AddressSource, language: LanguageCode): string {
  const address = place.address?.trim() ?? '';
  const addressEn = place.addressEn?.trim() || null;
  if (language === 'ko') return address || addressEn || '';

  const official = localAddressFor(place.localAddresses, language);
  if (official) return official;

  const split = address ? splitBusanAddress(address) : null;
  // 베트남어(vi)는 관광공사 번역 주소가 없다 — 영어 주소로 보여 준다(S15P21E201-2003).
  if (language === 'en' || language === 'vi') {
    if (addressEn) return addressEn;
    // 🔴 영어·베트남어 화면에는 한글 조각을 섞지 않는다 — 「신창로4가 area, Jung-gu, Busan」이 나왔다
    //    (베트남어 10/10 점검 S15P21E201-2043, 영어 10/14 갤럭시 S22 S15P21E201-2056).
    //    도로명·동 이름은 로마자로 적는다(「Sinchang-ro 4-ga area, Jung-gu, Busan」). 한국어 원문은 koreanAddressLine 이 곁에 적는다.
    //    전에는 영어 화면이 한글 도로명을 뒀고(S15P21E201-1877) 베트남어 화면은 도로명을 뺐다(S15P21E201-2043) —
    //    로마자로 옮길 방법이 없어서였다. 이제 둘 다 같은 줄이다.
    const dropHangul = language === 'vi';
    if (!split) return dropHangul && HANGUL.test(address) ? '' : address;
    const gu = BUSAN_GU[split.gu];
    const rest = romanizeAddressPart(split.rest);
    // 로마자로 못 옮긴 한글(낱자 따위)이 남으면 베트남어 화면에서는 그 조각을 뺀다 — 2043 의 약속.
    return [dropHangul && HANGUL.test(rest) ? '' : rest, gu.en, CITY.en].filter(Boolean).join(', ');
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
