import { oneLineFor } from '@/plan/placeOneLine';

const line = { ko: '광안대교가 정면으로 보이는 백사장', en: 'Sandy beach facing Gwangan Bridge', ja: '広安大橋を望む砂浜', 'zh-Hant': '正對廣安大橋的沙灘' };

describe('oneLineFor — 일정 카드의 장소 한 줄 소개(S15P21E201-2029)', () => {
  it('사용자 언어의 문장을 고른다', () => {
    expect(oneLineFor(line, 'ko')).toBe('광안대교가 정면으로 보이는 백사장');
    expect(oneLineFor(line, 'ja')).toBe('広安大橋を望む砂浜');
  });
  it('간체가 없으면 번체, 그다음 영어로 물러선다', () => {
    expect(oneLineFor(line, 'zh-Hans')).toBe('正對廣安大橋的沙灘');
    expect(oneLineFor({ ko: '백사장', en: 'Beach' }, 'zh-Hans')).toBe('Beach');
    expect(oneLineFor({ ko: '백사장', en: 'Beach' }, 'vi')).toBe('Beach');
  });
  it('🔴 외국어 화면에 한국어 문장을 대신 보이지 않는다', () => {
    expect(oneLineFor({ ko: '백사장' }, 'en')).toBeNull();
  });
  it('없거나 빈 값이면 null — 줄째 안 그린다', () => {
    expect(oneLineFor(undefined, 'ko')).toBeNull();
    expect(oneLineFor({ ko: '  ' }, 'ko')).toBeNull();
  });
});
