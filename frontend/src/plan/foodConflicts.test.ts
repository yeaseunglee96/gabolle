import { conflictingFoodCode } from './foodConflicts';

describe('conflictingFoodCode', () => {
  it('알레르기가 겹치면 그 알레르기 코드를 돌려준다', () => {
    const result = conflictingFoodCode('SEAFOOD', ['SHELLFISH_CRUSTACEAN'], []);
    expect(result).toEqual({ code: 'SHELLFISH_CRUSTACEAN', kind: 'allergy' });
  });

  it('식단 제한이 겹치면 식단 코드를 돌려준다 — 알레르기가 없을 때만', () => {
    const result = conflictingFoodCode('PORK_SOUP', [], ['HALAL']);
    expect(result).toEqual({ code: 'HALAL', kind: 'diet' });
  });

  it('알레르기가 식단보다 먼저 걸린다 — 둘 다 겹쳐도 알레르기만 돌려준다', () => {
    const result = conflictingFoodCode('MILMYEON', ['WHEAT'], ['GLUTEN_FREE']);
    expect(result).toEqual({ code: 'WHEAT', kind: 'allergy' });
  });

  it('겹치는 게 없으면 null이다', () => {
    expect(conflictingFoodCode('SEAFOOD', ['MILK_DAIRY'], ['VEGAN'])).toBeNull();
  });

  it('재료가 알려지지 않은 음식(MARKET)은 절대 충돌하지 않는다 — 지어내지 않는 설계', () => {
    expect(conflictingFoodCode('MARKET', ['SHELLFISH_CRUSTACEAN'], ['HALAL'])).toBeNull();
  });
});
