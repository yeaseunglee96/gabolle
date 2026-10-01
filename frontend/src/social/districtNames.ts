// 기록의 동네 이름 — 영어판에서 부산 구·군 이름을 영어로 보인다(S15P21E201-1707, 조율 세션 결정 E).
//
// 🔴 글쓴이가 고른 글자와 **글자 그대로 똑같을 때만** 바꾼다. 「부산 해운대구」「해운대구 우동」처럼
//    조금이라도 다르면 그대로 둔다 — 떼어 내 맞춰 보다가 엉뚱한 이름을 붙이는 것보다 한국어가 낫다.
//    저장된 값은 건드리지 않는다. 보여 줄 때만 바꾸고, 걸러 보기는 원래 글자로 비교한다.

/** 부산광역시의 구 15 · 군 1 — 부산시가 쓰는 로마자 표기. */
export const BUSAN_DISTRICTS_EN: Readonly<Record<string, string>> = {
  중구: 'Jung-gu', 서구: 'Seo-gu', 동구: 'Dong-gu', 영도구: 'Yeongdo-gu', 부산진구: 'Busanjin-gu', 동래구: 'Dongnae-gu',
  남구: 'Nam-gu', 북구: 'Buk-gu', 해운대구: 'Haeundae-gu', 사하구: 'Saha-gu', 금정구: 'Geumjeong-gu', 강서구: 'Gangseo-gu',
  연제구: 'Yeonje-gu', 수영구: 'Suyeong-gu', 사상구: 'Sasang-gu', 기장군: 'Gijang-gun',
};

/** 동네 이름을 화면 언어로 — 한국어판은 받은 글자 그대로. */
export function regionText(region: string, tx: (ko: string, en: string) => string, place?: { ko: string; shown: string } | null): string {
  // 🔴 지역 고르기 칸은 「장소 · 구」를 « · » 로 묶어 저장한다(예: 「감천문화마을 · 사하구」). 통째로는 표에 없어
  //    영어 화면에 한국어가 그대로 남았다(실기기 10/2, S15P21E201-1912). 우리 묶음 기호로만 나눠 마디마다 바꾼다 —
  //    마디 안은 여전히 글자 그대로 같을 때만. 장소 마디는 그 기록의 장소 이름(화면 언어)과 같을 때 바꾼다.
  const one = (part: string) => (place && part === place.ko ? place.shown : tx(part, BUSAN_DISTRICTS_EN[part] ?? part));
  return region.includes(' · ') ? region.split(' · ').map(one).join(' · ') : one(region);
}
