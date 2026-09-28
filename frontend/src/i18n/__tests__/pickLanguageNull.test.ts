// pickLanguage 가 ko 에 null/undefined 가 와도 죽지 않는지 — S15P21E201-1725.
//
// 🔴 이 시험이 지키는 것은 「번역이 맞는가」가 아니라 «다섯 언어가 같은 입력에 대해 같은 방식으로
//    반응하는가»이다. 실기(웹, 2026-09-26)로 발견했다 — app/place/[id].tsx 가 주소 없는 장소에서
//    tx(place.address, …) 를 호출하는데 address 가 null 이면, 한국어·영어는 멀쩡한데 일본어·중국어
//    간체·번체 세 언어에서만 「Cannot read properties of null (reading 'replace')」로 화면이 죽었다.
//    ko/en 갈래는 text.ko 를 그대로 반환만 하고, ja/zh 갈래만 getTranslation·numericShape 로 그
//    글자를 만지기 때문이다 — 한국어로 확인해서는 이 종류의 결함을 못 잡는다.
import { pickLanguage } from '../pick';

describe('pickLanguage — ko 가 null/undefined 여도 죽지 않는다', () => {
  const languages = ['ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'] as const;

  it.each(languages)('%s — ko 가 null, en 이 있으면 en 을 돌려준다(ko·en 은 원래도 그렇다)', (language) => {
    expect(() => pickLanguage(language, { ko: null as unknown as string, en: 'Address unknown' })).not.toThrow();
  });

  it.each(languages)('%s — ko·en 이 둘 다 null 이어도 죽지 않는다(빈 문단을 그린다)', (language) => {
    expect(() => pickLanguage(language, { ko: null as unknown as string, en: null as unknown as string })).not.toThrow();
  });

  it('일본어·중국어에서 ko 가 null 이면 en 값으로 떨어진다 — ko·en 갈래와 같은 결과로 수렴한다', () => {
    const text = { ko: null as unknown as string, en: 'Address unknown' };
    expect(pickLanguage('ja', text)).toBe('Address unknown');
    expect(pickLanguage('zh-Hans', text)).toBe('Address unknown');
    expect(pickLanguage('zh-Hant', text)).toBe('Address unknown');
  });

  it('ko 가 실제 문자열이면 지금까지처럼 번역표를 그대로 탄다(회귀 없음)', () => {
    expect(pickLanguage('ja', { ko: '취소', en: 'Cancel' })).toBe('キャンセル');
  });

  it('숫자가 낀 문구도 ko 가 null 일 때 안전하다(모양 찾기 자체를 건너뛴다)', () => {
    expect(() => pickLanguage('zh-Hant', { ko: null as unknown as string, en: '3 of 5' })).not.toThrow();
  });
});
