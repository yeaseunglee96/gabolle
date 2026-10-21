// 한글만 든 글에 lang="ko" — 일본어·중국어 화면에서도 낱말 중간에서 안 꺾이게(S15P21E201-1950).
import { Platform } from 'react-native';

import { webHangulLang } from '@/components/Text';

describe('webHangulLang', () => {
  const original = Platform.OS;
  beforeAll(() => { Object.defineProperty(Platform, 'OS', { get: () => 'web', configurable: true }); });
  afterAll(() => { Object.defineProperty(Platform, 'OS', { get: () => original, configurable: true }); });

  it('🔴 한글 장소 이름은 ko — 「해운대 블루라 / 인」으로 꺾이지 않게', () => {
    expect(webHangulLang('해운대 해수욕장 → 해운대 블루라인 해변열차')).toBe('ko');
    expect(webHangulLang(['광안리 ', '밀면집'])).toBe('ko');
  });

  it('🔴 일본어·중국어 문장에 한글이 섞이면 달지 않는다 — 그 문장은 그 나라 규칙으로 끊긴다', () => {
    expect(webHangulLang('광안리 밀면집 に行く')).toBeUndefined();
    expect(webHangulLang('去광안리')).toBeUndefined();
  });

  it('한글이 없으면 달지 않는다', () => {
    expect(webHangulLang('Gwangalli Beach')).toBeUndefined();
    expect(webHangulLang(12)).toBeUndefined();
  });
});
