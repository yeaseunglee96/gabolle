// 계정에 기억된 답 여덟 — 세 질문(꾸러미 하나)과 취향 다섯(차원 다섯)을 한 번에 읽는다
// //
import { getSpendProfile, type SpendAnswers } from '@/onboarding/spendProfile';
import { countTasteAnswers, getTasteProfile, type TasteAnswers } from '@/preferences/tasteProfile';

export const PREFERENCES_KEY = ['me', 'preferences'] as const;

/** 세 질문 + 취향 다섯. 답한 것만 들어 있다. */
export type AccountPreferences = { spend: SpendAnswers; taste: TasteAnswers };

export const EMPTY_PREFERENCES: AccountPreferences = { spend: {}, taste: {} };

/** 답 여덟 중 몇 개를 답했나. 설정 줄의 「n / 8」이 이 값이다. */
export const PREFERENCE_TOTAL = 8;

export function countAnswered(preferences: AccountPreferences): number {
  return Object.keys(preferences.spend).length + countTasteAnswers(preferences.taste);
}

/**
 * 둘을 나란히 부른다. 줄줄이 부르면 화면이 뜨는 시간이 두 배가 되는데, 두 답은 서로를
 * 필요로 하지 않는다.
 */
export async function loadAccountPreferences(accessToken: string | null): Promise<AccountPreferences> {
  const [spend, taste] = await Promise.all([
    getSpendProfile(accessToken).then((result) => result.answers ?? {}).catch((): SpendAnswers => ({})),
    getTasteProfile(accessToken).catch((): TasteAnswers => ({})),
  ]);
  return { spend, taste };
}
