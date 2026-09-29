// 기록에 달린 장소 이름 — 서버가 실어 보내는 영어 이름(nameEn)을 쓴다(사용자 지적 2026-09-30).
import { storyPlaceName } from '../stories';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;
// 일본어: 번역표에 있으면 그것, 없으면 tx 가 영어로 떨어진다.
const ja = (k: string, e: string) => (k === '감천문화마을' ? '甘川文化村' : e);

describe('storyPlaceName', () => {
  it('🔴 일본어·중국어는 서버의 관광공사 번역 이름이 가장 먼저(S15P21E201-1860)', () => {
    const place = { name: '국제시장', nameEn: 'Gukje Market', localNames: { ja: '国際市場', 'zh-Hant': '國際市場' } };
    expect(storyPlaceName(place, ja, 'ja')).toBe('国際市場');
    expect(storyPlaceName(place, (_k: string, e: string) => e, 'zh-Hant')).toBe('國際市場');
    // 간체 이름이 없으면 예전 길(번역표 → 영어)
    expect(storyPlaceName(place, (_k: string, e: string) => e, 'zh-Hans')).toBe('Gukje Market');
  });
  it('한국어는 그대로', () => {
    expect(storyPlaceName({ name: '감천문화마을', nameEn: 'Gamcheon Culture Village' }, ko, 'ko')).toBe('감천문화마을');
  });
  it('🔴 영어는 서버의 영어 이름', () => {
    expect(storyPlaceName({ name: '감천문화마을', nameEn: 'Gamcheon Culture Village' }, en, 'en')).toBe('Gamcheon Culture Village');
  });
  it('🔴 일본어는 번역표 → 영어 이름 → 한국어 순', () => {
    expect(storyPlaceName({ name: '감천문화마을', nameEn: 'Gamcheon Culture Village' }, ja, 'ja')).toBe('甘川文化村');
    expect(storyPlaceName({ name: '황령산', nameEn: 'Hwangnyeongsan' }, ja, 'ja')).toBe('Hwangnyeongsan');
    expect(storyPlaceName({ name: '넉아웃' }, ja, 'ja')).toBe('넉아웃');
  });
  it('빈 영어 이름은 없는 것으로', () => {
    expect(storyPlaceName({ name: '모모스', nameEn: '  ' }, en, 'en')).toBe('모모스');
  });
});
