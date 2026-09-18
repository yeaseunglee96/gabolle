// 여행 조건 모달을 띄울지 말지 (S15P21E201-1233).
//
// 🔴 상태가 **넷**이다. 「나중에」와 「다시 묻지 않기」는 다른 답이라 셋으로는 못 담는다.
//
//   null(안 물어봄) → 로그인 후 홈 첫 진입에 모달
//   LATER           → 「일정 물어보기」를 누를 때마다 다시 (문구가 바뀐다)
//   NEVER · SAVED   → 더 안 묻는다. 마이페이지에서만 고친다
//
// 🔴 서버(`/api/v1/me/preferences/constraints`, S15P21E201-1231)가 정본이고, 이 파일은
//    **그 길이 아직 배포 안 된 동안** 기기에 적어 두는 임시 자리다. 서버가 열리면
//    읽는 곳을 바꾸면 되고, 이 값은 그때 한 번 올려 보내면 된다.
//
// 🔴 못 읽으면 **안 물어본 것으로 보지 않는다.** 그러면 저장소가 막힌 기기에서 모달이
//    매번 뜬다. 모르면 「묻지 않는다」 쪽으로 기운다 — 귀찮게 하는 것이 더 나쁘다.
import AsyncStorage from '@react-native-async-storage/async-storage';

export type ConditionsPromptState = null | 'LATER' | 'NEVER' | 'SAVED';

const KEY = 'gabolle.conditions-prompt';

export async function loadConditionsPromptState(userId: string | null): Promise<ConditionsPromptState> {
  if (!userId) return 'NEVER';
  try {
    const raw = await AsyncStorage.getItem(`${KEY}:${userId}`);
    return raw === 'LATER' || raw === 'NEVER' || raw === 'SAVED' ? raw : null;
  } catch {
    return 'NEVER';
  }
}

export async function saveConditionsPromptState(userId: string | null, next: Exclude<ConditionsPromptState, null>): Promise<void> {
  if (!userId) return;
  try {
    await AsyncStorage.setItem(`${KEY}:${userId}`, next);
  } catch {
    // 못 적어도 이번 실행 동안의 선택은 화면 상태로 지켜진다.
  }
}

/** 홈 첫 진입에 띄우나. 🔴 한 번도 안 물어본 사람에게만. */
export function shouldPromptOnHome(state: ConditionsPromptState): boolean {
  return state === null;
}

/** 「일정 물어보기」를 누를 때 띄우나. 🔴 「나중에」를 고른 사람에게만 다시 묻는다. */
export function shouldPromptBeforePlan(state: ConditionsPromptState): boolean {
  return state === null || state === 'LATER';
}

/**
 * 「다시 묻기」 — 마이페이지에서 켠다.
 *
 * 🔴 「다시 묻지 않기」를 누른 사람이 마음을 바꿀 길이 여기 말고는 없다. 되돌릴 수 없는
 * 선택으로 두면, 조건을 안 적은 채로 굳는다.
 */
export async function resetConditionsPrompt(userId: string | null): Promise<void> {
  if (!userId) return;
  try {
    await AsyncStorage.removeItem(`${KEY}:${userId}`);
  } catch {
    // 못 지워도 화면은 그대로 둔다 — 「켰다」고 거짓말하지 않는다.
  }
}
