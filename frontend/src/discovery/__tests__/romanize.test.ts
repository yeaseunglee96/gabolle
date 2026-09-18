import { placeNameForLanguage, romanizeKorean } from '../romanize';

// 명세 4절 — 장소의 영어 이름은 대부분 null 이고 앞으로도 채워질 계획이 없다.
// 영어 화면에서 한글만 덩그러니 두면 읽지도 못하고 물어보지도 못한다.
// 이것은 공식 표기가 아니라 「읽는 법」이다. 시험도 그 선을 지키는지를 본다.

describe('한글을 읽는 법으로 적기', () => {
  it('글자마다 소리를 옮긴다', () => {
    expect(romanizeKorean('부산')).toBe('Busan');
    expect(romanizeKorean('해운대')).toBe('Haeundae');
  });

  it('받침도 옮긴다', () => {
    expect(romanizeKorean('국수락')).toBe('Guksurak');
  });

  it('한글이 없으면 아무것도 만들지 않는다 — 빈 괄호를 만들지 않으려고', () => {
    expect(romanizeKorean('BIFF')).toBeNull();
    expect(romanizeKorean('123')).toBeNull();
  });
});

describe('화면에 보여줄 장소 이름', () => {
  it('한국어 화면은 지금까지와 같다', () => {
    expect(placeNameForLanguage('광안리', 'Gwangalli', 'ko')).toBe('광안리 (Gwangalli)');
    expect(placeNameForLanguage('국수락', null, 'ko')).toBe('국수락');
  });

  it('영어 이름이 있으면 그것을 앞에 둔다', () => {
    expect(placeNameForLanguage('광안리', 'Gwangalli', 'en')).toBe('Gwangalli (광안리)');
  });

  it('🔴 영어 이름이 없으면 한글을 지우지 않고 읽는 법을 덧붙인다', () => {
    const shown = placeNameForLanguage('국수락', null, 'en');
    expect(shown).toContain('국수락');
    expect(shown).toContain('Guksurak');
  });

  it('한글이 아닌 이름은 그대로 둔다', () => {
    expect(placeNameForLanguage('BIFF', null, 'en')).toBe('BIFF');
  });
});
