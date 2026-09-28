// 「이 검색어로는 원래 못 찾는다」를 가리는가 — S15P21E201-1519.
//
// 🔴 이 시험이 지키는 것 둘.
//    ① 일본어·중국어로 친 사람에게 길을 알려 준다 — 실측으로 0건이던 바로 그 검색어들
//    ② 한국어·영어로 친 사람의 화면은 안 바뀐다 — 한 글자라도 찾아지는 글자가 있으면 거짓
import { needsKoreanOrEnglishName } from '@/discovery/nameSearchHint';

describe('장소 이름 검색 — 한국어나 영어로 찾아 달라고 말해야 하나', () => {
  it('🔴 2026-09-23 배포 서버에서 0건이던 검색어는 전부 참이다', () => {
    // 앱이 입력칸 예시로 권하던 것들이다 — 택시 카드(일본어·중국어), 꼭 가고 싶은 장소(일본어)
    for (const query of ['海雲台海水浴場', '海云台海水浴场', '甘川文化村', '広安里', '广安里', '釜山駅', '釜山站']) {
      expect(needsKoreanOrEnglishName(query)).toBe(true);
    }
  });

  it('가타카나 · 히라가나 · 반각 가타카나도', () => {
    expect(needsKoreanOrEnglishName('チャガルチ市場')).toBe(true);
    expect(needsKoreanOrEnglishName('かんちょん')).toBe(true);
    expect(needsKoreanOrEnglishName('ﾁｬｶﾞﾙﾁ')).toBe(true);
  });

  it('🔴 한국어·영어로 친 사람에게는 안 붙는다 — 그 화면은 지금과 같다', () => {
    for (const query of ['해운대해수욕장', '감천문화마을', 'Haeundae Beach', 'Gamcheon', 'gwangalli', 'Ｈａｅｕｎｄａｅ']) {
      expect(needsKoreanOrEnglishName(query)).toBe(false);
    }
  });

  it('🔴 한글이나 영문자가 한 글자라도 섞이면 안 붙는다 — 이미 찾아지는 글자를 쓰고 있다', () => {
    expect(needsKoreanOrEnglishName('海雲台 Beach')).toBe(false);
    expect(needsKoreanOrEnglishName('해운대 海水浴場')).toBe(false);
  });

  it('비었거나 글자가 아니면 안 붙는다', () => {
    for (const query of ['', '   ', null, undefined, '123', '!!']) {
      expect(needsKoreanOrEnglishName(query)).toBe(false);
    }
  });
});
