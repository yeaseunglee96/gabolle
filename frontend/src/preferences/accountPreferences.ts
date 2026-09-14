// 계정에 기억된 답 여덟 — 세 질문(꾸러미 하나)과 취향 다섯(차원 다섯)을 한 번에 읽는다
// (S15P21E201-960).
//
// 🔴 이 파일이 있는 이유는 **캐시 하나를 같이 쓰기 위해서**다. 설정 화면의 「여행 취향」
// 줄이 「n / 8 답함」을 보여주고, 그 줄을 누르면 열리는 화면이 같은 값을 그린다. 각자
// 읽으면 같은 화면 안에서 두 번 부르고, 한쪽만 새로 읽혀 숫자와 목록이 어긋난다.
//
// 🔴 라우트 파일(app/…)에서 내보내지 않는다. Expo Router 는 화면 파일의 export 를 라우트
// 설정으로 읽으므로, 거기에 남의 화면이 쓰는 값을 얹으면 안 된다.
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
 * 🔴 둘을 나란히 부른다. 줄줄이 부르면 화면이 뜨는 시간이 두 배가 되는데, 두 답은 서로를
 * 필요로 하지 않는다.
 *
 * 🔴 한쪽이 실패해도 나머지는 보여준다. 취향 경로가 아직 없는 서버에서도 세 질문은
 * 고칠 수 있어야 한다 — 하나가 없다고 화면 전체를 못 쓰게 만들지 않는다.
 */
export async function loadAccountPreferences(accessToken: string | null): Promise<AccountPreferences> {
  const [spend, taste] = await Promise.all([
    getSpendProfile(accessToken).then((result) => result.answers ?? {}).catch((): SpendAnswers => ({})),
    getTasteProfile(accessToken).catch((): TasteAnswers => ({})),
  ]);
  return { spend, taste };
}
