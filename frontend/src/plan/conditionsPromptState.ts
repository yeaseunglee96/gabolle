// 여행 조건 모달을 띄울지 말지 (1244).
import { consumeAskAgain, loadTravelConditions, type TravelConditions } from '@/plan/travelConditions';

export type ConditionsPromptState = null | 'LATER' | 'NEVER' | 'SAVED';

/** 화면이 들고 있는 것 — 언제 물을지와, 이미 적어 둔 답. */
export type ConditionsPrompt = { state: ConditionsPromptState; conditions: TravelConditions | null };

/**
 * 로그인 안 한 사람에게는 안 묻는다. 이 조건은 계정에 붙는 것이라, 로그인 전에 받아
 * 두면 로그인한 순간 어느 쪽을 믿을지 정해야 한다.
 */
export async function loadConditionsPrompt(userId: string | null, accessToken: string | null): Promise<ConditionsPrompt> {
  if (!userId) return { state: 'NEVER', conditions: null };
  const record = await loadTravelConditions(userId, accessToken);
  // 마이페이지에서 「다시 묻기」를 켰으면 이번 한 번은 물어본다 — 적어 둔 답은 그대로 둔 채로.
  if (await consumeAskAgain(userId)) return { state: 'LATER', conditions: record.conditions };
  return { state: record.status, conditions: record.conditions };
}

/** 홈 첫 진입에 띄우나. 한 번도 안 물어본 사람에게만. */
export function shouldPromptOnHome(state: ConditionsPromptState): boolean {
  return state === null;
}

/** 「일정 물어보기」를 누를 때 띄우나. 「나중에」를 고른 사람에게만 다시 묻는다. */
export function shouldPromptBeforePlan(state: ConditionsPromptState): boolean {
  return state === null || state === 'LATER';
}

export { askConditionsAgain as resetConditionsPrompt } from '@/plan/travelConditions';
