// 통역 — 입력 글자로 원문 언어를 고른다(S15P21E201-1904, 팀원 보고).
import { directionForText } from '@/field/translate';

describe('입력 글자로 원문 언어 고르기', () => {
  it('🔴 일본어 화면에서 중국어(간체)로 쳐도 중국어로 번역한다', () => {
    expect(directionForText('这个多少钱', 'JA_TO_KO')).toBe('ZH_HANS_TO_KO');
  });
  it('🔴 중국어 간체 화면에서 번체로 치면 번체로 번역한다', () => {
    expect(directionForText('這個多少錢', 'ZH_HANS_TO_KO')).toBe('ZH_HANT_TO_KO');
  });
  it('가나가 있으면 일본어', () => {
    expect(directionForText('これはいくらですか', 'ZH_HANS_TO_KO')).toBe('JA_TO_KO');
  });
  it('라틴 글자는 영어, 한글뿐이면 번역하지 않는다', () => {
    expect(directionForText('how much', 'JA_TO_KO')).toBe('EN_TO_KO');
    expect(directionForText('얼마예요', 'JA_TO_KO')).toBeNull();
  });
  it('번체 화면에서 공통 한자만 치면 번체 그대로', () => {
    expect(directionForText('多少', 'ZH_HANT_TO_KO')).toBe('ZH_HANT_TO_KO');
  });
});
