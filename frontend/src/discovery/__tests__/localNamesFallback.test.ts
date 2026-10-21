// 장소 번역 이름 대체(S15P21E201-1945) — 그 언어에 없으면 같은 한자를 읽는 언어로.
import { localNameFor } from '../localNames';

describe('localNameFor 대체', () => {
  it('일본어에 없으면 번체 — 간체는 일본어로 못 읽어 쓰지 않는다', () => {
    expect(localNameFor({ 'zh-Hant': '廣安里海水浴場', 'zh-Hans': '广安里海水浴场' }, 'ja')).toBe('廣安里海水浴場');
    expect(localNameFor({ 'zh-Hans': '金井山' }, 'ja')).toBeNull();
    expect(localNameFor({ ja: '広安里海水浴場', 'zh-Hant': '廣安里海水浴場' }, 'ja')).toBe('広安里海水浴場');
  });

  it('간체 ↔ 번체', () => {
    expect(localNameFor({ 'zh-Hans': '金井山' }, 'zh-Hant')).toBe('金井山');
    expect(localNameFor({ 'zh-Hant': '廣安里海水浴場' }, 'zh-Hans')).toBe('廣安里海水浴場');
  });

  it('한국어·영어 화면은 언제나 null', () => {
    expect(localNameFor({ ja: '広安里' }, 'ko')).toBeNull();
    expect(localNameFor({ ja: '広安里' }, 'en')).toBeNull();
  });
});
