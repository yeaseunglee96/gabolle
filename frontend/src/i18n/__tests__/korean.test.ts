import { koreanToward } from '../korean';

describe('koreanToward — 「로」·「으로」', () => {
  it('받침 없음 → 로, 받침 있음 → 으로, ㄹ 받침 → 로', () => {
    expect(koreanToward('해운대')).toBe('로');
    expect(koreanToward('동백섬 · 누리마루')).toBe('로');
    expect(koreanToward('국제시장')).toBe('으로');
    expect(koreanToward('해운대 해수욕장')).toBe('으로');
    expect(koreanToward('태종대 자갈마당 방파제 끝')).toBe('으로');
    expect(koreanToward('전포카페거리 골목길')).toBe('로');
  });

  it('한글이 아니거나 끝에 기호가 붙어도 지어내지 않는다', () => {
    expect(koreanToward('Gwangalli')).toBe('로');
    expect(koreanToward('BIFF 광장')).toBe('으로');
    expect(koreanToward('국제시장 (본점)')).toBe('으로');
    expect(koreanToward('해운대  ')).toBe('로');
    expect(koreanToward('')).toBe('로');
  });
});
