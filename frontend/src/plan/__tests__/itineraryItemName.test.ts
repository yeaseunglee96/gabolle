import { itemOtherName } from '@/plan/itineraryItemName';
import { stopNameForLanguage } from '@/discovery/romanize';

// 일정 응답이 실은 외국어 이름을 쓴다 — 사진 묶음에 없는 곳도 다른 언어에서 한국어만 보이지 않게(S15P21E201-1938).
describe('itemOtherName', () => {
  const item = { nameEn: 'Paskucci Gwangalli', localNames: { ja: 'パスクチ 広安里店', 'zh-Hans': '帕斯库奇 广安里店' } };

  it('일본어·중국어는 일정 항목의 번역 이름을 고른다', () => {
    expect(itemOtherName(item, null, 'ja')).toBe('パスクチ 広安里店');
    expect(itemOtherName(item, null, 'zh-Hans')).toBe('帕斯库奇 广安里店');
    expect(stopNameForLanguage('파스쿠치 광안리점', itemOtherName(item, null, 'ja'), 'ja')).toBe('パスクチ 広安里店 (파스쿠치 광안리점)');
  });

  it('번역 이름이 없으면 같은 한자를 읽는 언어로(번체 ← 간체, S15P21E201-1945), 영어 화면은 영어', () => {
    expect(itemOtherName(item, null, 'zh-Hant')).toBe('帕斯库奇 广安里店');
    expect(itemOtherName(item, null, 'en')).toBe('Paskucci Gwangalli');
  });

  it('옛 서버(이름 칸 없음)는 따로 받은 장소 정보로 물러선다', () => {
    expect(itemOtherName({}, { nameEn: 'Haeundae Beach', localNames: { ja: '海雲台海水浴場' } }, 'ja')).toBe('海雲台海水浴場');
    expect(itemOtherName({}, null, 'ja')).toBeNull();
  });
});
