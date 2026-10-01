// 기록 지역 — 「장소 · 구」 묶음도 화면 언어로 — S15P21E201-1912(실기기 10/2 영어 화면).
import { regionText } from '@/social/districtNames';

const en = (_ko: string, english: string) => english;
const ko = (korean: string) => korean;

it('🔴 「장소 · 구」 묶음을 마디마다 바꾼다 — 장소는 기록의 장소 이름, 구는 로마자', () => {
  expect(regionText('감천문화마을 · 사하구', en, { ko: '감천문화마을', shown: 'Gamcheon Culture Village' })).toBe('Gamcheon Culture Village · Saha-gu');
  expect(regionText('감천문화마을 · 사하구', en)).toBe('감천문화마을 · Saha-gu');
});

it('한국어 화면·한 마디 값은 그대로 동작한다', () => {
  expect(regionText('감천문화마을 · 사하구', ko, { ko: '감천문화마을', shown: '감천문화마을' })).toBe('감천문화마을 · 사하구');
  expect(regionText('해운대구', en)).toBe('Haeundae-gu');
  expect(regionText('부산 해운대구', en)).toBe('부산 해운대구');
});
