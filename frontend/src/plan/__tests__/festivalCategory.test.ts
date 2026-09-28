// 여행 갈래 「축제 & 행사」 — S15P21E201-1642.
//
// 🔴 이 시험이 지키는 것:
//    ① 갈래 질문에 FESTIVAL_EVENT 카드가 있고, 부제가 「여행 날짜에 열리는 축제만」임을 말한다(서버 규칙 — 기대를 맞춘다).
//    ② 장소 갈래로 FESTIVAL_EVENT 가 와도 낱말 그대로나 📍 가 나오지 않는다.
//    ③ 모든 갈래 카드에 그림이 있다 — 하나만 글자 카드가 되지 않는다.
//    둘러보기 사전의 FESTIVAL(「축제」)과는 다른 낱말이다 — 서버가 두 사전의 낱말이 겹치지 않게 지킨다.
import { PLACE_CATEGORY_LABELS } from '@/discovery/placeCategoryLabels';
import { categoryGlyph } from '@/plan/placePhotos';
import { CATEGORY_IMAGES, CATEGORY_OPTIONS } from '@/plan/planOptions';

describe('「축제 & 행사」 갈래', () => {
  it('🔴 갈래 질문에 있고, 여행 날짜에 여는 축제만 나온다고 미리 말한다', () => {
    const option = CATEGORY_OPTIONS.find(([code]) => code === 'FESTIVAL_EVENT');
    expect(option).toBeDefined();
    expect(option?.[1]).toContain('축제');
    expect(option?.[3]).toContain('여행 날짜');
  });

  it('🔴 장소 갈래로 와도 사람 말로 — 낱말 그대로나 📍 가 아니다', () => {
    expect(PLACE_CATEGORY_LABELS.FESTIVAL_EVENT).toEqual(['축제·행사', 'Festivals & events']);
    expect(categoryGlyph('FESTIVAL_EVENT')).not.toBe('📍');
  });

  it('모든 갈래 카드에 그림이 있다', () => {
    for (const [code] of CATEGORY_OPTIONS) expect(CATEGORY_IMAGES[code]).toBeDefined();
  });
});
