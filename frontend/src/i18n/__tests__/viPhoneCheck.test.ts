// 10/10 휴대폰 점검 — 베트남어 금액·주소 (S15P21E201-2043).
import { pickLanguage } from '@/i18n/pick';
import { addressForLanguage } from '@/discovery/localAddress';

describe('베트남어 금액', () => {
  it('「3,0 vạn won」이 아니라 「30.000 won」', () => {
    expect(pickLanguage('vi', { ko: '3.0만원', en: '₩30,000' })).toBe('30.000 won');
    expect(pickLanguage('vi', { ko: '22.0만원', en: '₩219,500' })).toBe('219.500 won');
    expect(pickLanguage('vi', { ko: '5만원', en: '₩50,000' })).toBe('50.000 won');
  });
});

describe('베트남어 주소에 한글을 섞지 않는다', () => {
  it('영문 주소가 없으면 도로명을 빼고 구·시만', () => {
    expect(addressForLanguage({ address: '부산광역시 중구 신창로4가 일원' }, 'vi')).toBe('Jung-gu, Busan');
    // 영어는 전처럼 한글 도로명을 둔다(S15P21E201-1877 결정).
    expect(addressForLanguage({ address: '부산광역시 중구 신창로4가 일원' }, 'en')).toBe('신창로4가 area, Jung-gu, Busan');
  });
  it('영문 주소가 있으면 그것', () => {
    expect(addressForLanguage({ address: '부산광역시 중구 신창로 1', addressEn: '1 Sinchang-ro, Jung-gu, Busan' }, 'vi')).toBe('1 Sinchang-ro, Jung-gu, Busan');
  });
});
