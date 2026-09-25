// 가격대(PRICE_LEVEL)를 사람이 읽는 말로 — S15P21E201-1680.
//
// 🔴 이 시험이 지키는 것: 서버 값은 {"band":"MID","raw":"mid"} 모양이다(PlaceFeatureNdjsonReader). 화면이 raw(조사원이 쓴
//    영어 낱말)를 그대로 써서 장소 상세에 「low」·「mid」가, 축제 입장료 자리에 「high」가 떴다. band 로 고른다(조율 세션 결정).
//    모르는 등급은 안 보인다 — raw 로 물러서지 않는다.
import { formatFeatureSlot, type FeatureSlot } from '../places';

const tx = (ko: string) => ko;
const slot = (value: unknown, evidenceStatus = 'VERIFIED'): FeatureSlot => ({ value, evidenceStatus } as unknown as FeatureSlot);

describe('가격대 등급', () => {
  it('🔴 band 로 고른다 — 저렴한 편 · 보통 · 조금 비싼 편 · 비싼 편', () => {
    expect(formatFeatureSlot(slot({ band: 'LOW', raw: 'low' }), tx)).toBe('저렴한 편');
    expect(formatFeatureSlot(slot({ band: 'MID', raw: 'mid' }), tx)).toBe('보통');
    expect(formatFeatureSlot(slot({ band: 'MID_HIGH', raw: 'mid-high' }), tx)).toBe('조금 비싼 편');
    expect(formatFeatureSlot(slot({ band: 'HIGH', raw: 'high' }), tx)).toBe('비싼 편');
  });

  it('추정이면 「(추정)」을 붙인다 — 다른 칸과 같다', () => {
    expect(formatFeatureSlot(slot({ band: 'MID', raw: 'mid' }, 'ESTIMATED'), tx)).toBe('보통 (추정)');
  });

  it('🔴 모르는 등급이면 안 보인다 — 영어 raw 로 물러서지 않는다', () => {
    expect(formatFeatureSlot(slot({ band: 'LUXURY', raw: 'luxury' }), tx)).toBeNull();
  });

  it('band 가 없는 값(영업시간 등)은 전과 같다', () => {
    expect(formatFeatureSlot(slot('무료'), tx)).toBe('무료');
  });
});
