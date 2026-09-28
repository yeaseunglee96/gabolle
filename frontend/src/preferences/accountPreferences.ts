// 계정에 기억된 답 — 세 질문(꾸러미 하나)과 취향(차원 넷)을 한 번에 읽는다
// //
import { getSpendProfile, SPEND_QUESTIONS, type SpendAnswers } from '@/onboarding/spendProfile';
import { countTasteAnswers, getTasteProfile, TASTE_KEYS, type TasteAnswers } from '@/preferences/tasteProfile';

export const PREFERENCES_KEY = ['me', 'preferences'] as const;

/**
 * 세 질문 + 취향. 답한 것만 들어 있다.
 * - loadFailed — 둘 중 하나라도 서버에서 못 읽었다(S15P21E201-1681). 「안 답했다」와 「못 읽었다」는 다른 말이다.
 * - spendSkipped — 세 질문을 건너뛰었다(서버 상태 SKIPPED). 한 번도 안 물어본 것(UNKNOWN)과 다르다.
 */
export type AccountPreferences = { spend: SpendAnswers; taste: TasteAnswers; loadFailed?: boolean; spendSkipped?: boolean };

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
  // 🔴 오류를 빈 답으로 바꾸되 «못 읽었다»는 표시는 남긴다 — 전에는 그냥 삼켜서 화면이 「처음에 건너뛰셨어요」라고 했다.
  //    서버는 한 번도 저장 안 한 사람에게 404 가 아니라 UNKNOWN 으로 200 을 준다(SpendProfileResponse) — 오류는 진짜 실패다.
  let loadFailed = false;
  const [spendProfile, taste] = await Promise.all([
    getSpendProfile(accessToken).catch(() => { loadFailed = true; return null; }),
    getTasteProfile(accessToken).catch((): TasteAnswers => { loadFailed = true; return {}; }),
  ]);
  return {
    spend: spendProfile?.answers ?? {},
    taste,
    ...(loadFailed ? { loadFailed: true } : {}),
    ...(spendProfile?.status === 'SKIPPED' ? { spendSkipped: true } : {}),
  };
}
