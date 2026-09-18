// 여행 조건 모달을 띄울지 말지 (S15P21E201-1233 · 1244).
//
// 🔴 상태가 **넷**이다. 「나중에」와 「다시 묻지 않기」는 다른 답이라 셋으로는 못 담는다.
//
//   null(안 물어봄) → 로그인 후 홈 첫 진입에 모달
//   LATER           → 「일정 물어보기」를 누를 때마다 다시 (문구가 바뀐다)
//   NEVER · SAVED   → 더 안 묻는다. 마이페이지에서만 고친다
//
// 🔴 **2026-09-18 — 이 파일이 기기에만 적고 있었다. 그게 사고였다.**
//    모달에 다 적고 저장해도 값은 아무 데도 안 남았고(초안의 그 칸들은 새로고침마다
//    비워진다), 여기에는 「SAVED」만 남아 **다시 묻지도 않았다.** 빠져나갈 길이 없었다.
//    이제 정본은 서버이고([[travelConditions]] · S15P21E201-1231), 이 파일은 그 앞의
//    얇은 껍데기다.
//
// 🔴 못 읽으면 **안 물어본 것으로 보지 않는다.** 그러면 저장소가 막힌 기기에서 모달이
//    매번 뜬다. 모르면 「묻지 않는다」 쪽으로 기운다 — 귀찮게 하는 것이 더 나쁘다.
import { consumeAskAgain, loadTravelConditions, type TravelConditions } from '@/plan/travelConditions';

export type ConditionsPromptState = null | 'LATER' | 'NEVER' | 'SAVED';

/** 화면이 들고 있는 것 — 언제 물을지와, 이미 적어 둔 답. */
export type ConditionsPrompt = { state: ConditionsPromptState; conditions: TravelConditions | null };

/**
 * 🔴 로그인 안 한 사람에게는 안 묻는다. 이 조건은 계정에 붙는 것이라, 로그인 전에 받아
 * 두면 로그인한 순간 어느 쪽을 믿을지 정해야 한다.
 */
export async function loadConditionsPrompt(userId: string | null, accessToken: string | null): Promise<ConditionsPrompt> {
  if (!userId) return { state: 'NEVER', conditions: null };
  const record = await loadTravelConditions(userId, accessToken);
  // 마이페이지에서 「다시 묻기」를 켰으면 이번 한 번은 물어본다 — 적어 둔 답은 그대로 둔 채로.
  if (await consumeAskAgain(userId)) return { state: 'LATER', conditions: record.conditions };
  return { state: record.status, conditions: record.conditions };
}

// 🔴 **저장 함수는 여기 없다 — 모달이 직접 한다** (`ConditionsPromptModal`).
//    부르는 화면이 넷이라, 상태를 적는 일을 화면에 맡기면 그중 하나만 빠뜨려도 같은
//    사고가 다시 난다. 실제로 2026-09-18 에 그렇게 났다: 상태만 적히고 답은 안 적혔다.

/** 홈 첫 진입에 띄우나. 🔴 한 번도 안 물어본 사람에게만. */
export function shouldPromptOnHome(state: ConditionsPromptState): boolean {
  return state === null;
}

/** 「일정 물어보기」를 누를 때 띄우나. 🔴 「나중에」를 고른 사람에게만 다시 묻는다. */
export function shouldPromptBeforePlan(state: ConditionsPromptState): boolean {
  return state === null || state === 'LATER';
}

export { askConditionsAgain as resetConditionsPrompt } from '@/plan/travelConditions';
