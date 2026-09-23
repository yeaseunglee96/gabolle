// 장소 이름 검색이 «이 검색어로는 원래 못 찾는» 경우를 가린다 — S15P21E201-1519.
//
// 서버 이름 검색(`GET /api/v1/places?query=`)은 한국어 이름(nameKo)과 영어 이름(nameEn)만
// 대조한다. 데이터에 일본어·중국어 이름이 없다. 그래서 2026-09-23 배포 서버 실측:
//
//   해운대해수욕장 1건 · Haeundae Beach 1건 · 海雲台海水浴場 0건 · 海云台海水浴场 0건
//   감천문화마을  1건 · Gamcheon       1건 · 甘川文化村       0건
//   광안리        3건 · Gwangalli      2건 · 広安里           0건 · 广安里 0건
//
// 여기까지는 서버·데이터의 한계다. 앱의 잘못은 그다음이었다 — 빈 결과를 「없어요」·「다르게
// 적어 보세요」로만 말해서, 일본어·중국어로 친 사람은 「그 장소가 없다」로 읽거나 어떻게 다르게
// 적어야 하는지 끝내 몰랐다. 택시 카드는 외국인 여행자가 쓰라고 있는 도구다.
//
// 🔴 **한자·가나로만 친 경우에만** 참이다. 한글이나 영문자가 한 글자라도 있으면 거짓 —
//    그 사람은 이미 찾아지는 글자를 쓰고 있고, 못 찾았다면 이유가 다른 데 있다.
//    한국어·영어로 친 사람이 보는 화면은 이 함수 때문에 달라지지 않는다.

/**
 * 두 화면(택시 카드 · 꼭 가고 싶은 장소)이 같이 쓰는 안내. `{ ko, en }` 모양이라 번역 검사가 본다.
 * 🔴 두 줄로 나눈다 — 한 줄로 이으면 폰 폭에서 「예:」가 줄 끝에 홀로 남고 예시가 다음 줄로
 *    넘어갔다(2026-09-23 실측).
 */
export const KOREAN_OR_ENGLISH_HINT = {
  ko: '장소 이름은 한국어나 영어로 찾을 수 있어요.\n예: 해운대해수욕장, Haeundae Beach',
  en: 'Place names can be searched in Korean or English.\ne.g. Haeundae Beach',
};

/** 한자(통합·확장 A·호환) · 히라가나 · 가타카나(반각 포함). */
const HAN_OR_KANA = /[぀-ヿㇰ-ㇿ㐀-䶿一-鿿豈-﫿ｦ-ﾟ]/;
/** 한글(음절·자모·호환 자모)과 영문자(전각 포함) — 서버가 대조할 수 있는 글자. */
const SEARCHABLE_LETTER = /[가-힣ᄀ-ᇿ㄰-㆏A-Za-zＡ-Ｚａ-ｚ]/;

/**
 * 결과가 비었을 때 「한국어나 영어로 찾아 주세요」를 말해야 하나.
 * 결과가 있으면 부르지 않는다 — 한자 이름으로 등록된 곳(예: 海雲台電影大道)은 한자로도 찾힌다.
 */
export function needsKoreanOrEnglishName(query: string | null | undefined): boolean {
  const text = (query ?? '').trim();
  if (!text) return false;
  return HAN_OR_KANA.test(text) && !SEARCHABLE_LETTER.test(text);
}
