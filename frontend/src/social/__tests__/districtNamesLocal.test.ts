// 일본어·중국어 화면의 구 이름(S15P21E201-1945) — 전에는 번역표에 없어 「Saha-gu」(영어)로 나왔다.
// 🔴 번역표와 주소 표(localAddress.ts)가 같은 한자를 쓰는지 본다 — 한쪽만 고치면 같은 화면에 沙下区 와 沙下區 가 섞인다.
import { BUSAN_GU } from '@/discovery/localAddress';
import { TRANSLATIONS } from '@/i18n/translations';
import { BUSAN_DISTRICTS_EN } from '../districtNames';

describe('구 이름 번역', () => {
  it.each(Object.keys(BUSAN_DISTRICTS_EN))('%s — 번역표가 주소 표와 같은 한자', (gu) => {
    const row = (TRANSLATIONS as Record<string, { ja: string; zhHans: string; zhHant: string }>)[gu];
    expect(row).toBeDefined();
    expect(row).toEqual({ ja: BUSAN_GU[gu].ja, zhHans: BUSAN_GU[gu].zhHans, zhHant: BUSAN_GU[gu].zhHant });
  });
});
