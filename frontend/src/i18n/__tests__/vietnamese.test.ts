// 여섯째 언어 베트남어(vi) — S15P21E201-2003.
//
// 🔴 번역표(translations.ts)에 vi 가 빠지면 베트남어 화면에서 그 줄만 «조용히» 영어로 떨어진다.
//    한국어로 보면 끝까지 멀쩡하므로 여기서 기계가 지킨다. 값이 끼는 자리(%d·%s)는 개수와 순서가 어긋나면
//    값이 서로 바뀌어 들어가도 아무 오류가 없다 — 그것도 잰다.
import { pickLanguage } from '@/i18n/pick';
import { TRANSLATIONS } from '@/i18n/translations';
import { addressForLanguage } from '@/discovery/localAddress';
import { defaultCurrencyFor } from '@/field/exchangeRates';
import { directionForLanguage, directionForText } from '@/field/translate';

const placeholders = (text: string) => (text.match(/%[ds]|\n/g) ?? []).join(' ');

describe('번역표의 베트남어', () => {
  const entries = Object.entries(TRANSLATIONS);

  it('🔴 모든 줄에 vi 가 있다', () => {
    const missing = entries.filter(([, t]) => typeof t.vi !== 'string').map(([ko]) => ko);
    expect(missing).toEqual([]);
  });

  it('🔴 값 자리(%d·%s)와 줄바꿈의 개수·순서가 한국어 원문과 같다', () => {
    const broken = entries
      .filter(([ko, t]) => typeof t.vi === 'string' && placeholders(ko) !== placeholders(t.vi))
      .map(([ko, t]) => `${ko} → ${t.vi}`);
    expect(broken).toEqual([]);
  });

  it('브랜드는 다른 외국어 화면과 같이 GABOLLE 로 적는다 — 한글 「가볼래」가 남지 않는다', () => {
    const leaked = entries
      .filter(([, t]) => typeof t.vi === 'string' && t.vi.includes('가볼래') && !t.vi.includes('GABOLLE (가볼래)'))
      .map(([ko]) => ko);
    expect(leaked).toEqual([]);
  });
});

describe('베트남어 화면에서 고르기', () => {
  it('표에 있으면 베트남어다', () => {
    expect(pickLanguage('vi', { ko: '로그인', en: 'Sign in' })).toBe('Đăng nhập');
  });

  it('숫자가 낀 문구는 모양으로 찾는다', () => {
    expect(pickLanguage('vi', { ko: '사진 2/3', en: 'Photo 2/3' })).toBe('Ảnh 2/3');
  });

  it('표에 없으면 영어로 떨어진다 — 한국어로 떨어지지 않는다', () => {
    expect(pickLanguage('vi', { ko: '표에 없는 문구 S15P21E201-2003', en: 'Not in the table' })).toBe('Not in the table');
  });
});

describe('베트남어 데이터가 없는 자리는 영어로', () => {
  it('주소는 관광공사 번역 주소가 없으니 영문 주소다', () => {
    expect(addressForLanguage({ address: '부산 해운대구 해운대해변로 264', addressEn: '264 Haeundaehaebyeon-ro, Haeundae-gu, Busan' }, 'vi'))
      .toBe('264 Haeundaehaebyeon-ro, Haeundae-gu, Busan');
  });

  it('기본 통화는 동(VND)이다', () => {
    expect(defaultCurrencyFor('vi')).toBe('VND');
  });

  it('통역 방향은 베트남어 → 한국어다', () => {
    expect(directionForLanguage('vi')).toBe('VI_TO_KO');
  });

  it('🔴 성조 글자가 있는 라틴 문장은 영어가 아니라 베트남어로 본다', () => {
    expect(directionForText('Cho tôi xuống ở đây', 'VI_TO_KO')).toBe('VI_TO_KO');
    expect(directionForText('Where is the toilet?', 'VI_TO_KO')).toBe('EN_TO_KO');
  });
});
