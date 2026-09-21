// 계정에 기억된 답 — 세 질문(꾸러미 하나)과 취향(차원 넷)을 한 번에 읽는다
// //
import { getSpendProfile, SPEND_QUESTIONS, type SpendAnswers } from '@/onboarding/spendProfile';
import { countTasteAnswers, getTasteProfile, TASTE_KEYS, type TasteAnswers } from '@/preferences/tasteProfile';

export const PREFERENCES_KEY = ['me', 'preferences'] as const;

/** 세 질문 + 취향. 답한 것만 들어 있다. */
export type AccountPreferences = { spend: SpendAnswers; taste: TasteAnswers };

export const EMPTY_PREFERENCES: AccountPreferences = { spend: {}, taste: {} };

/**
 * 설정 줄의 「n / N」의 N. 문항 수를 «세어» 정한다 — 숫자를 박아 두면 문항이 줄어도 그대로 남는다.
 *
 * 🔴 실제로 그랬다. S15P21E201-1423 이 관광지 문항을 빼서 취향이 다섯에서 넷이 됐는데
 *    여기는 8 로 남았다. 그래서 답할 수 있는 것을 «전부» 답해도 영원히 7 / 8 이었고,
 *    사용자는 못 찾은 한 문항을 계속 찾았다(팀원 실기 지적, APK 29).
 */
export const PREFERENCE_TOTAL = SPEND_QUESTIONS.length + TASTE_KEYS.length;

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
