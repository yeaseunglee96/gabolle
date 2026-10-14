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
  it('영문 주소가 없으면 도로명을 로마자로 — 한글을 섞지도, 도로명을 빼지도 않는다', () => {
    // 전에는 로마자로 옮길 방법이 없어 도로명을 빼고 「Jung-gu, Busan」만 적었다(S15P21E201-2043).
    // 한글 도로명을 로마자로 옮기게 된 뒤(S15P21E201-2056)로는 영어 화면과 같은 줄이다.
    expect(addressForLanguage({ address: '부산광역시 중구 신창로4가 일원' }, 'vi')).toBe('Sinchang-ro 4-ga area, Jung-gu, Busan');
    // 영어도 한글을 남기지 않는다(2026-10-14 갤럭시 S22 — 「신창로4가 area」가 보였다).
    expect(addressForLanguage({ address: '부산광역시 중구 신창로4가 일원' }, 'en')).toBe('Sinchang-ro 4-ga area, Jung-gu, Busan');
  });
  it('영문 주소가 있으면 그것', () => {
    expect(addressForLanguage({ address: '부산광역시 중구 신창로 1', addressEn: '1 Sinchang-ro, Jung-gu, Busan' }, 'vi')).toBe('1 Sinchang-ro, Jung-gu, Busan');
  });
});
